package com.breakinblocks.whysoslow.mixin;

import com.breakinblocks.whysoslow.profiler.WorldGenProfiler;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class MixinChunkGenerator {

    @Unique
    private static final ThreadLocal<Long> whysoslow$decoStartTime = new ThreadLocal<>();

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void whysoslow$onDecorationStart(WorldGenLevel level, ChunkAccess chunk,
                                              StructureManager structureManager,
                                              CallbackInfo ci) {
        if (WorldGenProfiler.isActive()) {
            whysoslow$decoStartTime.set(System.nanoTime());
        }
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void whysoslow$onDecorationEnd(WorldGenLevel level, ChunkAccess chunk,
                                            StructureManager structureManager,
                                            CallbackInfo ci) {
        if (WorldGenProfiler.isActive()) {
            Long start = whysoslow$decoStartTime.get();
            if (start != null) {
                long elapsed = System.nanoTime() - start;
                whysoslow$decoStartTime.remove();
                WorldGenProfiler.recordBiomeDecoration(elapsed);
            }
        }
    }
}
