package com.breakinblocks.whysoslow.profiler;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class WorldLoadProfiler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile boolean active = false;
    private static volatile long worldLoadStartNanos = 0;
    private static volatile long worldLoadEndNanos = 0;
    private static volatile long createLevelsStartNanos = 0;
    private static volatile long createLevelsEndNanos = 0;
    private static volatile long memoryAtStart = 0;
    private static volatile long memoryAtEnd = 0;
    private static volatile String worldName = "Unknown";

    private static final ConcurrentHashMap<String, DimensionLoadData> dimensionData = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ModWorldLoadData> modContributions = new ConcurrentHashMap<>();
    private static final List<LoadMilestone> milestones = Collections.synchronizedList(new ArrayList<>());

    public static void begin(String name) {
        active = true;
        worldLoadStartNanos = System.nanoTime();
        memoryAtStart = getUsedMemory();
        worldName = name;
        dimensionData.clear();
        modContributions.clear();
        milestones.clear();
        initialSpawnNanos = 0;
        initialSpawnDimension = null;
        spawnSession = null;
        spawnCapture = null;
        LOGGER.info("World load profiling started for '{}'", name);
    }

    public static void end() {
        if (!active) return;
        worldLoadEndNanos = System.nanoTime();
        memoryAtEnd = getUsedMemory();
        active = false;
        LOGGER.info("World load profiling complete. Total: {}ms",
                (worldLoadEndNanos - worldLoadStartNanos) / 1_000_000);
    }

    public static boolean isActive() {
        return active;
    }

    public static void onCreateLevelsStart() {
        createLevelsStartNanos = System.nanoTime();
        addMilestone("createLevels started");
    }

    public static void onCreateLevelsEnd() {
        createLevelsEndNanos = System.nanoTime();
        addMilestone("createLevels completed");
    }

    public static void onDimensionLoadStart(String dimensionId) {
        if (!active) return;
        DimensionLoadData data = dimensionData.computeIfAbsent(dimensionId, k -> new DimensionLoadData());
        data.startNanos = System.nanoTime();
        data.memoryBefore = getUsedMemory();
    }

    public static void onDimensionLoadEnd(String dimensionId) {
        if (!active) return;
        DimensionLoadData data = dimensionData.get(dimensionId);
        if (data != null && data.startNanos > 0) {
            data.endNanos = System.nanoTime();
            data.memoryAfter = getUsedMemory();
        }
    }

    public static void recordDimensionConstructed(String dimensionId) {
        if (!active) return;
        addMilestone("Dimension " + dimensionId + " constructed");
    }

    public static void recordDimensionConstruction(String dimensionId, long nanos, long memoryBefore, long memoryAfter) {
        if (!active) return;
        DimensionLoadData data = dimensionData.computeIfAbsent(dimensionId, k -> new DimensionLoadData());
        long now = System.nanoTime();
        data.startNanos = now - nanos;
        data.endNanos = now;
        data.memoryBefore = memoryBefore;
        data.memoryAfter = memoryAfter;
    }

    private static volatile long initialSpawnNanos = 0;
    private static volatile String initialSpawnDimension = null;
    private static volatile ChunkPipelineProfiler.Session spawnSession;
    private static volatile long initialSpawnStart = 0;
    private static volatile WorldGenProfiler.Capture spawnCapture;

    public static void onInitialSpawnStart(String dimensionId, net.minecraft.server.MinecraftServer server) {
        if (!active) return;
        spawnCapture = null;
        WorldGenProfiler.beginCapture(server);
        initialSpawnStart = System.nanoTime();
        initialSpawnDimension = dimensionId;
        addMilestone("Initial spawn search started in " + dimensionId);
        spawnSession = ChunkPipelineProfiler.begin("initial spawn");
    }

    public static void onInitialSpawnEnd() {
        if (!active || initialSpawnStart == 0) return;
        initialSpawnNanos = System.nanoTime() - initialSpawnStart;
        initialSpawnStart = 0;
        ChunkPipelineProfiler.end(spawnSession);
        spawnCapture = WorldGenProfiler.endCapture();
        addMilestone("Initial spawn search complete");
    }

    public static long getInitialSpawnNanos() { return initialSpawnNanos; }
    public static String getInitialSpawnDimension() { return initialSpawnDimension; }
    public static ChunkPipelineProfiler.Session getSpawnSession() { return spawnSession; }
    public static WorldGenProfiler.Capture getSpawnCapture() { return spawnCapture; }

    public static void recordModContribution(String modId, String activity, long nanos) {
        if (!active) return;
        ModWorldLoadData data = modContributions.computeIfAbsent(modId, k -> new ModWorldLoadData());
        data.totalNanos.addAndGet(nanos);
        data.activities.merge(activity, nanos, Long::sum);
    }

    public static void addMilestone(String description) {
        milestones.add(new LoadMilestone(System.nanoTime(), description));
    }

    public static long getWorldLoadStartNanos() { return worldLoadStartNanos; }
    public static long getWorldLoadEndNanos() { return worldLoadEndNanos; }
    public static long getCreateLevelsStartNanos() { return createLevelsStartNanos; }
    public static long getCreateLevelsEndNanos() { return createLevelsEndNanos; }
    public static long getMemoryAtStart() { return memoryAtStart; }
    public static long getMemoryAtEnd() { return memoryAtEnd; }
    public static String getWorldName() { return worldName; }
    public static Map<String, DimensionLoadData> getDimensionData() { return Collections.unmodifiableMap(dimensionData); }
    public static Map<String, ModWorldLoadData> getModContributions() { return Collections.unmodifiableMap(modContributions); }
    public static List<LoadMilestone> getMilestones() { return Collections.unmodifiableList(milestones); }

    private static long getUsedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    public static class DimensionLoadData {
        public volatile long startNanos;
        public volatile long endNanos;
        public volatile long memoryBefore;
        public volatile long memoryAfter;

        public long getElapsedNanos() {
            return endNanos > 0 ? endNanos - startNanos : 0;
        }

        public long getMemoryDelta() {
            return memoryAfter - memoryBefore;
        }
    }

    public static class ModWorldLoadData {
        public final AtomicLong totalNanos = new AtomicLong();
        public final ConcurrentHashMap<String, Long> activities = new ConcurrentHashMap<>();
    }

    public static class LoadMilestone {
        public final long nanoTime;
        public final String description;

        public LoadMilestone(long nanoTime, String description) {
            this.nanoTime = nanoTime;
            this.description = description;
        }
    }
}
