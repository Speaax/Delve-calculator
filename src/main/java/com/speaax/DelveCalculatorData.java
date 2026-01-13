package com.speaax;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is a simple data container used for serializing and deserializing
 * the kill counts with Gson. It holds all the data that needs to be saved.
 */
@Data
public class DelveCalculatorData
{
    private Map<String, DelveProfile> profiles = new HashMap<>();

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
    }
}