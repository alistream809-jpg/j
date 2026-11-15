package com.chestesp.esp;

import com.chestesp.ChestESPClient;
import com.chestesp.config.ESPConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws every container outline as thin camera-relative prisms (real geometry, so thickness is
 * exact and independent of driver line-width limits). Depth test is disabled for see-through ESP.
 */
public final class ESPRenderer {
    private ESPRenderer() {}

    private static boolean errorLogged = false;

    public static void render(WorldRenderContext ctx) {
        try {
            draw(ctx);
        } catch (Throwable t) {
            if (!errorLogged) {
                errorLogged = true;
                ChestESPClient.LOGGER.error("ChestESP render error (further errors suppressed)", t);
            }
        }
    }

    private static void draw(WorldRenderContext ctx) {
        ESPConfig cfg = ChestESPClient.config();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!cfg.enabled || mc.world == null || mc.player == null) return;
        if (cfg.disableOnMultiplayer && !mc.isInSingleplayer()) return;

        List<ContainerScanner.Target> list = ContainerScanner.targets();
        if (list.isEmpty()) return;
        MatrixStack stack = ctx.matrixStack();
        if (stack == null) return;

        Matrix4f mat = stack.peek().getPositionMatrix();
        Vec3d cam = ctx.camera().getPos();
        Frustum frustum = ctx.frustum();

        double maxDist = cfg.renderDistance;
        double maxSq = maxDist * maxDist;
        double fadeStart = maxDist * 0.6;

        BufferBuilder buf = null;
        for (ContainerScanner.Target t : list) {
            if (!cfg.isEnabled(t.type())) continue;
            Box b = t.box();
            Vec3d c = b.getCenter();
            double dx = c.x - cam.x, dy = c.y - cam.y, dz = c.z - cam.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > maxSq) continue;
            if (frustum != null && !frustum.isVisible(b.expand(0.15))) continue;

            double dist = Math.sqrt(d2);
            float fade = 1f;
            if (cfg.distanceFade && dist > fadeStart) {
                fade = (float) Math.max(0.1, 1.0 - (dist - fadeStart) / Math.max(1.0, maxDist - fadeStart));
            }

            int rgb = cfg.getColor(t.type());
            float r = ((rgb >> 16) & 0xFF) / 255f;
            float g = ((rgb >> 8) & 0xFF) / 255f;
            float bl = (rgb & 0xFF) / 255f;
            float outA = cfg.outlineOpacity * fade;
            float fillA = cfg.fillOpacity * fade;

            float x1 = (float) (b.minX - cam.x), y1 = (float) (b.minY - cam.y), z1 = (float) (b.minZ - cam.z);
            float x2 = (float) (b.maxX - cam.x), y2 = (float) (b.maxY - cam.y), z2 = (float) (b.maxZ - cam.z);

            // half-width grows with distance so the on-screen thickness stays ~constant
            float h = (float) Math.min(0.06, Math.max(0.0025, cfg.outlineThickness * dist * 0.00065));

            if (buf == null) buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            if (fillA > 0.002f) cuboid(buf, mat, x1, y1, z1, x2, y2, z2, r, g, bl, fillA);

            float[] ys = {y1, y2}, zs = {z1, z2}, xs = {x1, x2};
            for (float yy : ys) for (float zz : zs)
                cuboid(buf, mat, x1 - h, yy - h, zz - h, x2 + h, yy + h, zz + h, r, g, bl, outA);
            for (float xx : xs) for (float zz : zs)
                cuboid(buf, mat, xx - h, y1 - h, zz - h, xx + h, y2 + h, zz + h, r, g, bl, outA);
            for (float xx : xs) for (float yy : ys)
                cuboid(buf, mat, xx - h, yy - h, z1 - h, xx + h, yy + h, z2 + h, r, g, bl, outA);
        }
        if (buf == null) return;

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        if (cfg.throughWalls) RenderSystem.disableDepthTest(); else RenderSystem.enableDepthTest();
        try {
            BufferRenderer.drawWithGlobalProgram(buf.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void v(BufferBuilder b, Matrix4f m, float x, float y, float z, float r, float g, float bl, float a) {
        b.vertex(m, x, y, z).color(r, g, bl, a);
    }

    private static void cuboid(BufferBuilder b, Matrix4f m, float x1, float y1, float z1,
                               float x2, float y2, float z2, float r, float g, float bl, float a) {
        // -Y
        v(b, m, x1, y1, z1, r, g, bl, a); v(b, m, x2, y1, z1, r, g, bl, a); v(b, m, x2, y1, z2, r, g, bl, a); v(b, m, x1, y1, z2, r, g, bl, a);
        // +Y
        v(b, m, x1, y2, z1, r, g, bl, a); v(b, m, x1, y2, z2, r, g, bl, a); v(b, m, x2, y2, z2, r, g, bl, a); v(b, m, x2, y2, z1, r, g, bl, a);
        // -Z
        v(b, m, x1, y1, z1, r, g, bl, a); v(b, m, x1, y2, z1, r, g, bl, a); v(b, m, x2, y2, z1, r, g, bl, a); v(b, m, x2, y1, z1, r, g, bl, a);
        // +Z
        v(b, m, x1, y1, z2, r, g, bl, a); v(b, m, x2, y1, z2, r, g, bl, a); v(b, m, x2, y2, z2, r, g, bl, a); v(b, m, x1, y2, z2, r, g, bl, a);
        // -X
        v(b, m, x1, y1, z1, r, g, bl, a); v(b, m, x1, y1, z2, r, g, bl, a); v(b, m, x1, y2, z2, r, g, bl, a); v(b, m, x1, y2, z1, r, g, bl, a);
        // +X
        v(b, m, x2, y1, z1, r, g, bl, a); v(b, m, x2, y2, z1, r, g, bl, a); v(b, m, x2, y2, z2, r, g, bl, a); v(b, m, x2, y1, z2, r, g, bl, a);
    }
}
