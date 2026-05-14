package com.iceysmp;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side leaderboard chest GUI for {@code /leaderboard} with no arg.
 * Mirrors {@link SkillsScreen}: one item per category, but the lore lists
 * the top 5 ranked players for that category instead of the viewer's own
 * progress. Read-only, no click handlers — purely a render of current
 * leaderboard state.
 */
public final class LeaderboardGui {

    private LeaderboardGui() {}

    private static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 17};

    public static void open(ServerPlayerEntity player) {
        if (player == null) return;
        try {
            SimpleInventory inv = new SimpleInventory(27);

            ItemStack border = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
            trySetCustomName(border, Text.literal(" "));
            for (int i = 0; i < 27; i++) inv.setStack(i, border.copy());

            LeaderboardManager.Category[] cats = LeaderboardManager.Category.values();
            for (int i = 0; i < cats.length && i < CATEGORY_SLOTS.length; i++) {
                inv.setStack(CATEGORY_SLOTS[i], categoryItem(cats[i], player));
            }

            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, playerInv, p) -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInv, inv),
                    Text.literal("§6§l✦ Leaderboards ✦")
            ));
        } catch (Throwable t) {
            System.out.println("[IceySMP] LeaderboardGui.open failed: " + t);
            player.sendMessage(Text.literal("§c[Icey SMP] Couldn't open leaderboard GUI: " + t.getMessage()), false);
        }
    }

    private static ItemStack categoryItem(LeaderboardManager.Category cat, ServerPlayerEntity viewer) {
        ItemStack stack = new ItemStack(iconFor(cat.id()));
        trySetCustomName(stack, Text.literal(coloredLabel(cat)));

        List<LeaderboardManager.Ranked> ranked = (IceySmp.leaderboard != null)
                ? IceySmp.leaderboard.top(cat.id())
                : List.of();

        List<Text> lore = new ArrayList<>();
        lore.add(line("§7Top 5 — §f" + cat.label()));
        lore.add(line(" "));
        int shown = 0;
        for (int i = 0; i < ranked.size() && shown < 5; i++) {
            LeaderboardManager.Ranked r = ranked.get(i);
            if (r.value == 0) continue;
            String medal = switch (i) {
                case 0 -> "§e§l1.§r";
                case 1 -> "§7§l2.§r";
                case 2 -> "§6§l3.§r";
                default -> "§7" + (i + 1) + ".§r";
            };
            lore.add(line(medal + " §f" + r.name + " §8— §b" + formatValue(cat.id(), r.value)));
            shown++;
        }
        if (shown == 0) lore.add(line("§8  (no entries yet)"));

        // Viewer's own rank at the bottom if outside the top 5
        if (IceySmp.leaderboard != null && viewer != null) {
            int myRank = -1;
            long myValue = 0;
            for (int i = 0; i < ranked.size(); i++) {
                if (ranked.get(i).uuid.equals(viewer.getUuid())) { myRank = i; myValue = ranked.get(i).value; break; }
            }
            if (myRank >= 5 && myValue > 0) {
                lore.add(line(" "));
                lore.add(line("§8──────────────"));
                lore.add(line("§7You: §6#" + (myRank + 1) + " §8— §f" + formatValue(cat.id(), myValue)));
            }
        }

        trySetLore(stack, lore);
        return stack;
    }

    private static String coloredLabel(LeaderboardManager.Category cat) {
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

    private static Text line(String s) {
        return Text.literal(s).setStyle(Style.EMPTY.withItalic(false));
    }

    private static void trySetCustomName(ItemStack stack, Text name) {
        try {
            stack.set(DataComponentTypes.CUSTOM_NAME,
                    name.copy().setStyle(name.getStyle().withItalic(false)));
        } catch (Throwable ignored) {}
    }

    private static void trySetLore(ItemStack stack, List<Text> lines) {
        try { stack.set(DataComponentTypes.LORE, new LoreComponent(lines)); }
        catch (Throwable ignored) {}
    }
}
