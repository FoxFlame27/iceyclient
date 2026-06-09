package com.iceymod.mixin;

import com.iceymod.network.IceyNetwork;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Prepend an Icey Client badge character to every confirmed Icey
 * user's display name. The character lives in our custom font
 * ({@code iceymod:icons}) which maps {@code } to the badge
 * PNG.
 *
 * <p>Why this approach instead of a 3D quad mixin or HUD-render
 * projection: vanilla's text renderer handles font dispatch for us,
 * so the badge appears in EVERY context that draws the player's
 * name — nameplate above head, TAB player list, chat, scoreboard,
 * sign text — with zero extra code per surface.
 *
 * <p>The badge is client-side only. Non-Icey-Client players don't
 * have our mixin or font, so they see the player's name unchanged.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerNameBadgeMixin {

    private static final String BADGE_CHAR = "";
    private static final Identifier BADGE_FONT = Identifier.of("iceymod", "icons");

    @Inject(
        method = {"getDisplayName", "method_5476"},
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void iceymod$addBadge(CallbackInfoReturnable<Text> cir) {
        try {
            PlayerEntity self = (PlayerEntity) (Object) this;
            UUID uuid = self.getUuid();
            if (uuid == null) return;
            if (!IceyNetwork.isOnline(uuid)) return;

            Text original = cir.getReturnValue();
            if (original == null) return;

            // Badge character in the custom font, then a space, then
            // the original display name. The badge keeps its own
            // styling (the font scope) while the rest of the name
            // inherits whatever the server set.
            MutableText badge = Text.literal(BADGE_CHAR)
                .styled(s -> s.withFont(BADGE_FONT));
            MutableText combined = Text.empty()
                .append(badge)
                .append(Text.literal(" "))
                .append(original);
            cir.setReturnValue(combined);
        } catch (Throwable ignored) {}
    }
}
