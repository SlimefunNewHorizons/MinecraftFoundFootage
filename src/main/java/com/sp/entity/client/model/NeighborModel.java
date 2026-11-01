package com.sp.entity.client.model;

import com.sp.entity.custom.NeighborEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/**
 * The Neighbor: about 2.6 blocks tall, thin limbs, arms that reach his knees, a long neck and a head that is always
 * a little tilted. He never breathes and never sways; the only thing that moves is his head, following you, and
 * his arm when he waves.
 */
public class NeighborModel extends EntityModel<NeighborEntity> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart neck;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public NeighborModel(ModelPart root) {
        super(RenderLayer::getEntityCutoutNoCull);
        this.root = root;
        this.head = root.getChild("head");
        this.neck = root.getChild("neck");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        root.addChild("head", ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -9.0F, -4.0F, 8.0F, 9.0F, 8.0F),
                ModelTransform.pivot(0.0F, -9.0F, 0.0F));
        root.addChild("neck", ModelPartBuilder.create().uv(56, 0).cuboid(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                ModelTransform.pivot(0.0F, -6.0F, 0.0F));
        root.addChild("body", ModelPartBuilder.create().uv(32, 0).cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                ModelTransform.pivot(0.0F, -6.0F, 0.0F));
        // Arms end at the wrist: the hands below make the fingertips reach his knees.
        ModelPartData rightArm = root.addChild("right_arm", ModelPartBuilder.create().uv(0, 18).cuboid(-1.5F, 0.0F, -1.5F, 3.0F, 14.0F, 3.0F),
                ModelTransform.pivot(-5.5F, -5.0F, 0.0F));
        addHand(rightArm, 1.0F);
        ModelPartData leftArm = root.addChild("left_arm", ModelPartBuilder.create().uv(12, 18).cuboid(-1.5F, 0.0F, -1.5F, 3.0F, 14.0F, 3.0F),
                ModelTransform.pivot(5.5F, -5.0F, 0.0F));
        addHand(leftArm, -1.0F);
        root.addChild("right_leg", ModelPartBuilder.create().uv(24, 18).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 18.0F, 4.0F),
                ModelTransform.pivot(-2.1F, 6.0F, 0.0F));
        root.addChild("left_leg", ModelPartBuilder.create().uv(40, 18).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 18.0F, 4.0F),
                ModelTransform.pivot(2.1F, 6.0F, 0.0F));

        return TexturedModelData.of(modelData, 64, 64);
    }

    /**
     * A flat palm turned towards the leg, four fingers of different lengths curling a little towards it, and a thumb
     * on the front edge. inward is +1 for the right arm (the body is towards +x) and -1 for the left one.
     */
    private static void addHand(ModelPartData arm, float inward) {
        ModelPartData hand = arm.addChild("hand", ModelPartBuilder.create().uv(0, 42).cuboid(-1.0F, 0.0F, -1.75F, 2.0F, 3.0F, 3.5F),
                ModelTransform.pivot(0.0F, 14.0F, 0.0F));

        // Index (front, -z) to pinky (back): 0.8 wide with 0.1 gaps fills the 3.5 wide palm.
        float[] lengths = {2.9F, 3.3F, 3.1F, 2.4F};
        float[] curl = {0.12F, 0.1F, 0.14F, 0.22F};
        for (int i = 0; i < lengths.length; i++) {
            float z = -1.75F + i * 0.9F;
            hand.addChild("finger" + i, ModelPartBuilder.create().uv(24, 42).cuboid(-0.45F, 0.0F, 0.0F, 0.9F, lengths[i], 0.8F),
                    ModelTransform.of(0.1F * inward, 3.0F, z, 0.0F, 0.0F, -curl[i] * inward));
        }

        hand.addChild("thumb", ModelPartBuilder.create().uv(24, 42).cuboid(-0.45F, 0.0F, -0.4F, 0.9F, 2.2F, 0.8F),
                ModelTransform.of(0.3F * inward, 0.8F, -1.75F, -0.45F, 0.0F, -0.25F * inward));
    }

    @Override
    public void setAngles(NeighborEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        this.head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
        this.head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE;
        this.head.roll = entity.getHeadTilt() * MathHelper.RADIANS_PER_DEGREE;

        // Arms hang perfectly still, a little away from the body.
        this.rightArm.pitch = 0.0F;
        this.rightArm.roll = 0.04F;
        this.leftArm.pitch = 0.0F;
        this.leftArm.roll = -0.04F;
        this.rightLeg.pitch = 0.0F;
        this.leftLeg.pitch = 0.0F;

        if (entity.isWaving()) {
            // A slow, friendly wave. Too slow.
            this.rightArm.pitch = -2.75F;
            this.rightArm.roll = -0.25F + MathHelper.sin(animationProgress * 0.22F) * 0.4F;
            this.head.roll *= 1.6F;
        }
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        this.root.render(matrices, vertices, light, overlay, red, green, blue, alpha);
    }
}
