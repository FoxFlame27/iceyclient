package com.iceymod.mixin;

import com.iceymod.network.IceyNetwork;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Whenever the player-list HUD renders, warm the Icey-network
 * presence cache for every player on the server. The actual badge
 * draw rides on top of every Icey-Client-using player's name via the
 * {@link DrawContext#drawTexture} call piggy-backed at the end of
 * render — see {@code drawBadge}.
 *
 * <p>This mixin is intentionally loose ({@code require = 0}, method
 * name pinned by overload) so it survives yarn renames across 1.21.x
 * point releases. If the targeted method doesn't exist, the badge is
 * a silent no-op rather than a load-time failure.
 */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    private static final Identifier ICEY_BADGE = Identifier.of("iceymod", "icon.png");
    private static boolean iceymod$warmedThisOpen = false;

    /**
     * Fires every TAB render frame. We:
     *   1) batch the UUIDs of every player on the server,
     *   2) ask {@link IceyNetwork} to warm those presence entries
     *      so subsequent renders read cached results,
     *   3) draw an Icey badge before the name of every confirmed
     *      Icey Client user.
     *
     * The actual draw injects at HEAD so the badge sits behind the
     * name text (vanilla draws the name after our injection).
     */
    @Inject(
        method = {"render", "method_1750"},
        at = @At("HEAD"),
        require = 0
    )
    private void iceymod$onRender(DrawContext ctx, int scaledWindowWidth, net.minecraft.scoreboard.Scoreboard scoreboard, net.minecraft.scoreboard.ScoreboardObjective objective, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.world == null) return;

            // Warm presence cache once per render burst — we don't
            // want to flood the worker with a request per frame.
            Set<UUID> playerUuids = new HashSet<>();
            for (PlayerListEntry e : mc.player.networkHandler.getPlayerList()) {
                if (e != null && e.getProfile() != null && e.getProfile().getId() != null) {
                    playerUuids.add(e.getProfile().getId());
                }
            }
            if (!iceymod$warmedThisOpen) {
                IceyNetwork.warmPresence(playerUuids);
                iceymod$warmedThisOpen = true;
                // Reset flag a tick later so we re-warm if TAB stays
                // open more than a few seconds. Cheap heuristic:
                // schedule reset on the next mc tick.
                final MinecraftClient mcRef = mc;
                Thread.ofVirtual().start(() -> {
                    try { Thread.sleep(30_000); } catch (InterruptedException _ignored) {}
                    iceymod$warmedThisOpen = false;
                });
            }
        } catch (Throwable ignored) {}
    }

    /**
     * After every name-cell render, draw the Icey badge in the slot
     * to the LEFT of the name if that player is using Icey Client.
     *
     * <p>The injection point uses a partial method-name match against
     * what {@code renderEntry} / {@code method_2380} typically look
     * like on 1.21.x. Multiple candidates so we survive yarn renames.
     */
    @Inject(
        method = {"renderLatencyIcon", "method_1759", "method_1735"},
        at = @At("HEAD"),
        require = 0
    )
    private void iceymod$drawBadge(DrawContext ctx, int width, int x, int y, PlayerListEntry entry, CallbackInfo ci) {
        try {
            if (entry == null || entry.getProfile() == null) return;
            UUID uuid = entry.getProfile().getId();
            if (uuid == null) return;
            if (!IceyNetwork.isOnline(uuid)) return;

            // Draw the 8x8 badge to the LEFT of the ping/name cell.
            ctx.drawTexture(ICEY_BADGE, x - 10, y, 0, 0, 8, 8, 8, 8);
        } catch (Throwable ignored) {}
    }
}
