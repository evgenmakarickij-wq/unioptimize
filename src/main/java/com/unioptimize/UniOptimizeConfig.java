package com.unioptimize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class UniOptimizeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enableEntityCleanup = true;
    public int itemDespawnSeconds = 120;
    public int xpOrbDespawnSeconds = 90;
    public int cleanupIntervalTicks = 200;
    public boolean enableTickRateTuning = true;
    public int randomTickSpeedLowLoad = 3;
    public int randomTickSpeedHighLoad = 1;
    public int highLoadEntityThreshold = 800;
    public boolean enableMobCapGuard = true;
    public int maxHostileMobsPerChunkArea = 40;
    public int mobCapCheckRadiusChunks = 6;
    public boolean enableClientAutoProfile = true;
    public boolean reduceParticles = true;
    public boolean reduceEntityDistance = true;
    public double entityDistanceScale = 0.75;
    public boolean disableClouds = true;

    public static UniOptimizeConfig loadOrCreate(Path path) {
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    UniOptimizeConfig loaded = GSON.fromJson(reader, UniOptimizeConfig.class);
                    if (loaded != null) return loaded;
                }
            }
        } catch (IOException e) {
            UniOptimizeMod.LOGGER.warn("Не вдалося прочитати конфіг", e);
        }
        UniOptimizeConfig fresh = new UniOptimizeConfig();
        fresh.save(path);
        return fresh;
    }

    public void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            UniOptimizeMod.LOGGER.warn("Не вдалося зберегти конфіг", e);
        }
    }
                     }
