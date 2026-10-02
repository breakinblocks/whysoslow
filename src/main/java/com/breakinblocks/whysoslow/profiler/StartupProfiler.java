package com.breakinblocks.whysoslow.profiler;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.lifecycle.*;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class StartupProfiler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadMXBean THREAD_MX = ManagementFactory.getThreadMXBean();

    private static final long JVM_START_MS = ManagementFactory.getRuntimeMXBean().getStartTime();
    private static volatile long firstEventTimeMs = 0;
    private static volatile long lastEventTimeMs = 0;

    private static volatile boolean active = true;

    private static final ConcurrentHashMap<String, ModStartupData> modData = new ConcurrentHashMap<>();

    private static final ThreadLocal<Long> wallStart = new ThreadLocal<>();
    private static final ThreadLocal<Long> cpuStart = new ThreadLocal<>();
    private static final ThreadLocal<Long> memStart = new ThreadLocal<>();
    private static final ThreadLocal<StackSampler.Watch> listenerWatch = new ThreadLocal<>();
    private static final ConcurrentHashMap<Thread, StackSampler.Watch> gapWatches = new ConcurrentHashMap<>();
    private static final List<FrameworkGap> frameworkGaps = Collections.synchronizedList(new ArrayList<>());

    private static final long LISTENER_SAMPLE_THRESHOLD_MS = 250;
    private static final long GAP_SAMPLE_THRESHOLD_MS = 500;
    private static final long GAP_REPORT_MIN_RUNNABLE_MS = 500;

    public static void onModEventStart(String modId, Event event) {
        if (!active) return;

        long now = System.currentTimeMillis();
        if (firstEventTimeMs == 0) {
            firstEventTimeMs = now;
        }

        String label = modId + " / " + getPhaseFromEvent(event);
        closeGap(Thread.currentThread(), label);
        listenerWatch.set(StackSampler.begin(Thread.currentThread(), label, LISTENER_SAMPLE_THRESHOLD_MS));

        wallStart.set(System.nanoTime());
        if (THREAD_MX.isCurrentThreadCpuTimeSupported()) {
            cpuStart.set(THREAD_MX.getCurrentThreadCpuTime());
        }
        memStart.set(getUsedMemory());
    }

    public static void onModEventEnd(String modId, Event event) {
        StackSampler.Profile profile = StackSampler.end(listenerWatch.get());
        listenerWatch.remove();
        if (!active) return;

        lastEventTimeMs = System.currentTimeMillis();

        Long wStart = wallStart.get();
        Long cStart = cpuStart.get();
        Long mStart = memStart.get();

        if (wStart == null) return;

        long wallElapsed = System.nanoTime() - wStart;
        long cpuElapsed = cStart != null ? THREAD_MX.getCurrentThreadCpuTime() - cStart : 0;
        long memAfter = getUsedMemory();
        long memDelta = mStart != null ? memAfter - mStart : 0;

        wallStart.remove();
        cpuStart.remove();
        memStart.remove();

        String phase = getPhaseFromEvent(event);
        ModStartupData data = modData.computeIfAbsent(modId, k -> new ModStartupData(modId));
        data.recordPhase(phase, wallElapsed, cpuElapsed, memDelta);

        if (profile != null && profile.samples() > 0) {
            data.recordHotSpots(phase, profile);
        }

        Thread thread = Thread.currentThread();
        if (!active) return;
        gapWatches.put(thread, StackSampler.begin(thread, modId + " / " + phase, GAP_SAMPLE_THRESHOLD_MS));
    }

    private static void closeGap(Thread thread, String nextLabel) {
        StackSampler.Watch watch = gapWatches.remove(thread);
        if (watch == null) return;
        StackSampler.Profile profile = StackSampler.end(watch);
        if (profile == null || profile.runnableMillis() < GAP_REPORT_MIN_RUNNABLE_MS) return;
        frameworkGaps.add(new FrameworkGap(thread.getName(), watch.label(), nextLabel, watch.elapsedNanos(), profile));
    }

    public static List<FrameworkGap> getFrameworkGaps() {
        synchronized (frameworkGaps) {
            List<FrameworkGap> copy = new ArrayList<>(frameworkGaps);
            copy.sort(Comparator.comparingLong((FrameworkGap g) -> g.profile().runnableSamples()).reversed());
            return copy;
        }
    }

    public record FrameworkGap(String threadName, String afterLabel, String beforeLabel, long wallNanos,
                               StackSampler.Profile profile) {
    }

    public static void markComplete() {
        active = false;
        for (Thread thread : new ArrayList<>(gapWatches.keySet())) {
            closeGap(thread, "load complete");
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static Map<String, ModStartupData> getModData() {
        return Collections.unmodifiableMap(modData);
    }

    public static long getJvmStartMs() { return JVM_START_MS; }
    public static long getFirstEventTimeMs() { return firstEventTimeMs; }
    public static long getLastEventTimeMs() { return lastEventTimeMs; }
    public static int getModCount() { return modData.size(); }

    public static Map<String, Map<String, Integer>> countRegistryEntries() {
        Map<String, Map<String, Integer>> counts = new HashMap<>();

        countRegistry(counts, "blocks", BuiltInRegistries.BLOCK);
        countRegistry(counts, "items", BuiltInRegistries.ITEM);
        countRegistry(counts, "block_entities", BuiltInRegistries.BLOCK_ENTITY_TYPE);
        countRegistry(counts, "entities", BuiltInRegistries.ENTITY_TYPE);
        countRegistry(counts, "menus", BuiltInRegistries.MENU);
        countRegistry(counts, "recipe_serializers", BuiltInRegistries.RECIPE_SERIALIZER);
        countRegistry(counts, "sounds", BuiltInRegistries.SOUND_EVENT);
        countRegistry(counts, "effects", BuiltInRegistries.MOB_EFFECT);
        countRegistry(counts, "particles", BuiltInRegistries.PARTICLE_TYPE);

        return counts;
    }

    private static <T> void countRegistry(Map<String, Map<String, Integer>> counts,
                                          String registryName, Registry<T> registry) {
        for (var key : registry.keySet()) {
            String namespace = key.getNamespace();
            counts.computeIfAbsent(namespace, k -> new LinkedHashMap<>())
                    .merge(registryName, 1, Integer::sum);
        }
    }

    private static long getUsedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static String getPhaseFromEvent(Event event) {
        if (event instanceof FMLConstructModEvent) return "Construction";
        if (event instanceof FMLCommonSetupEvent) return "Common Setup";
        if (event instanceof FMLLoadCompleteEvent) return "Load Complete";

        String className = event.getClass().getSimpleName();
        if (className.contains("Register")) return "Registry";
        return className;
    }

    public static class ModStartupData {
        private final String modId;
        private final ConcurrentHashMap<String, PhaseData> phases = new ConcurrentHashMap<>();
        private final AtomicLong totalWallNanos = new AtomicLong();
        private final AtomicLong totalCpuNanos = new AtomicLong();
        private final AtomicLong totalMemDelta = new AtomicLong();

        public ModStartupData(String modId) {
            this.modId = modId;
        }

        public void recordPhase(String phase, long wallNanos, long cpuNanos, long memDelta) {
            PhaseData pd = phases.computeIfAbsent(phase, k -> new PhaseData());
            pd.wallNanos.addAndGet(wallNanos);
            pd.cpuNanos.addAndGet(cpuNanos);
            pd.memDelta.addAndGet(memDelta);
            pd.invocations.incrementAndGet();

            totalWallNanos.addAndGet(wallNanos);
            totalCpuNanos.addAndGet(cpuNanos);
            totalMemDelta.addAndGet(memDelta);
        }

        public String getModId() { return modId; }
        public long getTotalWallNanos() { return totalWallNanos.get(); }
        public long getTotalCpuNanos() { return totalCpuNanos.get(); }
        public long getTotalMemDelta() { return totalMemDelta.get(); }
        public Map<String, PhaseData> getPhases() { return Collections.unmodifiableMap(phases); }

        private final ConcurrentHashMap<String, StackSampler.Profile> hotSpots = new ConcurrentHashMap<>();

        public void recordHotSpots(String phase, StackSampler.Profile profile) {
            hotSpots.computeIfAbsent(phase, k -> new StackSampler.Profile()).merge(profile);
        }

        public Map<String, StackSampler.Profile> getHotSpots() { return Collections.unmodifiableMap(hotSpots); }
    }

    public static class PhaseData {
        public final AtomicLong wallNanos = new AtomicLong();
        public final AtomicLong cpuNanos = new AtomicLong();
        public final AtomicLong memDelta = new AtomicLong();
        public final AtomicLong invocations = new AtomicLong();
    }
}
