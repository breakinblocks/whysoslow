package com.breakinblocks.whysoslow.mixin;

import com.breakinblocks.whysoslow.profiler.StartupProfiler;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FMLModContainer.class, remap = false)
public abstract class MixinModContainer {

    @Inject(method = "acceptEvent", at = @At("HEAD"))
    private void whysoslow$onAcceptEventStart(Event e, CallbackInfo ci) {
        if (StartupProfiler.isActive()) {
            StartupProfiler.onModEventStart(((ModContainer) (Object) this).getModId(), e);
        }
    }

    @Inject(method = "acceptEvent", at = @At("RETURN"))
    private void whysoslow$onAcceptEventEnd(Event e, CallbackInfo ci) {
        if (StartupProfiler.isActive()) {
            StartupProfiler.onModEventEnd(((ModContainer) (Object) this).getModId(), e);
        }
    }
}
