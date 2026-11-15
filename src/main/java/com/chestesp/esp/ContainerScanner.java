package com.chestesp.esp;

import com.chestesp.ChestESPClient;
import com.chestesp.config.ESPConfig;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/** Reads real block entities from loaded client chunks and caches highlight boxes. */
public final class ContainerScanner {
    private ContainerScanner() {}

    public record Target(ESPConfig.Type type, Box box) {}

    private static volatile List<Target> targets = List.of();
    private static ClientWorld lastWorld;
    private static int tickCounter = 0;
    private static boolean force = true;

    public static List<Target> targets() {
        return targets;
    }

    public static void clear() {
        targets = List.of();
        lastWorld = null;
        force = true;
    }

    public static void invalidate() {
        force = true;
    }

    public static void tick(MinecraftClient mc) {
        ESPConfig cfg = ChestESPClient.config();
        ClientWorld world = mc.world;
        if (world == null || mc.player == null || !cfg.enabled) {
            if (!targets.isEmpty()) targets = List.of();
            force = true;
            return;
        }
        if (world != lastWorld) {
            lastWorld = world;
            targets = List.of();
            force = true;
        }
        if (!force && (tickCounter++ % 10) != 0) return;
        force = false;
        tickCounter = 1;

        double range = cfg.renderDistance + 16.0;
        double rangeSq = range * range;
        Vec3d pp = mc.player.getPos();
        int pcx = mc.player.getBlockPos().getX() >> 4;
        int pcz = mc.player.getBlockPos().getZ() >> 4;
        int r = (int) Math.ceil(range / 16.0);

        List<Target> out = new ArrayList<>();
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                Chunk chunk = world.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (!(chunk instanceof WorldChunk wc)) continue;
                for (BlockEntity be : new ArrayList<>(wc.getBlockEntities().values())) {
                    if (be == null || be.isRemoved()) continue;
                    BlockState st = be.getCachedState();
                    ESPConfig.Type type = classify(st.getBlock());
                    if (type == null) continue;
                    BlockPos pos = be.getPos();
                    if (pp.squaredDistanceTo(Vec3d.ofCenter(pos)) > rangeSq) continue;

                    Box box = boxFor(world, pos, st);
                    if (st.getBlock() instanceof ChestBlock && st.contains(ChestBlock.CHEST_TYPE)) {
                        ChestType ct = st.get(ChestBlock.CHEST_TYPE);
                        if (ct != ChestType.SINGLE && cfg.mergeDoubleChests) {
                            BlockPos other = pos.offset(ChestBlock.getFacing(st));
                            BlockState os = world.getBlockState(other);
                            if (os.getBlock() == st.getBlock() && os.contains(ChestBlock.CHEST_TYPE)
                                    && os.get(ChestBlock.CHEST_TYPE) == ct.getOpposite()) {
                                if (ct == ChestType.RIGHT) continue; // LEFT half draws the merged box
                                box = box.union(boxFor(world, other, os));
                            }
                        }
                    }
                    out.add(new Target(type, box));
                }
            }
        }
        targets = out;
    }

    private static ESPConfig.Type classify(Block b) {
        if (b instanceof TrappedChestBlock) return ESPConfig.Type.TRAPPED;
        if (b instanceof ChestBlock) return ESPConfig.Type.CHEST;
        if (b instanceof EnderChestBlock) return ESPConfig.Type.ENDER;
        if (b instanceof BarrelBlock) return ESPConfig.Type.BARREL;
        if (b instanceof ShulkerBoxBlock) return ESPConfig.Type.SHULKER;
        return null;
    }

    private static Box boxFor(ClientWorld world, BlockPos pos, BlockState st) {
        VoxelShape shape = st.getOutlineShape(world, pos);
        if (shape.isEmpty()) return new Box(pos);
        return shape.getBoundingBox().offset(pos);
    }
}
