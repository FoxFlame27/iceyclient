package com.iceysmp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Max-level rewards — one themed weapon/armor piece per category. All
 * given via /give command rather than registered custom Items because
 * the Item.Settings + Registry signatures drift across the 1.21.x yarn
 * matrix. Plain /give with components is the most version-stable path.
 *
 * <p>Each reward is a vanilla item with:
 *   - a custom name (aqua + bold for swords, gold for armor, etc.)
 *   - 3-line lore (theme, mechanic hint, "Max-level reward — <category>")
 *   - thematic enchantments
 *   - minecraft:rarity = epic (the purple name glow)
 *
 * <p>Awarded once per (player, category) — tracked in
 * {@link PlayerStats#frostfangAwardedFor} (semicolon list).
 */
public final class WeaponDrops {

    private WeaponDrops() {}

    /** Public entry — used by LeaderboardManager (auto on max) and by
     *  /icey reward (admin manual handout). */
    public static boolean giveReward(ServerPlayerEntity player, String categoryId, String categoryLabel) {
        Reward r = forCategory(categoryId);
        if (r == null) return false;
        return run(player, r, categoryLabel);
    }

    /** Back-compat — old code path called giveFrostfang. */
    public static boolean giveFrostfang(ServerPlayerEntity player, String reasonLabel) {
        return run(player, forCategory("pvp"), reasonLabel);
    }

    // ── Reward catalog ─────────────────────────────────────────────────

    private record Reward(String item, String name, String nameColor, String[] lore, String enchants) {}

    private static Reward forCategory(String categoryId) {
        return switch (categoryId) {
            case "mining" -> new Reward(
                    "minecraft:netherite_pickaxe",
                    "Frostpick",
                    "aqua",
                    new String[] {
                            "A pick that splits stone like ice.",
                            "Bonus drops · Never wears down"
                    },
                    "{\"minecraft:efficiency\":5,\"minecraft:fortune\":3,\"minecraft:unbreaking\":3,\"minecraft:mending\":1}"
            );
            case "pvp" -> new Reward(
                    "minecraft:diamond_sword",
                    "Frostfang",
                    "aqua",
                    new String[] {
                            "A blade forged in the cold north.",
                            "Slows on hit · Bonus reach"
                    },
                    "{\"minecraft:sharpness\":5,\"minecraft:knockback\":2,\"minecraft:fire_aspect\":2,\"minecraft:unbreaking\":3}"
            );
            case "playtime" -> new Reward(
                    "minecraft:netherite_helmet",
                    "Crown of Hours",
                    "gold",
                    new String[] {
                            "A crown earned through patience.",
                            "Protection · Underwater senses"
                    },
                    "{\"minecraft:protection\":4,\"minecraft:respiration\":3,\"minecraft:aqua_affinity\":1,\"minecraft:unbreaking\":3,\"minecraft:mending\":1}"
            );
            case "fishing" -> new Reward(
                    "minecraft:fishing_rod",
                    "Tidecaller",
                    "aqua",
                    new String[] {
                            "Whispers to the deep.",
                            "Better loot · Faster bites"
                    },
                    "{\"minecraft:luck_of_the_sea\":3,\"minecraft:lure\":3,\"minecraft:unbreaking\":3,\"minecraft:mending\":1}"
            );
            case "walking" -> new Reward(
                    "minecraft:netherite_boots",
                    "Wanderer's Treads",
                    "green",
                    new String[] {
                            "Boots worn smooth by a thousand roads.",
                            "Soul speed · Water-skimming"
                    },
                    "{\"minecraft:soul_speed\":3,\"minecraft:depth_strider\":3,\"minecraft:feather_falling\":4,\"minecraft:unbreaking\":3,\"minecraft:mending\":1}"
            );
            case "jumps" -> new Reward(
                    "minecraft:netherite_leggings",
                    "Springheel Greaves",
                    "yellow",
                    new String[] {
                            "Light as air, hard as stone.",
                            "Soften falls · Resist damage"
                    },
                    "{\"minecraft:protection\":4,\"minecraft:swift_sneak\":3,\"minecraft:unbreaking\":3,\"minecraft:mending\":1}"
            );
            case "dmgtaken" -> new Reward(
                    "minecraft:netherite_chestplate",
                    "Stonewall",
                    "dark_red",
                    new String[] {
                            "Soaks blows the way stone soaks rain.",
                            "Top-tier protection · Reflects damage"
                    },
                    "{\"minecraft:protection\":4,\"minecraft:thorns\":3,\"minecraft:unbreaking\":3,\"minecraft:mending\":1}"
            );
            default -> null;
        };
    }

    private static boolean run(ServerPlayerEntity player, Reward r, String reasonLabel) {
        if (player == null || r == null) return false;
        MinecraftServer server = IceySmp.server;
        if (server == null) return false;
        try {
            StringBuilder lore = new StringBuilder();
            for (int i = 0; i < r.lore.length; i++) {
                if (i > 0) lore.append(",");
                lore.append("'{\"text\":\"").append(escapeJson(r.lore[i]))
                        .append("\",\"italic\":false,\"color\":\"")
                        .append(i == 0 ? "gray" : "dark_aqua").append("\"}'");
            }
            lore.append(",'{\"text\":\"Max-level reward — ").append(escapeJson(reasonLabel))
                    .append("\",\"italic\":false,\"color\":\"dark_gray\"}'");

            String cmd = "give " + player.getName().getString() + " " + r.item + "["
                    + "minecraft:custom_name='{\"text\":\"" + escapeJson(r.name)
                    + "\",\"italic\":false,\"color\":\"" + r.nameColor + "\",\"bold\":true}',"
                    + "minecraft:lore=[" + lore + "],"
                    + "minecraft:enchantments={levels:" + r.enchants + "},"
                    + "minecraft:rarity=\"epic\""
                    + "] 1";
            if (!VersionShim.executeServerCommand(server, cmd)) {
                System.out.println("[IceySMP] no server-command executor found — couldn't /give reward");
                return false;
            }

            // Big banner.
            try {
                player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(10, 60, 20));
                player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                        Text.literal("§" + colorCode(r.nameColor) + "§l" + r.name.toUpperCase())));
                player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                        Text.literal("§7Max-level reward · §f" + reasonLabel)));
            } catch (Throwable ignored) {}

            try {
                server.getPlayerManager().broadcast(
                        Text.literal("§b§l[Icey SMP] §a§l" + player.getName().getString()
                                + " §r§7earned a §" + colorCode(r.nameColor) + "§l" + r.name
                                + " §r§7for maxing §b" + reasonLabel + "§7!"),
                        false);
            } catch (Throwable ignored) {}
            return true;
        } catch (Throwable t) {
            System.out.println("[IceySMP] Reward give failed: " + t);
            return false;
        }
    }

    /** Map a JSON-named color to a Minecraft formatting code char. */
    private static String colorCode(String c) {
        return switch (c) {
            case "aqua" -> "b";
            case "gold" -> "6";
            case "green" -> "a";
            case "yellow" -> "e";
            case "dark_red" -> "4";
            case "red" -> "c";
            case "blue" -> "9";
            case "light_purple" -> "d";
            default -> "f";
        };
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
