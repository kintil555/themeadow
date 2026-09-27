package com.themeadow.portal;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Drives the "glow at the edges + particle effects" look the user asked
 * for on the floating (jagged) Meadow portal, matching the reference
 * screenshot: a warm gold/peach glow tracing the torn outline, plus a
 * few extra glowing squares near the detached shard fragments.
 *
 * Deliberately server-side only (Level.addParticle, confirmed unchanged
 * in the real 26.2 client jar - see NOTES_FOR_NEXT_AI.md). Server-spawned
 * particles are automatically sent to nearby clients by vanilla, so this
 * needs no client-side render event, no WorldRenderEvents hook, and no
 * mixin - avoiding the biggest unverified API surface (26.2's submit-node
 * rendering rewrite) for a purely cosmetic effect.
 *
 * Register {@link #track} right after a floating portal is spawned (it
 * needs the JaggedPortalShape.Result to know where the edges/shards are)
 * and call {@link #tickAll} once per server tick from TheMeadow's
 * existing END_SERVER_TICK handler, passing the MinecraftServer so it
 * can resolve each tracked portal by UUID across all loaded levels. A
 * tracked entry is dropped automatically once its portal is no longer
 * alive or can't be found in any loaded level.
 */
public final class PortalEdgeEffects {
    private PortalEdgeEffects() {}

    // Warm gold/peach to match the reference screenshot's glow color.
    private static final int GLOW_COLOR = 0xFFC98A; // RGB, matches DustParticleOptions(int rgb, float scale)
    private static final float DUST_SCALE = 1.1f;
    // How often (in ticks) each tracked portal emits a burst of edge
    // particles. Not every tick - a jagged edge with many segments would
    // otherwise spam far more particles than the reference image's calm,
    // sparse drift.
    private static final int EMIT_INTERVAL_TICKS = 4;
    private static final Random RANDOM = new Random();

    private static final Map<UUID, JaggedPortalShape.Result> TRACKED = new HashMap<>();
    private static int tickCounter = 0;

    public static void track(Portal portal, JaggedPortalShape.Result shapeResult) {
        TRACKED.put(portal.getUUID(), shapeResult);
    }

    public static void tickAll(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % EMIT_INTERVAL_TICKS != 0) return;
        if (TRACKED.isEmpty()) return;

        TRACKED.entrySet().removeIf(entry -> {
            for (ServerLevel level : server.getAllLevels()) {
                Object entity = level.getEntity(entry.getKey());
                if (entity instanceof Portal portal) {
                    if (!portal.isAlive()) return true;
                    emit(level, portal, entry.getValue());
                    return false;
                }
            }
            return true; // portal not found in any loaded level anymore - drop it
        });
    }

    private static void emit(ServerLevel level, Portal portal, JaggedPortalShape.Result shapeResult) {
        Vec3 origin = portal.getOriginPos();
        Vec3 axisW = portal.getAxisW();
        Vec3 axisH = portal.getAxisH();
        double halfW = portal.getWidth() / 2.0;
        double halfH = portal.getHeight() / 2.0;
        DustParticleOptions dust = new DustParticleOptions(GLOW_COLOR, DUST_SCALE);

        // A handful of random points along the perimeter edges each
        // emission - sparse, drifting glow rather than a dense outline
        // redrawn every tick.
        List<double[]> edges = shapeResult.perimeterEdges();
        if (!edges.isEmpty()) {
            int samples = Math.min(edges.size(), 4);
            for (int i = 0; i < samples; i++) {
                double[] edge = edges.get(RANDOM.nextInt(edges.size()));
                double t = RANDOM.nextDouble();
                double localX = edge[0] + (edge[2] - edge[0]) * t;
                double localY = edge[1] + (edge[3] - edge[1]) * t;
                Vec3 world = toWorld(origin, axisW, axisH, localX, localY, halfW, halfH);
                level.sendParticles(dust, world.x, world.y, world.z, 1, 0.02, 0.02, 0.02, 0.0);
                if (RANDOM.nextInt(3) == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, world.x, world.y, world.z, 1, 0.01, 0.01, 0.01, 0.005);
                }
            }
        }

        // One shard square lightly glowing per emission, cycling through
        // the shard list, so each detached fragment periodically pulses.
        List<double[]> shards = shapeResult.shardCells();
        if (!shards.isEmpty()) {
            double[] shard = shards.get(RANDOM.nextInt(shards.size()));
            double localX = (shard[0] + shard[2]) / 2.0;
            double localY = (shard[1] + shard[3]) / 2.0;
            Vec3 world = toWorld(origin, axisW, axisH, localX, localY, halfW, halfH);
            level.sendParticles(dust, world.x, world.y, world.z, 2, 0.03, 0.03, 0.03, 0.0);
        }
    }

    private static Vec3 toWorld(Vec3 origin, Vec3 axisW, Vec3 axisH, double localX, double localY, double halfW, double halfH) {
        return origin
            .add(axisW.scale(localX * halfW))
            .add(axisH.scale(localY * halfH));
    }
}
