package com.chestesp.gui;

import com.chestesp.ChestESPClient;
import com.chestesp.config.ESPConfig;
import com.chestesp.esp.ContainerScanner;
import com.chestesp.input.KeyBindings;
import com.chestesp.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Dark glass settings UI. Immediate-mode: hit areas are rebuilt every frame. */
public class ESPScreen extends Screen {
    private static final int ACCENT = 0xFF6C8CFF;
    private static final String[] CATS = {"General", "Containers", "Colors", "Render", "Keybinds"};

    private interface DragFn { void at(double mx, double my); }

    private record Hit(int x, int y, int w, int h, boolean drag, DragFn fn) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final Screen parent;
    private final ESPConfig cfg = ChestESPClient.config();
    private final long openTime = Util.getMeasuringTimeMs();
    private long lastFrame = openTime;
    private float dt;
    private float alpha;

    private final Map<String, Float> anims = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private Hit activeHit;

    private int category = 0;
    private ESPConfig.Type colorSel = ESPConfig.Type.CHEST;
    private ESPConfig.Type hsvFor = null;
    private float hue, sat, val;
    private KeyBinding rebinding;

    private int px, py, pw, ph;
    private static final int SIDEBAR = 104;

    public ESPScreen(Screen parent) {
        super(Text.literal("ChestESP"));
        this.parent = parent;
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void close() {
        ChestESPClient.saveConfig();
        if (client != null) client.setScreen(parent);
    }

    @Override public void removed() { ChestESPClient.saveConfig(); }

    // ---------------------------------------------------------------- helpers

    private float openProgress() {
        float t = Math.min(1f, (Util.getMeasuringTimeMs() - openTime) / 240f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private float yOffset() { return (1f - openProgress()) * 14f; }

    private float smooth(String key, float target) {
        float cur = anims.getOrDefault(key, target);
        cur += (target - cur) * Math.min(1f, dt * 16f);
        anims.put(key, cur);
        return cur;
    }

    private int al(int argb) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private int txtColor(int argb) {
        int a = Math.max(4, Math.round(((argb >>> 24) & 0xFF) * alpha));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private void text(DrawContext c, String s, int x, int y, int color) {
        c.drawText(textRenderer, s, x, y, txtColor(color), false);
    }

    private void textRight(DrawContext c, String s, int right, int y, int color) {
        text(c, s, right - textRenderer.getWidth(s), y, color);
    }

    private void rrect(DrawContext c, int x, int y, int w, int h, int r, int argb) {
        int col = al(argb);
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { c.fill(x, y, x + w, y + h, col); return; }
        c.fill(x, y + r, x + w, y + h - r, col);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, col);
        }
    }

    private boolean over(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- widgets

    private void toggleRow(DrawContext c, String id, String label, int dot, int x, int y, int w,
                           boolean value, Consumer<Boolean> set, int mx, int my) {
        float hv = smooth("h:" + id, over(x, y, w, 24, mx, my) ? 1f : 0f);
        rrect(c, x, y, w, 24, 6, ((int) (10 + hv * 16) << 24) | 0xFFFFFF);
        int tx = x + 10;
        if (dot != 0) { rrect(c, x + 10, y + 8, 8, 8, 3, 0xFF000000 | dot); tx += 15; }
        text(c, label, tx, y + 8, 0xFFE8EAF0);
        float t = smooth("t:" + id, value ? 1f : 0f);
        int sx = x + w - 40, sy = y + 5;
        rrect(c, sx, sy, 30, 14, 7, ColorUtil.lerpArgb(0x55FFFFFF, ACCENT, t));
        rrect(c, sx + 2 + Math.round(t * 16), sy + 2, 10, 10, 5, 0xFFFFFFFF);
        hits.add(new Hit(x, y, w, 24, false, (a, b) -> set.accept(!value)));
    }

    private void sliderRow(DrawContext c, String id, String label, int x, int y, int w,
                           float min, float max, float step, float value, String valueText,
                           Consumer<Float> set) {
        text(c, label, x + 10, y + 2, 0xFFE8EAF0);
        textRight(c, valueText, x + w - 10, y + 2, 0xFFB8C4FF);
        final int tx = x + 10, tw = w - 20, ty = y + 18;
        float t = ColorUtil.clamp((value - min) / (max - min), 0f, 1f);
        rrect(c, tx, ty, tw, 4, 2, 0x33FFFFFF);
        rrect(c, tx, ty, Math.max(4, (int) (tw * t)), 4, 2, ACCENT);
        rrect(c, tx + (int) (tw * t) - 4, ty - 4, 8, 12, 4, 0xFFFFFFFF);
        hits.add(new Hit(tx - 4, ty - 8, tw + 8, 20, true, (a, b) -> {
            float n = ColorUtil.clamp((float) ((a - tx) / tw), 0f, 1f);
            float v = min + n * (max - min);
            v = Math.round(v / step) * step;
            set.accept(ColorUtil.clamp(v, min, max));
        }));
    }

    private void button(DrawContext c, String id, String label, int x, int y, int w, int h,
                        int color, Runnable action, int mx, int my) {
        float hv = smooth("b:" + id, over(x, y, w, h, mx, my) ? 1f : 0f);
        int base = 0x55000000 | (color & 0xFFFFFF);
        int hot = 0xCC000000 | (color & 0xFFFFFF);
        rrect(c, x, y, w, h, 6, ColorUtil.lerpArgb(base, hot, hv));
        text(c, label, x + (w - textRenderer.getWidth(label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        hits.add(new Hit(x, y, w, h, false, (a, b) -> action.run()));
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = Util.getMeasuringTimeMs();
        dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        alpha = openProgress();
        hits.clear();

        pw = Math.min(440, width - 16);
        ph = Math.min(280, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        renderBackground(c, mouseX, mouseY, delta); // vanilla blur + darkening where supported

        float off = yOffset();
        int mx = mouseX, my = Math.round(mouseY - off);
        c.getMatrices().push();
        c.getMatrices().translate(0f, off, 0f);

        // white glow, border, glass body
        for (int i = 6; i >= 1; i--) rrect(c, px - i, py - i, pw + 2 * i, ph + 2 * i, 10 + i, 0x0AFFFFFF);
        rrect(c, px - 1, py - 1, pw + 2, ph + 2, 11, 0x40FFFFFF);
        rrect(c, px, py, pw, ph, 10, 0xD0101218);

        // sidebar
        rrect(c, px + 6, py + 6, SIDEBAR - 6, ph - 12, 8, 0x14FFFFFF);
        c.drawText(textRenderer, Text.literal("ChestESP").formatted(Formatting.BOLD), px + 16, py + 14, txtColor(0xFFFFFFFF), false);
        text(c, "v" + "1.0", px + 16, py + 25, 0x66FFFFFF);
        for (int i = 0; i < CATS.length; i++) {
            final int idx = i;
            int bx = px + 12, by = py + 42 + i * 24, bw = SIDEBAR - 18;
            float sel = smooth("cat:" + i, category == i ? 1f : 0f);
            float hv = smooth("catH:" + i, over(bx, by, bw, 20, mx, my) ? 1f : 0f);
            rrect(c, bx, by, bw, 20, 6, ColorUtil.lerpArgb(((int) (hv * 22) << 24) | 0xFFFFFF, 0x99000000 | (ACCENT & 0xFFFFFF), sel));
            text(c, CATS[i], bx + 10, by + 6, ColorUtil.lerpArgb(0xFFB5B9C6, 0xFFFFFFFF, Math.max(sel, hv)));
            hits.add(new Hit(bx, by, bw, 20, false, (a, b) -> { category = idx; rebinding = null; }));
        }
        text(c, cfg.enabled ? "ESP: ON" : "ESP: OFF", px + 16, py + ph - 22, cfg.enabled ? 0xFF5CE08A : 0xFFFF6B6B);

        // content
        int cx = px + SIDEBAR + 12, cw = pw - SIDEBAR - 24;
        c.drawText(textRenderer, Text.literal(CATS[category]).formatted(Formatting.BOLD), cx + 2, py + 14, txtColor(0xFFFFFFFF), false);
        c.fill(cx, py + 28, cx + cw, py + 29, al(0x22FFFFFF));
        int y0 = py + 36;

        switch (category) {
            case 0 -> pageGeneral(c, cx, y0, cw, mx, my);
            case 1 -> pageContainers(c, cx, y0, cw, mx, my);
            case 2 -> pageColors(c, cx, y0, cw, mx, my);
            case 3 -> pageRender(c, cx, y0, cw);
            default -> pageKeys(c, cx, y0, cw, mx, my);
        }
        c.getMatrices().pop();
    }

    private void pageGeneral(DrawContext c, int cx, int y, int cw, int mx, int my) {
        toggleRow(c, "en", "Enable ChestESP", 0, cx, y, cw, cfg.enabled, v -> { cfg.enabled = v; ContainerScanner.invalidate(); }, mx, my);
        y += 26;
        toggleRow(c, "tw", "See through walls", 0, cx, y, cw, cfg.throughWalls, v -> cfg.throughWalls = v, mx, my);
        y += 26;
        toggleRow(c, "md", "Merge double chests", 0, cx, y, cw, cfg.mergeDoubleChests, v -> { cfg.mergeDoubleChests = v; ContainerScanner.invalidate(); }, mx, my);
        y += 26;
        toggleRow(c, "df", "Fade with distance", 0, cx, y, cw, cfg.distanceFade, v -> cfg.distanceFade = v, mx, my);
        y += 26;
        toggleRow(c, "mp", "Disable on multiplayer servers", 0, cx, y, cw, cfg.disableOnMultiplayer, v -> cfg.disableOnMultiplayer = v, mx, my);
        y += 32;
        button(c, "reset", "Reset configuration", cx, y, 140, 20, 0xFF5C5C, () -> {
            cfg.resetToDefaults();
            hsvFor = null;
            ContainerScanner.invalidate();
            ChestESPClient.saveConfig();
        }, mx, my);
        text(c, "Only use where server rules allow it.", cx + 2, py + ph - 20, 0x66FFFFFF);
    }

    private void pageContainers(DrawContext c, int cx, int y, int cw, int mx, int my) {
        for (ESPConfig.Type t : ESPConfig.Type.values()) {
            toggleRow(c, "ty:" + t.name(), t.label, cfg.getColor(t), cx, y, cw, cfg.isEnabled(t), v -> cfg.setEnabled(t, v), mx, my);
            y += 26;
        }
        text(c, "Double chests are merged into one box (see General).", cx + 2, py + ph - 20, 0x66FFFFFF);
    }

    private void pageRender(DrawContext c, int cx, int y, int cw) {
        sliderRow(c, "rd", "Render distance", cx, y, cw, 8f, 256f, 1f, cfg.renderDistance,
                Math.round(cfg.renderDistance) + " blocks", v -> { cfg.renderDistance = v; ContainerScanner.invalidate(); });
        y += 34;
        sliderRow(c, "ot", "Outline thickness", cx, y, cw, 0.5f, 8f, 0.5f, cfg.outlineThickness,
                String.format("%.1f", cfg.outlineThickness), v -> cfg.outlineThickness = v);
        y += 34;
        sliderRow(c, "oo", "Outline opacity", cx, y, cw, 0.1f, 1f, 0.01f, cfg.outlineOpacity,
                Math.round(cfg.outlineOpacity * 100) + "%", v -> cfg.outlineOpacity = v);
        y += 34;
        sliderRow(c, "fo", "Fill opacity", cx, y, cw, 0f, 0.6f, 0.01f, cfg.fillOpacity,
                Math.round(cfg.fillOpacity * 100) + "%", v -> cfg.fillOpacity = v);
    }

    private void applyHsv() {
        cfg.setColor(colorSel, ColorUtil.hsvToRgb(hue, sat, val));
    }

    private void pageColors(DrawContext c, int cx, int y0, int cw, int mx, int my) {
        final int lw = 112;
        ESPConfig.Type[] types = ESPConfig.Type.values();
        for (int i = 0; i < types.length; i++) {
            final ESPConfig.Type t = types[i];
            int ry = y0 + i * 26;
            float s = smooth("cs:" + t.name(), t == colorSel ? 1f : 0f);
            float hv = smooth("csH:" + t.name(), over(cx, ry, lw, 24, mx, my) ? 1f : 0f);
            rrect(c, cx, ry, lw, 24, 6, ColorUtil.lerpArgb(((int) (10 + hv * 14) << 24) | 0xFFFFFF, 0x66000000 | (ACCENT & 0xFFFFFF), s));
            rrect(c, cx + 8, ry + 8, 8, 8, 3, 0xFF000000 | cfg.getColor(t));
            text(c, t.label, cx + 22, ry + 8, 0xFFE8EAF0);
            hits.add(new Hit(cx, ry, lw, 24, false, (a, b) -> { colorSel = t; hsvFor = null; }));
        }

        if (hsvFor != colorSel) {
            float[] hsv = ColorUtil.rgbToHsv(cfg.getColor(colorSel));
            hue = hsv[0]; sat = hsv[1]; val = hsv[2];
            hsvFor = colorSel;
        }

        final int sx = cx + lw + 12;
        final int sy = y0;
        final int sq = Math.max(48, Math.min(88, ph - 70));

        // saturation / value square
        for (int i = 0; i < sq; i += 2) {
            float s = (i + 1f) / sq;
            int top = 0xFF000000 | ColorUtil.hsvToRgb(hue, s, 1f);
            c.fillGradient(sx + i, sy, sx + Math.min(i + 2, sq), sy + sq, al(top), al(0xFF000000));
        }
        c.fill(sx - 1, sy - 1, sx + sq + 1, sy, al(0x66FFFFFF));
        c.fill(sx - 1, sy + sq, sx + sq + 1, sy + sq + 1, al(0x66FFFFFF));
        c.fill(sx - 1, sy, sx, sy + sq, al(0x66FFFFFF));
        c.fill(sx + sq, sy, sx + sq + 1, sy + sq, al(0x66FFFFFF));
        int mkx = sx + Math.round(sat * sq), mky = sy + Math.round((1f - val) * sq);
        c.fill(mkx - 3, mky - 3, mkx + 4, mky + 4, al(0xFFFFFFFF));
        c.fill(mkx - 2, mky - 2, mkx + 3, mky + 3, al(0xFF000000 | cfg.getColor(colorSel)));
        hits.add(new Hit(sx, sy, sq, sq, true, (a, b) -> {
            sat = ColorUtil.clamp((float) ((a - sx) / sq), 0f, 1f);
            val = 1f - ColorUtil.clamp((float) ((b - sy) / sq), 0f, 1f);
            applyHsv();
        }));

        // hue bar
        final int hx = sx + sq + 8;
        for (int i = 0; i < sq; i += 2) {
            c.fill(hx, sy + i, hx + 12, sy + Math.min(i + 2, sq), al(0xFF000000 | ColorUtil.hsvToRgb(i / (float) sq, 1f, 1f)));
        }
        int hmy = sy + Math.round(hue * sq);
        c.fill(hx - 2, hmy - 1, hx + 14, hmy + 2, al(0xFFFFFFFF));
        hits.add(new Hit(hx - 2, sy, 16, sq, true, (a, b) -> {
            hue = ColorUtil.clamp((float) ((b - sy) / sq), 0f, 0.999f);
            applyHsv();
        }));

        // preview + values
        final int rx = hx + 22;
        int rgb = cfg.getColor(colorSel);
        rrect(c, rx, sy, 56, 28, 6, 0xFF000000 | rgb);
        text(c, String.format("#%06X", rgb), rx, sy + 34, 0xFFE8EAF0);
        text(c, "R " + ((rgb >> 16) & 0xFF), rx, sy + 46, 0xFFFF8A8A);
        text(c, "G " + ((rgb >> 8) & 0xFF), rx, sy + 57, 0xFF8AFF9A);
        text(c, "B " + (rgb & 0xFF), rx, sy + 68, 0xFF8AB4FF);
        button(c, "rc", "Reset", rx, sy + sq - 18, 56, 18, 0xFF5C5C, () -> {
            cfg.setColor(colorSel, colorSel.defaultColor);
            hsvFor = null;
        }, mx, my);
    }

    private void pageKeys(DrawContext c, int cx, int y0, int cw, int mx, int my) {
        KeyBinding[] kbs = KeyBindings.all();
        String[] names = KeyBindings.names();
        for (int i = 0; i < kbs.length; i++) {
            final KeyBinding kb = kbs[i];
            int ry = y0 + i * 25;
            rrect(c, cx, ry, cw, 22, 6, 0x0CFFFFFF);
            text(c, names[i], cx + 10, ry + 7, 0xFFE8EAF0);
            boolean listening = rebinding == kb;
            String label = listening ? "Press a key..." : kb.getBoundKeyLocalizedText().getString();
            int bw = 96, bx = cx + cw - bw - 6;
            float hv = smooth("kb:" + i, listening || over(bx, ry + 2, bw, 18, mx, my) ? 1f : 0f);
            rrect(c, bx, ry + 2, bw, 18, 5, ColorUtil.lerpArgb(0x33FFFFFF, 0xAA000000 | (ACCENT & 0xFFFFFF), hv));
            text(c, label, bx + (bw - textRenderer.getWidth(label)) / 2, ry + 7, 0xFFFFFFFF);
            hits.add(new Hit(bx, ry + 2, bw, 18, false, (a, b) -> rebinding = kb));
        }
        text(c, "Click a key, then press the new one. Del = unbind, Esc = cancel.", cx + 2, py + ph - 20, 0x66FFFFFF);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        double my = mouseY - yOffset();
        if (rebinding != null) rebinding = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mouseX, my)) {
                h.fn().at(mouseX, my);
                if (h.drag()) activeHit = h;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (activeHit != null && button == 0) {
            activeHit.fn().at(mouseX, mouseY - yOffset());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeHit != null) {
            activeHit = null;
            ChestESPClient.saveConfig();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (rebinding != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                rebinding = null;
                return true;
            }
            InputUtil.Key key = (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE)
                    ? InputUtil.UNKNOWN_KEY : InputUtil.fromKeyCode(keyCode, scanCode);
            rebinding.setBoundKey(key);
            KeyBinding.updateKeysByCode();
            if (client != null) client.options.write();
            rebinding = null;
            return true;
        }
        if (KeyBindings.MENU != null && KeyBindings.MENU.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
