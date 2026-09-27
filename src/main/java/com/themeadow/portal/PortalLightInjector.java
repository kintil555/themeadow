package com.themeadow.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Makes the floating Meadow portal itself a real light source, instead of
 * only a particle effect - "inject" the glow into the portal's own
 * silhouette rather than scattering separate glow objects around it.
 *
 * Uses vanilla Blocks.LIGHT (added in 21w13a): an invisible block whose
 * only purpose is emitting a configurable light level 0-15. This is the
 * same primitive real published dynamic-light datapacks/mods use under
 * the hood (e.g. "Dynamic Lights [Server]" - it has no client
 * requirement, needs no mixin, and is guaranteed stable across MC
 * versions since it's just a normal block).
 *
 * Placement is restricted to AIR blocks that the portal's own silhouette
 * (perimeter-edge cells, not just the outer box) actually occupies -
 * never overwrites solid terrain, and skips a cell if something already
 * changed it since spawn. Cleared automatically the moment the tracked
 * Portal is no longer alive (checked from PortalEdgeEffects.tickAll,
 * which already visits every tracked portal once a tick).
 */
public final class PortalLightInjector {
    private PortalLightInjector() {}

    private static final int LIGHT_LEVEL = 15;
    // Injected once per (roughly) unique world cell the mesh occupies -
    // duplicate cells at fractional resolution would just be wasted
    // setBlock calls for the same block position.
    private static final Map<UUID, Set<BlockPos>> INJECTED = new HashMap<>();

    /** Call once, right after a floating portal is spawned. */
    public static void inject(ServerLevel level, Portal portal, JaggedPortalShape.Result shapeResult) {
        Vec3 origin = portal.getOriginPos();
        Vec3 axisW = portal.getAxisW();
        Vec3 axisH = portal.getAxisH();
        double halfW = portal.getWidth() / 2.0;
        double halfH = portal.getHeight() / 2.0;

        Set<BlockPos> placed = new HashSet<>();
        List<double[]> edges = shapeResult.perimeterEdges();

        // Walk every perimeter edge's midpoint and both endpoints so the
        // light coverage traces the actual torn silhouette, not just a
        // bounding box.
        for (double[] edge : edges) {
            placeIfAir(level, placed, toWorld(origin, axisW, axisH, edge[0], edge[1], halfW, halfH));
            placeIfAir(level, placed, toWorld(origin, axisW, axisH, edge[2], edge[3], halfW, halfH));
            double midX = (edge[0] + edge[2]) / 2.0, midY = (edge[1] + edge[3]) / 2.0;
            placeIfAir(level, placed, toWorld(origin, axisW, axisH, midX, midY, halfW, halfH));
        }
        // Also light the shard fragments and the portal's own center so
        // the whole face reads as glowing, not just the rim.
        for (double[] shard : shapeResult.shardCells()) {
            double cx = (shard[0] + shard[2]) / 2.0, cy = (shard[1] + shard[3]) / 2.0;
            placeIfAir(level, placed, toWorld(origin, axisW, axisH, cx, cy, halfW, halfH));
        }
        placeIfAir(level, placed, origin);

        if (!placed.isEmpty()) {
            INJECTED.put(portal.getUUID(), placed);
        }
    }

    /** Removes every light block previously injected for portalId, searching all given levels since the portal's last known level isn't tracked separately. */
    public static void clear(Iterable<ServerLevel> levels, UUID portalId) {
        Set<BlockPos> placed = INJECTED.remove(portalId);
        if (placed == null) return;
        for (ServerLevel level : levels) {
            for (BlockPos pos : placed) {
                if (level.getBlockState(pos).is(Blocks.LIGHT)) {
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void placeIfAir(ServerLevel level, Set<BlockPos> placed, Vec3 worldPos) {
        BlockPos pos = BlockPos.containing(worldPos);
        if (placed.contains(pos)) return; // already handled this cell for this portal
        if (!level.getBlockState(pos).isAir()) return; // never overwrite real terrain
        BlockState lightState = Blocks.LIGHT.defaultBlockState()
            .setValue(BlockStateProperties.LEVEL, LIGHT_LEVEL);
        level.setBlock(pos, lightState, 3);
        placed.add(pos);
    }

    private static Vec3 toWorld(Vec3 origin, Vec3 axisW, Vec3 axisH, double localX, double localY, double halfW, double halfH) {
        return origin
            .add(axisW.scale(localX * halfW))
            .add(axisH.scale(localY * halfH));
    }
}
