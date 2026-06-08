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
                if (e == null || e.getProfile() == null) continue;
                UUID uu = iceymod$profileUuid(e.getProfile());
                if (uu != null) playerUuids.add(uu);
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
            UUID uuid = iceymod$profileUuid(entry.getProfile());
            if (uuid == null) return;
            if (!IceyNetwork.isOnline(uuid)) return;

            // 1.21.11 changed DrawContext.drawTexture's signature to
            // require a RenderPipeline as first arg. The package layout
            // for RenderPipeline / RenderPipelines varies across point
            // releases. Use reflection to find a compatible overload +
            // a sensible default pipeline constant, all at runtime —
            // safer than pinning compile-time imports.
            iceymod$drawTextureReflective(ctx, ICEY_BADGE, x - 10, y, 8, 8);
        } catch (Throwable ignored) {}
    }

    private static java.lang.reflect.Method iceymod$cachedDrawTexture;
    private static Object iceymod$cachedPipeline;
    private static boolean iceymod$drawLookupTried;

    /** Find DrawContext.drawTexture(RenderPipeline, Identifier, ...) + a pipeline, cache, invoke. */
    private static void iceymod$drawTextureReflective(DrawContext ctx, Identifier tex, int x, int y, int w, int h) {
        if (!iceymod$drawLookupTried) {
            iceymod$drawLookupTried = true;
            try {
                for (java.lang.reflect.Method m : DrawContext.class.getMethods()) {
                    if (!m.getName().equals("drawTexture")) continue;
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length != 10) continue;
                    if (p[1] != Identifier.class) continue;
                    if (p[2] != int.class || p[3] != int.class) continue;
                    if (p[4] != float.class || p[5] != float.class) continue;
                    iceymod$cachedDrawTexture = m;
                    iceymod$cachedPipeline = iceymod$resolvePipeline(p[0]);
                    break;
                }
            } catch (Throwable ignored) {}
        }
        if (iceymod$cachedDrawTexture == null || iceymod$cachedPipeline == null) return;
        try {
            iceymod$cachedDrawTexture.invoke(ctx, iceymod$cachedPipeline, tex, x, y, 0f, 0f, w, h, w, h);
        } catch (Throwable ignored) {}
    }

    /**
     * Pull the UUID off a GameProfile-shaped object without pinning a
     * compile-time accessor name. Authlib has cycled through
     * {@code getId()} / {@code id()} / {@code getProfileId()} /
     * {@code uuid()} across recent versions, and Loom remaps based on
     * whatever Authlib version the dev environment shipped — so the
     * compile-time name is whatever-yarn-says-this-week.
     *
     * <p>We just try the half-dozen common names by reflection. Misses
     * cost a {@code NoSuchMethodException} once per call site; hits
     * are cached implicitly by the JIT.
     */
    private static UUID iceymod$profileUuid(Object profile) {
        if (profile == null) return null;
        Class<?> c = profile.getClass();
        String[] names = { "id", "getId", "getProfileId", "uuid", "getUuid" };
        for (String n : names) {
            try {
                java.lang.reflect.Method m = c.getMethod(n);
                if (m.getReturnType() == UUID.class) {
                    Object v = m.invoke(profile);
                    if (v instanceof UUID u) return u;
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        // Field fallback — record-style classes may expose the field
        // even if the accessor name keeps moving.
        try {
            for (java.lang.reflect.Field f : c.getFields()) {
                if (f.getType() == UUID.class) {
                    Object v = f.get(profile);
                    if (v instanceof UUID u) return u;
                }
            }
        } catch (ReflectiveOperationException ignored) {}
        return null;
    }

    private static Object iceymod$resolvePipeline(Class<?> pipelineType) {
        String pkg = pipelineType.getPackage() != null ? pipelineType.getPackage().getName() : "";
        String[] holders = {
            pkg + ".RenderPipelines",
            "net.minecraft.client.gl.RenderPipelines",
            "net.minecraft.client.render.RenderPipelines",
            "com.mojang.blaze3d.pipeline.RenderPipelines"
        };
        String[] fields = { "GUI_TEXTURED", "GUI", "MAIN_TARGET", "POSITION_TEX" };
        for (String h : holders) {
            try {
                Class<?> c = Class.forName(h);
                for (String f : fields) {
                    try {
                        java.lang.reflect.Field fld = c.getField(f);
                        Object v = fld.get(null);
                        if (v != null) return v;
                    } catch (ReflectiveOperationException ignored) {}
                }
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }
}
