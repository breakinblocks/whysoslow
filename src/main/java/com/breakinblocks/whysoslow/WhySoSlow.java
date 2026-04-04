package com.breakinblocks.whysoslow;

import com.breakinblocks.whysoslow.commands.WhySoSlowCommand;
import com.breakinblocks.whysoslow.events.WhySoSlowEventHandler;
import com.breakinblocks.whysoslow.profiler.ReportWriter;
import com.breakinblocks.whysoslow.profiler.StartupProfiler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(WhySoSlow.MOD_ID)
public class WhySoSlow {
    public static final String MOD_ID = "whysoslow";
    public static final Logger LOGGER = LogManager.getLogger();

    public static WhySoSlow instance;

    public WhySoSlow() {
        instance = this;
        LOGGER.info("WhySoSlow initializing - performance profiling is active");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Write startup report once all mods are done loading
        modEventBus.addListener(this::onLoadComplete);

        // Register server/common event handlers
        MinecraftForge.EVENT_BUS.register(WhySoSlowEventHandler.class);
        MinecraftForge.EVENT_BUS.register(WhySoSlowCommand.class);
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            StartupProfiler.markComplete();
            ReportWriter.writeStartupReport(FMLPaths.GAMEDIR.get());
        });
    }
}
