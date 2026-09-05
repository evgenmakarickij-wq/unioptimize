package com.unioptimize;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class UniOptimizeMod implements ModInitializer {

    public static final String MOD_ID = "unioptimize";
    public static final Logger LOGGER = LoggerFactory.getLogger("UniOptimize");
    public static UniOptimizeConfig CONFIG;

    private int tickCounter = 0;
    private int totalTicks = 0;
    private final java.util.Map<java.util.UUID, Integer> firstSeenTick = new java.util.HashMap<>();

    @Override
    public void onInitialize() {
        CONFIG = UniOptimizeConfig.loadOrCreate(
                FabricLoader.getInstance().getConfigDir().resolve("unioptimize.json"));
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        registerCommands();
    }

    private void onServerTick(MinecraftServer server) {
        totalTicks++;
        tickCounter++;
        if (tickCounter < CONFIG.cleanupIntervalTicks) return;
        tickCounter = 0;

        for (ServerLevel level : server.getAllLevels()) {
            if (CONFIG.enableEntityCleanup) cleanupStaleEntities(level);
            if (CONFIG.enableTickRateTuning) tuneRandomTickSpeed(level);
            if (CONFIG.enableMobCapGuard) guardMobCap(level);
        }
    }

    private void cleanupStaleEntities(ServerLevel level) {
        int itemTicksLimit = CONFIG.itemDespawnSeconds * 20;
        int orbTicksLimit = CONFIG.xpOrbDespawnSeconds * 20;
        java.util.Set<java.util.UUID> stillPresent = new java.util.HashSet<>();

        for (Entity entity : level.getAllEntities()) {
            boolean isItem = entity instanceof ItemEntity;
            boolean isOrb = entity instanceof ExperienceOrb;
            if (!isItem && !isOrb) continue;

            java.util.UUID id = entity.getUUID();
            stillPresent.add(id);
            int firstSeen = firstSeenTick.computeIfAbsent(id, k -> totalTicks);
            int limit = isItem ? itemTicksLimit : orbTicksLimit;

            if (totalTicks - firstSeen > limit) {
                entity.discard();
                firstSeenTick.remove(id);
            }
        }
        firstSeenTick.keySet().retainAll(stillPresent);
    }

    private void tuneRandomTickSpeed(ServerLevel level) {
        int entityCount = 0;
        for (Entity ignored : level.getAllEntities()) {
            entityCount++;
            if (entityCount > CONFIG.highLoadEntityThreshold) break;
        }
        int target = entityCount >= CONFIG.highLoadEntityThreshold
                ? CONFIG.randomTickSpeedHighLoad : CONFIG.randomTickSpeedLowLoad;
        int current = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        if (current != target) {
            level.getServer().getCommands().performPrefixedCommand(
                    level.getServer().createCommandSourceStack(),
                    "gamerule randomTickSpeed " + target);
        }
    }

    private void guardMobCap(ServerLevel level) {
        List<Monster> hostiles = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Monster monster && monster.isAlive()) hostiles.add(monster);
        }
        int limit = CONFIG.maxHostileMobsPerChunkArea;
        if (hostiles.size() <= limit) return;
        int excess = hostiles.size() - limit;
        hostiles.sort((a, b) -> Double.compare(distanceToNearestPlayer(b), distanceToNearestPlayer(a)));
        int discarded = 0;
        for (Monster hostile : hostiles) {
            if (discarded >= excess) break;
            if (distanceToNearestPlayer(hostile) > CONFIG.mobCapCheckRadiusChunks * 16.0) {
                hostile.discard();
                discarded++;
            }
        }
    }

    private double distanceToNearestPlayer(Entity entity) {
        double best = Double.MAX_VALUE;
        for (var player : entity.level().players()) {
            double d = player.distanceToSqr(entity);
            if (d < best) best = d;
        }
        return Math.sqrt(best);
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("unioptimize")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("status").executes(this::sendStatus))
                    .then(Commands.literal("cleanup")
                            .then(Commands.argument("enabled", BoolArgumentType.bool())
                                    .executes(ctx -> {
                                        CONFIG.enableEntityCleanup = BoolArgumentType.getBool(ctx, "enabled");
                                        CONFIG.save(FabricLoader.getInstance().getConfigDir().resolve("unioptimize.json"));
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "cleanup = " + CONFIG.enableEntityCleanup), true);
                                        return 1;
                                    })))
                    .then(Commands.literal("tickrate")
                            .then(Commands.argument("enabled", BoolArgumentType.bool())
                                    .executes(ctx -> {
                                        CONFIG.enableTickRateTuning = BoolArgumentType.getBool(ctx, "enabled");
                                        CONFIG.save(FabricLoader.getInstance().getConfigDir().resolve("unioptimize.json"));
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "tickrate = " + CONFIG.enableTickRateTuning), true);
                                        return 1;
                                    })))
                    .then(Commands.literal("mobcap")
                            .then(Commands.argument("enabled", BoolArgumentType.bool())
                                    .executes(ctx -> {
                                        CONFIG.enableMobCapGuard = BoolArgumentType.getBool(ctx, "enabled");
                                        CONFIG.save(FabricLoader.getInstance().getConfigDir().resolve("unioptimize.json"));
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "mobcap = " + CONFIG.enableMobCapGuard), true);
                                        return 1;
                                    })))
            );
        });
    }

    private int sendStatus(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "UniOptimize | cleanup: " + CONFIG.enableEntityCleanup +
                        " | tickrate: " + CONFIG.enableTickRateTuning +
                        " | mobcap: " + CONFIG.enableMobCapGuard), false);
        return 1;
    }
          }
