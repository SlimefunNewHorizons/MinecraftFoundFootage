package com.sp.entity.client.model;

import com.sp.entity.custom.PaperPoliceEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

public class PaperPoliceModel<T extends PaperPoliceEntity> extends SinglePartEntityModel<T> {
    private final ModelPart paper;

    public PaperPoliceModel(ModelPart root) {
        super(RenderLayer::getEntityCutoutNoCull);
        this.paper = root.getChild("paper");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();
        root.addChild("paper", ModelPartBuilder.create()
                        .uv(0, 0).cuboid(-4.0F, -13.0F, 0.0F, 8.0F, 3.0F, 0.0F)
                        .uv(0, 3).cuboid(-3.5F, -10.0F, 0.0F, 7.0F, 7.0F, 0.0F)
                        .uv(0, 10).cuboid(-4.5F, -2.0F, 0.0F, 9.0F, 11.0F, 0.0F)
                        .uv(0, 21).cuboid(-6.5F, -2.0F, 0.0F, 2.0F, 8.0F, 0.0F)
                        .uv(4, 21).cuboid(4.5F, -2.0F, 0.0F, 2.0F, 8.0F, 0.0F)
                        .uv(0, 29).cuboid(-3.5F, 10.0F, 0.0F, 3.0F, 6.0F, 0.0F)
                        .uv(6, 29).cuboid(0.5F, 10.0F, 0.0F, 3.0F, 6.0F, 0.0F),
                ModelTransform.pivot(0.0F, 23.0F, 0.0F));

        return TexturedModelData.of(modelData, 64, 64);
    }

    @Override
    public void setAngles(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.paper.pitch = MathHelper.sin(ageInTicks * 0.05F) * 0.02F;
        this.paper.roll = MathHelper.cos(ageInTicks * 0.04F) * 0.015F;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha) {
        paper.render(matrices, vertexConsumer, light, overlay, red, green, blue, alpha);
    }

    @Override
    public ModelPart getPart() {
        return paper;
    }
}