package com.breakinblocks.whysoslow.mixin.client;

import com.breakinblocks.whysoslow.profiler.ResourceReloadProfiler;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;

@Mixin(SpriteLoader.class)
public abstract class MixinSpriteLoader {

    @Shadow
    @Final
    private Identifier location;

    @Inject(method = "stitch", at = @At("HEAD"))
    private void whysoslow$onStitchStart(List<SpriteContents> sprites, int maxMipmapLevels, Executor executor,
                                         CallbackInfoReturnable<SpriteLoader.Preparations> cir) {
        int wanted = 1 << maxMipmapLevels;
        List<ResourceReloadProfiler.MipLimiter> limiters = new ArrayList<>();
        for (SpriteContents sprite : sprites) {
            int lowest = Math.min(Integer.lowestOneBit(sprite.width()), Integer.lowestOneBit(sprite.height()));
            if (lowest < wanted) {
                limiters.add(new ResourceReloadProfiler.MipLimiter(sprite.name().toString(),
                        sprite.width(), sprite.height(), Integer.numberOfTrailingZeros(lowest)));
            }
        }
        limiters.sort(Comparator.comparingInt(ResourceReloadProfiler.MipLimiter::limitedTo)
                .thenComparing(ResourceReloadProfiler.MipLimiter::sprite));
        ResourceReloadProfiler.recordAtlasLimiters(location.toString(), maxMipmapLevels, limiters);
    }

    @Inject(method = "stitch", at = @At("RETURN"))
    private void whysoslow$onStitchEnd(List<SpriteContents> sprites, int maxMipmapLevels, Executor executor,
                                       CallbackInfoReturnable<SpriteLoader.Preparations> cir) {
        SpriteLoader.Preparations result = cir.getReturnValue();
        if (result == null) return;
        ResourceReloadProfiler.recordAtlasResult(location.toString(), result.width(), result.height(),
                result.mipLevel(), result.regions().size());
    }
}
