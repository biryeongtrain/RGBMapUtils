package kim.biryeong.maprgbutils;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class ModEntryPoint implements ModInitializer {
    public static final String MOD_ID = "rgbmaplibs";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        RgbMapShaderPatcher.register();
        PolymerResourcePackUtils.markAsRequired();
        registerResourcePackGeneration();
        LOGGER.info("Registered rgb_maps shader patcher for Polymer resource pack");

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            RgbMapDebugCommand.register();
            LOGGER.info("Registered /rgbmapdebug command (development environment only)");
        }
    }

    private static void registerResourcePackGeneration() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            if (!PolymerResourcePackUtils.hasResources()) {
                LOGGER.debug("Skipping Polymer resource pack generation because no resources are registered");
                return;
            }

            Path outputPath = PolymerResourcePackUtils.getMainPath();
            boolean success = PolymerResourcePackUtils.buildMain(outputPath, status -> LOGGER.debug("Polymer RP build status: {}", status));
            if (success) {
                LOGGER.info("Generated Polymer resource pack: {}", outputPath.toAbsolutePath());
            } else {
                LOGGER.warn("Failed to generate Polymer resource pack. You can retry with /generate-pack.");
            }
        });
    }
}
