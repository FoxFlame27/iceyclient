package com.iceymod.mixin;

import com.iceymod.network.IceyNetwork;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x: warm the Icey presence cache while TAB is open and badge Icey Client users. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudMixin {

    private static final Identifier ICEY_BADGE = Identifier.fromNamespaceAndPath("iceymod", "icon.png");
    private static boolean iceymod$warmedThisOpen = false;

    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
    private void iceymod$onRender(GuiGraphicsExtractor ctx, int scaledWindowWidth, net.minecraft.world.scores.Scoreboard scoreboard, net.minecraft.world.scores.Objective objective, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null) return;
            Set<UUID> playerUuids = new HashSet<>();
            for (PlayerInfo e : mc.player.connection.getOnlinePlayers()) {
                if (e == null || e.getProfile() == null) continue;
                UUID uu = iceymod$profileUuid(e.getProfile());
                if (uu != null) playerUuids.add(uu);
            }
            if (!iceymod$warmedThisOpen) {
                IceyNetwork.warmPresence(playerUuids);
                iceymod$warmedThisOpen = true;
                Thread.ofVirtual().start(() -> {
                    try { Thread.sleep(30_000); } catch (InterruptedException ignored) {}
                    iceymod$warmedThisOpen = false;
                });
            }
        } catch (Throwable ignored) {}
    }

    @Inject(method = "extractPingIcon", at = @At("HEAD"), require = 0)
    private void iceymod$drawBadge(GuiGraphicsExtractor ctx, int width, int x, int y, PlayerInfo entry, CallbackInfo ci) {
        try {
            if (entry == null || entry.getProfile() == null) return;
            UUID uuid = iceymod$profileUuid(entry.getProfile());
            if (uuid == null || !IceyNetwork.isOnline(uuid)) return;
            ctx.blit(RenderPipelines.GUI_TEXTURED, ICEY_BADGE, x - 10, y, 0f, 0f, 8, 8, 8, 8);
        } catch (Throwable ignored) {}
    }

    /** GameProfile's UUID accessor has been renamed across authlib versions; try the usual names. */
    private static UUID iceymod$profileUuid(Object profile) {
        if (profile == null) return null;
        Class<?> c = profile.getClass();
        for (String n : new String[] { "id", "getId", "getProfileId", "uuid", "getUuid" }) {
            try {
                java.lang.reflect.Method m = c.getMethod(n);
                if (m.getReturnType() == UUID.class) {
                    Object v = m.invoke(profile);
                    if (v instanceof UUID u) return u;
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }
}
