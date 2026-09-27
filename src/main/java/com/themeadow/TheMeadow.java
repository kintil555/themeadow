package com.themeadow;

import com.themeadow.entity.ShepherdEntity;
import com.themeadow.portal.RandomPortalSpawner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.Random;

/**
 * Common (server+client) entrypoint. Referenced from fabric.mod.json's
 * "main" entrypoint (previously pointed at a class that didn't exist —
 * see NOTES_FOR_NEXT_AI.md TODO #6, this file resolves that blocker).
 *
 * NOT YET VERIFIED against a real Gradle build (no Fabric maven access in
 * this sandbox) — FabricEntityTypeBuilder / FabricDefaultAttributeRegistry
 * package paths are the standard fabric-api locations as of recent
 * versions; ServerTickEvents.END_SERVER_TICK confirmed still present via
 * web search (no breaking changes reported through MC 1.21.11-era
 * fabric-api). Double-check imports against the real fabric-api jar on
 * first real build if compilation fails here.
 */
public class TheMeadow implements ModInitializer {
    public static final String MOD_ID = "themeadow";

    public static EntityType<ShepherdEntity> SHEPHERD;

    private final Random random = new Random();

    @Override
    public void onInitialize() {
        SHEPHERD = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(MOD_ID, "shepherd"),
            FabricEntityTypeBuilder.<ShepherdEntity>create(MobCategory.CREATURE, ShepherdEntity::new)
                .dimensions(EntityDimensions.scalable(0.7f, 2.2f))
                .trackRangeChunks(10)
                .build()
        );
        FabricDefaultAttributeRegistry.register(SHEPHERD, ShepherdEntity.createAttributes());

        // wire the real Meadow dimension key now that it's registered
        // (see NOTES_FOR_NEXT_AI.md TODO #4) — spawner no-ops until this is set.
        RandomPortalSpawner.meadowDimension =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(MOD_ID, "the_meadow"));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                RandomPortalSpawner.trySpawnFor(player, random);
            }
        });
    }
}
