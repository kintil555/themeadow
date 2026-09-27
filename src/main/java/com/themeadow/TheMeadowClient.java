package com.themeadow;

import com.themeadow.entity.client.ShepherdRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Client-only entrypoint. Referenced from fabric.mod.json's "client"
 * entrypoint (previously pointed at a class that didn't exist — see
 * NOTES_FOR_NEXT_AI.md TODO #6, resolved here alongside TheMeadow.java).
 */
public class TheMeadowClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(TheMeadow.SHEPHERD, ShepherdRenderer::new);
    }
}
