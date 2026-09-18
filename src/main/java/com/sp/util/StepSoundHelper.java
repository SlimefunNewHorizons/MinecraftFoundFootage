package com.sp.util;

import com.sp.clientWrapper.ClientWrapper;
import com.sp.entity.ik.parts.sever_limbs.ServerLimb;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Helper to decouple ServerLimb step callbacks from direct ClientWrapper references on dedicated servers.
 */
public class StepSoundHelper {
    public static void skinWalkerPlayStepSound(ServerLimb limb) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientStepSound.playSkinWalker(limb);
        }
    }

    public static void walkerPlayStepSound(ServerLimb limb) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientStepSound.playWalker(limb);
        }
    }

    private static class ClientStepSound {
        private static void playSkinWalker(ServerLimb limb) {
            ClientWrapper.skinWalkerPlayStepSound(limb);
        }

        private static void playWalker(ServerLimb limb) {
            ClientWrapper.walkerPlayStepSound(limb);
        }
    }
}
