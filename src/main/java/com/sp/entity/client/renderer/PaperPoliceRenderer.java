package com.sp.entity.client.renderer;

import com.sp.SPBRevamped;
import com.sp.entity.client.model.PaperPoliceModel;
import com.sp.entity.custom.PaperPoliceEntity;
import com.sp.init.ModModelLayers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

public class PaperPoliceRenderer extends MobEntityRenderer<PaperPoliceEntity, PaperPoliceModel<PaperPoliceEntity>> {
    private static final Identifier TEXTURE = new Identifier(SPBRevamped.MOD_ID, "textures/entity/paper_police/paper_police.png");

    public PaperPoliceRenderer(EntityRendererFactory.Context context) {
        super(context, new PaperPoliceModel<>(context.getPart(ModModelLayers.PAPER_POLICE)), 0.3f);
    }

    @Override
    public void render(PaperPoliceEntity mobEntity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i) {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();

        matrixStack.translate(0, 1, 0);
        float angle = lookAtEntityAroundYAxis(mobEntity.getEyePos(), camera.getPos());
        matrixStack.multiply(new Quaternionf().rotateXYZ(0, (float) Math.toRadians(angle), 0));
        matrixStack.translate(0, -1, 0);

        super.render(mobEntity, f, g, matrixStack, vertexConsumerProvider, i);
    }

    @Override
    public Identifier getTexture(PaperPoliceEntity entity) {
        return TEXTURE;
    }

    public static float lookAtEntityAroundYAxis(Vec3d position1, Vec3d position2) {
        double d = position2.getX() - position1.getX();
        double e = position2.getZ() - position1.getZ();
        float h = -(float) (MathHelper.atan2(e, d) * 180.0F / (float) Math.PI) - 90.0F;

        return h - 180.0F;
    }
}