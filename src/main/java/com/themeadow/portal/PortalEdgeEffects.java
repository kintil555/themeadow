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
 * screenshot: a dense warm gold/peach particle trail tracing the torn
 * outline (like a portal-gun rift's rim effect), pulsing glow on the
 * detached shard fragments, and a light outward particle drift from the
 * portal's face so it reads as an active light source even at range.
 *
 * Deliberately server-side only (Level.sendParticles, confirmed unchanged
 * in the real 26.2 client jar - see NOTES_FOR_NEXT_AI.md). Server-spawned
 * particles are automatically sent to nearby clients by vanilla, so this
 * needs no client-side render event, no WorldRenderEvents hook, and no
 * mixin - avoiding 26.2's rendering-pipeline rewrite entirely for a
 * purely cosmetic effect (custom outline quads were tried and dropped:
 * MultiBufferSource/RenderType moved/changed shape in 26.2 and the
 * debug-only Gizmos API throws outside a registered debug renderer, so
 * dense particles are the correct/only low-risk way to get this look).
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
    private static final int GLOW_COLOR_HOT = 0xFFE0B0; // slightly brighter variant, mixed in for sparkle
    private static final float DUST_SCALE = 1.15f;
    // Outline is now emitted every tick (not gated to a slow interval) so
    // it reads as a persistent glowing rim rather than an occasional
    // puff - this is what gives the "portal gun rift" density look.
    private static final int SHARD_PULSE_INTERVAL_TICKS = 4;
    private static final Random RANDOM = new Random();

    private static final Map<UUID, JaggedPortalShape.Result> TRACKED = new HashMap<>();
    private static int tickCounter = 0;

    public static void track(Portal portal, JaggedPortalShape.Result shapeResult) {
        TRACKED.put(portal.getUUID(), shapeResult);
    }

    public static void tickAll(MinecraftServer server) {
        tickCounter++;
        if (TRACKED.isEmpty()) return;

        boolean pulseTick = tickCounter % SHARD_PULSE_INTERVAL_TICKS == 0;
        TRACKED.entrySet().removeIf(entry -> {
            for (ServerLevel level : server.getAllLevels()) {
                Object entity = level.getEntity(entry.getKey());
                if (entity instanceof Portal portal) {
                    if (!portal.isAlive()) return true;
                    emitOutline(level, portal, entry.getValue());
                    if (pulseTick) emitShardsAndFace(level, portal, entry.getValue());
                    return false;
                }
            }
            return true; // portal not found in any loaded level anymore - drop it
        });
    }

    /** Every tick: a scattering of dust points along the torn edges - dense enough to read as a continuous glowing rim from a few blocks away, sparse enough per-point to stay performant. */
    private static void emitOutline(ServerLevel level, Portal portal, JaggedPortalShape.Result shapeResult) {
        Vec3 origin = portal.getOriginPos();
        Vec3 axisW = portal.getAxisW();
        Vec3 axisH = portal.getAxisH();
        double halfW = portal.getWidth() / 2.0;
        double halfH = portal.getHeight() / 2.0;

        List<double[]> edges = shapeResult.perimeterEdges();
        if (edges.isEmpty()) return;

        // Sample a handful of random points along random edges each tick,
        // scaled a little with edge count so a wider/longer outline still
        // reads as continuous rather than sparse.
        int samples = Math.min(edges.size(), 3 + edges.size() / 6);
        for (int i = 0; i < samples; i++) {
            double[] edge = edges.get(RANDOM.nextInt(edges.size()));
            double t = RANDOM.nextDouble();
            double localX = edge[0] + (edge[2] - edge[0]) * t;
            double localY = edge[1] + (edge[3] - edge[1]) * t;
            Vec3 world = toWorld(origin, axisW, axisH, localX, localY, halfW, halfH);

            int color = RANDOM.nextInt(3) == 0 ? GLOW_COLOR_HOT : GLOW_COLOR;
            DustParticleOptions dust = new DustParticleOptions(color, DUST_SCALE);
            level.sendParticles(dust, world.x, world.y, world.z, 1, 0.015, 0.015, 0.015, 0.0);

            if (RANDOM.nextInt(5) == 0) {
                level.sendParticles(ParticleTypes.END_ROD, world.x, world.y, world.z, 1, 0.01, 0.01, 0.01, 0.004);
            }
        }
    }

    /** Slower cadence: pulse the detached shard squares and drift a couple of glowing motes outward from the portal's face, like light escaping an active rift. */
    private static void emitShardsAndFace(ServerLevel level, Portal portal, JaggedPortalShape.Result shapeResult) {
        Vec3 origin = portal.getOriginPos();
        Vec3 axisW = portal.getAxisW();
        Vec3 axisH = portal.getAxisH();
        Vec3 normal = axisW.cross(axisH).normalize();
        double halfW = portal.getWidth() / 2.0;
        double halfH = portal.getHeight() / 2.0;
        DustParticleOptions dust = new DustParticleOptions(GLOW_COLOR, DUST_SCALE);

        List<double[]> shards = shapeResult.shardCells();
        if (!shards.isEmpty()) {
            double[] shard = shards.get(RANDOM.nextInt(shards.size()));
            double localX = (shard[0] + shard[2]) / 2.0;
            double localY = (shard[1] + shard[3]) / 2.0;
            Vec3 world = toWorld(origin, axisW, axisH, localX, localY, halfW, halfH);
            level.sendParticles(dust, world.x, world.y, world.z, 3, 0.04, 0.04, 0.04, 0.0);
        }

        // A couple of motes drifting outward from a random point on the
        // portal's face along its normal - reads as light/energy leaking
        // out of the rift, like a portal-gun shot's muzzle glow.
        for (int i = 0; i < 2; i++) {
            double localX = RANDOM.nextDouble() * 2.0 - 1.0;
            double localY = RANDOM.nextDouble() * 2.0 - 1.0;
            Vec3 facePoint = toWorld(origin, axisW, axisH, localX, localY, halfW, halfH);
            Vec3 outward = normal.scale(RANDOM.nextBoolean() ? 1 : -1);
            level.sendParticles(dust, facePoint.x, facePoint.y, facePoint.z, 1,
                outward.x * 0.05, outward.y * 0.05, outward.z * 0.05, 0.02);
        }
    }

    private static Vec3 toWorld(Vec3 origin, Vec3 axisW, Vec3 axisH, double localX, double localY, double halfW, double halfH) {
        return origin
            .add(axisW.scale(localX * halfW))
            .add(axisH.scale(localY * halfH));
    }
}
