package com.sp.mixin.rain;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sp.init.BackroomsLevels;
import net.minecraft.entity.LightningEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LightningEntity.class)
public class LightningNoFireMixin {
    @WrapMethod(method = "spawnFire")
    public void spbrevamped$LightingUpSkyDuringLightningSpawnFire(int spreadAttempts, Operation<Void> original) {
        LightningEntity thiz = ((LightningEntity) (Object) this);

        if (BackroomsLevels.isInBackroomsLevel(thiz.getWorld(), BackroomsLevels.LEVEL324_BACKROOMS_LEVEL)) {
            return;
        }

        original.call(spreadAttempts);
    }
}
