package com.iceysmp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Frostfang — the max-level reward weapon. Implemented as a /give command
 * with custom_name + lore + enchantments + custom_model_data, rather than a
 * registered custom Item. Reasons:
 *
 *   1. Item registration API (Registry.register, Item.Settings, SwordItem
 *      constructor signatures) has drifted noticeably across the 1.21.x
 *      yarn matrix — a real Item subclass would need a VersionShim per
 *      every method.
 *   2. /give is a stable vanilla command. Component syntax (the [...]
 *      after the item id) is stable across 1.21+.
 *   3. The texture + model files in assets/iceymodplus/ are still
 *      bundled; a future revision can wire them up via the diamond_sword
 *      model overrides without churning this hot-path code.
 *
 * Net: players see a vanilla diamond-sword model with a Frostfang name +
 * lore + sharpness 5 + knockback 2 + fire aspect 2.
 */
public final class WeaponDrops {

    private WeaponDrops() {}

    public static boolean giveFrostfang(ServerPlayerEntity player, String reasonLabel) {
        if (player == null) return false;
        MinecraftServer server = IceySmp.server;
        if (server == null) return false;
        try {
            // 1.21+ component syntax. Lore is a list of JSON-encoded Text components.
            String cmd = "give " + player.getName().getString()
                    + " minecraft:diamond_sword["
                    + "minecraft:custom_name='{\"text\":\"Frostfang\",\"italic\":false,\"color\":\"aqua\",\"bold\":true}',"
                    + "minecraft:lore=["
                    + "'{\"text\":\"A blade forged in the cold north.\",\"italic\":false,\"color\":\"gray\"}',"
                    + "'{\"text\":\"Slows on hit · Bonus reach\",\"italic\":false,\"color\":\"dark_aqua\"}',"
                    + "'{\"text\":\"Max-level reward — " + reasonLabel + "\",\"italic\":false,\"color\":\"dark_gray\"}'"
                    + "],"
                    + "minecraft:enchantments={levels:{\"minecraft:sharpness\":5,\"minecraft:knockback\":2,\"minecraft:fire_aspect\":2,\"minecraft:unbreaking\":3}},"
                    + "minecraft:rarity=\"epic\""
                    + "] 1";
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), cmd);

            // Big banner so they know what just happened.
            try {
                player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(10, 60, 20));
                player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                        Text.literal("§b§lFROSTFANG")));
                player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                        Text.literal("§7Max-level reward · §f" + reasonLabel)));
            } catch (Throwable ignored) {}

            // Server-wide announcement so the whole SMP sees the drop.
            try {
                server.getPlayerManager().broadcast(
                        Text.literal("§b§l[Icey SMP] §a§l" + player.getName().getString()
                                + " §r§7earned a §b§lFrostfang §r§7for maxing §b" + reasonLabel + "§7!"),
                        false);
            } catch (Throwable ignored) {}
            return true;
        } catch (Throwable t) {
            System.out.println("[IceySMP] Frostfang give failed: " + t);
            return false;
        }
    }
}
