package com.speaax;

import net.runelite.client.config.*;

@ConfigGroup("delvecalculator")
public interface DelveCalculatorConfig extends Config
{
    // --- General Settings ---
    @ConfigSection(
            name = "General",
            description = "General panel visibility settings",
            position = 0,
            closedByDefault = false
    )
    String generalSettings = "generalSettings";

    @ConfigItem(
            keyName = "alwaysShowPanel",
            name = "Show Everywhere",
            description = "Keeps the panel icon visible at all times. If off, it will only appear when specific conditions below are met.",
            section = generalSettings,
            position = 1
    )
    default boolean alwaysShowPanel()
    {
        return true;
    }


    // --- Region Settings ---
    @ConfigSection(
            name = "Region Settings",
            description = "Make the panel appear when you are in the Delve region.",
            position = 1
    )
    String regionSettings = "regionSettings";

    @ConfigItem(
            keyName = "showInRegion",
            name = "Show in Region",
            description = "Shows the panel icon when you are inside the Delve region.",
            section = regionSettings,
            position = 2
    )
    default boolean showInRegion()
    {
        return false;
    }

    @ConfigItem(
            keyName = "autoOpenInRegion",
            name = "Auto Open in Region",
            description = "Automatically open the panel when a new Region session begins.",
            section = regionSettings,
            position = 3
    )
    default boolean autoOpenInRegion()
    {
        return false;
    }

    @ConfigItem(
            keyName = "regionTimeout",
            name = "Region Session Timeout",
            description = "Session timer in minutes. The icon remains visible during this time, even if you leave to bank.",
            section = regionSettings,
            position = 4
    )
    @Units(Units.MINUTES)
    default int regionTimeout()
    {
        return 10;
    }


    // --- Scoreboard Settings ---
    @ConfigSection(
            name = "Scoreboard Settings",
            description = "Make the panel appear when the Delve scoreboard is open.",
            position = 2
    )
    String scoreboardSettings = "scoreboardSettings";

    @ConfigItem(
            keyName = "showOnScoreboard",
            name = "Show on Scoreboard",
            description = "Shows the panel icon when the Delve scoreboard is open.",
            section = scoreboardSettings,
            position = 5
    )
    default boolean showOnScoreboard()
    {
        return false;
    }

    @ConfigItem(
            keyName = "autoOpenOnScoreboard",
            name = "Auto Open on Scoreboard",
            description = "Automatically open the panel when you open the Delve scoreboard.",
            section = scoreboardSettings,
            position = 6
    )
    default boolean autoOpenOnScoreboard()
    {
        return true;
    }


    // --- Hidden Data ---
    @ConfigItem(
            keyName = "killCountData",
            name = "",
            description = "",
            hidden = true
    )
    default String killCountData() { return ""; }

    @ConfigItem(
            keyName = "killCountData",
            name = "",
            description = ""
    )
    void killCountData(String data);
}