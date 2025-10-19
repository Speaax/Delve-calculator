package com.speaax;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DelveCalculatorPanel extends PluginPanel
{
	private final ItemManager itemManager;
	private final DelveCalculatorConfig config;
	private final Gson gson;

	private final JLabel totalKillsLabel;
	private final JPanel progressPanel;
	private final JPanel noDataSectionPanel;

	private final Map<Integer, JLabel> levelValueLabels = new HashMap<>();
	private final Map<String, JPanel> itemPanels = new HashMap<>(); // To store and manage each reward panel

	private Map<Integer, Integer> currentLevelKills = new HashMap<>();
	private int currentWavesPast8 = 0;

	public DelveCalculatorPanel(DelveCalculatorPlugin plugin, DelveCalculatorConfig config, Gson gson)
	{
		this.itemManager = plugin.getItemManager();
		this.config = config;
		this.gson = gson;

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

		JPanel levelsPanel = new JPanel(new GridLayout(10, 1, 5, 2));
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

		// Progress and No Data Panels
		progressPanel = createProgressPanel();
		noDataSectionPanel = createNoDataSection();

		// Add components to main panel
		add(titleLabel, BorderLayout.NORTH);
		contentPanel.add(killCountsPanel);
		contentPanel.add(Box.createVerticalStrut(10));
		contentPanel.add(progressPanel);
		contentPanel.add(Box.createVerticalStrut(10));
		contentPanel.add(noDataSectionPanel);
		add(contentPanel, BorderLayout.CENTER);

		loadData();
		updateAllUI();
	}


	private void loadData()
	{
		String json = config.killCountData();
		if (json == null || json.isEmpty()) return;

		try
		{
			java.lang.reflect.Type type = new TypeToken<DelveCalculatorData>() {}.getType();
			DelveCalculatorData data = gson.fromJson(json, type);

			if (data != null)
			{
				this.currentLevelKills = data.getLevelKills() != null ? data.getLevelKills() : new HashMap<>();
				this.currentWavesPast8 = data.getWavesPast8();
			}
		}
		catch (Exception e) { log.debug("Error loading Delve Calculator data", e); }
	}

	private void saveData()
	{
		DelveCalculatorData data = new DelveCalculatorData();
		data.setLevelKills(currentLevelKills);
		data.setWavesPast8(currentWavesPast8);

		String json = gson.toJson(data);
		config.killCountData(json);
	}

	public void incrementFloorKills(int floor)
	{
		if (floor >= 1 && floor <= 8)
		{
			currentLevelKills.merge(floor, 1, Integer::sum);
			saveData();
			SwingUtilities.invokeLater(this::updateAllUI);
		}
	}

	public void incrementWavesPast8()
	{
		currentWavesPast8++;
		saveData();
		SwingUtilities.invokeLater(this::updateAllUI);
	}

	public void updateData(Map<Integer, Integer> levelKills, int wavesPast8)
	{
		this.currentLevelKills = new HashMap<>(levelKills);
		this.currentWavesPast8 = wavesPast8;
		saveData();
		SwingUtilities.invokeLater(this::updateAllUI);
	}

	public void updateAllUI()
	{
		int totalKills = currentLevelKills.values().stream().mapToInt(Integer::intValue).sum() + currentWavesPast8;
		totalKillsLabel.setText(String.valueOf(totalKills));

		for (int i = 1; i <= 8; i++)
		{
			levelValueLabels.get(i).setText(String.valueOf(currentLevelKills.getOrDefault(i, 0)));
		}
		levelValueLabels.get(9).setText(String.valueOf(currentWavesPast8));

		updateProgressBars();

		boolean hasData = totalKills > 0;
		noDataSectionPanel.setVisible(!hasData);
	}
	private JPanel createProgressPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(5, 5, 5, 5));

		JLabel headerLabel = new JLabel("Expected Drops:");
		headerLabel.setForeground(Color.YELLOW);
		headerLabel.setFont(FontManager.getRunescapeBoldFont());
		headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		panel.add(headerLabel);
		panel.add(Box.createVerticalStrut(5));

		// Create and store each panel
		itemPanels.put("Any Item", addAnyItemProgressBar(panel));
		itemPanels.put("Mokhaiotl Cloth", addProgressBar(panel, "Mokhaiotl Cloth", ItemID.MOKHAIOTL_CLOTH));
		itemPanels.put("Eye of Ayak", addProgressBar(panel, "Eye of Ayak", ItemID.EYE_OF_AYAK));
		itemPanels.put("Avernic Treads", addProgressBar(panel, "Avernic Treads", ItemID.AVERNIC_TREADS));
		itemPanels.put("Dom", addProgressBar(panel, "Dom", ItemID.DOMPET));

		return panel;
	}
	private JPanel createLevelPanel(String labelText)
	{
		JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		levelPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel levelLabel = new JLabel(labelText);
		levelLabel.setForeground(Color.YELLOW);
		levelLabel.setFont(FontManager.getRunescapeFont());

		JLabel levelValue = new JLabel("0");
		levelValue.setForeground(Color.WHITE);
		levelValue.setFont(FontManager.getRunescapeFont());

		levelPanel.add(levelLabel);
		levelPanel.add(levelValue);

		return levelPanel;
	}
	private JPanel addProgressBar(JPanel parent, String itemName, int itemId)
	{
		JPanel itemPanel = new JPanel(new BorderLayout(5, 0));
		itemPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		itemPanel.setBorder(new EmptyBorder(2, 0, 2, 0));

		ImageIcon icon = new ImageIcon(itemManager.getImage(itemId));
		JLabel iconLabel = new JLabel(icon);
		iconLabel.setPreferredSize(new Dimension(32, 32));

		JProgressBar progressBar = createProgressBar();
		JLabel expectedLabel = createExpectedLabel();

		// Store components for easy access
		itemPanel.putClientProperty("originalIcon", icon);
		itemPanel.putClientProperty("iconLabel", iconLabel);
		itemPanel.putClientProperty("progressBar", progressBar);
		itemPanel.putClientProperty("expectedLabel", expectedLabel);
		itemPanel.putClientProperty("itemName", itemName);

		itemPanel.add(iconLabel, BorderLayout.WEST);
		itemPanel.add(progressBar, BorderLayout.CENTER);
		itemPanel.add(expectedLabel, BorderLayout.EAST);

		parent.add(itemPanel);
		return itemPanel;
	}
	private JPanel addAnyItemProgressBar(JPanel parent)
	{
		JPanel anyItemPanel = new JPanel(new BorderLayout(5, 0));
		anyItemPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		anyItemPanel.setBorder(new EmptyBorder(2, 0, 2, 0));

		JPanel iconPanel = new JPanel(new GridBagLayout());
		iconPanel.setPreferredSize(new Dimension(32, 32));
		iconPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JLabel iconLabel = new JLabel("Any");
		iconLabel.setForeground(Color.YELLOW);
		iconLabel.setFont(FontManager.getRunescapeFont());
		iconPanel.add(iconLabel);

		JProgressBar progressBar = createProgressBar();
		JLabel expectedLabel = createExpectedLabel();

		anyItemPanel.putClientProperty("progressBar", progressBar);
		anyItemPanel.putClientProperty("expectedLabel", expectedLabel);
		anyItemPanel.putClientProperty("itemName", "Any Item");

		anyItemPanel.add(iconPanel, BorderLayout.WEST);
		anyItemPanel.add(progressBar, BorderLayout.CENTER);
		anyItemPanel.add(expectedLabel, BorderLayout.EAST);

		parent.add(anyItemPanel);
		return anyItemPanel;
	}
	private JProgressBar createProgressBar()
	{
		JProgressBar progressBar = new JProgressBar(0, 100);
		progressBar.setValue(0);
		progressBar.setStringPainted(true);
		progressBar.setString("0.0%");
		progressBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		progressBar.setBorder(BorderFactory.createLineBorder(ColorScheme.LIGHT_GRAY_COLOR, 1));
		return progressBar;
	}
	private JLabel createExpectedLabel()
	{
		JLabel expectedLabel = new JLabel("0");
		expectedLabel.setForeground(Color.WHITE);
		expectedLabel.setFont(FontManager.getRunescapeFont());
		expectedLabel.setBorder(new EmptyBorder(0, 5, 0, 0));
		expectedLabel.setHorizontalAlignment(SwingConstants.CENTER);
		return expectedLabel;
	}
	private void updateProgressBars()
	{
		Map<String, Double> itemProgress = calculateItemProgress();
		Map<String, DelveCalculatorConfig.RewardDisplayMode> displayModes = getDisplayModes();

		for (Map.Entry<String, JPanel> entry : itemPanels.entrySet())
		{
			String itemName = entry.getKey();
			JPanel itemPanel = entry.getValue();
			DelveCalculatorConfig.RewardDisplayMode mode = displayModes.getOrDefault(itemName, DelveCalculatorConfig.RewardDisplayMode.SHOW);

			// Handle visibility
			if (mode == DelveCalculatorConfig.RewardDisplayMode.HIDE)
			{
				itemPanel.setVisible(false);
				continue;
			}
			itemPanel.setVisible(true);

			// Get components
			JProgressBar progressBar = (JProgressBar) itemPanel.getClientProperty("progressBar");
			JLabel expectedLabel = (JLabel) itemPanel.getClientProperty("expectedLabel");
			JLabel iconLabel = (JLabel) itemPanel.getClientProperty("iconLabel");

			// Update progress values
			double progress = itemProgress.getOrDefault(itemName, 0.0);
			double expectedItems = Math.floor(progress);
			double progressTowardsNextItem = (progress - expectedItems) * 100;
			int barValue = (int) progressTowardsNextItem;

			expectedLabel.setText(String.valueOf((int)expectedItems));
			progressBar.setValue(barValue);
			progressBar.setString(String.format("%.1f%%", progressTowardsNextItem));

			// Handle display mode styling
			if (mode == DelveCalculatorConfig.RewardDisplayMode.GREY)
			{
				progressBar.setForeground(Color.GRAY);
				expectedLabel.setForeground(Color.GRAY);
				if (iconLabel != null) // "Any" panel has no iconLabel property with an image
				{
					ImageIcon originalIcon = (ImageIcon) itemPanel.getClientProperty("originalIcon");
					if (originalIcon != null)
					{
						// Create and set a greyscale version of the icon
						BufferedImage greyImage = ImageUtil.grayscaleImage(ImageUtil.bufferedImageFromImage(originalIcon.getImage()));
						iconLabel.setIcon(new ImageIcon(greyImage));
					}
				}
			}
			else // SHOW mode
			{
				progressBar.setForeground(calculateProgressColor(barValue));
				expectedLabel.setForeground(Color.WHITE);
				if (iconLabel != null)
				{
					// Restore the original colored icon
					iconLabel.setIcon((ImageIcon) itemPanel.getClientProperty("originalIcon"));
				}
			}
		}
	}

	private Map<String, DelveCalculatorConfig.RewardDisplayMode> getDisplayModes()
	{
		Map<String, DelveCalculatorConfig.RewardDisplayMode> modes = new HashMap<>();
		modes.put("Any Item", DelveCalculatorConfig.RewardDisplayMode.SHOW);
		modes.put("Mokhaiotl Cloth", config.mokhaiotlClothDisplay());
		modes.put("Eye of Ayak", config.eyeOfAyakDisplay());
		modes.put("Avernic Treads", config.avernicTreadsDisplay());
		modes.put("Dom", config.domDisplay());
		return modes;
	}

	private Map<String, Double> calculateItemProgress()
	{
		Map<String, Double> progress = new HashMap<>();

		double mokhaiotlClothProgress = 0;
		double eyeOfAyakProgress = 0;
		double avernicTreadsProgress = 0;
		double domProgress = 0;

		for (Map.Entry<Integer, DelveCalculatorPlugin.DropRates> entry : DelveCalculatorPlugin.getDropRates().entrySet()) {
			int level = entry.getKey();
			DelveCalculatorPlugin.DropRates rates = entry.getValue();
			int kills = (level == 9) ? currentWavesPast8 : currentLevelKills.getOrDefault(level, 0);

			mokhaiotlClothProgress += kills * rates.mokhaiotlCloth;
			eyeOfAyakProgress += kills * rates.eyeOfAyak;
			avernicTreadsProgress += kills * rates.avernicTreads;
			domProgress += kills * rates.dom;
		}

		progress.put("Mokhaiotl Cloth", mokhaiotlClothProgress);
		progress.put("Eye of Ayak", eyeOfAyakProgress);
		progress.put("Avernic Treads", avernicTreadsProgress);
		progress.put("Dom", domProgress);

		// Calculate "Any Item" progress based on which items are set to SHOW
		double anyItemProgress = 0;
		if (config.mokhaiotlClothDisplay() == DelveCalculatorConfig.RewardDisplayMode.SHOW) anyItemProgress += mokhaiotlClothProgress;
		if (config.eyeOfAyakDisplay() == DelveCalculatorConfig.RewardDisplayMode.SHOW) anyItemProgress += eyeOfAyakProgress;
		if (config.avernicTreadsDisplay() == DelveCalculatorConfig.RewardDisplayMode.SHOW) anyItemProgress += avernicTreadsProgress;
		if (config.domDisplay() == DelveCalculatorConfig.RewardDisplayMode.SHOW) anyItemProgress += domProgress;

		progress.put("Any Item", anyItemProgress);

		return progress;
	}
	private Color calculateProgressColor(int barValue)
	{
		// Simple red-to-green gradient
		if (barValue <= 50) return new Color(255, (int) (255 * (barValue / 50.0f)), 0);
		else return new Color((int) (255 * (1.0f - (barValue - 50) / 50.0f)), 255, 0);
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
}