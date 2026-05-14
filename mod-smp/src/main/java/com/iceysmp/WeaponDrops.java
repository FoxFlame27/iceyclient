package com.iceysmp;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

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
            case "water" -> new Reward(
                    "minecraft:trident",
                    "Wavebreaker",
                    "blue",
                    new String[] {
                            "A trident chiseled from the deep.",
                            "Returns to hand · Calls the storm"
                    },
                    // Riptide intentionally omitted — it conflicts with
                    // loyalty/channeling and we want the throw-and-return
                    // combat trident.
                    "{\"minecraft:loyalty\":3,\"minecraft:impaling\":5,\"minecraft:channeling\":1,\"minecraft:unbreaking\":3,\"minecraft:mending\":1}"
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
            // Two-stage delivery. The /give command's SNBT parser keeps
            // mis-rendering text-component values (item names showed up
            // as the literal JSON string instead of formatted text). So:
            //
            //   1. /give the bare item with enchants + rarity only (no
            //      custom_name, no lore). The /give path handles enchants
            //      and rarity cleanly across yarn versions — we've never
            //      seen those mis-parse.
            //   2. Walk the player's inventory, find the just-given stack
            //      (matches r.item, no custom_name yet, full count of 1),
            //      and patch custom_name + lore via the components API.
            //      SkillsScreen uses the same DataComponentTypes.X path
            //      successfully, so we know it works on every yarn build
            //      that has the components system at all.
            String playerName = player.getName().getString();

            // Snapshot inventory BEFORE /give so we can diff and find the
            // exact slot the new stack lands in. Without this we can't
            // tell our new Wavebreaker apart from a regular Trident the
            // player was already carrying.
            var inv = player.getInventory();
            int invSize;
            try { invSize = inv.size(); } catch (Throwable t) { invSize = 41; }
            ItemStack[] beforeStacks = new ItemStack[invSize];
            for (int i = 0; i < invSize; i++) {
                try { beforeStacks[i] = inv.getStack(i).copy(); }
                catch (Throwable ignored) { beforeStacks[i] = ItemStack.EMPTY; }
            }

            String[] giveFormats = {
                    "give " + playerName + " " + r.item + "[enchantments=" + r.enchants + ",rarity=epic] 1",
                    "give " + playerName + " " + r.item + "[enchantments={levels:" + r.enchants + "},rarity=epic] 1",
                    "give " + playerName + " " + r.item + "[enchantments=" + r.enchants + "] 1",
                    "give " + playerName + " " + r.item + " 1",
            };
            boolean delivered = false;
            for (int i = 0; i < giveFormats.length; i++) {
                if (VersionShim.executeServerCommand(server, giveFormats[i])) {
                    delivered = true;
                    if (i > 0) System.out.println("[IceySMP] /give fell back to format #" + i + " for " + r.name);
                    break;
                }
            }
            if (!delivered) {
                System.out.println("[IceySMP] every /give format failed — nothing delivered");
                return false;
            }

            // Diff-based patch: snapshot before /give, find the changed
            // slot, patch its components. Done in the next-tick callback
            // because /give runs via the command pipeline which is one
            // dispatch removed from this thread — by the time we check,
            // the new stack might not be visible yet on this tick.
            patchComponentsAfterGive(player, r, reasonLabel, beforeStacks);

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
                        Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §a§l" + player.getName().getString()
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

    /** Compare pre-give inventory to post-give. Whichever slot grew (or
     *  appeared from empty) is the slot /give just dropped the new stack
     *  into. Patch custom_name + lore on that exact stack via the
     *  components API — same path SkillsScreen uses successfully, so it
     *  sidesteps every /give SNBT-parsing quirk. */
    private static void patchComponentsAfterGive(ServerPlayerEntity player, Reward r, String reasonLabel,
                                                 ItemStack[] beforeStacks) {
        try {
            var inv = player.getInventory();
            int size = beforeStacks.length;
            ItemStack target = null;
            for (int i = 0; i < size; i++) {
                ItemStack now;
                try { now = inv.getStack(i); } catch (Throwable t) { continue; }
                if (now == null || now.isEmpty()) continue;
                ItemStack before = beforeStacks[i];
                if (before == null || before.isEmpty()) { target = now; break; }
                if (now.getCount() > before.getCount()) { target = now; break; }
            }
            if (target == null) {
                System.out.println("[IceySMP] patchComponentsAfterGive: no changed slot found — item still given but no custom name/lore");
                return;
            }

            Text name = Text.literal(r.name)
                    .setStyle(Style.EMPTY.withColor(parseFormatting(r.nameColor)).withBold(true).withItalic(false));
            try { target.set(DataComponentTypes.CUSTOM_NAME, name); } catch (Throwable t) {
                System.out.println("[IceySMP] set CUSTOM_NAME failed: " + t);
            }

            List<Text> loreLines = new ArrayList<>();
            for (int j = 0; j < r.lore.length; j++) {
                Formatting color = (j == 0) ? Formatting.GRAY : Formatting.DARK_AQUA;
                loreLines.add(Text.literal(r.lore[j])
                        .setStyle(Style.EMPTY.withColor(color).withItalic(false)));
            }
            loreLines.add(Text.literal("Max-level reward — " + reasonLabel)
                    .setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY).withItalic(false)));
            try { target.set(DataComponentTypes.LORE, new LoreComponent(loreLines)); } catch (Throwable t) {
                System.out.println("[IceySMP] set LORE failed: " + t);
            }
        } catch (Throwable t) {
            System.out.println("[IceySMP] patchComponentsAfterGive failed: " + t);
        }
    }

    private static Formatting parseFormatting(String c) {
        return switch (c) {
            case "aqua"         -> Formatting.AQUA;
            case "gold"         -> Formatting.GOLD;
            case "green"        -> Formatting.GREEN;
            case "yellow"       -> Formatting.YELLOW;
            case "dark_red"     -> Formatting.DARK_RED;
            case "red"          -> Formatting.RED;
            case "blue"         -> Formatting.BLUE;
            case "light_purple" -> Formatting.LIGHT_PURPLE;
            case "dark_aqua"    -> Formatting.DARK_AQUA;
            default             -> Formatting.WHITE;
        };
    }
}
