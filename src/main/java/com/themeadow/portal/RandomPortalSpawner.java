package com.themeadow.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.DQuaternion;

import java.util.List;
import java.util.Random;

/**
 * Periodically (per server tick, gated by a chance roll) tries to spawn a
 * random portal to The Meadow near an online player: either wall-mounted
 * (flush in a solid wall) or floating (vertical, random yaw, jagged edges).
 *
 * NOT YET WIRED to a tick event callback — call `trySpawnFor(player)` from
 * `TheMeadow`'s ServerTickEvents.END_SERVER_TICK handler (see TODO #6/#8 in
 * NOTES_FOR_NEXT_AI.md: confirm the event class still exists unchanged in
 * fabric-api 0.160.0+26.2 before wiring).
 */
public final class RandomPortalSpawner {
    private RandomPortalSpawner() {}

    // --- tune these ---
    private static final double SPAWN_CHANCE_PER_CALL = 0.01; // caller decides call frequency
    private static final int SEARCH_RADIUS = 12;
    private static final double PORTAL_WIDTH = 1.2;
    private static final double PORTAL_HEIGHT = 2.2;
    private static final double JAGGEDNESS = 0.3;
    private static final int EDGE_POINTS = 20;

    // TODO: replace with real Meadow dimension key once
    // data/themeadow/dimension/the_meadow.json is registered (see TODO #4).
    public static ResourceKey<Level> meadowDimension;
    // TODO: replace with a real safe-arrival-point finder once Meadow
    // terrain worldgen exists (see TODO #4). Fixed anchor for now — Y=100
    // is a guess mid-air above the reused overworld noise settings'
    // surface; may need adjusting once actually tested in-game (overworld
    // noise settings target ~ sea-level 62-ish surface, could be lower).
    private static final Vec3 MEADOW_ARRIVAL = new Vec3(0.5, 100.0, 0.5);

    public static void trySpawnFor(ServerPlayer player, Random random) {
        if (meadowDimension == null) return; // Meadow dimension not registered yet
        if (random.nextDouble() > SPAWN_CHANCE_PER_CALL) return;

        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();

        WallSpot wallSpot = findWallSpot(level, origin, random);
        if (wallSpot != null) {
            spawnWallMounted(level, wallSpot, random);
            return;
        }

        // fall back to floating portal at an open-air point near the player
        BlockPos airSpot = findOpenAirSpot(level, origin, random);
        if (airSpot != null) {
            spawnFloating(level, Vec3.atCenterOf(airSpot), random);
        }
    }

    private record WallSpot(BlockPos base, Direction facing) {}

    /** Scans nearby blocks for a 2-tall x player-width solid wall face. */
    private static WallSpot findWallSpot(ServerLevel level, BlockPos origin, Random random) {
        List<BlockPos> candidates = BlockPos.betweenClosedStream(
            origin.offset(-SEARCH_RADIUS, -3, -SEARCH_RADIUS),
            origin.offset(SEARCH_RADIUS, 3, SEARCH_RADIUS)
        ).map(BlockPos::immutable).toList();

        List<BlockPos> shuffled = new java.util.ArrayList<>(candidates);
        java.util.Collections.shuffle(shuffled, random);

        for (BlockPos pos : shuffled) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                if (isValidWallFace(level, pos, facing)) {
                    return new WallSpot(pos, facing);
                }
            }
        }
        return null;
    }

    /** pos+facing must be solid wall, the two blocks in front (player side) must be air, 2 tall. */
    private static boolean isValidWallFace(ServerLevel level, BlockPos wallPos, Direction facing) {
        BlockState wallState = level.getBlockState(wallPos);
        BlockState wallAboveState = level.getBlockState(wallPos.above());
        if (!wallState.isFaceSturdy(level, wallPos, facing) || !wallAboveState.isFaceSturdy(level, wallPos.above(), facing)) {
            return false;
        }
        BlockPos front = wallPos.relative(facing);
        return level.getBlockState(front).isAir() && level.getBlockState(front.above()).isAir();
    }

    private static BlockPos findOpenAirSpot(ServerLevel level, BlockPos origin, Random random) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = random.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
            int dy = random.nextInt(5) - 1;
            int dz = random.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
            BlockPos candidate = origin.offset(dx, dy, dz);
            if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.above()).isAir()) {
                return candidate;
            }
        }
        return null;
    }

    private static void spawnWallMounted(ServerLevel level, WallSpot spot, Random random) {
        Portal portal = new Portal(Portal.ENTITY_TYPE, level);
        BlockPos front = spot.base().relative(spot.facing());
        Vec3 min = Vec3.atLowerCornerOf(front);
        AABB area = new AABB(
            min.x, min.y, min.z,
            min.x + 1.0, min.y + 2.0, min.z + 1.0
        );
        portal.setPos(Vec3.atCenterOf(front).x, front.getY(), Vec3.atCenterOf(front).z);
        PortalAPI.setPortalOrthodoxShape(portal, spot.facing(), area);
        finishPortal(level, portal, random);
    }

    private static void spawnFloating(ServerLevel level, Vec3 position, Random random) {
        Portal portal = new Portal(Portal.ENTITY_TYPE, level);
        portal.setPos(position.x, position.y, position.z);
        DQuaternion orientation = PortalOrientation.randomVerticalFacing(random);
        PortalAPI.setPortalPositionOrientationAndSize(
            portal, position, orientation, PORTAL_WIDTH, PORTAL_HEIGHT
        );
        portal.setPortalShape(JaggedPortalShape.build(JAGGEDNESS, EDGE_POINTS, random));
        finishPortal(level, portal, random);
    }

    private static void finishPortal(ServerLevel level, Portal portal, Random random) {
        PortalAPI.setPortalTransformation(
            portal, meadowDimension, MEADOW_ARRIVAL, null, 1.0
        );
        PortalAPI.spawnServerEntity(portal);
    }
}
