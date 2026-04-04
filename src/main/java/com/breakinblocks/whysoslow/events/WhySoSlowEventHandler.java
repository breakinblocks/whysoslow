package com.breakinblocks.whysoslow.events;

import com.breakinblocks.whysoslow.WhySoSlow;
import com.breakinblocks.whysoslow.profiler.ReportWriter;
import com.breakinblocks.whysoslow.profiler.WorldGenProfiler;
import com.breakinblocks.whysoslow.profiler.WorldLoadProfiler;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public class WhySoSlowEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        String worldName = event.getServer().getWorldData().getLevelName();
        WorldLoadProfiler.begin(worldName);
        WorldLoadProfiler.addMilestone("ServerAboutToStartEvent fired");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStarting(ServerStartingEvent event) {
        WorldLoadProfiler.addMilestone("ServerStartingEvent fired");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        WorldLoadProfiler.addMilestone("ServerStartedEvent fired");
        WorldLoadProfiler.end();
        ReportWriter.writeWorldLoadReport(FMLPaths.GAMEDIR.get());
        WhySoSlow.LOGGER.info("World load profiling complete - report written to logs/whysoslow/worldload.log");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (WorldGenProfiler.isActive()) {
            WorldGenProfiler.stop();
            ReportWriter.writeWorldGenReport(FMLPaths.GAMEDIR.get());
            WhySoSlow.LOGGER.info("Server stopping - worldgen profiling report auto-saved");
        }
    }
}
