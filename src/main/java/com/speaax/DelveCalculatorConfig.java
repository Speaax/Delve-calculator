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
            name = "Show Icon Everywhere",
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
            name = "Show Icon in Region",
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
            name = "Hide plugin after",
            description = "<html>Session timer in minutes. This timer starts once you leave the region." +
                    "<br>" +
                    "The icon remains visible during this time while outside the region, nice for banking." +
                    "<br><br>" +
                    "If the panel is open when the timer runs out, it will hide once the side panel is closed, and the game loads (running/teleporting etc).</html>",
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
            name = "Show Icon on Scoreboard",
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

    // --- Reward Display Settings ---
    @ConfigSection(
            name = "Reward Display",
            description = "Configure how each reward is displayed and if it's included in the 'Any' unique calculation.",
            position = 3,
            closedByDefault = false
    )
    String rewardSettings = "rewardSettings";

    enum RewardDisplayMode
    {
        SHOW,  // Displayed normally and included in 'Any' calculation.
        GREY,  // Displayed but greyed out; NOT included in 'Any' calculation.
        HIDE   // Not displayed; NOT included in 'Any' calculation.
    }

    @ConfigItem(
            keyName = "mokhaiotlClothDisplay",
            name = "Mokhaiotl's Cloth",
            description = "How to display the Mokhaiotl's Cloth reward. 'Grey' or 'Hide' will exclude it from the 'Any' unique calculation.",
            section = rewardSettings,
            position = 7
    )
    default RewardDisplayMode mokhaiotlClothDisplay() { return RewardDisplayMode.SHOW; }

    @ConfigItem(
            keyName = "eyeOfAyakDisplay",
            name = "Eye of Ayak",
            description = "How to display the Eye of Ayak reward. 'Grey' or 'Hide' will exclude it from the 'Any' unique calculation.",
            section = rewardSettings,
            position = 8
    )
    default RewardDisplayMode eyeOfAyakDisplay() { return RewardDisplayMode.SHOW; }

    @ConfigItem(
            keyName = "avernicTreadsDisplay",
            name = "Avernic Treads",
            description = "How to display the Avernic Treads reward. 'Grey' or 'Hide' will exclude it from the 'Any' unique calculation.",
            section = rewardSettings,
            position = 9
    )
    default RewardDisplayMode avernicTreadsDisplay() { return RewardDisplayMode.SHOW; }

    @ConfigItem(
            keyName = "domDisplay",
            name = "Dom",
            description = "How to display the D.O.M. reward. 'Grey' or 'Hide' will exclude it from the 'Any' unique calculation.",
            section = rewardSettings,
            position = 10
    )
    default RewardDisplayMode domDisplay() { return RewardDisplayMode.SHOW; }

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