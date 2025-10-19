package com.speaax;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("delvecalculator")
public interface DelveCalculatorConfig extends Config
{
    @ConfigItem(
            keyName = "killCountData",
            name = "",
            description = "",
            hidden = true
    )
    default String killCountData()
    {
        return "";
    }
    @ConfigItem(
            keyName = "killCountData",
            name = "",
            description = ""
    )
    void killCountData(String data);

    @ConfigSection(
            name = "Panel Settings",
            description = "Configure when and how the side panel is displayed",
            position = 0
    )
    String panelSettings = "panelSettings";

    enum PanelDisplayMode
    {
        REGION,
        SCOREBOARD,
        ALWAYS
    }

    @ConfigItem(
            keyName = "displayPanel",
            name = "Display Panel",
            description = "Configures when the side panel should be visible.",
            section = panelSettings,
            position = 1
    )
    default PanelDisplayMode displayPanel()
    {
        return PanelDisplayMode.ALWAYS;
    }

    @ConfigItem(
            keyName = "autoOpenPanel",
            name = "Auto Open Panel",
            description = "Automatically open the side panel when the display condition is met. This does not apply to 'Always'.",
            section = panelSettings,
            position = 2
    )
    default boolean autoOpenPanel()
    {
        return true;
    }
}