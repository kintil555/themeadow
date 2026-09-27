package com.themeadow.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Shepherd - the entity that "owns" The Meadow.
 * Behavior (per spec): sometimes stands still watching the player from a
 * distance, sometimes moves toward / chases the player, then vanishes
 * (teleports away + goes invisible briefly) instead of ever attacking.
 * No AI goal list is used for the watch/chase/vanish cycle - it is driven
 * entirely from {@link #customServerAiStep} so the exact behavior spec is
 * easy to tune in one place instead of being spread across Goal classes.
 */
public class ShepherdEntity extends PathfinderMob {

    private static final int STATE_IDLE = 0;
    private static final int STATE_WATCHING = 1;
    private static final int STATE_CHASING = 2;
    private static final int STATE_VANISHING = 3;

    private static final TargetingConditions WATCH_TARGETING =
            TargetingConditions.forNonCombat().range(40.0).ignoreLineOfSight();

    private int state = STATE_IDLE;
    private int stateTicks = 0;
    private int decisionCooldown = 0;

    public ShepherdEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.noCulling = true; // it should stay "rendered" even when partially out of frustum, per far-watch spec
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Override
    protected void registerGoals() {
        // Deliberately empty: movement + look behavior is fully custom-driven below,
        // so vanilla goals never fight with the watch/chase/vanish state machine.
    }

    @Override
    public void customServerAiStep(ServerLevel level) {
        Player nearest = level.getNearestPlayer(WATCH_TARGETING, this, this.getX(), this.getY(), this.getZ());
        stateTicks++;

        if (nearest == null) {
            this.state = STATE_IDLE;
            return;
        }

        double distSq = this.distanceToSqr(nearest);

        switch (this.state) {
            case STATE_IDLE -> {
                this.getNavigation().stop();
                if (decisionCooldown-- <= 0) {
                    this.state = distSq < 900.0 ? STATE_WATCHING : STATE_IDLE;
                    decisionCooldown = 20 + this.random.nextInt(40);
                }
            }
            case STATE_WATCHING -> {
                this.getNavigation().stop();
                this.getLookControl().setLookAt(nearest, 30.0f, 30.0f);
                if (stateTicks > 60 + this.random.nextInt(120)) {
                    // Either commit to a chase or lose interest and go idle again
                    this.state = this.random.nextFloat() < 0.55f ? STATE_CHASING : STATE_IDLE;
                    stateTicks = 0;
                }
            }
            case STATE_CHASING -> {
                this.getNavigation().moveTo(nearest, 1.15);
                this.getLookControl().setLookAt(nearest, 30.0f, 30.0f);
                boolean closeEnough = distSq < 6.0;
                boolean chasedTooLong = stateTicks > 100 + this.random.nextInt(80);
                if (closeEnough || chasedTooLong) {
                    this.state = STATE_VANISHING;
                    stateTicks = 0;
                }
            }
            case STATE_VANISHING -> {
                this.getNavigation().stop();
                if (stateTicks == 1) {
                    level.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                            this.getSoundSource(), 0.6f, 0.6f);
                }
                if (stateTicks > 12) {
                    teleportAwayFrom(level, nearest);
                    this.state = STATE_IDLE;
                    stateTicks = 0;
                    decisionCooldown = 60 + this.random.nextInt(100);
                }
            }
        }
    }

    /** Never actually hurts the player - the chase is a scare, not an attack. */
    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        return false;
    }

    /** Exposes current behavior state to the client renderer for fade/vanish visuals. */
    public boolean isVanishing() {
        return this.state == STATE_VANISHING;
    }

    public boolean isWatching() {
        return this.state == STATE_WATCHING;
    }

    private void teleportAwayFrom(ServerLevel level, LivingEntity player) {
        for (int i = 0; i < 12; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2;
            double dist = 18 + this.random.nextDouble() * 14;
            double x = player.getX() + Math.cos(angle) * dist;
            double z = player.getZ() + Math.sin(angle) * dist;
            BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(x, 0, z));
            if (level.getWorldBorder().isWithinBounds(top)) {
                this.teleportTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
                return;
            }
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
        // The Shepherd never gets shoved around by the player or other mobs.
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }
}
