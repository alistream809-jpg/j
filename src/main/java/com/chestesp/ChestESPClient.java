package com.chestesp;

import com.chestesp.config.ESPConfig;
import com.chestesp.esp.ContainerScanner;
import com.chestesp.esp.ESPRenderer;
import com.chestesp.gui.ESPScreen;
import com.chestesp.input.KeyBindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class ChestESPClient implements ClientModInitializer {
    public static final String MOD_ID = "chestesp";
    public static final Logger LOGGER = LoggerFactory.getLogger("ChestESP");

    private static ESPConfig config = new ESPConfig();
    private static Path configPath;
    private static boolean tickErrorLogged = false;

    public static ESPConfig config() {
        return config;
    }

    public static void saveConfig() {
        if (configPath != null && !config.save(configPath)) {
            LOGGER.warn("Could not save {}", configPath);
        }
    }

    @Override
    public void onInitializeClient() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("chestesp.json");
        config = ESPConfig.load(configPath);
        saveConfig();

        KeyBindings.register();

        ClientTickEvents.END_CLIENT_TICK.register(ChestESPClient::onTick);
        WorldRenderEvents.LAST.register(ESPRenderer::render);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ContainerScanner.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ContainerScanner.clear());

        LOGGER.info("ChestESP initialised (config: {})", configPath);
    }

    private static void onTick(MinecraftClient mc) {
        try {
            if (mc.player != null && mc.world != null) {
                while (KeyBindings.MENU.wasPressed()) {
                    if (mc.currentScreen == null) mc.setScreen(new ESPScreen(null));
                }
                while (KeyBindings.TOGGLE.wasPressed()) {
                    config.enabled = !config.enabled;
                    ContainerScanner.invalidate();
                    saveConfig();
                    notify(mc, "ChestESP", config.enabled);
                }
                for (ESPConfig.Type t : ESPConfig.Type.values()) {
                    KeyBinding kb = KeyBindings.TYPE_KEYS.get(t);
                    while (kb.wasPressed()) {
                        config.setEnabled(t, !config.isEnabled(t));
                        saveConfig();
                        notify(mc, t.label + " ESP", config.isEnabled(t));
                    }
                }
            }
            ContainerScanner.tick(mc);
        } catch (Throwable t) {
            if (!tickErrorLogged) {
                tickErrorLogged = true;
                LOGGER.error("ChestESP tick error (further errors suppressed)", t);
            }
        }
    }

    private static void notify(MinecraftClient mc, String name, boolean on) {
        if (mc.player == null) return;
        mc.player.sendMessage(Text.literal(name + ": ")
                .append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED)), true);
    }
}
