package com.breakinblocks.whysoslow.mixin;

import com.breakinblocks.whysoslow.profiler.WorldLoadProfiler;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.Executor;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void whysoslow$onConstructTail(MinecraftServer server, Executor executor,
                                            LevelStorageSource.LevelStorageAccess storage,
                                            ServerLevelData levelData,
                                            ResourceKey<Level> dimension,
                                            LevelStem levelStem,
                                            boolean isDebug, long seed,
                                            List<?> spawners, boolean tickTime,
                                            CallbackInfo ci) {
        if (WorldLoadProfiler.isActive()) {
            String dimId = dimension.identifier().toString();
            WorldLoadProfiler.recordDimensionConstructed(dimId);
        }
    }
}
