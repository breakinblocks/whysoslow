package com.breakinblocks.whysoslow.mixin;

import com.breakinblocks.whysoslow.profiler.WorldLoadProfiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into MinecraftServer.createLevels to measure the time spent
 * creating and initializing all world dimensions.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {

    @Inject(method = "createLevels", at = @At("HEAD"))
    private void whysoslow$onCreateLevelsStart(ChunkProgressListener listener, CallbackInfo ci) {
        WorldLoadProfiler.onCreateLevelsStart();
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void whysoslow$onCreateLevelsEnd(ChunkProgressListener listener, CallbackInfo ci) {
        WorldLoadProfiler.onCreateLevelsEnd();
    }
}
