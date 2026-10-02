package com.breakinblocks.whysoslow.mixin;

import com.breakinblocks.whysoslow.profiler.TickProfiler;
import com.breakinblocks.whysoslow.profiler.WorldLoadProfiler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {

    @Inject(method = "createLevels", at = @At("HEAD"))
    private void whysoslow$onCreateLevelsStart(CallbackInfo ci) {
        WorldLoadProfiler.onCreateLevelsStart();
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void whysoslow$onCreateLevelsEnd(CallbackInfo ci) {
        WorldLoadProfiler.onCreateLevelsEnd();
    }

    @WrapOperation(method = "createLevels", at = @At(value = "NEW", target = "net/minecraft/server/level/ServerLevel"))
    private ServerLevel whysoslow$timeLevelConstruction(MinecraftServer server, Executor executor,
                                                        LevelStorageSource.LevelStorageAccess storage,
                                                        ServerLevelData levelData, ResourceKey<Level> dimension,
                                                        LevelStem levelStem, boolean isDebug, long seed,
                                                        List<?> spawners, boolean tickTime,
                                                        Operation<ServerLevel> original) {
        long memoryBefore = usedMemory();
        long start = System.nanoTime();
        ServerLevel level = original.call(server, executor, storage, levelData, dimension, levelStem, isDebug, seed,
                spawners, tickTime);
        WorldLoadProfiler.recordDimensionConstruction(dimension.identifier().toString(), System.nanoTime() - start,
                memoryBefore, usedMemory());
        return level;
    }

    @Inject(method = "setInitialSpawn", at = @At("HEAD"))
    private static void whysoslow$onInitialSpawnStart(ServerLevel level, ServerLevelData levelData,
                                                      boolean spawnBonusChest, boolean isDebug,
                                                      LevelLoadListener listener, CallbackInfo ci) {
        WorldLoadProfiler.onInitialSpawnStart(level.dimension().identifier().toString(), level.getServer());
    }

    @Inject(method = "setInitialSpawn", at = @At("RETURN"))
    private static void whysoslow$onInitialSpawnEnd(ServerLevel level, ServerLevelData levelData,
                                                    boolean spawnBonusChest, boolean isDebug,
                                                    LevelLoadListener listener, CallbackInfo ci) {
        WorldLoadProfiler.onInitialSpawnEnd();
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"))
    private void whysoslow$onPrepareLevelsStart(CallbackInfo ci) {
        WorldLoadProfiler.addMilestone("prepareLevels (initial chunk loading) started");
    }

    @Inject(method = "prepareLevels", at = @At("RETURN"))
    private void whysoslow$onPrepareLevelsEnd(CallbackInfo ci) {
        WorldLoadProfiler.addMilestone("prepareLevels (initial chunk loading) complete");
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void whysoslow$onTickStart(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        TickProfiler.onTickStart();
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void whysoslow$onTickEnd(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        TickProfiler.onTickEnd();
    }

    @Unique
    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
