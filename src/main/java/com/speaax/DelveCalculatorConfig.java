package com.speaax;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

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
}