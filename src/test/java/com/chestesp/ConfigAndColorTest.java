package com.chestesp;

import com.chestesp.config.ESPConfig;
import com.chestesp.util.ColorUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigAndColorTest {
    @Test
    void configRoundTrip(@TempDir Path dir) {
        Path p = dir.resolve("chestesp.json");
        ESPConfig c = new ESPConfig();
        c.enabled = false;
        c.renderDistance = 100f;
        c.setColor(ESPConfig.Type.ENDER, 0x123456);
        c.setEnabled(ESPConfig.Type.BARREL, false);
        assertTrue(c.save(p));

        ESPConfig r = ESPConfig.load(p);
        assertFalse(r.enabled);
        assertEquals(100f, r.renderDistance);
        assertEquals(0x123456, r.getColor(ESPConfig.Type.ENDER));
        assertFalse(r.isEnabled(ESPConfig.Type.BARREL));
        assertTrue(r.isEnabled(ESPConfig.Type.CHEST));
    }

    @Test
    void corruptFileFallsBackToDefaults(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("chestesp.json");
        Files.writeString(p, "{ this is not json");
        ESPConfig r = ESPConfig.load(p);
        assertTrue(r.enabled);
        assertEquals(64f, r.renderDistance);
    }

    @Test
    void sanitizeClampsValues() {
        ESPConfig c = new ESPConfig();
        c.renderDistance = 99999f;
        c.outlineThickness = -3f;
        c.typeColor = null;
        c.sanitize();
        assertEquals(256f, c.renderDistance);
        assertEquals(0.5f, c.outlineThickness);
        assertEquals(ESPConfig.Type.CHEST.defaultColor, c.getColor(ESPConfig.Type.CHEST));
    }

    @Test
    void resetRestoresDefaults() {
        ESPConfig c = new ESPConfig();
        c.setColor(ESPConfig.Type.CHEST, 0x000001);
        c.enabled = false;
        c.resetToDefaults();
        assertTrue(c.enabled);
        assertEquals(ESPConfig.Type.CHEST.defaultColor, c.getColor(ESPConfig.Type.CHEST));
    }

    @Test
    void hsvRoundTrip() {
        int[] samples = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFA21A, 0x808080, 0xB04CFF};
        for (int rgb : samples) {
            float[] hsv = ColorUtil.rgbToHsv(rgb);
            int back = ColorUtil.hsvToRgb(hsv[0], hsv[1], hsv[2]);
            for (int shift = 0; shift <= 16; shift += 8) {
                assertTrue(Math.abs(((rgb >> shift) & 0xFF) - ((back >> shift) & 0xFF)) <= 1,
                        "channel mismatch for " + Integer.toHexString(rgb));
            }
        }
    }
}
