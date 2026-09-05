package com.unioptimize;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ParticleStatus;

public class UniOptimizeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (UniOptimizeMod.CONFIG == null || !UniOptimizeMod.CONFIG.enableClientAutoProfile) return;
            applyLightProfile(client);
        });
    }

    private void applyLightProfile(Minecraft client) {
        Options options = client.options;
        UniOptimizeConfig cfg = UniOptimizeMod.CONFIG;
        if (cfg.reduceParticles) options.particles().set(ParticleStatus.MINIMAL);
        if (cfg.reduceEntityDistance) {
            double clamped = Math.max(0.5, Math.min(1.0, cfg.entityDistanceScale));
            options.entityDistanceScaling().set(clamped);
        }
        if (cfg.disableClouds) options.cloudStatus().set(CloudStatus.OFF);
        options.save();
    }
}
