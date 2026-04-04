package com.breakinblocks.whysoslow.mixin;

import com.breakinblocks.whysoslow.profiler.StartupProfiler;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into Forge's ModContainer.acceptEvent to time every mod's handling
 * of every lifecycle event during startup. This is applied at class-transform
 * time, so it captures events from the very first dispatch.
 */
@Mixin(value = ModContainer.class, remap = false)
public abstract class MixinModContainer {

    @Shadow
    public abstract String getModId();

    @Inject(method = "acceptEvent", at = @At("HEAD"))
    private void whysoslow$onAcceptEventStart(Event e, CallbackInfo ci) {
        if (StartupProfiler.isActive()) {
            StartupProfiler.onModEventStart(getModId(), e);
        }
    }

    @Inject(method = "acceptEvent", at = @At("RETURN"))
    private void whysoslow$onAcceptEventEnd(Event e, CallbackInfo ci) {
        if (StartupProfiler.isActive()) {
            StartupProfiler.onModEventEnd(getModId(), e);
        }
    }
}
