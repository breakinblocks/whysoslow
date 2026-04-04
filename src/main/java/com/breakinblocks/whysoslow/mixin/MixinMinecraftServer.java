package com.breakinblocks.whysoslow.mixin;

import com.breakinblocks.whysoslow.profiler.WorldLoadProfiler;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(method = "prepareLevels", at = @At("HEAD"))
    private void whysoslow$onPrepareLevelsStart(CallbackInfo ci) {
        WorldLoadProfiler.addMilestone("prepareLevels (initial chunk loading) started");
    }

    @Inject(method = "prepareLevels", at = @At("RETURN"))
    private void whysoslow$onPrepareLevelsEnd(CallbackInfo ci) {
        WorldLoadProfiler.addMilestone("prepareLevels (initial chunk loading) complete");
    }
}
