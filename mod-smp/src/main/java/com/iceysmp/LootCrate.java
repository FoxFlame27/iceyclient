package com.iceysmp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

// RNG only used for the tier-pickRandom() helper now that spawn is exact.

/**
 * Admin-spawned loot crate. {@code /icey crate [tier]} drops a chest at
 * a random coord within ~200 blocks of the caller, fills it with
 * tier-themed loot, lightning-strikes the spot for visibility, and
 * broadcasts coords to the whole server so players can race.
 *
 * <p>No automatic timer — purely an event command. Admin chooses when
 * to fire it.
 *
 * <p>Loot is injected via {@code /setblock minecraft:chest[block_entity_data={...}]}.
 * The {@code count} vs {@code Count} field name flipped at 1.20.5/1.21.5
 * boundary, so we try both forms and let the first that parses win.
 */
public final class LootCrate {

    private static final Random RNG = new Random();

    public enum Tier {
        COMMON("Loot Crate",           "§e",  60),
        RARE  ("Rare Loot Crate",      "§b§l",30),
        EPIC  ("§5§lEPIC §rLoot Crate","§d§l",10);
        final String label, colorPrefix;
        final int weight;
        Tier(String label, String colorPrefix, int weight) {
            this.label = label; this.colorPrefix = colorPrefix; this.weight = weight;
        }
        static Tier pickRandom() {
            int total = 0;
            for (Tier t : values()) total += t.weight;
            int r = RNG.nextInt(total);
            int acc = 0;
            for (Tier t : values()) {
                acc += t.weight;
                if (r < acc) return t;
            }
            return COMMON;
        }
    }

    /** Crate "theme" — general / armor / gear / food. Each theme has its
     *  own per-tier loot table. The general theme is what /crate spawns;
     *  /armorcrate, /gearcrate, /foodcrate target the other three. */
    public enum Theme {
        GENERAL("Loot Crate"),
        ARMOR  ("Armor Crate"),
        GEAR   ("Gear Crate"),
        FOOD   ("Food Crate");
        final String labelBase;
        Theme(String labelBase) { this.labelBase = labelBase; }
    }

    private record LootItem(String id, int count) {}

    /** Per-(theme, tier) loot. Slot ids auto-assigned by index — kept
     *  under 27 items so the single chest never overflows. */
    private static LootItem[] lootFor(Theme theme, Tier tier) {
        return switch (theme) {
            case GENERAL -> switch (tier) {
                case COMMON -> new LootItem[] {
                        new LootItem("minecraft:cooked_beef", 16),
                        new LootItem("minecraft:iron_ingot", 8),
                        new LootItem("minecraft:gold_ingot", 4),
                        new LootItem("minecraft:arrow", 32),
                        new LootItem("minecraft:saddle", 1),
                        new LootItem("minecraft:experience_bottle", 8),
                };
                case RARE -> new LootItem[] {
                        new LootItem("minecraft:diamond", 8),
                        new LootItem("minecraft:totem_of_undying", 1),
                        new LootItem("minecraft:golden_apple", 4),
                        new LootItem("minecraft:beacon", 1),
                        new LootItem("minecraft:ender_pearl", 8),
                        new LootItem("minecraft:experience_bottle", 16),
                };
                case EPIC -> new LootItem[] {
                        new LootItem("minecraft:diamond", 16),
                        new LootItem("minecraft:netherite_ingot", 1),
                        new LootItem("minecraft:shulker_shell", 4),
                        new LootItem("minecraft:nether_star", 1),
                        new LootItem("minecraft:enchanted_golden_apple", 1),
                        new LootItem("minecraft:totem_of_undying", 2),
                        new LootItem("minecraft:experience_bottle", 32),
                };
            };
            case ARMOR -> switch (tier) {
                case COMMON -> new LootItem[] {
                        new LootItem("minecraft:iron_helmet", 1),
                        new LootItem("minecraft:iron_chestplate", 1),
                        new LootItem("minecraft:iron_leggings", 1),
                        new LootItem("minecraft:iron_boots", 1),
                        new LootItem("minecraft:shield", 1),
                };
                case RARE -> new LootItem[] {
                        new LootItem("minecraft:diamond_helmet", 1),
                        new LootItem("minecraft:diamond_chestplate", 1),
                        new LootItem("minecraft:diamond_leggings", 1),
                        new LootItem("minecraft:diamond_boots", 1),
                        new LootItem("minecraft:turtle_helmet", 1),
                        new LootItem("minecraft:shield", 1),
                };
                case EPIC -> new LootItem[] {
                        new LootItem("minecraft:netherite_helmet", 1),
                        new LootItem("minecraft:netherite_chestplate", 1),
                        new LootItem("minecraft:netherite_leggings", 1),
                        new LootItem("minecraft:netherite_boots", 1),
                        new LootItem("minecraft:elytra", 1),
                        new LootItem("minecraft:totem_of_undying", 2),
                };
            };
            case GEAR -> switch (tier) {
                case COMMON -> new LootItem[] {
                        new LootItem("minecraft:iron_sword", 1),
                        new LootItem("minecraft:iron_pickaxe", 1),
                        new LootItem("minecraft:iron_axe", 1),
                        new LootItem("minecraft:iron_shovel", 1),
                        new LootItem("minecraft:bow", 1),
                        new LootItem("minecraft:arrow", 32),
                };
                case RARE -> new LootItem[] {
                        new LootItem("minecraft:diamond_sword", 1),
                        new LootItem("minecraft:diamond_pickaxe", 1),
                        new LootItem("minecraft:diamond_axe", 1),
                        new LootItem("minecraft:crossbow", 1),
                        new LootItem("minecraft:enchanted_book", 2),
                        new LootItem("minecraft:experience_bottle", 16),
                };
                case EPIC -> new LootItem[] {
                        new LootItem("minecraft:netherite_sword", 1),
                        new LootItem("minecraft:netherite_pickaxe", 1),
                        new LootItem("minecraft:netherite_axe", 1),
                        new LootItem("minecraft:trident", 1),
                        new LootItem("minecraft:mace", 1),
                        new LootItem("minecraft:enchanted_book", 3),
                        new LootItem("minecraft:experience_bottle", 32),
                };
            };
            case FOOD -> switch (tier) {
                case COMMON -> new LootItem[] {
                        new LootItem("minecraft:cooked_beef", 32),
                        new LootItem("minecraft:bread", 16),
                        new LootItem("minecraft:cooked_chicken", 16),
                        new LootItem("minecraft:carrot", 8),
                        new LootItem("minecraft:apple", 8),
                };
                case RARE -> new LootItem[] {
                        new LootItem("minecraft:cooked_beef", 64),
                        new LootItem("minecraft:golden_apple", 4),
                        new LootItem("minecraft:golden_carrot", 16),
                        new LootItem("minecraft:cake", 2),
                        new LootItem("minecraft:honey_bottle", 4),
                        new LootItem("minecraft:pumpkin_pie", 8),
                };
                case EPIC -> new LootItem[] {
                        new LootItem("minecraft:enchanted_golden_apple", 4),
                        new LootItem("minecraft:golden_apple", 16),
                        new LootItem("minecraft:golden_carrot", 32),
                        new LootItem("minecraft:honey_bottle", 8),
                        new LootItem("minecraft:cake", 4),
                        new LootItem("minecraft:suspicious_stew", 8),
                };
            };
        };
    }

    /** Back-compat overload — defaults to the GENERAL theme. */
    public static boolean spawnNearCaller(ServerCommandSource src, Tier tier) {
        return spawnNearCaller(src, Theme.GENERAL, tier);
    }

    /** Entrypoint from /crate / /armorcrate / /gearcrate / /foodcrate.
     *  Spawns the chest at the caller's exact block position. Returns
     *  success/fail for the command. */
    public static boolean spawnNearCaller(ServerCommandSource src, Theme theme, Tier tier) {
        MinecraftServer server = src.getServer();
        if (server == null) return false;
        int x, y, z;
        ServerPlayerEntity caller = src.getPlayer();
        if (caller != null) {
            x = caller.getBlockX();
            y = caller.getBlockY();
            z = caller.getBlockZ();
        } else {
            BlockPos sp = resolveWorldSpawn(server);
            x = sp.getX(); y = sp.getY(); z = sp.getZ();
        }

        LootItem[] loot = lootFor(theme, tier);
        if (!placeChest(server, x, y, z, loot)) {
            src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c Failed to /setblock the crate"), false);
            return false;
        }

        // Lightning visual at the chest position — cosmetic.
        VersionShim.executeServerCommand(server,
                "summon minecraft:lightning_bolt " + x + " " + (y + 1) + " " + z);

        // Crate label = tier prefix + (theme name if non-general).
        String labelBody = (theme == Theme.GENERAL) ? tier.label : tier.label + " · " + theme.labelBase;
        server.getPlayerManager().broadcast(
                Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §rA " + tier.colorPrefix + labelBody
                        + " §rhas spawned at §f§l(" + x + ", " + y + ", " + z + ")"
                        + (caller != null ? " §7— placed by §f" + caller.getName().getString() : "")),
                false);
        return true;
    }

    private static boolean placeChest(MinecraftServer server, int x, int y, int z, LootItem[] items) {
        // Modern (1.21.5+) — block_entity_data + lowercase 'count' in items
        String itemsModern = buildItemsTag(items, false);
        String modernCmd = "setblock " + x + " " + y + " " + z
                + " minecraft:chest[block_entity_data={Items:" + itemsModern + "}] replace";
        if (VersionShim.executeServerCommand(server, modernCmd)) return true;
        // Legacy (1.21.0-1.21.4) — raw NBT + capital 'Count'
        String itemsLegacy = buildItemsTag(items, true);
        String legacyCmd = "setblock " + x + " " + y + " " + z
                + " minecraft:chest{Items:" + itemsLegacy + "} replace";
        if (VersionShim.executeServerCommand(server, legacyCmd)) return true;
        // Final fallback: empty chest, no loot. At least something is there.
        return VersionShim.executeServerCommand(server, "setblock " + x + " " + y + " " + z + " minecraft:chest replace");
    }

    /** Build {@code [{Slot:0b,id:"...",count:N},...]} or the capital-Count
     *  variant. Slot numbering: 0..items.length-1 packed in the top row. */
    private static String buildItemsTag(LootItem[] items, boolean legacy) {
        StringBuilder sb = new StringBuilder("[");
        String countField = legacy ? "Count" : "count";
        for (int i = 0; i < items.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("{Slot:").append(i).append("b,id:\"")
              .append(items[i].id).append("\",")
              .append(countField).append(":").append(items[i].count).append("b}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static BlockPos resolveWorldSpawn(MinecraftServer server) {
        try {
            Object world = server.getOverworld();
            Object pos = world.getClass().getMethod("getSpawnPos").invoke(world);
            if (pos instanceof BlockPos bp) return bp;
        } catch (Throwable ignored) {}
        try {
            Object world = server.getOverworld();
            Object props = world.getClass().getMethod("getLevelProperties").invoke(world);
            Object pos = props.getClass().getMethod("getSpawnPos").invoke(props);
            if (pos instanceof BlockPos bp) return bp;
        } catch (Throwable ignored) {}
        return new BlockPos(0, 64, 0);
    }
}
