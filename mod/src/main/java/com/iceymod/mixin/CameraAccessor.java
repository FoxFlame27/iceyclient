package com.iceymod.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Camera position / rotation without depending on accessor names that
 * differ per version (getPosition() vanished in 1.21.11, 26.x has
 * position()). The fields are named the same on every version we ship for.
 */
@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("position") Vec3 iceymod$position();
    @Accessor("yRot") float iceymod$yRot();
    @Accessor("xRot") float iceymod$xRot();
}
