package com.speaax;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
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
import com.google.inject.Provides;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
	name = "Delve Calculator",
	description = "Calculates expected unique drops",
	tags = {"delve", "calculator", "drops", "rates", "doom"}
)
public class DelveCalculatorPlugin extends Plugin
{
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private DelveCalculatorConfig config;
	@Inject private Gson gson;
	@Inject private ClientToolbar clientToolbar;
	@Getter @Inject private ItemManager itemManager;

	private DelveCalculatorPanel panel;
	private NavigationButton navButton;
	private Timer sessionTimeoutTimer;
	private boolean panelVisible = false;
	private Instant lastRegionEntryTime = null;
	private boolean inDelveRegion = false;

	// Widget group ID
	private static final int WIDGET_GROUP = 920;
	private static final int[] DELVE_REGION_IDS = {5269, 13668, 14180};
	private static final Map<Integer, DropRates> DROP_RATES_BY_LEVEL = new HashMap<>();

	static {
		//DROP_RATES_BY_LEVEL.put(x... where x is delve level and 9 is everything from 9 and above
		DROP_RATES_BY_LEVEL.put(2, new DropRates(1.0/2500, 1.0/2500, 0, 0, 0, 0));
		DROP_RATES_BY_LEVEL.put(3, new DropRates(1.0/1000, 1.0/2000, 1.0/2000, 0, 0, 0));
		DROP_RATES_BY_LEVEL.put(4, new DropRates(1.0/450, 1.0/1350, 1.0/1350, 1.0/1350, 0, 0));
		DROP_RATES_BY_LEVEL.put(5, new DropRates(1.0/270, 1.0/810, 1.0/810, 1.0/810, 0, 0));
		DROP_RATES_BY_LEVEL.put(6, new DropRates(1.0/255, 1.0/765, 1.0/765, 1.0/765, 1.0/1000, 0));
		DROP_RATES_BY_LEVEL.put(7, new DropRates(1.0/240, 1.0/720, 1.0/720, 1.0/720, 1.0/750, 0));
		DROP_RATES_BY_LEVEL.put(8, new DropRates(1.0/210, 1.0/630, 1.0/630, 1.0/630, 1.0/500, 0));
		DROP_RATES_BY_LEVEL.put(9, new DropRates(1.0/180, 1.0/540, 1.0/540, 1.0/540, 1.0/250, 0));
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
		sessionTimeoutTimer.stop();
		SwingUtilities.invokeLater(() -> clientToolbar.removeNavigation(navButton));
		navButton = null;
		panel = null;
		lastRegionEntryTime = null;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			String message = event.getMessage();

			// Check for delve completion messages
			// Format: "Delve level: 4 duration: 1:45. Personal best: 1:27"
			// Format: "Delve level: 8+ (11) duration: 1:10.20. Personal best: 1:10.20"
			if (message.contains("Delve level:") && message.contains("duration:"))
			{
				// Extract delve level from the message
				String[] parts = message.split(" ");
				for (int i = 0; i < parts.length; i++)
				{
					if (parts[i].equals("level:") && i + 1 < parts.length)
					{
						String levelText = parts[i + 1];

						// Check if it's level 8+ (special case)
						if (levelText.equals("8+"))
						{
							log.debug("Delve completed! Level: 8+");
							panel.incrementWavesPast8();
							break;
						}
						else
						{
							// Regular level (1-8)
							try
							{
								int level = Integer.parseInt(levelText);
								if (level >= 1 && level <= 8)
								{
									log.debug("Delve completed! Level: {}", level);
									panel.incrementFloorKills(level);
									break;
								}
								else
								{
									log.debug("Invalid delve level: {}", level);
								}
							}
							catch (NumberFormatException e)
							{
								log.debug("Failed to parse delve level from message: {}", message);
							}
						}
					}
				}
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("delvecalculator"))
		{
			updatePanelVisibility();

			if (panel != null)
			{
				panel.updateAllUI();
			}
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == WIDGET_GROUP)
		{
			clientThread.invokeLater(this::updateKillCounts);
			updatePanelVisibility();
			if (config.autoOpenOnScoreboard())
			{
				togglePanel(true, true);
			}
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == WIDGET_GROUP)
		{
			updatePanelVisibility();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		clientThread.invokeLater(() -> {
			boolean wasInDelveRegion = inDelveRegion;
			inDelveRegion = isInDelveRegion();

			// Just entered the region
			if (inDelveRegion && !wasInDelveRegion)
			{
				sessionTimeoutTimer.stop();
				lastRegionEntryTime = Instant.now();
				togglePanel(true, config.autoOpenInRegion());
			}
			// Just left the region
			else if (!inDelveRegion && wasInDelveRegion)
			{
				// Start the timer to hide the panel later
				sessionTimeoutTimer.setInitialDelay((int) TimeUnit.MINUTES.toMillis(config.regionTimeout()));
				sessionTimeoutTimer.start();
			}

			updatePanelVisibility();
		});
	}

	/**
	 * The main logic hub. Determines if the panel icon should be visible based on all config settings.
	 */
	private void updatePanelVisibility()
	{
		// This method is now just a simple state checker. The timer is handled elsewhere.
		boolean shouldBeVisible = shouldShowPanel();
		togglePanel(shouldBeVisible, false); // This check never auto-opens.
	}

	/**
	 * Determines if the panel icon should be visible by checking all configured conditions.
	 * @return true if any condition for visibility is met.
	 */
	private boolean shouldShowPanel()
	{
		// Condition 1: "Always Show" is enabled.
		if (config.alwaysShowPanel())
		{
			return true;
		}

		// Condition 2: "Show on Scoreboard" is enabled AND the scoreboard is visible.
		if (config.showOnScoreboard() && isScoreboardVisible())
		{
			return true;
		}

		// Condition 3: Player is currently in the region.
		if (config.showInRegion() && inDelveRegion)
		{
			return true;
		}

		// Condition 4: The hide timer is currently running (player has left, but grace period is active).
		if (config.showInRegion() && sessionTimeoutTimer.isRunning())
		{
			return true;
		}

		return false; // No conditions met.
	}

	/**
	 * Handles the session-based logic for the REGION display mode.
	 */
	private void handleRegionSession()
	{
		boolean inRegion = isInDelveRegion();
		boolean sessionActive = lastRegionEntryTime != null &&
				Duration.between(lastRegionEntryTime, Instant.now()).toMinutes() < config.regionTimeout();

		if (inRegion)
		{
			if (!sessionActive)
			{
				lastRegionEntryTime = Instant.now();
				// Use the specific config for region auto-opening
				togglePanel(true, config.autoOpenInRegion());
			}
			else
			{
				togglePanel(true, false);
			}
		}
		else // Not in the region
		{
			if (!sessionActive)
			{
				lastRegionEntryTime = null;
				togglePanel(false, false);
				return;
			}
		}

		if (lastRegionEntryTime != null)
		{
			long minutesSinceEntry = Duration.between(lastRegionEntryTime, Instant.now()).toMinutes();
			long minutesRemaining = config.regionTimeout() - minutesSinceEntry;

			if (minutesRemaining > 0)
			{
				sessionTimeoutTimer.setInitialDelay((int) TimeUnit.MINUTES.toMillis(minutesRemaining));
				sessionTimeoutTimer.start();
			}
			else
			{
				updatePanelVisibility(); // Re-run logic if timer is already supposed to be expired
			}
		}
	}

	private boolean isInDelveRegion()
	{
		if (client.getGameState() != GameState.LOGGED_IN) return false;
		return Arrays.stream(client.getMapRegions()).anyMatch(
				regionId -> Arrays.stream(DELVE_REGION_IDS).anyMatch(delveId -> delveId == regionId)
		);
	}

	private boolean isScoreboardVisible()
	{
		Widget scoreboardWidget = client.getWidget(WIDGET_GROUP, 0);
		return scoreboardWidget != null && !scoreboardWidget.isHidden();
	}

	private void togglePanel(boolean show, boolean autoOpen)
	{
		SwingUtilities.invokeLater(() -> {
			if (show)
			{
				if (!panelVisible)
				{
					clientToolbar.addNavigation(navButton);
					panelVisible = true;
				}

				if (autoOpen)
				{
					SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
				}
			}
			else
			{
				// ** THE CRITICAL SAFETY CHECK **
				// Only remove the button if our panel is not the one currently selected.
				if (navButton.getPanel().isShowing())
				{
					return; // Do nothing, leave the button visible.
				}

				// If it's safe to hide, proceed with removal.
				clientToolbar.removeNavigation(navButton);
				panelVisible = false;
			}
		});
	}


	// Static method to get drop rates for external access
	public static Map<Integer, DropRates> getDropRates()
	{
		return DROP_RATES_BY_LEVEL;
	}

	private void updateKillCounts()
	{
		Map<Integer, Integer> levelKills = new HashMap<>();
		int wavesPast8 = 0;

		log.debug("Starting updateKillCounts - reading from widget group {}", WIDGET_GROUP);

		// Simple approach: read from child ID 46 to 70 with 3 increments
		// This covers Level 1 (46) through Level 8+ (70)
		for (int i = 0; i < 9; i++) // 9 levels: 1-8 + 8+
		{
			int childId = 46 + (i * 3);
			Widget widget = client.getWidget(WIDGET_GROUP, childId);

			log.debug("Reading widget {} for level {}: {}", childId, i + 1, widget != null ? widget.getText() : "null");

			if (widget != null && widget.getText() != null && !widget.getText().isEmpty())
			{
				String text = widget.getText().trim();

				try
				{
					// Extract just the number from the text
					String numberText = text.replaceAll("[^0-9]", "");
					if (!numberText.isEmpty())
					{
						int kills = Integer.parseInt(numberText);
						if (i == 8)
						{
							// This is Level 8+
							wavesPast8 = kills;
							log.debug("Level 8+ (waves past 8): {}", kills);
						}
						else
						{
							// This is a regular level (1-8)
							levelKills.put(i + 1, kills);
							log.debug("Level {}: {}", i + 1, kills);
						}
					}
					else
					{
						// No numeric data found
						if (i == 8)
						{
							wavesPast8 = 0;
							log.debug("Level 8+ (waves past 8): 0 (no numeric data)");
						}
						else
						{
							levelKills.put(i + 1, 0);
							log.debug("Level {}: 0 (no numeric data)", i + 1);
						}
					}
				}
				catch (NumberFormatException e)
				{
					if (i == 8)
					{
						wavesPast8 = 0;
						log.debug("Level 8+ (waves past 8): 0 (parse error)");
					}
					else
					{
						levelKills.put(i + 1, 0);
						log.debug("Level {}: 0 (parse error)", i + 1);
					}
				}
			}
			else
			{
				if (i == 8)
				{
					wavesPast8 = 0;
					log.debug("Level 8+ (waves past 8): 0 (widget null/empty)");
				}
				else
				{
					levelKills.put(i + 1, 0);
					log.debug("Level {}: 0 (widget null/empty)", i + 1);
				}
			}
		}

		log.debug("Final levelKills map: {}", levelKills);
		log.debug("Final wavesPast8: {}", wavesPast8);

		// Update the panel with new data
		if (panel != null)
		{
			log.debug("Calling panel.updateData with {} levels and {} waves past 8", levelKills.size(), wavesPast8);
			panel.updateData(levelKills, wavesPast8);
		}
		else
		{
			log.debug("Panel is null, cannot update data");
		}
	}

	// Helper class to store drop rates for each level
	public static class DropRates
	{
		public final double overallChance;
		public final double mokhaiotlCloth;
		public final double eyeOfAyak;
		public final double avernicTreads;
		public final double dom;
		public final double cumulativeChance;

		public DropRates(double overall, double mokhaiotl, double eye, double avernic, double dom, double cumulative)
		{
			this.overallChance = overall;
			this.mokhaiotlCloth = mokhaiotl;
			this.eyeOfAyak = eye;
			this.avernicTreads = avernic;
			this.dom = dom;
			this.cumulativeChance = cumulative;
		}
	}
}
