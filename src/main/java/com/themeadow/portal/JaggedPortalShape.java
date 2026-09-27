package com.themeadow.portal;

import qouteall.imm_ptl.core.portal.shape.PortalShape;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.q_misc_util.my_util.Mesh2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the jagged/torn-edge floating-portal shape, matching the user's
 * reference screenshot: a blocky, stepped rectangular silhouette (grid-
 * aligned notches, not smooth random jitter), with a few small detached
 * "shard" squares floating just outside the main body's edge.
 *
 * Design (2026-09-27 redesign):
 * - A GRID x GRID cell grid spans the normalized [-1,1] mesh space. The
 *   main body starts as a filled rectangle inset from the grid edges,
 *   then boundary cells are randomly removed/added (stepped in/out) to
 *   produce the blocky notch look, while keeping the body a single
 *   4-connected region (so the actual teleport/collision mesh - which is
 *   this same connected shape, per SpecialFlatPortalShape - never has a
 *   disconnected hole or island in it).
 * - `Result.perimeterEdges` is the exact list of grid-cell boundary edges
 *   between an included and an excluded cell, in mesh-local [-1,1] space.
 *   com.themeadow.portal.client.PortalGlowRenderer/ParticleTicker walks
 *   this same list so the glow outline traces the identical stepped edge
 *   the player sees rendered by Seamless Portals - never hand-duplicated
 *   geometry that could drift out of sync with the real shape.
 * - `Result.shardCells` is a handful of extra cells picked just outside
 *   the main body (touching it, not included in it) - these are the
 *   small detached floating squares in the reference image. They are
 *   NOT added to the mesh (kept out of collision/teleport), only used by
 *   the glow/particle effect to draw small decorative glowing squares
 *   near the portal.
 */
public final class JaggedPortalShape {
    private JaggedPortalShape() {}

    /** Cells per side of the local [-1,1] shape grid. Higher = finer steps. */
    private static final int GRID = 10;
    private static final double CELL = 2.0 / GRID;

    public record Result(PortalShape shape, List<double[]> perimeterEdges, List<double[]> shardCells) {}

    /**
     * @param jaggedness 0.0 = perfect rectangle, higher = more/bigger notches.
     *                   0.2-0.4 is a good jagged-but-recognizable range.
     */
    public static Result build(double jaggedness, Random random) {
        boolean[][] cell = new boolean[GRID][GRID]; // [x][y]

        // Start from a rectangle inset by 1 cell on every side so there's
        // room to step outward as well as inward.
        int inset = 1;
        for (int x = inset; x < GRID - inset; x++) {
            for (int y = inset; y < GRID - inset; y++) {
                cell[x][y] = true;
            }
        }

        int notchPasses = Math.max(1, (int) Math.round(jaggedness * GRID));
        for (int i = 0; i < notchPasses; i++) {
            stepBoundary(cell, random, jaggedness);
        }

        ensureSingleConnectedRegion(cell);

        Mesh2D mesh = new Mesh2D();
        for (int x = 0; x < GRID; x++) {
            for (int y = 0; y < GRID; y++) {
                if (cell[x][y]) {
                    addCellQuad(mesh, x, y);
                }
            }
        }

        List<double[]> perimeterEdges = collectPerimeterEdges(cell);
        List<double[]> shardCells = collectShardCells(cell, random);

        return new Result(new SpecialFlatPortalShape(mesh), perimeterEdges, shardCells);
    }

    /** One pass: for every boundary cell, randomly flip a neighboring empty cell to filled (step out) or flip a boundary cell to empty (step in). */
    private static void stepBoundary(boolean[][] cell, Random random, double jaggedness) {
        List<int[]> boundary = new ArrayList<>();
        for (int x = 0; x < GRID; x++) {
            for (int y = 0; y < GRID; y++) {
                if (cell[x][y] && isBoundaryCell(cell, x, y)) {
                    boundary.add(new int[]{x, y});
                }
            }
        }
        for (int[] pos : boundary) {
            if (random.nextDouble() >= jaggedness) continue;
            int x = pos[0], y = pos[1];
            if (random.nextBoolean()) {
                // step in: remove this boundary cell
                cell[x][y] = false;
            } else {
                // step out: fill one empty orthogonal neighbor
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                int[] dir = dirs[random.nextInt(dirs.length)];
                int nx = x + dir[0], ny = y + dir[1];
                if (nx >= 0 && nx < GRID && ny >= 0 && ny < GRID && !cell[nx][ny]) {
                    cell[nx][ny] = true;
                }
            }
        }
    }

    private static boolean isBoundaryCell(boolean[][] cell, int x, int y) {
        return !isFilled(cell, x + 1, y) || !isFilled(cell, x - 1, y)
            || !isFilled(cell, x, y + 1) || !isFilled(cell, x, y - 1);
    }

    private static boolean isFilled(boolean[][] cell, int x, int y) {
        if (x < 0 || x >= GRID || y < 0 || y >= GRID) return false;
        return cell[x][y];
    }

    /**
     * The step-out moves in stepBoundary can occasionally pinch off a
     * cell into its own disconnected island (e.g. two step-ins isolate a
     * single filled cell). Flood-fill from the grid center and drop any
     * filled cell not reachable, so the teleport mesh always stays one
     * connected piece - a disconnected island in the actual portal plane
     * would be a walkable/teleportable surface floating in mid-air with
     * no connection to the rest of the portal, which reads as a bug
     * in-game rather than the intended "torn edge" look.
     */
    private static void ensureSingleConnectedRegion(boolean[][] cell) {
        boolean[][] visited = new boolean[GRID][GRID];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        int cx = GRID / 2, cy = GRID / 2;
        if (!cell[cx][cy]) cell[cx][cy] = true; // guarantee a seed
        queue.add(new int[]{cx, cy});
        visited[cx][cy] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : dirs) {
                int nx = p[0] + d[0], ny = p[1] + d[1];
                if (nx >= 0 && nx < GRID && ny >= 0 && ny < GRID && cell[nx][ny] && !visited[nx][ny]) {
                    visited[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        for (int x = 0; x < GRID; x++) {
            for (int y = 0; y < GRID; y++) {
                if (cell[x][y] && !visited[x][y]) {
                    cell[x][y] = false;
                }
            }
        }
    }

    private static void addCellQuad(Mesh2D mesh, int x, int y) {
        double x0 = -1.0 + x * CELL;
        double y0 = -1.0 + y * CELL;
        double x1 = x0 + CELL;
        double y1 = y0 + CELL;
        mesh.addQuad(x0, y0, x1, y1);
    }

    /** Every grid edge between a filled cell and an empty/out-of-bounds neighbor, as [x0,y0,x1,y1] segments in mesh-local space. */
    private static List<double[]> collectPerimeterEdges(boolean[][] cell) {
        List<double[]> edges = new ArrayList<>();
        for (int x = 0; x < GRID; x++) {
            for (int y = 0; y < GRID; y++) {
                if (!cell[x][y]) continue;
                double x0 = -1.0 + x * CELL;
                double y0 = -1.0 + y * CELL;
                double x1 = x0 + CELL;
                double y1 = y0 + CELL;
                if (!isFilled(cell, x, y - 1)) edges.add(new double[]{x0, y0, x1, y0}); // bottom
                if (!isFilled(cell, x, y + 1)) edges.add(new double[]{x0, y1, x1, y1}); // top
                if (!isFilled(cell, x - 1, y)) edges.add(new double[]{x0, y0, x0, y1}); // left
                if (!isFilled(cell, x + 1, y)) edges.add(new double[]{x1, y0, x1, y1}); // right
            }
        }
        return edges;
    }

    /** A handful of empty cells touching the main body, picked as small detached decorative shards (the floating squares in the reference image). Each entry is [x0,y0,x1,y1] in mesh-local space. */
    private static List<double[]> collectShardCells(boolean[][] cell, Random random) {
        List<int[]> candidates = new ArrayList<>();
        for (int x = 0; x < GRID; x++) {
            for (int y = 0; y < GRID; y++) {
                if (cell[x][y]) continue;
                if (isFilled(cell, x + 1, y) || isFilled(cell, x - 1, y)
                    || isFilled(cell, x, y + 1) || isFilled(cell, x, y - 1)) {
                    candidates.add(new int[]{x, y});
                }
            }
        }
        java.util.Collections.shuffle(candidates, random);
        int shardCount = Math.min(candidates.size(), 3 + random.nextInt(3)); // 3-5 shards
        List<double[]> shards = new ArrayList<>();
        for (int i = 0; i < shardCount; i++) {
            int x = candidates.get(i)[0], y = candidates.get(i)[1];
            double x0 = -1.0 + x * CELL;
            double y0 = -1.0 + y * CELL;
            shards.add(new double[]{x0, y0, x0 + CELL, y0 + CELL});
        }
        return shards;
    }
}
