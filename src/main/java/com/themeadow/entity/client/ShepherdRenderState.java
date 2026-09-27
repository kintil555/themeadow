package com.themeadow.entity.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class ShepherdRenderState extends EntityRenderState {
    public boolean vanishing;
    public boolean watching;
    public float walkAnim;
    // world-space position is already provided by the base class
    // (x, y, z) — used in ShepherdRenderer to compute billboard yaw
    // toward the camera each frame.
}
