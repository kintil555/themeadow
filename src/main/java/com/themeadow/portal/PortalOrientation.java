package com.themeadow.portal;

import net.minecraft.world.phys.Vec3;
import qouteall.q_misc_util.my_util.DQuaternion;

import java.util.Random;

/**
 * Builds a DQuaternion for the floating portal variant: plane stays
 * perfectly vertical (facing horizontally) but points a random yaw.
 *
 * Decompile-confirmed against seamlessportals-fabric-1_0_0.jar this
 * session: DQuaternion.fromFacingVecs(Vec3 axisW, Vec3 axisH) builds a
 * rotation directly from the portal's local width-axis and height-axis
 * in world space — no manual axis-angle math needed. axisH = Vec3(0,1,0)
 * keeps the plane vertical; axisW is a random horizontal unit vector,
 * perpendicular to axisH, which is exactly a random yaw.
 */
public final class PortalOrientation {
    private PortalOrientation() {}

    public static DQuaternion randomVerticalFacing(Random random) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        Vec3 axisW = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
        Vec3 axisH = new Vec3(0.0, 1.0, 0.0);
        return DQuaternion.fromFacingVecs(axisW, axisH);
    }
}
