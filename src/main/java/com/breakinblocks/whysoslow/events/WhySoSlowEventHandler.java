package com.breakinblocks.whysoslow.events;

import com.breakinblocks.whysoslow.WhySoSlow;
import com.breakinblocks.whysoslow.profiler.ReportWriter;
import com.breakinblocks.whysoslow.profiler.WorldLoadProfiler;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;

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
        if (com.breakinblocks.whysoslow.profiler.WorldGenProfiler.isActive()) {
            com.breakinblocks.whysoslow.profiler.WorldGenProfiler.stop();
            ReportWriter.writeWorldGenReport(FMLPaths.GAMEDIR.get());
            WhySoSlow.LOGGER.info("Server stopping - worldgen profiling report auto-saved");
        }
    }
}
