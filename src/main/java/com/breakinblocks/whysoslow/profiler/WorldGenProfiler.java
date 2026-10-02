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
    private static volatile boolean capturing = false;
    private static volatile long profilingStartMs = 0;
    private static volatile long profilingEndMs = 0;

    private static final IdentityHashMap<PlacedFeature, Identifier> featureIds = new IdentityHashMap<>();
    private static final IdentityHashMap<Structure, Identifier> structureIds = new IdentityHashMap<>();
    private static final IdentityHashMap<ConfiguredWorldCarver<?>, Identifier> carverIds = new IdentityHashMap<>();

    private static final ConcurrentHashMap<Identifier, TimingEntry> featureTimings = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Identifier, TimingEntry> structureTimings = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Identifier, TimingEntry> carverTimings = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Identifier, TimingEntry> structureStartTimings = new ConcurrentHashMap<>();

    private static final AtomicLong totalNoiseFillNanos = new AtomicLong();
    private static final AtomicLong totalSurfaceBuildNanos = new AtomicLong();
    private static final AtomicLong totalBiomeDecorationNanos = new AtomicLong();
    private static final AtomicLong noiseFillCount = new AtomicLong();
    private static final AtomicLong surfaceBuildCount = new AtomicLong();
    private static final AtomicLong biomeDecorationCount = new AtomicLong();
    private static final AtomicLong totalChunksProfiled = new AtomicLong();
    private static final AtomicLong totalBiomeFillNanos = new AtomicLong();
    private static final AtomicLong biomeFillCount = new AtomicLong();

    private static volatile ChunkPipelineProfiler.Session pipelineSession;
    private static volatile Map<Long, ThreadCpuSample> threadCpuAtStart = Map.of();
    private static volatile List<ThreadGroupCpu> threadCpuResult = List.of();

    public static boolean isActive() {
        return active;
    }

    public static void start(MinecraftServer server) {
        if (capturing) endCapture();
        if (active) return;

        LOGGER.info("Building registry lookup maps...");
        buildRegistryMaps(server);

        resetTimings();
        threadCpuResult = List.of();
        threadCpuAtStart = snapshotThreadCpu();
        TickProfiler.start();
        pipelineSession = ChunkPipelineProfiler.begin("worldgen");

        profilingStartMs = System.currentTimeMillis();
        active = true;
        LOGGER.info("WorldGen profiling started. Feature registry has {} entries, {} structures, {} carvers.",
                featureIds.size(), structureIds.size(), carverIds.size());
    }

    public static boolean beginCapture(MinecraftServer server) {
        if (active) return false;
        buildRegistryMaps(server);
        resetTimings();
        capturing = true;
        active = true;
        return true;
    }

    public static Capture endCapture() {
        if (!capturing) return null;
        active = false;
        capturing = false;
        Capture capture = new Capture(new HashMap<>(featureTimings), new HashMap<>(structureStartTimings),
                new HashMap<>(structureTimings), new HashMap<>(carverTimings));
        resetTimings();
        return capture;
    }

    private static void resetTimings() {
        featureTimings.clear();
        structureTimings.clear();
        carverTimings.clear();
        structureStartTimings.clear();
        totalNoiseFillNanos.set(0);
        totalSurfaceBuildNanos.set(0);
        totalBiomeDecorationNanos.set(0);
        noiseFillCount.set(0);
        surfaceBuildCount.set(0);
        biomeDecorationCount.set(0);
        totalChunksProfiled.set(0);
        totalBiomeFillNanos.set(0);
        biomeFillCount.set(0);
    }

    public record Capture(Map<Identifier, TimingEntry> features, Map<Identifier, TimingEntry> structureStarts,
                          Map<Identifier, TimingEntry> structures, Map<Identifier, TimingEntry> carvers) {
    }

    public static void stop() {
        active = false;
        profilingEndMs = System.currentTimeMillis();
        ChunkPipelineProfiler.end(pipelineSession);
        TickProfiler.stop();
        threadCpuResult = diffThreadCpu(threadCpuAtStart, snapshotThreadCpu());
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

    public static void recordStructureStart(net.minecraft.core.Holder<Structure> structure, long nanos) {
        if (!active) return;
        Identifier id = structure.unwrapKey().map(ResourceKey::identifier)
                .orElseGet(() -> structureIds.getOrDefault(structure.value(),
                        Identifier.fromNamespaceAndPath("unknown", "unknown_structure")));
        structureStartTimings.computeIfAbsent(id, k -> new TimingEntry()).record(nanos);
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

    public static void recordBiomeFill(long nanos) {
        if (!active) return;
        totalBiomeFillNanos.addAndGet(nanos);
        biomeFillCount.incrementAndGet();
    }

    public static long getTotalBiomeFillNanos() { return totalBiomeFillNanos.get(); }
    public static long getBiomeFillCount() { return biomeFillCount.get(); }
    public static ChunkPipelineProfiler.Session getPipelineSession() { return pipelineSession; }
    public static List<ThreadGroupCpu> getThreadCpu() { return threadCpuResult; }

    private static final java.lang.management.ThreadMXBean THREAD_MX = java.lang.management.ManagementFactory.getThreadMXBean();

    private record ThreadCpuSample(String name, long cpuNanos) {
    }

    public record ThreadGroupCpu(String group, int threads, long cpuNanos) {
    }

    private static Map<Long, ThreadCpuSample> snapshotThreadCpu() {
        Map<Long, ThreadCpuSample> map = new java.util.HashMap<>();
        if (!THREAD_MX.isThreadCpuTimeSupported()) return map;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            long cpu = THREAD_MX.getThreadCpuTime(thread.threadId());
            if (cpu >= 0) map.put(thread.threadId(), new ThreadCpuSample(thread.getName(), cpu));
        }
        return map;
    }

    private static List<ThreadGroupCpu> diffThreadCpu(Map<Long, ThreadCpuSample> before, Map<Long, ThreadCpuSample> after) {
        Map<String, long[]> groups = new java.util.HashMap<>();
        after.forEach((id, sample) -> {
            ThreadCpuSample prior = before.get(id);
            long delta = sample.cpuNanos() - (prior != null ? prior.cpuNanos() : 0);
            if (delta <= 0) return;
            String group = sample.name().replaceAll("[0-9]+", "#");
            long[] acc = groups.computeIfAbsent(group, k -> new long[2]);
            acc[0] += delta;
            acc[1]++;
        });
        List<ThreadGroupCpu> list = new java.util.ArrayList<>();
        groups.forEach((group, acc) -> list.add(new ThreadGroupCpu(group, (int) acc[1], acc[0])));
        list.sort((a, b) -> Long.compare(b.cpuNanos(), a.cpuNanos()));
        return list;
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
    public static Map<Identifier, TimingEntry> getStructureStartTimings() { return Collections.unmodifiableMap(structureStartTimings); }
    public static Map<Identifier, TimingEntry> getCarverTimings() { return Collections.unmodifiableMap(carverTimings); }

    public static boolean hasData() {
        return totalChunksProfiled.get() > 0 || !featureTimings.isEmpty()
                || !structureTimings.isEmpty() || !carverTimings.isEmpty() || !structureStartTimings.isEmpty();
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
