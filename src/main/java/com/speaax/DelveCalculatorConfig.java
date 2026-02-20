package com.speaax;

import net.runelite.client.config.*;

@ConfigGroup("delvecalculator")
public interface DelveCalculatorConfig extends Config
{



    // --- Panel Settings ---
    @ConfigSection(
            name = "Panel Settings",
            description = "Configure when the panel should appear.",
            position = 0,
            closedByDefault = false
    )
    String panelSettings = "panelSettings";

    @ConfigItem(
            keyName = "showInRegion",
            name = "Only show panel in region",
            description = "When enabled, the panel only appears when you are in the Delve region.",
            section = panelSettings,
            position = 1
    )
    default boolean onlyShowInRegion()
    {
        return false;
    }

    @ConfigItem(
            keyName = "autoOpenInRegion",
            name = "Auto Open in Region",
            description = "Automatically open the panel when a new Region session begins.",
            section = panelSettings,
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
                    "The panel remains visible during this time while outside the region, nice for banking." +
                    "<br><br>" +
                    "If the panel is open when the timer runs out, it will hide once the side panel is closed, and the game loads (running/teleporting etc).</html>"+
                    "<br><br>" +
                    "This timer is only used when 'Only show panel in region' is enabled.",
            section = panelSettings,
            position = 4
    )
    @Units(Units.MINUTES)
    default int regionTimeout()
    {
        return 10;
    }




    @ConfigItem(
            keyName = "autoOpenOnScoreboard",
            name = "Auto Open on Scoreboard",
            description = "Automatically open the panel when you open the Delve scoreboard.",
            section = panelSettings,
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

    @ConfigItem(
            keyName = "activeViewTab",
            name = "Active View Tab",
            description = "The last selected view tab.",
            hidden = true
    )
    default String activeViewTab() { return "ALL"; }

    @ConfigItem(keyName = "activeViewTab", name = "", description = "")
    void activeViewTab(String tab);

    @ConfigItem(
            keyName = "activeModeTab",
            name = "Active Mode Tab",
            description = "The last selected mode tab.",
            hidden = true
    )
    default String activeModeTab() { return "EXPECTED"; }

    @ConfigItem(keyName = "activeModeTab", name = "", description = "")
    void activeModeTab(String mode);
}