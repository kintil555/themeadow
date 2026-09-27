package com.themeadow.portal;

import qouteall.imm_ptl.core.portal.shape.PortalShape;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.q_misc_util.my_util.Mesh2D;

import java.util.Random;

/**
 * Builds the jagged/torn-edge floating-portal shape.
 *
 * Decompile-confirmed this session (seamlessportals-fabric-1_0_0.jar):
 * - SpecialFlatPortalShape(Mesh2D mesh) implements PortalShape directly —
 *   handles collision/clipping/raytrace/rendering for us. We only need to
 *   hand it a triangulated 2D mesh; no custom render-event hookery needed
 *   (the old NOTES_FOR_NEXT_AI plan (a)/(b) is obsolete — this is simpler
 *   and fully supported by the library).
 * - Mesh2D coordinates are normalized to [-1, 1] on both axes (confirmed
 *   from Mesh2D.indexPoint's Mth.clamp(-1,1) and Portal.setWidth/Height
 *   scaling that local space up afterwards) — so shape here is
 *   independent of the portal's actual width/height, set separately via
 *   PortalAPI.setPortalPositionOrientationAndSize.
 * - Mesh2D.addTriangle(x1,y1,x2,y1,x3,y3) auto-fixes winding order, so
 *   triangle vertex order here doesn't need to be pre-sorted CCW/CW.
 *
 * Approach: start from a rectangle inscribed slightly inside [-1,1], then
 * push each edge-midpoint-ish sample point in/out randomly to fake a torn
 * edge, fan-triangulating from the center (0,0).
 */
public final class JaggedPortalShape {
    private JaggedPortalShape() {}

    /**
     * @param jaggedness 0.0 = perfect rectangle, higher = more torn.
     *                   0.2-0.4 is a good jagged-but-recognizable range.
     * @param edgePoints number of perimeter samples (more = finer teeth).
     */
    public static PortalShape build(double jaggedness, int edgePoints, Random random) {
        Mesh2D mesh = new Mesh2D();

        double[] px = new double[edgePoints];
        double[] py = new double[edgePoints];
        for (int i = 0; i < edgePoints; i++) {
            double t = (double) i / edgePoints;
            // Base point walks a rectangle perimeter (perimeter-parameterized,
            // not angle-parameterized, so edges stay roughly straight before
            // jitter is applied — a proper "torn rectangle" rather than a
            // torn circle).
            double bx;
            double by;
            double seg = t * 4.0;
            if (seg < 1.0) {
                bx = -1.0 + 2.0 * seg;
                by = -1.0;
            } else if (seg < 2.0) {
                bx = 1.0;
                by = -1.0 + 2.0 * (seg - 1.0);
            } else if (seg < 3.0) {
                bx = 1.0 - 2.0 * (seg - 2.0);
                by = 1.0;
            } else {
                bx = -1.0;
                by = 1.0 - 2.0 * (seg - 3.0);
            }
            double jitter = 1.0 - jaggedness + random.nextDouble() * jaggedness;
            px[i] = bx * jitter;
            py[i] = by * jitter;
        }

        // Fan-triangulate from center so the mesh stays simple/convex-safe
        // even with jagged (non-convex) perimeter points.
        for (int i = 0; i < edgePoints; i++) {
            int next = (i + 1) % edgePoints;
            mesh.addTriangle(0.0, 0.0, px[i], py[i], px[next], py[next]);
        }

        return new SpecialFlatPortalShape(mesh);
    }
}
