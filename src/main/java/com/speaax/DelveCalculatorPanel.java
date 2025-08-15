package com.speaax;

//import net.runelite.api.ItemID;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelveCalculatorPanel extends PluginPanel
{
	private final DelveCalculatorPlugin plugin;
	private final ItemManager itemManager;
	
	private static final String DATA_FOLDER_NAME = "delve-calculator";
	private static final Path DATA_FOLDER_PATH = Paths.get(System.getProperty("user.home"), ".runelite", DATA_FOLDER_NAME);
	
	private JLabel totalKillsLabel;
	private JPanel progressPanel;
	private JPanel killCountsPanel;
	private JPanel noDataSectionPanel; // New field for the no data section panel
	
	// Store references to level value labels for easy updating
	private Map<Integer, JLabel> levelValueLabels = new HashMap<>();
	
	private Map<Integer, Integer> currentLevelKills = new HashMap<>();
	private int currentWavesPast8 = 0;
	
	// Method to get the data file path
	private Path getDataFilePath() {
		return DATA_FOLDER_PATH.resolve("delve-calculator.properties");
	}
	
	// Method to ensure data directory exists
	private boolean ensureDataDirectory() {
		try {
			if (!Files.exists(DATA_FOLDER_PATH)) {
				Files.createDirectories(DATA_FOLDER_PATH);
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public DelveCalculatorPanel(DelveCalculatorPlugin plugin)
	{
		this.plugin = plugin;
		this.itemManager = plugin.getItemManager();
		
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());
		
		// Ensure data directory exists and create initial data file if needed
		ensureDataDirectory();
		
		// Load saved data
		loadData();
		
		// If no data exists, create an initial empty data file
		if (currentLevelKills.isEmpty() && currentWavesPast8 == 0)
		{
			saveData();
		}
		
		buildPanel();
		
		// Update the total kills label with loaded data
		int totalKills = currentLevelKills.values().stream().mapToInt(Integer::intValue).sum() + currentWavesPast8;
		totalKillsLabel.setText(String.valueOf(totalKills));
		
		// Update level kill labels with loaded data
		updateLevelKillLabels();
		
		// Update progress bars with loaded data
		updateProgressBars();
	}

	private void buildPanel()
	{
		// Title
		JLabel titleLabel = new JLabel("Delve Calculator");
		titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(24.0f)); // Bigger font
		titleLabel.setForeground(Color.YELLOW);
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		titleLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
		
		// Add components
		add(titleLabel, BorderLayout.NORTH);
		
		JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		
		// Kill counts section
		killCountsPanel = createKillCountsPanel();
		
		// Progress section - always create this panel, it handles data/no-data internally
		progressPanel = createProgressPanel();
		
		// No data section - create but don't add yet
		noDataSectionPanel = createNoDataSection();
		
		contentPanel.add(killCountsPanel);
		contentPanel.add(Box.createVerticalStrut(10));
		contentPanel.add(progressPanel);
		
		// Conditionally add the no data section
		if (currentLevelKills.isEmpty() && currentWavesPast8 == 0)
		{
			contentPanel.add(Box.createVerticalStrut(10));
			contentPanel.add(noDataSectionPanel);
		}
		
		add(contentPanel, BorderLayout.CENTER);
		
		// Just revalidate and repaint
		revalidate();
		repaint();
	}

	private JPanel createKillCountsPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(5, 5, 5, 5));
		
		// Total Kills header
		JPanel totalKillsPanel = new JPanel();
		totalKillsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
		totalKillsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		
		JLabel totalKillsLabelText = new JLabel("Total Levels: ");
		totalKillsLabelText.setForeground(Color.YELLOW);
		totalKillsLabelText.setFont(FontManager.getRunescapeBoldFont());
		
		totalKillsLabel = new JLabel("0");
		totalKillsLabel.setForeground(Color.WHITE);
		totalKillsLabel.setFont(FontManager.getRunescapeBoldFont());
		
		totalKillsPanel.add(totalKillsLabelText);
		totalKillsPanel.add(totalKillsLabel);
		
		panel.add(totalKillsPanel);
		panel.add(Box.createVerticalStrut(5));
		
		// Create a grid panel for the level kills
		JPanel levelsPanel = new JPanel();
		levelsPanel.setLayout(new GridLayout(10, 1, 5, 2)); // Changed from 9 to 10 rows
		levelsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		
		// Add labels for each level (1-8)
		for (int i = 1; i <= 8; i++)
		{
			JPanel levelPanel = createLevelPanel("Level " + i + ": ", "0");
			levelValueLabels.put(i, (JLabel) levelPanel.getComponent(1)); // Store the value label
			levelsPanel.add(levelPanel);
		}
		
		// Add Level + (waves past 8) as a separate entry
		JPanel levelPlusPanel = createLevelPanel("Level +: ", "0");
		levelValueLabels.put(9, (JLabel) levelPlusPanel.getComponent(1)); // Store the value label (using index 9 for Level +)
		levelsPanel.add(levelPlusPanel);
		
		panel.add(levelsPanel);
		
		return panel;
	}
	
	private JPanel createLevelPanel(String labelText, String valueText)
	{
		JPanel levelPanel = new JPanel();
		levelPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
		levelPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		
		JLabel levelLabel = new JLabel(labelText);
		levelLabel.setForeground(Color.YELLOW);
		levelLabel.setFont(FontManager.getRunescapeFont());
		
		JLabel levelValue = new JLabel(valueText);
		levelValue.setForeground(Color.WHITE);
		levelValue.setFont(FontManager.getRunescapeFont());
		
		levelPanel.add(levelLabel);
		levelPanel.add(levelValue);
		
		return levelPanel;
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
		
		// Always show the complete expected drops table
		// Add Any Item Progress at the top (highest percentage)
		addAnyItemProgressBar(panel);
		
		// Add progress bars for each item with their in-game icons
		addProgressBar(panel, "Mokhaiotl Cloth", ItemID.MOKHAIOTL_CLOTH); // Placeholder - replace with actual item ID
		addProgressBar(panel, "Eye of Ayak", ItemID.EYE_OF_AYAK); // Placeholder - replace with actual item ID
		addProgressBar(panel, "Avernic Treads", ItemID.AVERNIC_TREADS); // Placeholder - replace with actual item ID
		addProgressBar(panel, "Dom", ItemID.DOMPET); // Placeholder - replace with actual item ID
		
		return panel;
	}

	private void addProgressBar(JPanel parent, String itemName, int itemId)
	{
		JPanel itemPanel = new JPanel();
		itemPanel.setLayout(new BorderLayout(5, 0));
		itemPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		itemPanel.setBorder(new EmptyBorder(2, 0, 2, 0));
		
		// Item icon
		JLabel iconLabel = new JLabel();
		iconLabel.setIcon(new ImageIcon(itemManager.getImage(itemId)));
		iconLabel.setBorder(new EmptyBorder(0, 0, 0, 5));
		iconLabel.setPreferredSize(new Dimension(32, 32));
		
		// Progress bar
		JProgressBar progressBar = createProgressBar();
		
		// Expected items count
		JLabel expectedLabel = createExpectedLabel();
		
		// Store references for updating later
		itemPanel.putClientProperty("progressBar", progressBar);
		itemPanel.putClientProperty("expectedLabel", expectedLabel);
		itemPanel.putClientProperty("itemName", itemName);
		itemPanel.putClientProperty("itemId", itemId);
		
		itemPanel.add(iconLabel, BorderLayout.WEST);
		itemPanel.add(progressBar, BorderLayout.CENTER);
		itemPanel.add(expectedLabel, BorderLayout.EAST);
		
		parent.add(itemPanel);
		parent.add(Box.createVerticalStrut(2));
	}
	
	private JProgressBar createProgressBar()
	{
		JProgressBar progressBar = new JProgressBar(0, 100);
		progressBar.setValue(0);
		progressBar.setStringPainted(true);
		progressBar.setString("0.0%");
		progressBar.setForeground(Color.RED);
		progressBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		progressBar.setBorder(BorderFactory.createLineBorder(ColorScheme.LIGHT_GRAY_COLOR, 1));
		progressBar.setPreferredSize(new Dimension(150, 20));
		return progressBar;
	}
	
	private JLabel createExpectedLabel()
	{
		JLabel expectedLabel = new JLabel("0");
		expectedLabel.setForeground(Color.WHITE);
		expectedLabel.setFont(FontManager.getRunescapeFont());
		expectedLabel.setBorder(new EmptyBorder(0, 5, 0, 0));
		expectedLabel.setPreferredSize(new Dimension(30, 20));
		expectedLabel.setHorizontalAlignment(SwingConstants.CENTER);
		return expectedLabel;
	}

	private void loadData()
	{
		try
		{
			Path dataFilePath = getDataFilePath();
			
			if (Files.exists(dataFilePath))
			{
				Properties props = new Properties();
				try (FileInputStream fis = new FileInputStream(dataFilePath.toFile())) {
					props.load(fis);
				}
				
				// Load level kills (level1-level8)
				for (int i = 1; i <= 8; i++) {
					String value = props.getProperty("level" + i, "0");
					try {
						currentLevelKills.put(i, Integer.parseInt(value));
					} catch (NumberFormatException e) {
						currentLevelKills.put(i, 0);
					}
				}
				
				// Load waves past 8
				String wavesPast8Value = props.getProperty("level8+", "0");
				try {
					currentWavesPast8 = Integer.parseInt(wavesPast8Value);
				} catch (NumberFormatException e) {
					currentWavesPast8 = 0;
				}
			}
		}
		catch (Exception e)
		{
			// Silently handle loading errors
		}
	}
	
	private void saveData()
	{
		try
		{
			// Ensure the delve-calculator directory exists
			Files.createDirectories(DATA_FOLDER_PATH);
			
			// Get the data file path
			Path dataFilePath = getDataFilePath();
			
			// Create properties object
			Properties props = new Properties();
			
			// Save level kills (level1-level8)
			for (int i = 1; i <= 8; i++) {
				int kills = currentLevelKills.getOrDefault(i, 0);
				props.setProperty("level" + i, String.valueOf(kills));
			}
			
			// Save waves past 8
			props.setProperty("level8+", String.valueOf(currentWavesPast8));
			
			// Write to file
			try (FileOutputStream fos = new FileOutputStream(dataFilePath.toFile())) {
				props.store(fos, "Delve Calculator Data");
			}
			
			// Debug logging
			log.info("Delve tracker data saved successfully to: {}", dataFilePath);
		}
		catch (Exception e)
		{
			log.error("Failed to save delve tracker data: {}", e.getMessage(), e);
		}
	}
	

	
	public void incrementFloorKills(int floor)
	{
		if (floor >= 1 && floor <= 8)
		{
			int currentKills = currentLevelKills.getOrDefault(floor, 0);
			currentLevelKills.put(floor, currentKills + 1);
			
			// Save data immediately
			saveData();
			
			// Update the total kills label
			int totalKills = currentLevelKills.values().stream().mapToInt(Integer::intValue).sum() + currentWavesPast8;
			totalKillsLabel.setText(String.valueOf(totalKills));
			
			// Update level kill labels
			updateLevelKillLabels();
			
			// Update progress bars
			updateProgressBars();
			
			// Handle no data section visibility
			updateNoDataSectionVisibility();
			
			// Just revalidate and repaint
			revalidate();
			repaint();
		}
	}

	public void incrementWavesPast8()
	{
		currentWavesPast8++;
		
		// Save data immediately
		saveData();
		
		// Update the total kills label
		int totalKills = currentLevelKills.values().stream().mapToInt(Integer::intValue).sum() + currentWavesPast8;
		totalKillsLabel.setText(String.valueOf(totalKills));
		
		// Update level kill labels
		updateLevelKillLabels();
		
		// Update progress bars
		updateProgressBars();
		
		// Handle no data section visibility
		updateNoDataSectionVisibility();
		
		// Just revalidate and repaint
		revalidate();
		repaint();
	}
	
	private void addAnyItemProgressBar(JPanel parent)
	{
		JPanel anyItemPanel = new JPanel();
		anyItemPanel.setLayout(new BorderLayout(5, 0));
		anyItemPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		anyItemPanel.setBorder(new EmptyBorder(2, 0, 2, 0));
		
		// "Any" text instead of empty space - use GridBagLayout for precise centering
		JPanel iconPanel = new JPanel();
		iconPanel.setLayout(new GridBagLayout());
		iconPanel.setPreferredSize(new Dimension(32, 32));
		iconPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		iconPanel.setBorder(new EmptyBorder(0, 0, 0, 5));
		
		JLabel iconLabel = new JLabel("Any");
		iconLabel.setForeground(Color.YELLOW);
		iconLabel.setFont(FontManager.getRunescapeFont());
		
		// Use GridBagConstraints to center the label exactly
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.NONE;
		
		iconPanel.add(iconLabel, gbc);
		
		// Progress bar
		JProgressBar progressBar = createProgressBar();
		
		// Expected items count (as integer)
		JLabel expectedLabel = createExpectedLabel();
		
		// Store references for updating later
		anyItemPanel.putClientProperty("progressBar", progressBar);
		anyItemPanel.putClientProperty("expectedLabel", expectedLabel);
		anyItemPanel.putClientProperty("itemName", "Any Item");
		anyItemPanel.putClientProperty("itemId", -1); // Special ID for Any Item
		
		anyItemPanel.add(iconPanel, BorderLayout.WEST);
		anyItemPanel.add(progressBar, BorderLayout.CENTER);
		anyItemPanel.add(expectedLabel, BorderLayout.EAST);
		
		parent.add(anyItemPanel);
		parent.add(Box.createVerticalStrut(2));
	}
	
	public void updateData(Map<Integer, Integer> levelKills, int wavesPast8)
	{
		log.info("Panel.updateData called with levelKills: {}, wavesPast8: {}", levelKills, wavesPast8);
		
		this.currentLevelKills = levelKills;
		this.currentWavesPast8 = wavesPast8;
		
		// Save data when widget data is updated
		log.info("Calling saveData() to save to properties file...");
		saveData();
		
		// Update the total kills label
		int totalKills = currentLevelKills.values().stream().mapToInt(Integer::intValue).sum() + currentWavesPast8;
		totalKillsLabel.setText(String.valueOf(totalKills));
		
		// Update level kill labels
		updateLevelKillLabels();
		
		// Update progress bars
		updateProgressBars();
		
		// Handle no data section visibility
		updateNoDataSectionVisibility();
		
		// Just revalidate and repaint instead of rebuilding everything
		revalidate();
		repaint();
		
		log.info("Panel.updateData completed successfully");
	}
	
	private void updateNoDataSectionVisibility()
	{
		boolean hasData = !currentLevelKills.isEmpty() || currentWavesPast8 > 0;
		
		// Get the content panel (which contains all sections)
		Component[] components = getComponents();
		JPanel contentPanel = null;
		for (Component comp : components)
		{
			if (comp instanceof JPanel)
			{
				JPanel panel = (JPanel) comp;
				if (panel.getLayout() instanceof BoxLayout)
				{
					contentPanel = panel;
					break;
				}
			}
		}
		
		if (contentPanel != null)
		{
			if (!hasData)
			{
				// Show no data section if it's not already visible
				if (!contentPanel.isAncestorOf(noDataSectionPanel))
				{
					contentPanel.add(Box.createVerticalStrut(10));
					contentPanel.add(noDataSectionPanel);
				}
			}
			else
			{
				// Hide no data section if it's visible
				if (contentPanel.isAncestorOf(noDataSectionPanel))
				{
					contentPanel.remove(noDataSectionPanel);
					// Remove the spacing above it too
					for (int i = 0; i < contentPanel.getComponentCount(); i++)
					{
						Component comp = contentPanel.getComponent(i);
						if (comp instanceof Box.Filler && i + 1 < contentPanel.getComponentCount() && 
							contentPanel.getComponent(i + 1) == noDataSectionPanel)
						{
							contentPanel.remove(i);
							break;
						}
					}
				}
			}
		}
	}
	
	private void updateProgressBars()
	{
		// Calculate progress for each item based on current kills
		Map<String, Double> itemProgress = calculateItemProgress();
		
		// Update each progress bar
		for (Component comp : progressPanel.getComponents())
		{
			if (comp instanceof JPanel)
			{
				JPanel itemPanel = (JPanel) comp;
				String itemName = (String) itemPanel.getClientProperty("itemName");
				JProgressBar progressBar = (JProgressBar) itemPanel.getClientProperty("progressBar");
				JLabel expectedLabel = (JLabel) itemPanel.getClientProperty("expectedLabel");
				
				if (itemName != null && progressBar != null && expectedLabel != null)
				{
					double progress;
					if ("Any Item".equals(itemName))
					{
						// Calculate total probability of getting any unique item
						progress = itemProgress.values().stream().mapToDouble(Double::doubleValue).sum();
					}
					else
					{
						progress = itemProgress.getOrDefault(itemName, 0.0);
					}
					
					// Calculate expected items (can be fractional)
					double expectedItems = Math.floor(progress);
					expectedLabel.setText(String.valueOf((int)expectedItems));
					
					// Calculate progress bar percentage for the current item being worked on
					// If you have 2.2 items, show progress towards the 3rd item (0.2 = 20%)
					double progressTowardsNextItem = progress - Math.floor(progress);
					double progressPercentage = progressTowardsNextItem * 100;
					int barValue = (int) progressPercentage;
					
					progressBar.setValue(barValue);
					progressBar.setString(String.format("%.1f%%", progressPercentage));
					
					// Set color based on progress
					progressBar.setForeground(calculateProgressColor(barValue));
				}
			}
		}
	}
	
	private Map<String, Double> calculateItemProgress()
	{
		Map<String, Double> progress = new HashMap<>();
		
		// Calculate progress for each item by summing up individual floor contributions
		progress.put("Mokhaiotl Cloth", calculateItemProgressForItem("mokhaiotlCloth"));
		progress.put("Eye of Ayak", calculateItemProgressForItem("eyeOfAyak"));
		progress.put("Avernic Treads", calculateItemProgressForItem("avernicTreads"));
		progress.put("Dom", calculateItemProgressForItem("dom"));
		
		return progress;
	}
	
	private double calculateItemProgressForItem(String itemType)
	{
		double totalProgress = 0.0;
		
		// Calculate progress for each floor independently
		for (int floor = 2; floor <= 9; floor++)
		{
			DelveCalculatorPlugin.DropRates rates = DelveCalculatorPlugin.getDropRates().get(floor);
			if (rates == null) continue;
			
			// Get the drop rate for this specific item on this floor
			double itemDropRate = getItemDropRate(rates, itemType);
			if (itemDropRate <= 0) continue; // Item not available on this floor
			
			// Get kills for this floor
			int floorKills = getFloorKills(floor);
			if (floorKills <= 0) continue; // No kills on this floor
			
			// Calculate progress for this floor: (Kills / Expected Kills) * 100%
			double expectedKills = 1.0 / itemDropRate;
			double floorProgress = (floorKills / expectedKills) * 100.0;
			
			totalProgress += floorProgress;
		}
		
		// Convert from percentage to decimal (e.g., 150% = 1.5 items)
		return totalProgress / 100.0;
	}
	
	private double getItemDropRate(DelveCalculatorPlugin.DropRates rates, String itemType)
	{
		switch (itemType)
		{
			case "mokhaiotlCloth":
				return rates.mokhaiotlCloth;
			case "eyeOfAyak":
				return rates.eyeOfAyak;
			case "avernicTreads":
				return rates.avernicTreads;
			case "dom":
				return rates.dom;
			default:
				return 0.0;
		}
	}
	
	private int getFloorKills(int floor)
	{
		if (floor == 9)
		{
			// Level 9+ includes Level 8 kills + waves past 8
			return currentLevelKills.getOrDefault(8, 0) + currentWavesPast8;
		}
		else
		{
			// Regular floors use their specific kill count
			return currentLevelKills.getOrDefault(floor, 0);
		}
	}
	
	private Color calculateProgressColor(int barValue)
	{
		// Smooth color gradient: Red (0%) -> Yellow (50%) -> Green (100%)
		if (barValue <= 50)
		{
			// Red to Yellow gradient (0% to 50%)
			float ratio = barValue / 50.0f;
			int red = 255;
			int green = (int) (255 * ratio);
			int blue = 0;
			return new Color(red, green, blue);
		}
		else
		{
			// Yellow to Green gradient (50% to 100%)
			float ratio = (barValue - 50) / 50.0f;
			int red = (int) (255 * (1.0f - ratio));
			int green = 255;
			int blue = 0;
			return new Color(red, green, blue);
		}
	}
	
	private void updateLevelKillLabels()
	{
		// Update each level kill label
		for (int i = 1; i <= 8; i++)
		{
			int kills = currentLevelKills.getOrDefault(i, 0);
			// Find the level panel and update its value label
			updateLevelPanelValue(i, String.valueOf(kills));
		}
		
		// Update Level + (waves past 8) - show only the waves past 8, not combined with Level 8
		updateLevelPanelValue(9, String.valueOf(currentWavesPast8));
	}
	
	private void updateLevelPanelValue(int levelIndex, String value)
	{
		// Use the stored reference to update the level value label
		JLabel valueLabel = levelValueLabels.get(levelIndex);
		if (valueLabel != null)
		{
			valueLabel.setText(value);
		}
	}

	private JPanel createNoDataSection()
	{
		// Create a separate panel for the no data section
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		
		// Add the no data message
		JLabel noDataLabel = new JLabel("No data, read scoreboard outside the bossroom");
		noDataLabel.setForeground(Color.ORANGE);
		noDataLabel.setFont(FontManager.getRunescapeFont());
		noDataLabel.setHorizontalAlignment(SwingConstants.CENTER);
		noDataLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		panel.add(noDataLabel);
		
		return panel;
	}
}
