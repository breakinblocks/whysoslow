package com.breakinblocks.whysoslow.mixin;

import com.breakinblocks.whysoslow.profiler.ChunkPipelineProfiler;
import com.breakinblocks.whysoslow.profiler.WorldGenProfiler;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(NoiseBasedChunkGenerator.class)
public abstract class MixinNoiseBasedChunkGenerator {

    @Unique
    private static final ThreadLocal<Long> whysoslow$noiseStartTime = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<Long> whysoslow$surfaceStartTime = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<Long> whysoslow$biomeStartTime = new ThreadLocal<>();

    @Inject(method = "doFill", at = @At("HEAD"))
    private void whysoslow$onFillNoiseStart(Blender blender,
                                             StructureManager structureManager,
                                             RandomState randomState,
                                             ChunkAccess chunk,
                                             int minCellY,
                                             int cellCountY,
                                             CallbackInfoReturnable<ChunkAccess> cir) {
        if (WorldGenProfiler.isActive()) {
            whysoslow$noiseStartTime.set(System.nanoTime());
        }
        ChunkPipelineProfiler.onNoiseStart();
    }

    @Inject(method = "doFill", at = @At("RETURN"))
    private void whysoslow$onFillNoiseEnd(Blender blender,
                                           StructureManager structureManager,
                                           RandomState randomState,
                                           ChunkAccess chunk,
                                           int minCellY,
                                           int cellCountY,
                                           CallbackInfoReturnable<ChunkAccess> cir) {
        if (WorldGenProfiler.isActive()) {
            Long start = whysoslow$noiseStartTime.get();
            if (start != null) {
                long elapsed = System.nanoTime() - start;
                whysoslow$noiseStartTime.remove();
                WorldGenProfiler.recordNoiseFill(elapsed);
            }
        }
        ChunkPipelineProfiler.onNoiseEnd();
    }

    @Inject(method = "doCreateBiomes", at = @At("HEAD"))
    private void whysoslow$onBiomesStart(Blender blender, RandomState randomState,
                                          StructureManager structureManager, ChunkAccess chunk,
                                          CallbackInfo ci) {
        if (WorldGenProfiler.isActive()) {
            whysoslow$biomeStartTime.set(System.nanoTime());
        }
        ChunkPipelineProfiler.onBiomesStart();
    }

    @Inject(method = "doCreateBiomes", at = @At("RETURN"))
    private void whysoslow$onBiomesEnd(Blender blender, RandomState randomState,
                                        StructureManager structureManager, ChunkAccess chunk,
                                        CallbackInfo ci) {
        if (WorldGenProfiler.isActive()) {
            Long start = whysoslow$biomeStartTime.get();
            if (start != null) {
                long elapsed = System.nanoTime() - start;
                whysoslow$biomeStartTime.remove();
                WorldGenProfiler.recordBiomeFill(elapsed);
            }
        }
        ChunkPipelineProfiler.onBiomesEnd();
    }

    @Inject(method = "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V", at = @At("HEAD"))
    private void whysoslow$onBuildSurfaceStart(WorldGenRegion level,
                                                StructureManager structureManager,
                                                RandomState randomState,
                                                ChunkAccess chunk,
                                                CallbackInfo ci) {
        if (WorldGenProfiler.isActive()) {
            whysoslow$surfaceStartTime.set(System.nanoTime());
        }
    }

    @Inject(method = "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V", at = @At("RETURN"))
    private void whysoslow$onBuildSurfaceEnd(WorldGenRegion level,
                                              StructureManager structureManager,
                                              RandomState randomState,
                                              ChunkAccess chunk,
                                              CallbackInfo ci) {
        if (WorldGenProfiler.isActive()) {
            Long start = whysoslow$surfaceStartTime.get();
            if (start != null) {
                long elapsed = System.nanoTime() - start;
                whysoslow$surfaceStartTime.remove();
                WorldGenProfiler.recordSurfaceBuild(elapsed);
            }
        }
    }
}
