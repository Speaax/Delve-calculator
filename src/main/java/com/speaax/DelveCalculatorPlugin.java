package com.speaax;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
@PluginDescriptor(
	name = "Delve Calculator",
	description = "Calculates expected unique drops",
	tags = {"delve", "calculator", "drops", "rates", "doom"}
)
public class DelveCalculatorPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Getter
	@Inject
	private ItemManager itemManager;

	private DelveCalculatorPanel panel;
	private NavigationButton navButton;

	// Widget group ID
	private static final int WIDGET_GROUP = 920;

	// Drop rate data based on the table you provided
	private static final Map<Integer, DropRates> DROP_RATES_BY_LEVEL = new HashMap<>();

	static {
		// Delve level 2
		DROP_RATES_BY_LEVEL.put(2, new DropRates(
			1.0/2500, 1.0/2500, 0, 0, 0, 0
		));
		
		// Delve level 3
		DROP_RATES_BY_LEVEL.put(3, new DropRates(
			1.0/1000, 1.0/2000, 1.0/2000, 0, 0, 0
		));
		
		// Delve level 4
		DROP_RATES_BY_LEVEL.put(4, new DropRates(
			1.0/450, 1.0/1350, 1.0/1350, 1.0/1350, 0, 0
		));
		
		// Delve level 5
		DROP_RATES_BY_LEVEL.put(5, new DropRates(
			1.0/270, 1.0/810, 1.0/810, 1.0/810, 0, 0
		));
		
		// Delve level 6
		DROP_RATES_BY_LEVEL.put(6, new DropRates(
			1.0/255, 1.0/765, 1.0/765, 1.0/765, 1.0/1000, 0
		));
		
		// Delve level 7
		DROP_RATES_BY_LEVEL.put(7, new DropRates(
			1.0/240, 1.0/720, 1.0/720, 1.0/720, 1.0/750, 0
		));
		
		// Delve level 8
		DROP_RATES_BY_LEVEL.put(8, new DropRates(
			1.0/210, 1.0/630, 1.0/630, 1.0/630, 1.0/500, 0
		));
		
		// Delve level 9+
		DROP_RATES_BY_LEVEL.put(9, new DropRates(
			1.0/180, 1.0/540, 1.0/540, 1.0/540, 1.0/250, 0
		));
	}

	@Override
	protected void startUp() throws Exception
	{
		panel = new DelveCalculatorPanel(this);
		
		// Load icon with error handling
		BufferedImage icon = null;
		try
		{
			icon = ImageUtil.loadImageResource(getClass(), "/icon.png");
		}
		catch (Exception e)
		{
			log.warn("Failed to load plugin icon", e);
		}
		
		navButton = NavigationButton.builder()
			.tooltip("Delve Calculator")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();
		
		clientToolbar.addNavigation(navButton);
		

	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
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
							log.info("Delve completed! Level: 8+");
							panel.incrementWavesPast8();
							break;
						}
						else
						{
							// Regular level (2-8)
							try
							{
								int level = Integer.parseInt(levelText);
								if (level >= 2 && level <= 8)
								{
									log.info("Delve completed! Level: {}", level);
									panel.incrementFloorKills(level);
									break;
								}
								else
								{
									log.warn("Invalid delve level: {}", level);
								}
							}
							catch (NumberFormatException e)
							{
								log.warn("Failed to parse delve level from message: {}", message);
							}
						}
					}
				}
			}
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == WIDGET_GROUP)
		{
			log.info("Widget {} loaded, scheduling delayed data read", WIDGET_GROUP);
			
			// Widget loaded, but data might not be populated yet
			// Schedule a delayed read to allow the widget data to populate
			Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					log.info("Executing delayed updateKillCounts for widget {}", WIDGET_GROUP);
					updateKillCounts();
				}
			}, 500); // Wait 500ms for widget data to populate
		}
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

		log.info("Starting updateKillCounts - reading from widget group {}", WIDGET_GROUP);

		// Simple approach: read from child ID 46 to 70 with 3 increments
		// This covers Level 1 (46) through Level 8+ (70)
		for (int i = 0; i < 9; i++) // 9 levels: 1-8 + 8+
		{
			int childId = 46 + (i * 3);
			Widget widget = client.getWidget(WIDGET_GROUP, childId);
			
			log.info("Reading widget {} for level {}: {}", childId, i + 1, widget != null ? widget.getText() : "null");
			
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
							log.info("Level 8+ (waves past 8): {}", kills);
						}
						else
						{
							// This is a regular level (1-8)
							levelKills.put(i + 1, kills);
							log.info("Level {}: {}", i + 1, kills);
						}
					}
					else
					{
						// No numeric data found
						if (i == 8)
						{
							wavesPast8 = 0;
							log.info("Level 8+ (waves past 8): 0 (no numeric data)");
						}
						else
						{
							levelKills.put(i + 1, 0);
							log.info("Level {}: 0 (no numeric data)", i + 1);
						}
					}
				}
				catch (NumberFormatException e)
				{
					if (i == 8)
					{
						wavesPast8 = 0;
						log.info("Level 8+ (waves past 8): 0 (parse error)");
					}
					else
					{
						levelKills.put(i + 1, 0);
						log.info("Level {}: 0 (parse error)", i + 1);
					}
				}
			}
			else
			{
				if (i == 8)
				{
					wavesPast8 = 0;
					log.info("Level 8+ (waves past 8): 0 (widget null/empty)");
				}
				else
				{
					levelKills.put(i + 1, 0);
					log.info("Level {}: 0 (widget null/empty)", i + 1);
				}
			}
		}

		log.info("Final levelKills map: {}", levelKills);
		log.info("Final wavesPast8: {}", wavesPast8);

		// Update the panel with new data
		if (panel != null)
		{
			log.info("Calling panel.updateData with {} levels and {} waves past 8", levelKills.size(), wavesPast8);
			panel.updateData(levelKills, wavesPast8);
		}
		else
		{
			log.warn("Panel is null, cannot update data");
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
