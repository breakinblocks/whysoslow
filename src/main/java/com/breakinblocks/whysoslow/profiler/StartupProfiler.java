package com.breakinblocks.whysoslow.profiler;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.lifecycle.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class StartupProfiler {
    private static final Logger LOGGER = LogManager.getLogger("WhySoSlow/Startup");
    private static final ThreadMXBean THREAD_MX = ManagementFactory.getThreadMXBean();

    private static final long JVM_START_MS = ManagementFactory.getRuntimeMXBean().getStartTime();
    private static volatile long firstEventTimeMs = 0;
    private static volatile long lastEventTimeMs = 0;

    private static volatile boolean active = true;

    private static final ConcurrentHashMap<String, ModStartupData> modData = new ConcurrentHashMap<>();

    private static final ThreadLocal<Long> wallStart = new ThreadLocal<>();
    private static final ThreadLocal<Long> cpuStart = new ThreadLocal<>();
    private static final ThreadLocal<Long> memStart = new ThreadLocal<>();

    public static void onModEventStart(String modId, Event event) {
        if (!active) return;

        long now = System.currentTimeMillis();
        if (firstEventTimeMs == 0) {
            firstEventTimeMs = now;
        }

        wallStart.set(System.nanoTime());
        if (THREAD_MX.isCurrentThreadCpuTimeSupported()) {
            cpuStart.set(THREAD_MX.getCurrentThreadCpuTime());
        }
        memStart.set(getUsedMemory());
    }

    public static void onModEventEnd(String modId, Event event) {
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
    }

    public static void markComplete() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static Map<String, ModStartupData> getModData() {
        return Collections.unmodifiableMap(modData);
    }

    public static long getJvmStartMs() {
        return JVM_START_MS;
    }

    public static long getFirstEventTimeMs() {
        return firstEventTimeMs;
    }

    public static long getLastEventTimeMs() {
        return lastEventTimeMs;
    }

    public static int getModCount() {
        return modData.size();
    }

    public static Map<String, Map<String, Integer>> countRegistryEntries() {
        Map<String, Map<String, Integer>> counts = new HashMap<>();

        countRegistry(counts, "blocks", ForgeRegistries.BLOCKS);
        countRegistry(counts, "items", ForgeRegistries.ITEMS);
        countRegistry(counts, "block_entities", ForgeRegistries.BLOCK_ENTITY_TYPES);
        countRegistry(counts, "entities", ForgeRegistries.ENTITY_TYPES);
        countRegistry(counts, "menus", ForgeRegistries.MENU_TYPES);
        countRegistry(counts, "recipes", ForgeRegistries.RECIPE_SERIALIZERS);
        countRegistry(counts, "sounds", ForgeRegistries.SOUND_EVENTS);
        countRegistry(counts, "effects", ForgeRegistries.MOB_EFFECTS);
        countRegistry(counts, "enchantments", ForgeRegistries.ENCHANTMENTS);
        countRegistry(counts, "biomes", ForgeRegistries.BIOMES);
        countRegistry(counts, "features", ForgeRegistries.FEATURES);
        countRegistry(counts, "particles", ForgeRegistries.PARTICLE_TYPES);

        return counts;
    }

    private static <T> void countRegistry(Map<String, Map<String, Integer>> counts,
                                          String registryName, IForgeRegistry<T> registry) {
        for (var entry : registry.getEntries()) {
            String namespace = entry.getKey().location().getNamespace();
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
        if (event instanceof FMLClientSetupEvent) return "Client Setup";
        if (event instanceof FMLLoadCompleteEvent) return "Load Complete";
        if (event instanceof InterModEnqueueEvent) return "IMC Enqueue";
        if (event instanceof InterModProcessEvent) return "IMC Process";

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
    }

    public static class PhaseData {
        public final AtomicLong wallNanos = new AtomicLong();
        public final AtomicLong cpuNanos = new AtomicLong();
        public final AtomicLong memDelta = new AtomicLong();
        public final AtomicLong invocations = new AtomicLong();
    }
}
