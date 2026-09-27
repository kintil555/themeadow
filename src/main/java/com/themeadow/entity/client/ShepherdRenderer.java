package com.themeadow.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.themeadow.TheMeadow;
import com.themeadow.entity.ShepherdEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renders the Shepherd as a flat 2D camera-facing billboard sprite (per
 * user's explicit spec: a transparent PNG of a cartoonish black-robed
 * figure with a crook, NOT a normal 3D block-model mob).
 *
 * Extends EntityRenderer<ShepherdEntity, ShepherdRenderState> directly
 * (NOT LivingEntityRenderer) since there's no real EntityModel here — see
 * NOTES_FOR_NEXT_AI.md for why. Uses the 26.2 submit-node architecture:
 * custom geometry via submitCustomGeometry + a hand-built quad, same
 * VertexConsumer chain as pre-26.x Minecraft (unchanged API).
 *
 * NOT tested in-game (no Gradle in sandbox). Texture uses entityCutout
 * (hard alpha cutout) per NOTES_FOR_NEXT_AI.md's reasoning; the source
 * PNG has some soft-edge fringing from background removal, so if the cutout
 * looks harsh/aliased in-game, try RenderTypes.entityTranslucent instead
 * (see NOTES_FOR_NEXT_AI.md "26.2 API facts" section).
 */
public class ShepherdRenderer extends EntityRenderer<ShepherdEntity, ShepherdRenderState> {
    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(TheMeadow.MOD_ID, "textures/entity/shepherd.png");

    // sprite half-size in world units; tune once the real PNG aspect/scale is checked in-game
    private static final float HALF_WIDTH = 0.6f;
    private static final float HALF_HEIGHT = 1.1f;

    public ShepherdRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ShepherdRenderState createRenderState() {
        return new ShepherdRenderState();
    }

    @Override
    public void extractRenderState(ShepherdEntity entity, ShepherdRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.vanishing = entity.isVanishing();
        state.watching = entity.isWatching();
        state.walkAnim = entity.walkAnimation.speed(partialTicks);
    }

    @Override
    public void submit(
        ShepherdRenderState state, PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector, CameraRenderState camera
    ) {
        if (state.vanishing) {
            // fade-out cue: skip drawing on alternating frames near the end of
            // the vanish state for a flicker effect. Crude placeholder — revisit
            // once actual alpha-blended fade timing data is exposed from the
            // entity (currently only a boolean flag).
            return;
        }

        // billboard: rotate the whole sprite plane to face the camera every
        // frame (yaw only — sprite stays upright, per user spec: rendered as
        // a flat 2D sprite, not a full spherical billboard).
        double dx = camera.pos.x - state.x;
        double dz = camera.pos.z - state.z;
        float yawToCamera = (float) (Mth.atan2(dx, dz) * (180.0 / Math.PI));

        poseStack.pushPose();
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yawToCamera));

        var renderType = RenderTypes.entityCutout(TEXTURE);
        submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) ->
            drawQuad(pose, vertexConsumer, state.lightCoords)
        );

        poseStack.popPose();
    }

    private void drawQuad(PoseStack.Pose pose, VertexConsumer vertexConsumer, int light) {
        float x0 = -HALF_WIDTH, x1 = HALF_WIDTH;
        float y0 = 0.0f, y1 = HALF_HEIGHT * 2.0f;
        float z = 0.0f;

        vertexConsumer.addVertex(pose, x0, y1, z).setUv(0.0f, 0.0f).setColor(255, 255, 255, 255)
            .setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pose, 0.0f, 0.0f, 1.0f);
        vertexConsumer.addVertex(pose, x0, y0, z).setUv(0.0f, 1.0f).setColor(255, 255, 255, 255)
            .setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pose, 0.0f, 0.0f, 1.0f);
        vertexConsumer.addVertex(pose, x1, y0, z).setUv(1.0f, 1.0f).setColor(255, 255, 255, 255)
            .setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pose, 0.0f, 0.0f, 1.0f);
        vertexConsumer.addVertex(pose, x1, y1, z).setUv(1.0f, 0.0f).setColor(255, 255, 255, 255)
            .setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pose, 0.0f, 0.0f, 1.0f);
    }
}
