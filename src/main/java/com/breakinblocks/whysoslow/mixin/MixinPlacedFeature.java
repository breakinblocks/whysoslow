package com.breakinblocks.whysoslow.mixin;

import com.breakinblocks.whysoslow.profiler.WorldGenProfiler;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Times every individual placed feature execution during worldgen.
 * Attributes each feature to its mod by looking up the PlacedFeature
 * instance in the registry identity map.
 */
@Mixin(PlacedFeature.class)
public abstract class MixinPlacedFeature {

    @Unique
    private static final ThreadLocal<Long> whysoslow$startTime = new ThreadLocal<>();

    @Inject(method = "placeWithBiomeCheck", at = @At("HEAD"))
    private void whysoslow$onPlaceStart(WorldGenLevel level, ChunkGenerator generator,
                                        RandomSource random, BlockPos pos,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (WorldGenProfiler.isActive()) {
            whysoslow$startTime.set(System.nanoTime());
        }
    }

    @Inject(method = "placeWithBiomeCheck", at = @At("RETURN"))
    private void whysoslow$onPlaceEnd(WorldGenLevel level, ChunkGenerator generator,
                                      RandomSource random, BlockPos pos,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (WorldGenProfiler.isActive()) {
            Long start = whysoslow$startTime.get();
            if (start != null) {
                long elapsed = System.nanoTime() - start;
                whysoslow$startTime.remove();
                WorldGenProfiler.recordFeaturePlacement((PlacedFeature) (Object) this, elapsed);
            }
        }
    }
}
