package com.breakinblocks.whysoslow.mixin;

import com.breakinblocks.whysoslow.profiler.ChunkPipelineProfiler;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkStatusTasks.class)
public abstract class MixinChunkStatusTasks {

    @Inject(method = {"generateStructureStarts", "generateStructureReferences", "generateBiomes", "generateNoise",
            "generateSurface", "generateCarvers", "generateFeatures", "initializeLight", "light", "generateSpawn",
            "full"}, at = @At("HEAD"))
    private static void whysoslow$onStageStart(WorldGenContext context, ChunkStep step,
                                               StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
                                               CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (!ChunkPipelineProfiler.isRecording()) return;
        ChunkPipelineProfiler.onStageStart();
    }

    @Inject(method = {"generateStructureStarts", "generateStructureReferences", "generateBiomes", "generateNoise",
            "generateSurface", "generateCarvers", "generateFeatures", "initializeLight", "light", "generateSpawn",
            "full"}, at = @At("RETURN"))
    private static void whysoslow$onStageEnd(WorldGenContext context, ChunkStep step,
                                             StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
                                             CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (!ChunkPipelineProfiler.isRecording()) return;
        ChunkPipelineProfiler.onStageEnd(step.targetStatus().getName());
    }

    @Inject(method = "generateStructureStarts", at = @At("HEAD"))
    private static void whysoslow$onChunkStarted(WorldGenContext context, ChunkStep step,
                                                 StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
                                                 CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (!ChunkPipelineProfiler.isRecording()) return;
        ChunkPipelineProfiler.onChunkStarted(context.level().dimension().identifier().toString(), chunk.getPos().pack());
    }

    @Inject(method = "full", at = @At("RETURN"))
    private static void whysoslow$onChunkFull(WorldGenContext context, ChunkStep step,
                                              StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
                                              CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (!ChunkPipelineProfiler.isRecording()) return;
        String dimension = context.level().dimension().identifier().toString();
        long pos = chunk.getPos().pack();
        CompletableFuture<ChunkAccess> future = cir.getReturnValue();
        if (future == null) return;
        future.whenComplete((result, error) -> ChunkPipelineProfiler.onChunkCompleted(dimension, pos));
    }
}
