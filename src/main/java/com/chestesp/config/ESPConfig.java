package com.chestesp.config;

import com.chestesp.util.ColorUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent JSON configuration. Contains no Minecraft classes so it can be unit tested. */
public class ESPConfig {
    public enum Type {
        CHEST("Chest", 0xFFA21A),
        TRAPPED("Trapped Chest", 0xFF3B30),
        ENDER("Ender Chest", 0xB04CFF),
        BARREL("Barrel", 0x34C759),
        SHULKER("Shulker Box", 0xFF6FD8);

        public final String label;
        public final int defaultColor;

        Type(String label, int defaultColor) {
            this.label = label;
            this.defaultColor = defaultColor;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    public boolean throughWalls = true;
    public boolean mergeDoubleChests = true;
    public boolean distanceFade = true;
    public boolean disableOnMultiplayer = false;

    public float renderDistance = 64f;     // blocks
    public float outlineThickness = 2f;    // approx. pixels
    public float outlineOpacity = 1f;      // 0.1 .. 1
    public float fillOpacity = 0.12f;      // 0 .. 0.6

    public Map<String, Boolean> typeEnabled = new LinkedHashMap<>();
    public Map<String, Integer> typeColor = new LinkedHashMap<>();

    public ESPConfig() {
        for (Type t : Type.values()) {
            typeEnabled.put(t.name(), true);
            typeColor.put(t.name(), t.defaultColor);
        }
    }

    public boolean isEnabled(Type t) {
        Boolean b = typeEnabled.get(t.name());
        return b == null || b;
    }

    public void setEnabled(Type t, boolean v) {
        typeEnabled.put(t.name(), v);
    }

    public int getColor(Type t) {
        Integer c = typeColor.get(t.name());
        return (c == null ? t.defaultColor : c) & 0xFFFFFF;
    }

    public void setColor(Type t, int rgb) {
        typeColor.put(t.name(), rgb & 0xFFFFFF);
    }

    public void resetToDefaults() {
        ESPConfig d = new ESPConfig();
        enabled = d.enabled;
        throughWalls = d.throughWalls;
        mergeDoubleChests = d.mergeDoubleChests;
        distanceFade = d.distanceFade;
        disableOnMultiplayer = d.disableOnMultiplayer;
        renderDistance = d.renderDistance;
        outlineThickness = d.outlineThickness;
        outlineOpacity = d.outlineOpacity;
        fillOpacity = d.fillOpacity;
        typeEnabled = d.typeEnabled;
        typeColor = d.typeColor;
    }

    /** Clamp values and fill in anything missing (e.g. hand-edited or older files). */
    public void sanitize() {
        if (typeEnabled == null) typeEnabled = new LinkedHashMap<>();
        if (typeColor == null) typeColor = new LinkedHashMap<>();
        for (Type t : Type.values()) {
            typeEnabled.putIfAbsent(t.name(), true);
            typeColor.putIfAbsent(t.name(), t.defaultColor);
            typeColor.put(t.name(), typeColor.get(t.name()) & 0xFFFFFF);
        }
        renderDistance = ColorUtil.clamp(renderDistance, 8f, 256f);
        outlineThickness = ColorUtil.clamp(outlineThickness, 0.5f, 8f);
        outlineOpacity = ColorUtil.clamp(outlineOpacity, 0.1f, 1f);
        fillOpacity = ColorUtil.clamp(fillOpacity, 0f, 0.6f);
    }

    public static ESPConfig load(Path path) {
        ESPConfig cfg = null;
        if (path != null && Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                cfg = GSON.fromJson(r, ESPConfig.class);
            } catch (Exception ignored) {
                cfg = null; // corrupt file -> fall back to defaults
            }
        }
        if (cfg == null) cfg = new ESPConfig();
        cfg.sanitize();
        return cfg;
    }

    public boolean save(Path path) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) {
                GSON.toJson(this, w);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFail) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
