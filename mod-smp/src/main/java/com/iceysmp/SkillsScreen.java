package com.iceysmp;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Chest GUI replacement for the old text-based {@code /icey help}. Shows
 * one item per category with a progress bar in the lore. Read-only view —
 * no click handlers, the GUI just renders the player's current standing.
 *
 * <p>Layout: 9x3 chest. Top + bottom rows are gray glass panes (border).
 * Middle row holds the 7 category items in slots 10..16. Slot 9 and 17
 * are also bordered.
 */
public final class SkillsScreen {

    private SkillsScreen() {}

    private static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 17};
    /** Bar character count — 20 makes the percentage easy to read. */
    private static final int BAR_LEN = 20;

    public static void open(ServerPlayerEntity player) {
        if (player == null) return;
        try {
            PlayerStats ps = (IceySmp.stats != null)
                    ? IceySmp.stats.get(player.getUuid(), player.getName().getString())
                    : null;

            SimpleInventory inv = new SimpleInventory(27);

            // Border — gray glass panes with blank name so they don't read
            // as "stained glass pane" in the tooltip.
            ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
            trySetCustomName(border, Text.literal(" "));
            for (int i = 0; i < 27; i++) inv.setStack(i, border.copy());

            // Place category items in the middle row.
            LeaderboardManager.Category[] cats = LeaderboardManager.Category.values();
            for (int i = 0; i < cats.length && i < CATEGORY_SLOTS.length; i++) {
                inv.setStack(CATEGORY_SLOTS[i], categoryItem(cats[i], ps));
            }

            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, playerInv, p) -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInv, inv),
                    Text.literal("§b§l✦ Icey Skills ✦")
            ));
        } catch (Throwable t) {
            System.out.println("[IceySMP] SkillsScreen.open failed: " + t);
            player.sendMessage(Text.literal("§c[Icey SMP] Couldn't open skills GUI: " + t.getMessage()), false);
        }
    }

    private static ItemStack categoryItem(LeaderboardManager.Category cat, PlayerStats ps) {
        ItemStack stack = new ItemStack(iconFor(cat.id()));
        trySetCustomName(stack, Text.literal(coloredLabel(cat)));

        long current = (ps != null) ? cat.field().applyAsLong(ps) : 0L;
        double normalized = current / (double) cat.divisor();
        int level = (normalized < 1.0) ? 0 : (int) (Math.log(normalized) / Math.log(2)) + 1;
        long next = LeaderboardManager.nextLevelThreshold(current, cat.divisor());
        long progressInLevel = current - (level == 0 ? 0 : (long) (cat.divisor() * Math.pow(2, level - 1)));
        long levelSpan = next - (level == 0 ? 0 : (long) (cat.divisor() * Math.pow(2, level - 1)));
        if (levelSpan <= 0) levelSpan = next;
        double pct = Math.max(0, Math.min(1, progressInLevel / (double) levelSpan));

        List<Text> lore = new ArrayList<>();
        lore.add(line("§7Current: §f" + formatValue(cat.id(), current)));
        lore.add(line("§7Level: §b" + (level == 0 ? "—" : "Lv " + level)));
        lore.add(line("§7Progress: §a" + (int)(pct * 100) + "% §8" + bar(pct)));
        lore.add(line("§7Next level at: §f" + formatValue(cat.id(), next)));
        lore.add(line("§7Effect: §a" + effectName(cat.id())));
        lore.add(line(" "));
        boolean awarded = ps != null && ps.wasAwardedFrostfangFor(cat.id());
        String tier;
        if (awarded) {
            tier = "§a✓ Earned — " + rewardName(cat.id());
        } else {
            tier = "§7Custom reward: §6" + rewardName(cat.id())
                    + " §8| §e" + formatValue(cat.id(), current)
                    + "§7/§a" + formatValue(cat.id(), cat.weaponThreshold());
        }
        lore.add(line(tier));
        trySetLore(stack, lore);
        return stack;
    }

    private static String coloredLabel(LeaderboardManager.Category cat) {
        // Italic-off prefix uses §r§<color>§l — but server-side Text.literal
        // formatting via section signs is honored by the client renderer.
        String color = switch (cat.id()) {
            case "mining"   -> "§b";
            case "pvp"      -> "§c";
            case "playtime" -> "§e";
            case "fishing"  -> "§3";
            case "walking"  -> "§a";
            case "jumps"    -> "§d";
            case "water"    -> "§9";
            case "dmgtaken" -> "§4";
            default         -> "§f";
        };
        return color + "§l" + cat.label();
    }

    private static net.minecraft.item.Item iconFor(String catId) {
        return switch (catId) {
            case "mining"   -> Items.IRON_PICKAXE;
            case "pvp"      -> Items.DIAMOND_SWORD;
            case "playtime" -> Items.CLOCK;
            case "fishing"  -> Items.FISHING_ROD;
            case "walking"  -> Items.IRON_BOOTS;
            case "jumps"    -> Items.RABBIT_FOOT;
            case "water"    -> Items.HEART_OF_THE_SEA;
            case "dmgtaken" -> Items.SHIELD;
            default         -> Items.PAPER;
        };
    }

    private static String effectName(String catId) {
        return switch (catId) {
            case "mining"   -> "Haste";
            case "pvp"      -> "Strength";
            case "playtime" -> "Saturation";
            case "fishing"  -> "Luck";
            case "walking"  -> "Speed";
            case "jumps"    -> "Jump Boost";
            case "water"    -> "Dolphin's Grace";
            case "dmgtaken" -> "Resistance";
            default         -> "?";
        };
    }

    private static String rewardName(String catId) {
        return switch (catId) {
            case "mining"   -> "Frostpick";
            case "pvp"      -> "Frostfang";
            case "playtime" -> "Crown of Hours";
            case "fishing"  -> "Tidecaller";
            case "walking"  -> "Wanderer's Treads";
            case "jumps"    -> "Springheel Greaves";
            case "water"    -> "Wavebreaker";
            case "dmgtaken" -> "Stonewall";
            default         -> "?";
        };
    }

    /** Build a 20-char bar like §a██████████§7░░░░░░░░░░ from a 0..1 pct. */
    private static String bar(double pct) {
        int filled = Math.max(0, Math.min(BAR_LEN, (int) Math.round(pct * BAR_LEN)));
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < filled; i++) sb.append('█');
        sb.append("§8");
        for (int i = filled; i < BAR_LEN; i++) sb.append('░');
        return sb.toString();
    }

    private static String formatValue(String catId, long count) {
        return switch (catId) {
            case "playtime" -> {
                long sec = count / 20;
                long h = sec / 3600, m = (sec % 3600) / 60;
                if (h > 0) yield h + "h" + (m > 0 ? " " + m + "m" : "");
                if (m > 0) yield m + "m";
                yield sec + "s";
            }
            case "walking", "water" -> {
                double m = count / 100.0;
                if (m < 1000) yield String.format("%.1f m", m);
                yield String.format("%,.0f m", m);
            }
            case "dmgtaken" -> String.format("%.1f HP", count / 10.0);
            default         -> String.format("%,d", count);
        };
    }

    /** Lore line — italic off by default so it reads cleanly. The Text
     *  built from .literal will inherit italic from the slot rendering
     *  unless we set the style explicitly. Server-side section codes
     *  do this fine for color/bold but italic is sticky — set via style. */
    private static Text line(String s) {
        return Text.literal(s).setStyle(net.minecraft.text.Style.EMPTY.withItalic(false));
    }

    /** Component setters wrapped in try/catch — yarn renames
     *  DataComponentTypes.CUSTOM_NAME / LORE across some 1.21.x variants. */
    private static void trySetCustomName(ItemStack stack, Text name) {
        try {
            stack.set(DataComponentTypes.CUSTOM_NAME,
                    name.copy().setStyle(name.getStyle().withItalic(false)));
        } catch (Throwable ignored) {}
    }

    private static void trySetLore(ItemStack stack, List<Text> lines) {
        try {
            stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
        } catch (Throwable ignored) {}
    }
}
