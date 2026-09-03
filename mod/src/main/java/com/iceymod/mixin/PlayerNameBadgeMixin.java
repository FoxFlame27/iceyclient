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
                .styled(s -> iceymod$withBadgeFont(s));
            MutableText combined = Text.empty()
                .append(badge)
                .append(Text.literal(" "))
                .append(original);
            cir.setReturnValue(combined);
        } catch (Throwable ignored) {}
    }

    /**
     * {@code Style#withFont} took an {@link Identifier} up to 1.21.8; 1.21.11
     * changed the parameter to {@code net.minecraft.text.StyleSpriteSource}
     * (the font id is wrapped in a {@code StyleSpriteSource.Font} record).
     * Resolve whichever overload this runtime actually has.
     */
    private static net.minecraft.text.Style iceymod$withBadgeFont(net.minecraft.text.Style style) {
        try {
            for (java.lang.reflect.Method m : net.minecraft.text.Style.class.getMethods()) {
                if (!"withFont".equals(m.getName()) || m.getParameterCount() != 1) continue;
                Class<?> param = m.getParameterTypes()[0];
                if (param == Identifier.class) {
                    return (net.minecraft.text.Style) m.invoke(style, BADGE_FONT);
                }
                // 1.21.11+: wrap the identifier in the sprite-source record.
                Object wrapped = iceymod$wrapFontId(param);
                if (wrapped != null) {
                    return (net.minecraft.text.Style) m.invoke(style, wrapped);
                }
            }
        } catch (Throwable ignored) {}
        return style;
    }

    /** Builds a {@code StyleSpriteSource.Font}-like wrapper around BADGE_FONT. */
    private static Object iceymod$wrapFontId(Class<?> paramType) {
        try {
            Class<?> fontRecord = Class.forName(paramType.getName() + "$Font");
            java.lang.reflect.Constructor<?> ctor = fontRecord.getConstructor(Identifier.class);
            return ctor.newInstance(BADGE_FONT);
        } catch (Throwable ignored) {}
        // Fallback: any single-arg Identifier constructor on an implementor.
        try {
            for (Class<?> nested : paramType.getDeclaredClasses()) {
                try {
                    java.lang.reflect.Constructor<?> c = nested.getConstructor(Identifier.class);
                    return c.newInstance(BADGE_FONT);
                } catch (Throwable ignored2) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
