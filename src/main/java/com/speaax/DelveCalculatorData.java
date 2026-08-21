package com.speaax;

import lombok.Data;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is a simple data container used for serializing and deserializing
 * the kill counts with Gson. It holds all the data that needs to be saved.
 */
@Data
public class DelveCalculatorData
{
    private Map<String, DelveProfile> profiles = new HashMap<>();

    public boolean migrateLegacyProfiles()
    {
        if (profiles == null || profiles.isEmpty())
        {
            return false;
        }

        boolean modified = false;
        List<String> keys = new ArrayList<>(profiles.keySet());

        for (String key : keys)
        {
            DelveProfile profile = profiles.get(key);
            if (profile == null) continue;

            String targetKey = null;

            if (key.equals("MANUAL") || key.equals("MANUAL_MEMBERS") || key.equals("MEMBERS:MANUAL"))
            {
                targetKey = "STANDARD:MANUAL";
            }
            else if (key.equals("MEMBERS"))
            {
                targetKey = "STANDARD";
            }
            else if (key.contains("QUEST_SPEEDRUNNING"))
            {
                targetKey = key.endsWith(":MANUAL") ? "QUEST_SPEEDRUNNING:MANUAL" : "QUEST_SPEEDRUNNING";
            }
            else if (key.contains("SEASONAL"))
            {
                targetKey = key.endsWith(":MANUAL") ? "SEASONAL:MANUAL" : "SEASONAL";
            }
            else if (key.contains("DEADMAN"))
            {
                targetKey = key.endsWith(":MANUAL") ? "DEADMAN:MANUAL" : "DEADMAN";
            }
            else if (key.startsWith("MEMBERS_") || key.startsWith("SKILL_TOTAL"))
            {
                targetKey = key.endsWith(":MANUAL") ? "STANDARD:MANUAL" : "STANDARD";
            }

            if (targetKey != null && !targetKey.equals(key))
            {
                DelveProfile targetProfile = profiles.computeIfAbsent(targetKey, k -> new DelveProfile(k.endsWith(":MANUAL") ? "Manual" : k, true));
                targetProfile.mergeProfile(profile);
                profiles.remove(key);
                modified = true;
            }
        }

        return modified;
    }

    @Data
    public static class DelveProfile
    {
        private String name;
        private Map<Integer, Integer> levelKills = new HashMap<>();
        private int wavesPast8;
        private Map<Integer, Integer> obtainedUniques = new HashMap<>();

        public DelveProfile() {}

        public DelveProfile(String name, boolean initialize)
        {
            this.name = name;
        }

        public void addKills(int level, int count)
        {
            levelKills.merge(level, count, Integer::sum);
        }

        public void addWave8()
        {
            wavesPast8++;
        }

        public void addDrop(int itemId)
        {
            obtainedUniques.merge(itemId, 1, Integer::sum);
        }

        public void mergeProfile(DelveProfile other)
        {
            if (other == null) return;
            for (Map.Entry<Integer, Integer> entry : other.getLevelKills().entrySet())
            {
                this.levelKills.merge(entry.getKey(), entry.getValue(), Math::max);
            }
            this.wavesPast8 = Math.max(this.wavesPast8, other.getWavesPast8());
            for (Map.Entry<Integer, Integer> entry : other.getObtainedUniques().entrySet())
            {
                this.obtainedUniques.merge(entry.getKey(), entry.getValue(), Math::max);
            }
        }
    }
}