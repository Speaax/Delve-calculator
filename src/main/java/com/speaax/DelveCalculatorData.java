package com.speaax;

import lombok.Data;
import java.util.Map;

/**
 * This class is a simple data container used for serializing and deserializing
 * the kill counts with Gson. It holds all the data that needs to be saved.
 */
@Data
public class DelveCalculatorData
{
    private Map<Integer, Integer> levelKills;
    private int wavesPast8;
}