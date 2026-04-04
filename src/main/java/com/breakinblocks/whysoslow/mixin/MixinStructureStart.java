package com.breakinblocks.whysoslow.mixin;

import com.breakinblocks.whysoslow.profiler.WorldGenProfiler;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureStart.class)
public abstract class MixinStructureStart {

    @Unique
    private static final ThreadLocal<Long> whysoslow$startTime = new ThreadLocal<>();

    @Inject(method = "placeInChunk", at = @At("HEAD"))
    private void whysoslow$onPlaceStart(WorldGenLevel level, StructureManager structureManager,
                                        ChunkGenerator generator, RandomSource random,
                                        BoundingBox boundingBox, ChunkPos chunkPos,
                                        CallbackInfo ci) {
        if (WorldGenProfiler.isActive()) {
            whysoslow$startTime.set(System.nanoTime());
        }
    }

    @Inject(method = "placeInChunk", at = @At("RETURN"))
    private void whysoslow$onPlaceEnd(WorldGenLevel level, StructureManager structureManager,
                                      ChunkGenerator generator, RandomSource random,
                                      BoundingBox boundingBox, ChunkPos chunkPos,
                                      CallbackInfo ci) {
        if (WorldGenProfiler.isActive()) {
            Long start = whysoslow$startTime.get();
            if (start != null) {
                long elapsed = System.nanoTime() - start;
                whysoslow$startTime.remove();
                WorldGenProfiler.recordStructureGeneration((StructureStart) (Object) this, elapsed);
            }
        }
    }
}
