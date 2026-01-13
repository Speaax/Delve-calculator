package com.speaax;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
public class DelveCalculatorPanel extends PluginPanel
{
	// Key used for Manual data storage in JSON: e.g. "STANDARD:MANUAL"
	private String getManualProfileKey()
	{
		return currentGameMode + ":MANUAL";
	}

	private final DelveCalculatorPlugin plugin;
	private final ItemManager itemManager;
	private final DelveCalculatorConfig config;
	private final Gson gson;

	private final JLabel totalKillsLabel;
	private final JPanel progressPanel;
	private final JPanel noDataSectionPanel;
	private final MaterialTabGroup viewTabGroup = new MaterialTabGroup();
	private final MaterialTabGroup modeTabGroup = new MaterialTabGroup();

	private final Map<Integer, JLabel> levelValueLabels = new HashMap<>();
	private final Map<String, ProgressRow> progressRows = new HashMap<>();
	private final List<MaterialTab> modeTabs = new ArrayList<>();

	private static class ProgressRow
	{
		CustomProgressBar progressBar;
		JLabel expectedLabel;
		JLabel iconLabel;
		ImageIcon originalIcon;
	}

	public static class CustomProgressBar extends JPanel
	{
		private int value = 0;
		private int maximum = 100;
		private String text = "";
		private Color foreground = new Color(0, 200, 0); // Default Green
		private Double luckValue = null; // If set, draws center-split bar
		private Double maxLuck = null;

		public CustomProgressBar()
		{
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setBorder(BorderFactory.createLineBorder(ColorScheme.LIGHT_GRAY_COLOR, 1));
			setPreferredSize(new Dimension(-1, 32));
		}

		public void setValue(int value) { this.value = value; repaint(); }
		public void setMaximum(int maximum) { this.maximum = maximum; repaint(); }
		public void setString(String text) { this.text = text; repaint(); }
		@Override
		public void setForeground(Color fg) { this.foreground = fg; repaint(); }
		
		public void setLuckMode(Double luck, Double maxLuck) {
			this.luckValue = luck;
			this.maxLuck = maxLuck;
			repaint();
		}

		public void setNormalMode() {
			this.luckValue = null;
			this.maxLuck = null;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (!(g instanceof Graphics2D)) return;

			Graphics2D g2 = (Graphics2D) g;
			int width = getWidth();
			int height = getHeight();

			// 1. Draw Bar
			g2.setColor(foreground);
			
			if (luckValue != null && maxLuck != null)
			{
				// Received Mode: Center split
				int centerX = width / 2;
				int barWidth = (int) ((Math.abs(luckValue) / maxLuck) * (width / 2.0));
				barWidth = Math.min(barWidth, width / 2);

				if (luckValue > 0) {
					g2.fillRect(centerX, 0, barWidth, height);
				} else if (luckValue < 0) {
					g2.fillRect(centerX - barWidth, 0, barWidth, height);
				}

				// Center Line
				g2.setColor(Color.LIGHT_GRAY);
				g2.drawLine(centerX, 0, centerX, height);
			}
			else
			{
				// Expected Mode: Left to Right
				int barWidth = (int) (width * ((double) value / maximum));
				g2.fillRect(0, 0, barWidth, height);
			}

			// 2. Draw Text (Smart Placement)
			if (text != null && !text.isEmpty())
			{
				g2.setFont(FontManager.getRunescapeFont());
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				
				FontMetrics metrics = g2.getFontMetrics();
				int textWidth = metrics.stringWidth(text);
				int textY = (height + metrics.getAscent() - metrics.getDescent()) / 2;
				int textX;

				if (luckValue != null)
				{
					// RECEIVED MODE: Smart Placement
					// If Luck > 0 (Green bar on Right) -> Text on Left (Empty)
					// If Luck < 0 (Red bar on Left) -> Text on Right (Empty)
					if (luckValue > 0)
					{
						textX = (width / 2) - textWidth - 5; // Left of center
					}
					else if (luckValue < 0)
					{
						textX = (width / 2) + 5; // Right of center
					}
					else
					{
						textX = (width - textWidth) / 2; // Center
					}
				}
				else
				{
					// EXPECTED MODE: Center
					textX = (width - textWidth) / 2;
				}

				// Draw Main Text (White) - No outlines, no backgrounds
				g2.setColor(Color.WHITE);
				g2.drawString(text, textX, textY);
			}
		}
	}

	private DelveCalculatorData data = new DelveCalculatorData();
	private String currentGameMode = "STANDARD";
	private ViewTab currentView = ViewTab.ALL;
	private ModeTab currentMode = ModeTab.EXPECTED;

	private final JPanel manualResetPanel;

	public enum ViewTab { ALL, SESSION, MANUAL }
	public enum ModeTab { EXPECTED, RECEIVED }

	public DelveCalculatorPanel(DelveCalculatorPlugin plugin, DelveCalculatorConfig config, Gson gson)
	{
		this.plugin = plugin;
		this.itemManager = plugin.getItemManager();
		this.config = config;
		this.gson = gson;
		this.currentGameMode = plugin.getCurrentGameMode();

		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Title
		JLabel titleLabel = new JLabel("Delve Calculator");
		titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(24.0f));
		titleLabel.setForeground(Color.YELLOW);
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		titleLabel.setBorder(new EmptyBorder(0, 0, 10, 0));

		// Main Tabs (All, Session, Manual) and Help Icon
		JPanel mainHeader = new JPanel(new BorderLayout());
		mainHeader.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		mainHeader.setBorder(new EmptyBorder(0, 0, 10, 0));
		
		addTab(viewTabGroup, "All", () -> setView(ViewTab.ALL));
		addTab(viewTabGroup, "Session", () -> setView(ViewTab.SESSION));
		addTab(viewTabGroup, "Manual", () -> setView(ViewTab.MANUAL));
		mainHeader.add(viewTabGroup, BorderLayout.CENTER);

		JLabel viewHelpLabel = createCircularHelpLabel();
		viewHelpLabel.setToolTipText("<html>" +
				"<b>All:</b> All-time data mirrored from the in-game scoreboard.<br>" +
				"<b>Session:</b> Data tracked since opening the client.<br>" +
				"<b>Manual:</b> Custom data pool that can be reset at any time.</html>");
		
		JPanel viewHelpContainer = new JPanel(new GridBagLayout());
		viewHelpContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		viewHelpContainer.setBorder(new EmptyBorder(0, 0, 0, 5));
		viewHelpContainer.add(viewHelpLabel);
		mainHeader.add(viewHelpContainer, BorderLayout.EAST);

		// Kill Counts Panel
		JPanel killCountsPanel = new JPanel();
		killCountsPanel.setLayout(new BoxLayout(killCountsPanel, BoxLayout.Y_AXIS));
		killCountsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		killCountsPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

		JPanel totalKillsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		totalKillsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JLabel totalKillsLabelText = new JLabel("Total Levels: ");
		totalKillsLabelText.setForeground(Color.YELLOW);
		totalKillsLabelText.setFont(FontManager.getRunescapeBoldFont());
		totalKillsLabel = new JLabel("0");
		totalKillsLabel.setForeground(Color.WHITE);
		totalKillsLabel.setFont(FontManager.getRunescapeBoldFont());
		totalKillsPanel.add(totalKillsLabelText);
		totalKillsPanel.add(totalKillsLabel);
		killCountsPanel.add(totalKillsPanel);
		killCountsPanel.add(Box.createVerticalStrut(5));

		JPanel levelsPanel = new JPanel(new GridLayout(9, 1, 5, 2));
		levelsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		for (int i = 1; i <= 8; i++) {
			JPanel levelPanel = createLevelPanel("Level " + i + ": ");
			levelValueLabels.put(i, (JLabel) levelPanel.getComponent(1));
			levelsPanel.add(levelPanel);
		}
		JPanel levelPlusPanel = createLevelPanel("Level +: ");
		levelValueLabels.put(9, (JLabel) levelPlusPanel.getComponent(1));
		levelsPanel.add(levelPlusPanel);
		killCountsPanel.add(levelsPanel);

		// Sub Tabs (Expected vs Received) and Help Icon
		JPanel modeHeader = new JPanel(new BorderLayout());
		modeHeader.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		addTab(modeTabGroup, "Expected", () -> setMode(ModeTab.EXPECTED));
		addTab(modeTabGroup, "Received", () -> setMode(ModeTab.RECEIVED));
		modeHeader.add(modeTabGroup, BorderLayout.CENTER);

		JLabel helpLabel = createCircularHelpLabel();
		helpLabel.setToolTipText("<html>" +
				"<b>Expected Tab:</b><br>" +
				"<u>Bar</u>: Progress toward the next statistical drop.<br>" +
				"<u>Number</u>: Total drops you are expected to have.<br><br>" +
				"<b>Received Tab:</b><br>" +
				"<u>Bar</u>: Your 'luck' (Actual drops vs. Expectations).<br>" +
				"<u>Number</u>: Total drops you have actually received.</html>");
		
		JPanel helpContainer = new JPanel(new GridBagLayout());
		helpContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		helpContainer.setBorder(new EmptyBorder(0, 0, 0, 5));
		helpContainer.add(helpLabel);
		modeHeader.add(helpContainer, BorderLayout.EAST);

		// Progress and No Data Panels
		progressPanel = createProgressPanel();
		noDataSectionPanel = createNoDataSection();

		// Reward Section (Tabs + Progress)
		JPanel rewardSection = new JPanel();
		rewardSection.setLayout(new BoxLayout(rewardSection, BoxLayout.Y_AXIS));
		rewardSection.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		rewardSection.add(modeHeader);
		rewardSection.add(progressPanel);

		// Manual Reset Button
		manualResetPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		manualResetPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JButton resetButton = new JButton("Reset Manual Data");
		resetButton.setBackground(new Color(150, 0, 0)); // Dark red
		resetButton.setForeground(Color.WHITE);
		resetButton.setFont(FontManager.getRunescapeFont());
		resetButton.addActionListener(e -> confirmResetManualData());
		manualResetPanel.add(resetButton);
		manualResetPanel.setVisible(false);

		// Add components to main panel
		add(titleLabel, BorderLayout.NORTH);
		contentPanel.add(mainHeader);
		contentPanel.add(killCountsPanel);
		contentPanel.add(Box.createVerticalStrut(10));

		contentPanel.add(rewardSection);
		contentPanel.add(Box.createVerticalStrut(10));
		contentPanel.add(noDataSectionPanel);
		contentPanel.add(manualResetPanel);
		
		add(contentPanel, BorderLayout.CENTER);

		// Load initial tab states
		try {
			this.currentView = ViewTab.valueOf(config.activeViewTab());
		} catch (Exception e) { this.currentView = ViewTab.ALL; }
		try {
			this.currentMode = ModeTab.valueOf(config.activeModeTab());
		} catch (Exception e) { this.currentMode = ModeTab.EXPECTED; }

		viewTabGroup.select(viewTabGroup.getTab(currentView.ordinal()));
		modeTabGroup.select(modeTabGroup.getTab(currentMode.ordinal()));

		loadData();
		updateAllUI();
	}

	private void addTab(MaterialTabGroup group, String title, Runnable onSelect)
	{
		MaterialTab tab = new MaterialTab(title, group, null);
		tab.setOnSelectEvent(() -> {
			if (onSelect != null) onSelect.run();
			return true;
		});
		group.addTab(tab);
		if (group == modeTabGroup) modeTabs.add(tab);
	}

	private void setView(ViewTab view)
	{
		this.currentView = view;
		config.activeViewTab(view.name());
		if (totalKillsLabel != null) updateAllUI();
	}

	private void setMode(ModeTab mode)
	{
		this.currentMode = mode;
		config.activeModeTab(mode.name());
		
		// Highlight selected tab in yellow
		for (int i = 0; i < modeTabs.size(); i++)
		{
			MaterialTab tab = modeTabs.get(i);
			if (i == mode.ordinal())
			{
				tab.setForeground(Color.YELLOW);
			}
			else
			{
				tab.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		}

		if (totalKillsLabel != null) updateAllUI();
	}

	private void loadData()
	{
		String json = config.killCountData();
		if (json == null || json.isEmpty()) return;
		try {
			this.data = gson.fromJson(json, DelveCalculatorData.class);
			if (this.data == null) this.data = new DelveCalculatorData();
		} catch (Exception e) { log.debug("Error loading data", e); }
	}

	private void saveData()
	{
		config.killCountData(gson.toJson(data));
	}

	public void switchGameMode(String mode)
	{
		this.currentGameMode = mode;
		SwingUtilities.invokeLater(this::updateAllUI);
	}

	// Unified update method for All, Manual, and Session profiles
	private void updateProfiles(Consumer<DelveCalculatorData.DelveProfile> action)
	{
		// 1. All (Persistent Code)
		DelveCalculatorData.DelveProfile allProfile = data.getProfiles().computeIfAbsent(currentGameMode, k -> new DelveCalculatorData.DelveProfile(currentGameMode, true));
		action.accept(allProfile);

		// 2. Manual (Persistent Code : MANUAL)
		DelveCalculatorData.DelveProfile manualProfile = data.getProfiles().computeIfAbsent(getManualProfileKey(), k -> new DelveCalculatorData.DelveProfile("Manual", true));
		action.accept(manualProfile);

		// 3. Session (InMemory)
		DelveCalculatorData.DelveProfile sessionProfile = plugin.getSessionProfile(currentGameMode);
		action.accept(sessionProfile);

		saveData();
		SwingUtilities.invokeLater(this::updateAllUI);
	}

	public void incrementFloorKills(String mode, int floor)
	{
		// Ensure we are operating on the correct mode context
		if (!mode.equals(currentGameMode)) return; 
		updateProfiles(p -> p.addKills(floor, 1));
	}

	public void incrementWavesPast8(String mode)
	{
		// Ensure we are operating on the correct mode context
		if (!mode.equals(currentGameMode)) return;
		updateProfiles(DelveCalculatorData.DelveProfile::addWave8);
	}

	public void recordDrop(String mode, int itemId)
	{
		// Ensure we are operating on the correct mode context
		if (!mode.equals(currentGameMode)) return;
		updateProfiles(p -> p.addDrop(itemId));
	}

	public void syncOverallData(String mode, Map<Integer, Integer> levelKills, int wavesPast8)
	{
		DelveCalculatorData.DelveProfile profile = data.getProfiles().computeIfAbsent(mode, k -> new DelveCalculatorData.DelveProfile(mode, true));
		profile.getLevelKills().putAll(levelKills);
		profile.setWavesPast8(wavesPast8);
		saveData();
		SwingUtilities.invokeLater(this::updateAllUI);
	}

	public void syncCollectionLogData(String mode, Map<Integer, Integer> foundDrops)
	{
		DelveCalculatorData.DelveProfile profile = data.getProfiles().computeIfAbsent(mode, k -> new DelveCalculatorData.DelveProfile(mode, true));
		profile.getObtainedUniques().putAll(foundDrops);
		saveData();
		SwingUtilities.invokeLater(this::updateAllUI);
	}

	public void updateAllUI()
	{
		if (totalKillsLabel == null) return;

		DelveCalculatorData.DelveProfile profile = getActiveProfile();
		int totalKills = profile.getLevelKills().values().stream().mapToInt(Integer::intValue).sum() + profile.getWavesPast8();
		totalKillsLabel.setText(String.valueOf(totalKills));

		for (int i = 1; i <= 8; i++)
		{
			JLabel label = levelValueLabels.get(i);
			if (label != null) label.setText(String.valueOf(profile.getLevelKills().getOrDefault(i, 0)));
		}
		JLabel plusLabel = levelValueLabels.get(9);
		if (plusLabel != null) plusLabel.setText(String.valueOf(profile.getWavesPast8()));

		updateProgressBars(profile);

		boolean hasData = totalKills > 0;
		manualResetPanel.setVisible(currentView == ViewTab.MANUAL);
		noDataSectionPanel.setVisible(!hasData && currentView == ViewTab.ALL);
	}

	private DelveCalculatorData.DelveProfile getActiveProfile()
	{
		if (currentView == ViewTab.SESSION) return plugin.getSessionProfile(currentGameMode);
		if (currentView == ViewTab.MANUAL) return data.getProfiles().computeIfAbsent(getManualProfileKey(), k -> new DelveCalculatorData.DelveProfile("Manual", true));
		return data.getProfiles().computeIfAbsent(currentGameMode, k -> new DelveCalculatorData.DelveProfile(currentGameMode, true));
	}

	private void updateProgressBars(DelveCalculatorData.DelveProfile profile)
	{
		Map<String, Double> itemProgress = calculateItemProgress(profile);
		Map<String, DelveCalculatorConfig.RewardDisplayMode> displayModes = getDisplayModes();

		for (Map.Entry<String, ProgressRow> entry : progressRows.entrySet())
		{
			String itemName = entry.getKey();
			ProgressRow row = entry.getValue();
			DelveCalculatorConfig.RewardDisplayMode displayMode = displayModes.getOrDefault(itemName, DelveCalculatorConfig.RewardDisplayMode.SHOW);

			boolean visible = displayMode != DelveCalculatorConfig.RewardDisplayMode.HIDE;
			row.iconLabel.setVisible(visible);
			row.progressBar.setVisible(visible);
			row.expectedLabel.setVisible(visible);

			if (!visible) continue;

			CustomProgressBar progressBar = row.progressBar;
			JLabel label = row.expectedLabel;
			JLabel iconLabel = row.iconLabel;

			label.setVisible(true);

			double expected = itemProgress.getOrDefault(itemName, 0.0);
			int actual = getActualDrops(profile, itemName);

			if (currentMode == ModeTab.EXPECTED)
			{
				double progress = expected % 1.0;
				
				progressBar.setNormalMode();
				progressBar.setMaximum(100);
				progressBar.setValue((int) (progress * 100));
				
				// Reverted: Percentage on bar, Integer count on right
				progressBar.setString(String.format("%.1f%%", progress * 100));
				progressBar.setToolTipText(null);
				
				label.setText(String.valueOf((int) expected));
				progressBar.setForeground(calculateProgressColor((int)(progress * 100)));
			}
			else // RECEIVED
			{
				double luck = actual - expected;
				
				// Calculate max luck for relative scaling
				double maxLuck = 0;
				for (String name : progressRows.keySet()) {
					if (displayModes.getOrDefault(name, DelveCalculatorConfig.RewardDisplayMode.SHOW) != DelveCalculatorConfig.RewardDisplayMode.HIDE) {
						double itemExp = itemProgress.getOrDefault(name, 0.0);
						int itemAct = getActualDrops(profile, name);
						maxLuck = Math.max(maxLuck, Math.abs(itemAct - itemExp));
					}
				}
				// Ensure maxLuck is at least 1.0 to avoid division by zero or tiny bars
				maxLuck = Math.max(maxLuck, 1.0);

				progressBar.setLuckMode(luck, maxLuck);
				
				// Reverted: Luck value on bar, Actual count on right
				progressBar.setString(String.format("%+.2f", luck));
				progressBar.setToolTipText(null);
				
				label.setText(String.valueOf(actual));
				
				// Scaling and coloring
				if (luck > 0)
				{
					progressBar.setForeground(new Color(0, 100, 0)); // Even Darker Green
				}
				else if (luck < 0)
				{
					// Gradient from Yellow (0) to Red (-1)
					// Use darker colors: Yellow ~ (150, 150, 0), Red ~ (150, 0, 0)
					float ratio = (float) Math.min(Math.abs(luck), 1.0);
					int red = 150;
					int green = (int) (150 * (1.0 - ratio));
					progressBar.setForeground(new Color(red, green, 0));
				}
				else
				{
					progressBar.setForeground(Color.GRAY);
				}
			}

			if (displayMode == DelveCalculatorConfig.RewardDisplayMode.GREY)
			{
				progressBar.setForeground(Color.GRAY);
				label.setForeground(Color.GRAY);
				if (iconLabel != null && row.originalIcon != null) {
					iconLabel.setIcon(new ImageIcon(ImageUtil.grayscaleImage(ImageUtil.bufferedImageFromImage(row.originalIcon.getImage()))));
				}
			}
			else
			{
				label.setForeground(Color.WHITE);
				if (iconLabel != null) iconLabel.setIcon(row.originalIcon);
			}
		}
	}

	private int getActualDrops(DelveCalculatorData.DelveProfile profile, String itemName)
	{
		if (itemName.equals("Any Item")) return profile.getObtainedUniques().values().stream().mapToInt(Integer::intValue).sum();
		Integer itemId = DelveCalculatorPlugin.getUniqueDropsMap().get(itemName);
		return (itemId != null) ? profile.getObtainedUniques().getOrDefault(itemId, 0) : 0;
	}

	private Map<String, Double> calculateItemProgress(DelveCalculatorData.DelveProfile profile)
	{
		Map<String, Double> progress = new HashMap<>();
		double mokhaiotl = 0, eye = 0, avernic = 0, dom = 0;

		for (Map.Entry<Integer, DelveCalculatorPlugin.DropRates> entry : DelveCalculatorPlugin.getDropRates().entrySet())
		{
			int level = entry.getKey();
			DelveCalculatorPlugin.DropRates rates = entry.getValue();
			int kills = (level == 9) ? profile.getWavesPast8() : profile.getLevelKills().getOrDefault(level, 0);

			mokhaiotl += kills * rates.mokhaiotlCloth;
			eye += kills * rates.eyeOfAyak;
			avernic += kills * rates.avernicTreads;
			dom += kills * rates.dom;
		}

		progress.put("Mokhaiotl cloth", mokhaiotl);
		progress.put("Eye of ayak (uncharged)", eye);
		progress.put("Avernic treads", avernic);
		progress.put("Dom", dom);

		double any = 0;
		if (config.mokhaiotlClothDisplay() == DelveCalculatorConfig.RewardDisplayMode.SHOW) any += mokhaiotl;
		if (config.eyeOfAyakDisplay() == DelveCalculatorConfig.RewardDisplayMode.SHOW) any += eye;
		if (config.avernicTreadsDisplay() == DelveCalculatorConfig.RewardDisplayMode.SHOW) any += avernic;
		if (config.domDisplay() == DelveCalculatorConfig.RewardDisplayMode.SHOW) any += dom;
		progress.put("Any Item", any);

		return progress;
	}

	private Map<String, DelveCalculatorConfig.RewardDisplayMode> getDisplayModes()
	{
		Map<String, DelveCalculatorConfig.RewardDisplayMode> modes = new HashMap<>();
		modes.put("Any Item", DelveCalculatorConfig.RewardDisplayMode.SHOW);
		modes.put("Mokhaiotl cloth", config.mokhaiotlClothDisplay());
		modes.put("Eye of ayak (uncharged)", config.eyeOfAyakDisplay());
		modes.put("Avernic treads", config.avernicTreadsDisplay());
		modes.put("Dom", config.domDisplay());
		return modes;
	}

	private Color calculateProgressColor(int barValue)
	{
		// Even Darker colors for white text contrast
		// Max brightness ~100 instead of 180 (original 255)
		if (barValue <= 50) return new Color(100, (int) (100 * (barValue / 50.0f)), 0);
		else return new Color((int) (100 * (1.0f - (barValue - 50) / 50.0f)), 100, 0);
	}

	private JPanel createProgressPanel()
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(5, 5, 5, 5));

		int row = 0;
		addAnyItemProgressBar(panel, row++);
		addProgressBar(panel, "Mokhaiotl cloth", ItemID.MOKHAIOTL_CLOTH, row++);
		addProgressBar(panel, "Eye of ayak (uncharged)", ItemID.EYE_OF_AYAK_UNCHARGED, row++);
		addProgressBar(panel, "Avernic treads", ItemID.AVERNIC_TREADS, row++);
		addProgressBar(panel, "Dom", ItemID.DOM, row++);

		return panel;
	}

	private JPanel createLevelPanel(String labelText)
	{
		JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		levelPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JLabel levelLabel = new JLabel(labelText);
		levelLabel.setPreferredSize(new Dimension(52, 16));
		levelLabel.setForeground(Color.YELLOW);
		levelLabel.setFont(FontManager.getRunescapeFont());
		JLabel levelValue = new JLabel("0");
		levelValue.setForeground(Color.WHITE);
		levelValue.setFont(FontManager.getRunescapeFont());
		levelPanel.add(levelLabel);
		levelPanel.add(levelValue);
		return levelPanel;
	}

	private void addProgressBar(JPanel parent, String itemName, int itemId, int row)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 0, 2, 0);

		ImageIcon icon = new ImageIcon(itemManager.getImage(itemId));
		JLabel iconLabel = new JLabel(icon);
		iconLabel.setPreferredSize(new Dimension(32, 32));
		c.gridx = 0;
		c.gridy = row;
		c.anchor = GridBagConstraints.WEST;
		parent.add(iconLabel, c);

		CustomProgressBar progressBar = new CustomProgressBar();
		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		c.insets = new Insets(2, 5, 2, 5);
		parent.add(progressBar, c);

		JLabel expectedLabel = createExpectedLabel();
		c.gridx = 2;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(2, 0, 2, 0);
		parent.add(expectedLabel, c);

		ProgressRow progressRow = new ProgressRow();
		progressRow.iconLabel = iconLabel;
		progressRow.progressBar = progressBar;
		progressRow.expectedLabel = expectedLabel;
		progressRow.originalIcon = icon;
		progressRows.put(itemName, progressRow);
	}

	private void addAnyItemProgressBar(JPanel parent, int row)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 0, 2, 0);

		JPanel iconPanel = new JPanel(new GridBagLayout());
		iconPanel.setPreferredSize(new Dimension(32, 32));
		iconPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JLabel iconLabelText = new JLabel("Any");
		iconLabelText.setForeground(Color.YELLOW);
		iconLabelText.setFont(FontManager.getRunescapeFont());
		iconPanel.add(iconLabelText);
		c.gridx = 0;
		c.gridy = row;
		c.anchor = GridBagConstraints.WEST;
		parent.add(iconPanel, c);

		CustomProgressBar progressBar = new CustomProgressBar();
		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		c.insets = new Insets(2, 5, 2, 5);
		parent.add(progressBar, c);

		JLabel expectedLabel = createExpectedLabel();
		c.gridx = 2;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(2, 0, 2, 0);
		parent.add(expectedLabel, c);

		ProgressRow progressRow = new ProgressRow();
		progressRow.iconLabel = null; // Special case for Any
		progressRow.progressBar = progressBar;
		progressRow.expectedLabel = expectedLabel;
		progressRow.originalIcon = null;
		progressRows.put("Any Item", progressRow);
		
		// Map iconPanel to iconLabel for visibility toggling
		progressRow.iconLabel = iconLabelText; 
	}


	private JLabel createExpectedLabel()
	{
		JLabel expectedLabel = new JLabel("0");
		expectedLabel.setForeground(Color.WHITE);
		expectedLabel.setFont(FontManager.getRunescapeFont());
		expectedLabel.setBorder(new EmptyBorder(0, 5, 0, 0));
		expectedLabel.setHorizontalAlignment(SwingConstants.LEFT);
		return expectedLabel;
	}

	private JLabel createCircularHelpLabel()
	{
		JLabel label = new JLabel("?") {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getForeground());
				g2.drawOval(2, 2, getWidth() - 5, getHeight() - 5);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setPreferredSize(new Dimension(20, 20));
		return label;
	}

	private JPanel createNoDataSection()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		JLabel noDataLabel = new JLabel("No data, read scoreboard outside the bossroom");
		noDataLabel.setForeground(Color.ORANGE);
		noDataLabel.setFont(FontManager.getRunescapeFont());
		noDataLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		panel.add(noDataLabel);
		panel.setVisible(false);
		return panel;
	}

	private void confirmResetManualData()
	{
		int option = JOptionPane.showOptionDialog(
				this,
				"This will ONLY reset the data in the Manual tab. Proceed?",
				"Reset Manual Data",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE,
				null,
				new Object[]{"Reset", "Cancel"},
				"Cancel"
		);

		if (option == JOptionPane.YES_OPTION)
		{
			DelveCalculatorData.DelveProfile manualProfile = data.getProfiles().get(getManualProfileKey());
			if (manualProfile != null)
			{
				manualProfile.getLevelKills().clear();
				manualProfile.setWavesPast8(0);
				manualProfile.getObtainedUniques().clear();
				saveData();
				updateAllUI();
			}
		}
	}
}