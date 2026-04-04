package com.breakinblocks.whysoslow.mixin;

import com.breakinblocks.whysoslow.profiler.WorldGenProfiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(ConfiguredWorldCarver.class)
public abstract class MixinConfiguredWorldCarver {

    @Unique
    private static final ThreadLocal<Long> whysoslow$startTime = new ThreadLocal<>();

    @Inject(method = "carve", at = @At("HEAD"))
    private void whysoslow$onCarveStart(CarvingContext context, ChunkAccess chunk,
                                        Function<BlockPos, Holder<Biome>> biomeAccessor,
                                        RandomSource random, Aquifer aquifer,
                                        ChunkPos chunkPos, CarvingMask carvingMask,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (WorldGenProfiler.isActive()) {
            whysoslow$startTime.set(System.nanoTime());
        }
    }

    @Inject(method = "carve", at = @At("RETURN"))
    private void whysoslow$onCarveEnd(CarvingContext context, ChunkAccess chunk,
                                      Function<BlockPos, Holder<Biome>> biomeAccessor,
                                      RandomSource random, Aquifer aquifer,
                                      ChunkPos chunkPos, CarvingMask carvingMask,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (WorldGenProfiler.isActive()) {
            Long start = whysoslow$startTime.get();
            if (start != null) {
                long elapsed = System.nanoTime() - start;
                whysoslow$startTime.remove();
                @SuppressWarnings("unchecked")
                ConfiguredWorldCarver<?> self = (ConfiguredWorldCarver<?>) (Object) this;
                WorldGenProfiler.recordCarver(self, elapsed);
            }
        }
    }
}
