package com.sp.entity.client.renderer;

import com.sp.SPBRevamped;
import com.sp.entity.client.model.NeighborModel;
import com.sp.entity.custom.NeighborEntity;
import com.sp.init.ModModelLayers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class NeighborRenderer extends MobEntityRenderer<NeighborEntity, NeighborModel> {
    private static final Identifier TEXTURE = new Identifier(SPBRevamped.MOD_ID, "textures/entity/neighbor/neighbor.png");
    private static final RenderLayer EYES = RenderLayer.getEyes(new Identifier(SPBRevamped.MOD_ID, "textures/entity/neighbor/neighbor_eyes.png"));

    public NeighborRenderer(EntityRendererFactory.Context context) {
        super(context, new NeighborModel(context.getPart(ModModelLayers.NEIGHBOR)), 0.35F);
        this.addFeature(new NeighborEyes(this));
    }

    @Override
    public void render(NeighborEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        // His whole body always faces the camera, not just his head.
        Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        float towardsCamera = (float) (MathHelper.atan2(camera.z - entity.getZ(), camera.x - entity.getX()) * (180F / Math.PI)) - 90.0F;
        entity.bodyYaw = towardsCamera;
        entity.prevBodyYaw = towardsCamera;
        entity.headYaw = towardsCamera;
        entity.prevHeadYaw = towardsCamera;

        super.render(entity, towardsCamera, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(NeighborEntity entity) {
        return TEXTURE;
    }

    /**
     * Two pinpoints in his eyes that catch the light even where there is none.
     */
    private static final class NeighborEyes extends EyesFeatureRenderer<NeighborEntity, NeighborModel> {
        NeighborEyes(FeatureRendererContext<NeighborEntity, NeighborModel> context) {
            super(context);
        }

        @Override
        public RenderLayer getEyesTexture() {
            return EYES;
        }
    }
}
