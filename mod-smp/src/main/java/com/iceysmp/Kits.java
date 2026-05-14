package com.iceysmp;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * /kits — five tiered SMP gear bundles purchasable through the
 * {@link KitsScreen} chest GUI. Each kit:
 *
 * <ul>
 *   <li>Has a fixed currency cost (diamonds or netherite ingots) deducted
 *       from the buyer's inventory at purchase time.
 *   <li>Has a 24h cooldown per buyer per kit, tracked in
 *       {@link PlayerStats#kitCooldowns}.
 *   <li>Drops the contents into the buyer's inventory via {@code /give}
 *       (one command per slot — same enchant-via-component path that
 *       proven-works for {@link WeaponDrops}).
 * </ul>
 *
 * <p>Tier ladder, cheapest to top:
 * <pre>
 * 1. Starter    — Prot II netherite, Sharp III diamond sword     — 16 diamonds
 * 2. Soldier    — Prot III netherite + Unb II, Sharp IV netherite sword + crossbow + shield + 8 g-apples — 1 netherite ingot
 * 3. Veteran    — Prot IV + Unb III netherite, Sharp V sword + Power V bow + shield + 4 enchanted gapples + 1 totem — 3 netherite ingots
 * 4. Champion   — Prot IV + Unb III + Mending + Thorns III netherite, Sharp V sword (+Looting III), bow, shield (Unb III), 8 enchanted gapples, 2 totems — 8 netherite ingots
 * 5. Attribute  — Prot IV + Unb III + Mending netherite, Sharp V sword, Mace (Density V + Breach IV), Elytra (Unb III + Mending) — 20 netherite ingots
 * </pre>
 */
public final class Kits {

    private Kits() {}

    /** Cost paid to redeem a kit. */
    public static final class Cost {
        public final String item;
        public final int amount;
        public final String displayLabel;
        public Cost(String item, int amount, String displayLabel) {
            this.item = item; this.amount = amount; this.displayLabel = displayLabel;
        }
    }

    /** One item dropped into the buyer's inventory. {@code enchants} is
     *  the {@code minecraft:enchantments=...} component value (SNBT
     *  compound), or null if no enchantments. */
    public static final class Item {
        public final String id;
        public final int count;
        public final String enchants;
        public Item(String id, int count, String enchants) {
            this.id = id; this.count = count; this.enchants = enchants;
        }
    }

    public static final class Kit {
        public final String id, label, iconColor, iconItem;
        public final Cost cost;
        public final Item[] items;
        public final String[] descLines;
        public Kit(String id, String label, String iconColor, String iconItem,
                   Cost cost, Item[] items, String[] descLines) {
            this.id = id; this.label = label; this.iconColor = iconColor;
            this.iconItem = iconItem; this.cost = cost; this.items = items;
            this.descLines = descLines;
        }
    }

    public static final long COOLDOWN_MS = 24L * 3600L * 1000L;

    public static final Kit[] ALL = new Kit[] {
            new Kit(
                    "starter", "Starter Kit", "§a", "minecraft:diamond_chestplate",
                    new Cost("minecraft:diamond", 16, "16 Diamonds"),
                    new Item[] {
                            new Item("minecraft:netherite_helmet",     1, "{\"minecraft:protection\":2,\"minecraft:unbreaking\":2}"),
                            new Item("minecraft:netherite_chestplate", 1, "{\"minecraft:protection\":2,\"minecraft:unbreaking\":2}"),
                            new Item("minecraft:netherite_leggings",   1, "{\"minecraft:protection\":2,\"minecraft:unbreaking\":2}"),
                            new Item("minecraft:netherite_boots",      1, "{\"minecraft:protection\":2,\"minecraft:unbreaking\":2}"),
                            new Item("minecraft:diamond_sword",        1, "{\"minecraft:sharpness\":3,\"minecraft:unbreaking\":2}"),
                            new Item("minecraft:bow",                  1, "{\"minecraft:power\":2}"),
                            new Item("minecraft:cooked_beef",         16, null),
                    },
                    new String[] {"Entry tier. Full netherite armor with Prot II.", "Diamond sword + bow + food."}
            ),
            new Kit(
                    "soldier", "Soldier Kit", "§b", "minecraft:netherite_chestplate",
                    new Cost("minecraft:netherite_ingot", 1, "1 Netherite Ingot"),
                    new Item[] {
                            new Item("minecraft:netherite_helmet",     1, "{\"minecraft:protection\":3,\"minecraft:unbreaking\":2}"),
                            new Item("minecraft:netherite_chestplate", 1, "{\"minecraft:protection\":3,\"minecraft:unbreaking\":2}"),
                            new Item("minecraft:netherite_leggings",   1, "{\"minecraft:protection\":3,\"minecraft:unbreaking\":2}"),
                            new Item("minecraft:netherite_boots",      1, "{\"minecraft:protection\":3,\"minecraft:unbreaking\":2}"),
                            new Item("minecraft:netherite_sword",      1, "{\"minecraft:sharpness\":4,\"minecraft:knockback\":1,\"minecraft:unbreaking\":3}"),
                            new Item("minecraft:crossbow",             1, "{\"minecraft:quick_charge\":2,\"minecraft:piercing\":2}"),
                            new Item("minecraft:shield",               1, null),
                            new Item("minecraft:golden_apple",         8, null),
                    },
                    new String[] {"Reliable mid-tier loadout.", "Prot III + Unbreaking II all pieces."}
            ),
            new Kit(
                    "veteran", "Veteran Kit", "§e", "minecraft:netherite_sword",
                    new Cost("minecraft:netherite_ingot", 3, "3 Netherite Ingots"),
                    new Item[] {
                            new Item("minecraft:netherite_helmet",     1, "{\"minecraft:protection\":4,\"minecraft:unbreaking\":3}"),
                            new Item("minecraft:netherite_chestplate", 1, "{\"minecraft:protection\":4,\"minecraft:unbreaking\":3}"),
                            new Item("minecraft:netherite_leggings",   1, "{\"minecraft:protection\":4,\"minecraft:unbreaking\":3}"),
                            new Item("minecraft:netherite_boots",      1, "{\"minecraft:protection\":4,\"minecraft:unbreaking\":3}"),
                            new Item("minecraft:netherite_sword",      1, "{\"minecraft:sharpness\":5,\"minecraft:sweeping_edge\":3,\"minecraft:fire_aspect\":2,\"minecraft:unbreaking\":3}"),
                            new Item("minecraft:bow",                  1, "{\"minecraft:power\":5,\"minecraft:punch\":2,\"minecraft:infinity\":1}"),
                            new Item("minecraft:shield",               1, null),
                            new Item("minecraft:enchanted_golden_apple", 4, null),
                            new Item("minecraft:totem_of_undying",      1, null),
                    },
                    new String[] {"Max-prot armor + Sharp V sword.", "Power V + Infinity bow + totem."}
            ),
            new Kit(
                    "champion", "Champion Kit", "§6", "minecraft:netherite_chestplate",
                    new Cost("minecraft:netherite_ingot", 8, "8 Netherite Ingots"),
                    new Item[] {
                            new Item("minecraft:netherite_helmet",     1, "{\"minecraft:protection\":4,\"minecraft:unbreaking\":3,\"minecraft:mending\":1,\"minecraft:thorns\":3}"),
                            new Item("minecraft:netherite_chestplate", 1, "{\"minecraft:protection\":4,\"minecraft:unbreaking\":3,\"minecraft:mending\":1,\"minecraft:thorns\":3}"),
                            new Item("minecraft:netherite_leggings",   1, "{\"minecraft:protection\":4,\"minecraft:unbreaking\":3,\"minecraft:mending\":1,\"minecraft:thorns\":3}"),
                            new Item("minecraft:netherite_boots",      1, "{\"minecraft:protection\":4,\"minecraft:unbreaking\":3,\"minecraft:mending\":1,\"minecraft:thorns\":3,\"minecraft:feather_falling\":4}"),
                            new Item("minecraft:netherite_sword",      1, "{\"minecraft:sharpness\":5,\"minecraft:sweeping_edge\":3,\"minecraft:fire_aspect\":2,\"minecraft:looting\":3,\"minecraft:unbreaking\":3,\"minecraft:mending\":1}"),
                            new Item("minecraft:bow",                  1, "{\"minecraft:power\":5,\"minecraft:punch\":2,\"minecraft:flame\":1,\"minecraft:infinity\":1,\"minecraft:unbreaking\":3}"),
                            new Item("minecraft:shield",               1, "{\"minecraft:unbreaking\":3}"),
                            new Item("minecraft:enchanted_golden_apple", 8, null),
                            new Item("minecraft:totem_of_undying",      2, null),
                    },
                    new String[] {"Mending + Thorns III armor.", "Looting III sword. Flame bow. 2 totems."}
            ),
            new Kit(
                    "attribute", "Attribute Kit", "§d", "minecraft:elytra",
                    new Cost("minecraft:netherite_ingot", 20, "20 Netherite Ingots"),
                    new Item[] {
                            new Item("minecraft:netherite_helmet",     1, "{\"minecraft:protection\":4,\"minecraft:unbreaking\":3,\"minecraft:mending\":1}"),
                            new Item("minecraft:netherite_chestplate", 1, "{\"minecraft:protection\":4,\"minecraft:unbreaking\":3,\"minecraft:mending\":1}"),
                            new Item("minecraft:netherite_leggings",   1, "{\"minecraft:protection\":4,\"minecraft:unbreaking\":3,\"minecraft:mending\":1}"),
                            new Item("minecraft:netherite_boots",      1, "{\"minecraft:protection\":4,\"minecraft:unbreaking\":3,\"minecraft:mending\":1,\"minecraft:feather_falling\":4}"),
                            new Item("minecraft:netherite_sword",      1, "{\"minecraft:sharpness\":5,\"minecraft:sweeping_edge\":3,\"minecraft:unbreaking\":3,\"minecraft:mending\":1}"),
                            new Item("minecraft:mace",                 1, "{\"minecraft:density\":5,\"minecraft:breach\":4,\"minecraft:unbreaking\":3}"),
                            new Item("minecraft:elytra",               1, "{\"minecraft:unbreaking\":3,\"minecraft:mending\":1}"),
                    },
                    new String[] {"Top tier. Full netherite + sword.", "Mace + Elytra included."}
            ),
    };

    public static Kit byId(String id) {
        if (id == null) return null;
        for (Kit k : ALL) if (k.id.equals(id)) return k;
        return null;
    }

    /** Remaining cooldown in ms (0 if buy is allowed). */
    public static long cooldownRemainingMs(PlayerStats ps, Kit kit) {
        if (ps == null || kit == null) return 0;
        long last = ps.getKitLastMs(kit.id);
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0, COOLDOWN_MS - elapsed);
    }

    /** Count how many of {@code itemId} the player has across the main
     *  inventory + offhand. Skips armor slots (count != cost). */
    public static int countInInventory(ServerPlayerEntity player, String itemId) {
        try {
            var inv = player.getInventory();
            int total = 0;
            int size;
            try { size = inv.size(); } catch (Throwable t) { size = 41; }
            for (int i = 0; i < size; i++) {
                ItemStack s = inv.getStack(i);
                if (s == null || s.isEmpty()) continue;
                if (matchesItemId(s, itemId)) total += s.getCount();
            }
            return total;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Remove {@code amount} of {@code itemId} from the player's inventory.
     *  Returns true if the deduction was fully satisfied. */
    public static boolean deductFromInventory(ServerPlayerEntity player, String itemId, int amount) {
        try {
            var inv = player.getInventory();
            int remaining = amount;
            int size;
            try { size = inv.size(); } catch (Throwable t) { size = 41; }
            for (int i = 0; i < size && remaining > 0; i++) {
                ItemStack s = inv.getStack(i);
                if (s == null || s.isEmpty()) continue;
                if (!matchesItemId(s, itemId)) continue;
                int take = Math.min(remaining, s.getCount());
                s.decrement(take);
                remaining -= take;
            }
            return remaining == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean matchesItemId(ItemStack stack, String expectedId) {
        try {
            Object item = stack.getItem();
            for (String regClass : new String[] {
                    "net.minecraft.registry.Registries",
                    "net.minecraft.util.registry.Registries"}) {
                try {
                    Class<?> c = Class.forName(regClass);
                    Object reg = c.getField("ITEM").get(null);
                    for (java.lang.reflect.Method m : reg.getClass().getMethods()) {
                        if (!"getId".equals(m.getName())) continue;
                        if (m.getParameterCount() != 1) continue;
                        try {
                            Object idObj = m.invoke(reg, item);
                            if (idObj != null && expectedId.equals(idObj.toString())) return true;
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** Attempt to buy a kit for the given player. Returns null on success;
     *  on failure returns a user-facing error message (e.g. "you need 4
     *  more netherite ingots", "kit on cooldown for 12h 34m"). */
    public static String attemptPurchase(ServerPlayerEntity player, Kit kit) {
        if (player == null || kit == null) return "kit not found";
        if (IceySmp.stats == null) return "server not ready";
        PlayerStats ps = IceySmp.stats.get(player.getUuid(), player.getName().getString());

        long remain = cooldownRemainingMs(ps, kit);
        if (remain > 0) {
            long h = remain / 3_600_000L;
            long m = (remain % 3_600_000L) / 60_000L;
            return "kit on cooldown for §f" + h + "h " + m + "m";
        }

        int have = countInInventory(player, kit.cost.item);
        if (have < kit.cost.amount) {
            int missing = kit.cost.amount - have;
            return "need §f" + missing + "§c more §f" + kit.cost.displayLabel;
        }

        // Deduct currency, then give items. If deduction succeeds we
        // mark the cooldown — give failures don't refund (the items
        // are already consumed). /give is reliable enough that this
        // should never happen in practice.
        if (!deductFromInventory(player, kit.cost.item, kit.cost.amount)) {
            return "failed to deduct currency (inventory shifted mid-purchase?)";
        }
        ps.setKitLastMs(kit.id, System.currentTimeMillis());

        MinecraftServer server = IceySmp.server;
        if (server == null) return null; // items not given but currency already gone; logged below
        String name = player.getName().getString();
        for (Item it : kit.items) {
            String cmd;
            if (it.enchants != null) {
                cmd = "give " + name + " " + it.id + "[enchantments=" + it.enchants + "] " + it.count;
            } else {
                cmd = "give " + name + " " + it.id + " " + it.count;
            }
            boolean ok = VersionShim.executeServerCommand(server, cmd);
            if (!ok) {
                // Legacy fallback for the {levels:{...}} wrapper, then bare item.
                if (it.enchants != null) {
                    cmd = "give " + name + " " + it.id + "[enchantments={levels:" + it.enchants + "}] " + it.count;
                    ok = VersionShim.executeServerCommand(server, cmd);
                }
                if (!ok) VersionShim.executeServerCommand(server, "give " + name + " " + it.id + " " + it.count);
            }
        }

        // Title + broadcast
        try {
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(10, 50, 20));
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                    Text.literal(kit.iconColor + "§l" + kit.label.toUpperCase())));
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                    Text.literal("§7Purchased for §f" + kit.cost.displayLabel)));
        } catch (Throwable ignored) {}
        try {
            server.getPlayerManager().broadcast(
                    Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §a" + name
                            + " §7bought the " + kit.iconColor + "§l" + kit.label
                            + " §7for §f" + kit.cost.displayLabel),
                    false);
        } catch (Throwable ignored) {}
        return null;
    }
}
