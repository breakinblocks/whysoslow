package com.breakinblocks.whysoslow.mixin;

import com.breakinblocks.whysoslow.profiler.WorldGenProfiler;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Unique
    private static final ThreadLocal<Long> whysoslow$structureStartTime = new ThreadLocal<>();

    @Inject(method = "tryGenerateStructure", at = @At("HEAD"))
    private void whysoslow$onStructureStart(StructureSet.StructureSelectionEntry selected,
                                            StructureManager structureManager, RegistryAccess registryAccess,
                                            RandomState randomState, StructureTemplateManager templateManager,
                                            long seed, ChunkAccess centerChunk, ChunkPos sourceChunkPos,
                                            SectionPos sectionPos, ResourceKey<Level> level,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (WorldGenProfiler.isActive()) {
            whysoslow$structureStartTime.set(System.nanoTime());
        }
    }

    @Inject(method = "tryGenerateStructure", at = @At("RETURN"))
    private void whysoslow$onStructureEnd(StructureSet.StructureSelectionEntry selected,
                                          StructureManager structureManager, RegistryAccess registryAccess,
                                          RandomState randomState, StructureTemplateManager templateManager,
                                          long seed, ChunkAccess centerChunk, ChunkPos sourceChunkPos,
                                          SectionPos sectionPos, ResourceKey<Level> level,
                                          CallbackInfoReturnable<Boolean> cir) {
        Long start = whysoslow$structureStartTime.get();
        if (start == null) return;
        whysoslow$structureStartTime.remove();
        WorldGenProfiler.recordStructureStart(selected.structure(), System.nanoTime() - start);
    }
}
