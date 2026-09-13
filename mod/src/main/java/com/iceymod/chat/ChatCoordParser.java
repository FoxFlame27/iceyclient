package com.iceymod.chat;

import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.compat.ClientCmd;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects coordinate triples in chat messages and rewrites them as
 * clickable links that drop a waypoint at those coords. Click handling
 * goes through a client-side {@code /iceywp <x> <y> <z>} command so we
 * don't have to inject input handlers into ChatHud.
 *
 * Pattern matches {@code (123, 64, -567)}, {@code 123 64 -567},
 * {@code 123/64/-567}, etc. — three integers separated by any combo of
 * commas, slashes, or whitespace, optionally bracketed.
 */
public final class ChatCoordParser {

    // Matches three signed integers separated by commas/spaces/slashes,
    // with optional surrounding parens/brackets. Caps each integer at
    // 8 digits so we don't capture arbitrary number runs (player kill
    // counts, ping ms, etc.) — Y is in [-64, 320] so 4 digits is plenty.
    private static final Pattern COORD = Pattern.compile(
            "[\\(\\[]?\\s*(-?\\d{1,8})[,\\s/]+(-?\\d{1,4})[,\\s/]+(-?\\d{1,8})\\s*[\\)\\]]?"
    );

    private ChatCoordParser() {}

    public static void register() {
        // System / server messages (most coord shares: /tell, /msg, server
        // formatting). Fabric does NOT expose a MODIFY_CHAT for signed
        // player messages — those can't be rewritten client-side without
        // breaking the signature chain, so they pass through unchanged.
        try {
            ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) ->
                    overlay ? message : rewrite(message));
        } catch (Throwable t) {
            System.out.println("[IceyMod] ClientReceiveMessageEvents.MODIFY_GAME unavailable: " + t.getMessage());
        }

        // Register the click-target command. /iceywp <x> <y> <z> [name]
        try {
            ClientCmd.onRegister(dispatcher ->
                dispatcher.register(ClientCmd.literal("iceywp")
                        .then(ClientCmd.argument("x", IntegerArgumentType.integer())
                            .then(ClientCmd.argument("y", IntegerArgumentType.integer())
                                .then(ClientCmd.argument("z", IntegerArgumentType.integer())
                                    .executes(ctx -> addWaypoint(
                                            IntegerArgumentType.getInteger(ctx, "x"),
                                            IntegerArgumentType.getInteger(ctx, "y"),
                                            IntegerArgumentType.getInteger(ctx, "z"),
                                            "Chat"))
                                    .then(ClientCmd.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> addWaypoint(
                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                IntegerArgumentType.getInteger(ctx, "z"),
                                                StringArgumentType.getString(ctx, "name")))))))));
        } catch (Throwable t) {
            System.out.println("[IceyMod] Client command registration failed: " + t.getMessage());
        }
    }

    private static int addWaypoint(int x, int y, int z, String name) {
        WaypointManager.addWaypoint(name, x, y, z);
        Minecraft c = Minecraft.getInstance();
        if (c != null && c.player != null) {
            com.iceymod.compat.Chat.message(Component.literal(
                    "§b[IceyClient] §aWaypoint added: §f" + name + " §8(" + x + ", " + y + ", " + z + ")"), false);
        }
        return 1;
    }

    /**
     * Walk a Text and rebuild it with coord-substring matches replaced
     * by a clickable, hover-tooltipped span. Preserves original style.
     */
    public static Component rewrite(Component in) {
        if (in == null) return null;
        try {
            String raw = in.getString();
            Matcher m = COORD.matcher(raw);
            if (!m.find()) return in;
            m.reset();

            MutableComponent out = Component.empty();
            int last = 0;
            while (m.find()) {
                int x;
                int y;
                int z;
                try {
                    x = Integer.parseInt(m.group(1));
                    y = Integer.parseInt(m.group(2));
                    z = Integer.parseInt(m.group(3));
                } catch (NumberFormatException e) {
                    continue;
                }
                // Filter Y to plausible range so kill ratios "5/0/8" don't
                // accidentally match.
                if (y < -64 || y > 320) continue;

                if (m.start() > last) {
                    out.append(Component.literal(raw.substring(last, m.start())));
                }
                String seen = raw.substring(m.start(), m.end());
                Style clickStyle = Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("/iceywp " + x + " " + y + " " + z))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.literal("§b[IceyClient] §7Click to waypoint §f"
                                        + x + ", " + y + ", " + z)));
                out.append(Component.literal(seen).setStyle(clickStyle));
                last = m.end();
            }
            if (last < raw.length()) {
                out.append(Component.literal(raw.substring(last)));
            }
            return out;
        } catch (Throwable t) {
            return in;
        }
    }
}
