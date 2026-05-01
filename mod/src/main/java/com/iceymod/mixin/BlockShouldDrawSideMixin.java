package com.iceymod.mixin;

import com.iceymod.hud.modules.XrayModule;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * X-Ray hook. {@code Block.shouldDrawSide(state, view, pos, dir, otherPos)}
 * decides whether a given face of a block should be added to the chunk
 * render mesh. We intercept it: when X-ray is on, a face is drawn iff its
 * own block is in the user's see-through set. Non-target blocks therefore
 * end up with no faces in the mesh — invisible.
 *
 * <p>Required = false so the mod doesn't fail to load on a future MC
 * version where this method's signature shifts; X-ray just stops working
 * until we update the mixin.
 */
@Mixin(value = Block.class, priority = 1100)
public abstract class BlockShouldDrawSideMixin {

    @Inject(
        method = "shouldDrawSide(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;Lnet/minecraft/util/math/BlockPos;)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void iceymod$xray(BlockState state, BlockView view, BlockPos pos, Direction direction, BlockPos otherPos, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!XrayModule.shouldShow(state.getBlock())) {
                cir.setReturnValue(false);
            }
            // For target blocks we let vanilla decide — this avoids drawing
            // internal faces between two adjacent target blocks (e.g. two
            // diamond ores against each other) and keeps the mesh clean.
        } catch (Throwable ignored) {
            // Never break chunk rendering — silent fallthrough to vanilla.
        }
    }
}
