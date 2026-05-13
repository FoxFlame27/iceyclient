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

    private record LootItem(String id, int count) {}

    /** Per-tier loot — slot ids auto-assigned by index. Single-chest =
     *  27 slots, never overflow. Numbers per user request: epic gets
     *  more diamonds + 1 netherite ingot, no elytra. */
    private static LootItem[] lootFor(Tier tier) {
        return switch (tier) {
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
    }

    /** Entrypoint from /icey crate. Spawns the chest at the caller's
     *  exact block position. Returns success/fail for the command. */
    public static boolean spawnNearCaller(ServerCommandSource src, Tier tier) {
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

        LootItem[] loot = lootFor(tier);
        if (!placeChest(server, x, y, z, loot)) {
            src.sendFeedback(() -> Text.literal("§c[Icey SMP] Failed to /setblock the crate"), false);
            return false;
        }

        // Lightning visual at the chest position — cosmetic.
        VersionShim.executeServerCommand(server,
                "summon minecraft:lightning_bolt " + x + " " + (y + 1) + " " + z);

        server.getPlayerManager().broadcast(
                Text.literal("§b§l[Icey SMP] §rA " + tier.colorPrefix + tier.label
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
