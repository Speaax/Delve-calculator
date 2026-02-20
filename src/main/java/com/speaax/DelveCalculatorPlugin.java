package com.speaax;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.WorldType;
import net.runelite.api.events.*;
import net.runelite.api.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;
import com.google.inject.Provides;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
	name = "Delve Calculator",
	description = "Calculates expected unique drops",
	tags = {"delve", "calculator", "drops", "rates", "doom"}
)
public class DelveCalculatorPlugin extends Plugin
{
	@Inject private Client client;
	@Inject @Getter private ClientThread clientThread;
	@Inject private DelveCalculatorConfig config;
	@Inject private Gson gson;
	@Inject private ClientToolbar clientToolbar;
	@Getter @Inject private ItemManager itemManager;

	private DelveCalculatorPanel panel;
	private NavigationButton navButton;
	private Timer sessionTimeoutTimer;
	private boolean panelVisible = false;
	private boolean inDelveRegion = false;

	private final Map<String, DelveCalculatorData.DelveProfile> sessionProfiles = new HashMap<>();

	public DelveCalculatorData.DelveProfile getSessionProfile(String mode)
	{
		return sessionProfiles.computeIfAbsent(mode, k -> new DelveCalculatorData.DelveProfile("Session", false));
	}

	private static final int WIDGET_GROUP_SCOREBOARD = 920;
	private static final int WIDGET_GROUP_COLLECTION_LOG = 621;
	private static final int WIDGET_COLLECTION_LOG_ITEMS = 37;

	// Loot Interface Constants
	private static final int WIDGET_GROUP_LOOT = 919;
	private static final int WIDGET_LOOT_CLAIM_HEADER = 8;
	private static final int WIDGET_LOOT_CONTENTS = 19;

	private static final int[] DELVE_REGION_IDS = {5269, 13668, 14180};
	private static final Map<Integer, DropRates> DROP_RATES_BY_LEVEL = new HashMap<>();
	private static final Map<String, Integer> UNIQUE_DROPS = new HashMap<>();

	static {
		DROP_RATES_BY_LEVEL.put(2, new DropRates(1.0/2500, 1.0/2500, 0, 0, 0));
		DROP_RATES_BY_LEVEL.put(3, new DropRates(1.0/1000, 1.0/2000, 1.0/2000, 0, 0));
		DROP_RATES_BY_LEVEL.put(4, new DropRates(1.0/450, 1.0/1350, 1.0/1350, 1.0/1350, 0));
		DROP_RATES_BY_LEVEL.put(5, new DropRates(1.0/270, 1.0/810, 1.0/810, 1.0/810, 0));
		DROP_RATES_BY_LEVEL.put(6, new DropRates(1.0/255, 1.0/765, 1.0/765, 1.0/765, 1.0/1000));
		DROP_RATES_BY_LEVEL.put(7, new DropRates(1.0/240, 1.0/720, 1.0/720, 1.0/720, 1.0/750));
		DROP_RATES_BY_LEVEL.put(8, new DropRates(1.0/210, 1.0/630, 1.0/630, 1.0/630, 1.0/500));
		DROP_RATES_BY_LEVEL.put(9, new DropRates(1.0/180, 1.0/540, 1.0/540, 1.0/540, 1.0/250));

		UNIQUE_DROPS.put("Mokhaiotl cloth", ItemID.MOKHAIOTL_CLOTH);
		UNIQUE_DROPS.put("Eye of ayak (uncharged)", ItemID.EYE_OF_AYAK_UNCHARGED);
		UNIQUE_DROPS.put("Avernic treads", ItemID.AVERNIC_TREADS);
		UNIQUE_DROPS.put("Dom", ItemID.DOM);
	}

	@Provides
	DelveCalculatorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DelveCalculatorConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		panel = new DelveCalculatorPanel(this, config, gson);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

		navButton = NavigationButton.builder()
				.tooltip("Delve Calculator")
				.icon(icon)
				.priority(5)
				.panel(panel)
				.build();

		sessionTimeoutTimer = new Timer(0, e -> updatePanelVisibility());
		sessionTimeoutTimer.setRepeats(false);

		clientToolbar.addNavigation(navButton);
		panelVisible = true;
		updatePanelVisibility();
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (sessionTimeoutTimer != null) {
			sessionTimeoutTimer.stop();
		}

		if (navButton != null) {
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}

		if (panel != null) {
			panel = null;
		}
	}

	public String getCurrentGameMode()
	{
		if (client == null) return "STANDARD";
		EnumSet<WorldType> types = client.getWorldType();
		if (types == null || types.isEmpty()) return "STANDARD";

		// Sort and join all active world types to create a unique future-proof key
		return types.stream()
				.map(Enum::name)
				.sorted()
				.collect(Collectors.joining("_"));
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM)
		{
			String message = Text.removeTags(event.getMessage());
			if (message.contains("Delve level:") && message.contains("duration:"))
			{
				handleDelveCompletion(message);
			}
		}
		
		if (event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			String message = event.getMessage();
			if (message.equals("You have a funny feeling like you're being followed.") ||
				message.equals("You feel something weird sneaking into your backpack.") ||
				message.equals("You have a funny feeling like you would have been followed..."))
			{
				if (isInDelveRegion())
				{
					handleDropLogic(getCurrentGameMode(), ItemID.DOM);
				}
			}
		}
	}

	private void handleDelveCompletion(String message)
	{
		String[] parts = message.split(" ");
		for (int i = 0; i < parts.length; i++)
		{
			if (parts[i].equals("level:") && i + 1 < parts.length)
			{
				String levelText = parts[i + 1];
				String gameMode = getCurrentGameMode();
				if (levelText.equals("8+"))
				{
					if (panel != null) panel.incrementWavesPast8(gameMode);
					break;
				}
				else
				{
					try
					{
						int level = Integer.parseInt(levelText);
						if (level >= 1 && level <= 8)
						{
							if (panel != null) panel.incrementFloorKills(gameMode, level);
							break;
						}
					}
					catch (NumberFormatException ignored) {}
				}
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("delvecalculator"))
		{
			if ("showInRegion".equals(event.getKey()) || "autoOpenInRegion".equals(event.getKey()))
			{
				clientThread.invokeLater(() -> {
					inDelveRegion = (config.onlyShowInRegion() || config.autoOpenInRegion()) && isInDelveRegion();
					updatePanelVisibility();
				});
			}

			if ("regionTimeout".equals(event.getKey()))
			{
				if (sessionTimeoutTimer != null && sessionTimeoutTimer.isRunning())
				{
					sessionTimeoutTimer.setInitialDelay((int) TimeUnit.MINUTES.toMillis(config.regionTimeout()));
					sessionTimeoutTimer.restart();
				}
			}
			updatePanelVisibility();
			if (panel != null) panel.updateAllUI();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == WIDGET_GROUP_SCOREBOARD)
		{
			clientThread.invokeLater(this::updateKillCounts);
			if (config.autoOpenOnScoreboard()) togglePanel(true, true);
		}
		if (event.getGroupId() == WIDGET_GROUP_LOOT)
		{
			clientThread.invokeLater(this::scanLootInterface);
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		// COLLECTION_LOG_SEND_CATEGORY (1212) or COLLECTION_DRAW_LIST
		if (event.getScriptId() == 1212 || event.getScriptId() == ScriptID.COLLECTION_DRAW_LIST)
		{
			clientThread.invokeLater(this::syncCollectionLog);
		}
	}


	private void syncCollectionLog()
	{
		Widget itemsContainer = client.getWidget(WIDGET_GROUP_COLLECTION_LOG, WIDGET_COLLECTION_LOG_ITEMS);
		if (itemsContainer == null || itemsContainer.isHidden()) return;

		Widget[] children = itemsContainer.getChildren();
		if (children == null) return;

		Map<Integer, Integer> foundDrops = new HashMap<>();
		boolean isDelvePage = false;
		for (Widget child : children)
		{
			int itemId = child.getItemId();
			int quantity = child.getItemQuantity();
			if (child.getOpacity() > 0) quantity = 0;

			String name = itemManager.getItemComposition(itemId).getName();
			if (name == null) continue;

			for (Map.Entry<String, Integer> entry : UNIQUE_DROPS.entrySet()) {
				if (entry.getKey().equalsIgnoreCase(name)) {
					isDelvePage = true;
					foundDrops.put(entry.getValue(), quantity);
					break;
				}
			}
		}
		if (isDelvePage && panel != null)
		{
			panel.syncCollectionLogData(getCurrentGameMode(), foundDrops);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() != GameState.LOGGED_IN) return;
		clientThread.invokeLater(() -> {
			// Always update game mode for score tracking
			if (panel != null) panel.switchGameMode(getCurrentGameMode());

			// Skip region tracking entirely if the configs are disabled
			if (!config.onlyShowInRegion() && !config.autoOpenInRegion())
			{
				inDelveRegion = false;
				return;
			}

			boolean wasInDelveRegion = inDelveRegion;
			inDelveRegion = isInDelveRegion();

			if (inDelveRegion && !wasInDelveRegion)
			{
				if (sessionTimeoutTimer != null) sessionTimeoutTimer.stop();
				togglePanel(true, config.autoOpenInRegion());
			}
			else if (!inDelveRegion && wasInDelveRegion)
			{
				if (sessionTimeoutTimer != null) {
					sessionTimeoutTimer.setInitialDelay((int) TimeUnit.MINUTES.toMillis(config.regionTimeout()));
					sessionTimeoutTimer.start();
				}
			}

			updatePanelVisibility();
		});
	}

	private void updatePanelVisibility()
	{
		boolean shouldBeVisible = shouldShowPanel();
		togglePanel(shouldBeVisible, false);
	}

	private boolean shouldShowPanel()
	{
		if (config.autoOpenOnScoreboard() && isScoreboardVisible()) return true;
		if (!config.onlyShowInRegion()) return true;
		if (inDelveRegion) return true;
		if (sessionTimeoutTimer != null && sessionTimeoutTimer.isRunning()) return true;
		return false;
	}

	private boolean isInDelveRegion()
	{
		if (client.getGameState() != GameState.LOGGED_IN) return false;

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null) return false;

		WorldPoint wp = WorldPoint.fromLocalInstance(client, localPlayer.getLocalLocation());
		if (wp == null) wp = localPlayer.getWorldLocation();
		if (wp == null) return false;

		int playerRegion = wp.getRegionID();
		for (int delveId : DELVE_REGION_IDS)
		{
			if (playerRegion == delveId) return true;
		}

		return false;
	}

	private boolean isScoreboardVisible()
	{
		Widget scoreboardWidget = client.getWidget(WIDGET_GROUP_SCOREBOARD, 0);
		return scoreboardWidget != null && !scoreboardWidget.isHidden();
	}



	private void togglePanel(boolean show, boolean autoOpen)
	{
		SwingUtilities.invokeLater(() -> {
			if (show)
			{
				if (navButton == null) return;
				boolean newlyAdded = false;
				if (!panelVisible)
				{
					clientToolbar.addNavigation(navButton);
					panelVisible = true;
					newlyAdded = true;
				}
				if (autoOpen)
				{
					if (newlyAdded)
					{
						Timer timer = new Timer(200, e -> clientToolbar.openPanel(navButton));
						timer.setRepeats(false);
						timer.start();
					}
					else
					{
						clientToolbar.openPanel(navButton);
					}
				}
			}
			else
			{
				if (navButton == null) return;
				if (navButton.getPanel() != null && navButton.getPanel().isShowing()) return;
				clientToolbar.removeNavigation(navButton);
				panelVisible = false;
			}
		});
	}

	public static Map<Integer, DropRates> getDropRates()
	{
		return DROP_RATES_BY_LEVEL;
	}

	public static Map<String, Integer> getUniqueDropsMap()
	{
		return UNIQUE_DROPS;
	}

	private void updateKillCounts()
	{
		Map<Integer, Integer> levelKills = new HashMap<>();
		int wavesPast8 = 0;
		for (int i = 0; i < 9; i++)
		{
			int childId = 46 + (i * 3);
			Widget widget = client.getWidget(WIDGET_GROUP_SCOREBOARD, childId);
			if (widget != null && widget.getText() != null && !widget.getText().isEmpty())
			{
				String text = widget.getText().trim();
				try
				{
					String numberText = text.replaceAll("[^0-9]", "");
					if (!numberText.isEmpty())
					{
						int kills = Integer.parseInt(numberText);
						if (i == 8) wavesPast8 = kills;
						else levelKills.put(i + 1, kills);
					}
				}
				catch (NumberFormatException ignored) {}
			}
		}
		if (panel != null)
		{
			panel.syncOverallData(getCurrentGameMode(), levelKills, wavesPast8);
		}
	}

	private void scanLootInterface()
	{
		Widget lootInterface = client.getWidget(WIDGET_GROUP_LOOT, WIDGET_LOOT_CONTENTS);
		Widget claimHeader = client.getWidget(WIDGET_GROUP_LOOT, WIDGET_LOOT_CLAIM_HEADER);
		if (lootInterface == null || lootInterface.isHidden() || claimHeader == null || claimHeader.isHidden()) return;

		Widget[] children = lootInterface.getChildren();
		if (children == null) return;

		for (Widget item : children)
		{
			int itemId = item.getItemId();
			if (itemId <= -1) continue;
			String name = itemManager.getItemComposition(itemId).getName();
			if (name == null) continue;
			for (Map.Entry<String, Integer> entry : UNIQUE_DROPS.entrySet())
			{
				if (entry.getKey().equalsIgnoreCase(name))
				{
					if (!name.equalsIgnoreCase("Dom"))
					{
						handleDropLogic(getCurrentGameMode(), entry.getValue());
					}
					break;
				}
			}
		}
	}

	private void handleDropLogic(String gameMode, int itemId)
	{
		if (panel != null) panel.recordDrop(gameMode, itemId);
	}

	public static class DropRates
	{
		public final double overallChance;
		public final double mokhaiotlCloth;
		public final double eyeOfAyak;
		public final double avernicTreads;
		public final double dom;

		public DropRates(double overall, double mokhaiotl, double eye, double avernic, double dom)
		{
			this.overallChance = overall;
			this.mokhaiotlCloth = mokhaiotl;
			this.eyeOfAyak = eye;
			this.avernicTreads = avernic;
			this.dom = dom;
		}
	}
}
