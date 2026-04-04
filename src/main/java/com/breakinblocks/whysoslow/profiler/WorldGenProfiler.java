package com.breakinblocks.whysoslow.profiler;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class WorldGenProfiler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile boolean active = false;
    private static volatile long profilingStartMs = 0;
    private static volatile long profilingEndMs = 0;

    private static final IdentityHashMap<PlacedFeature, Identifier> featureIds = new IdentityHashMap<>();
    private static final IdentityHashMap<Structure, Identifier> structureIds = new IdentityHashMap<>();
    private static final IdentityHashMap<ConfiguredWorldCarver<?>, Identifier> carverIds = new IdentityHashMap<>();

    private static final ConcurrentHashMap<Identifier, TimingEntry> featureTimings = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Identifier, TimingEntry> structureTimings = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Identifier, TimingEntry> carverTimings = new ConcurrentHashMap<>();

    private static final AtomicLong totalNoiseFillNanos = new AtomicLong();
    private static final AtomicLong totalSurfaceBuildNanos = new AtomicLong();
    private static final AtomicLong totalBiomeDecorationNanos = new AtomicLong();
    private static final AtomicLong noiseFillCount = new AtomicLong();
    private static final AtomicLong surfaceBuildCount = new AtomicLong();
    private static final AtomicLong biomeDecorationCount = new AtomicLong();
    private static final AtomicLong totalChunksProfiled = new AtomicLong();

    public static boolean isActive() {
        return active;
    }

    public static void start(MinecraftServer server) {
        if (active) return;

        LOGGER.info("Building registry lookup maps...");
        buildRegistryMaps(server);

        featureTimings.clear();
        structureTimings.clear();
        carverTimings.clear();
        totalNoiseFillNanos.set(0);
        totalSurfaceBuildNanos.set(0);
        totalBiomeDecorationNanos.set(0);
        noiseFillCount.set(0);
        surfaceBuildCount.set(0);
        biomeDecorationCount.set(0);
        totalChunksProfiled.set(0);

        profilingStartMs = System.currentTimeMillis();
        active = true;
        LOGGER.info("WorldGen profiling started. Feature registry has {} entries, {} structures, {} carvers.",
                featureIds.size(), structureIds.size(), carverIds.size());
    }

    public static void stop() {
        active = false;
        profilingEndMs = System.currentTimeMillis();
        LOGGER.info("WorldGen profiling stopped. {} chunks profiled.", totalChunksProfiled.get());
    }

    private static void buildRegistryMaps(MinecraftServer server) {
        featureIds.clear();
        structureIds.clear();
        carverIds.clear();

        try {
            Registry<PlacedFeature> pfReg = server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
            for (Map.Entry<ResourceKey<PlacedFeature>, PlacedFeature> entry : pfReg.entrySet()) {
                featureIds.put(entry.getValue(), entry.getKey().identifier());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to build PlacedFeature lookup map", e);
        }

        try {
            Registry<Structure> sReg = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            for (Map.Entry<ResourceKey<Structure>, Structure> entry : sReg.entrySet()) {
                structureIds.put(entry.getValue(), entry.getKey().identifier());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to build Structure lookup map", e);
        }

        try {
            Registry<ConfiguredWorldCarver<?>> cReg = server.registryAccess().lookupOrThrow(Registries.CONFIGURED_CARVER);
            for (Map.Entry<ResourceKey<ConfiguredWorldCarver<?>>, ConfiguredWorldCarver<?>> entry : cReg.entrySet()) {
                carverIds.put(entry.getValue(), entry.getKey().identifier());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to build ConfiguredWorldCarver lookup map", e);
        }
    }

    public static void recordFeaturePlacement(PlacedFeature feature, long nanos) {
        if (!active) return;
        Identifier id = featureIds.getOrDefault(feature, Identifier.fromNamespaceAndPath("unknown", "unknown_feature"));
        featureTimings.computeIfAbsent(id, k -> new TimingEntry()).record(nanos);
    }

    public static void recordStructureGeneration(StructureStart structureStart, long nanos) {
        if (!active) return;
        Structure structure = structureStart.getStructure();
        Identifier id = structureIds.getOrDefault(structure, Identifier.fromNamespaceAndPath("unknown", "unknown_structure"));
        structureTimings.computeIfAbsent(id, k -> new TimingEntry()).record(nanos);
    }

    public static void recordCarver(ConfiguredWorldCarver<?> carver, long nanos) {
        if (!active) return;
        Identifier id = carverIds.getOrDefault(carver, Identifier.fromNamespaceAndPath("unknown", "unknown_carver"));
        carverTimings.computeIfAbsent(id, k -> new TimingEntry()).record(nanos);
    }

    public static void recordNoiseFill(long nanos) {
        if (!active) return;
        totalNoiseFillNanos.addAndGet(nanos);
        noiseFillCount.incrementAndGet();
    }

    public static void recordSurfaceBuild(long nanos) {
        if (!active) return;
        totalSurfaceBuildNanos.addAndGet(nanos);
        surfaceBuildCount.incrementAndGet();
    }

    public static void recordBiomeDecoration(long nanos) {
        if (!active) return;
        totalBiomeDecorationNanos.addAndGet(nanos);
        biomeDecorationCount.incrementAndGet();
        totalChunksProfiled.incrementAndGet();
    }

    public static long getProfilingStartMs() { return profilingStartMs; }
    public static long getProfilingEndMs() { return profilingEndMs; }
    public static long getTotalChunksProfiled() { return totalChunksProfiled.get(); }
    public static long getTotalNoiseFillNanos() { return totalNoiseFillNanos.get(); }
    public static long getTotalSurfaceBuildNanos() { return totalSurfaceBuildNanos.get(); }
    public static long getTotalBiomeDecorationNanos() { return totalBiomeDecorationNanos.get(); }
    public static long getNoiseFillCount() { return noiseFillCount.get(); }
    public static long getSurfaceBuildCount() { return surfaceBuildCount.get(); }
    public static long getBiomeDecorationCount() { return biomeDecorationCount.get(); }

    public static Map<Identifier, TimingEntry> getFeatureTimings() { return Collections.unmodifiableMap(featureTimings); }
    public static Map<Identifier, TimingEntry> getStructureTimings() { return Collections.unmodifiableMap(structureTimings); }
    public static Map<Identifier, TimingEntry> getCarverTimings() { return Collections.unmodifiableMap(carverTimings); }

    public static boolean hasData() {
        return totalChunksProfiled.get() > 0 || !featureTimings.isEmpty()
                || !structureTimings.isEmpty() || !carverTimings.isEmpty();
    }

    public static class TimingEntry {
        private final AtomicLong totalNanos = new AtomicLong();
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong maxNanos = new AtomicLong();

        public void record(long nanos) {
            totalNanos.addAndGet(nanos);
            count.incrementAndGet();
            long prev;
            do {
                prev = maxNanos.get();
                if (nanos <= prev) break;
            } while (!maxNanos.compareAndSet(prev, nanos));
        }

        public long getTotalNanos() { return totalNanos.get(); }
        public long getCount() { return count.get(); }
        public long getMaxNanos() { return maxNanos.get(); }
        public double getAvgNanos() { return count.get() > 0 ? (double) totalNanos.get() / count.get() : 0; }
    }
}
