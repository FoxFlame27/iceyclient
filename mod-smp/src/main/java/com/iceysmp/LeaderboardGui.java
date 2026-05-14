package com.iceysmp;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-level leaderboard chest GUI.
 *
 * <p>{@link #open(ServerPlayerEntity)} shows the picker — one item per
 * category, lore lists top 5. Clicking a category opens the per-category
 * view ({@link #openCategory}) which displays the full top 10 as individual
 * named items spread across the chest. Closing the per-category view
 * (ESC, drop-item key, inventory key) reopens the picker on the next tick.
 *
 * <p>Custom {@link ClickableScreenHandler} subclass blocks all item pickup
 * (chest is read-only) and routes slot clicks to a per-handler callback
 * keyed by the slot index.
 */
public final class LeaderboardGui {

    private LeaderboardGui() {}

    /** Picker layout: 9×3 chest, categories in middle row. */
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
                inv.setStack(CATEGORY_SLOTS[i], pickerItem(cats[i], player));
            }

            // Slot → category-id mapping for click routing.
            String[] slotToCatId = new String[27];
            for (int i = 0; i < cats.length && i < CATEGORY_SLOTS.length; i++) {
                slotToCatId[CATEGORY_SLOTS[i]] = cats[i].id();
            }

            player.openHandledScreen(new NamedScreenHandlerFactory() {
                @Override public Text getDisplayName() { return Brand.gradient("✦ Leaderboards ✦", 0xC040FF, 0x44004A, true); }
                @Override public ScreenHandler createMenu(int syncId, PlayerInventory pInv, PlayerEntity p) {
                    return new ClickableScreenHandler(syncId, pInv, inv, slot -> {
                        String catId = (slot >= 0 && slot < slotToCatId.length) ? slotToCatId[slot] : null;
                        if (catId == null) return;
                        // Defer one tick so the close animation completes
                        // cleanly before we open the next screen.
                        var server = player.getServer();
                        if (server != null) server.execute(() -> openCategory(player, catId));
                    });
                }
            });
        } catch (Throwable t) {
            System.out.println("[IceySMP] LeaderboardGui.open failed: " + t);
            player.sendMessage(Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c Couldn't open leaderboard GUI:" + t.getMessage()), false);
        }
    }

    /** Per-category "big" leaderboard — top 10 as one item per slot, with
     *  closing the screen returning to the picker. */
    public static void openCategory(ServerPlayerEntity player, String categoryId) {
        if (player == null) return;
        LeaderboardManager.Category cat = (IceySmp.leaderboard != null) ? IceySmp.leaderboard.categoryById(categoryId) : null;
        if (cat == null) return;
        try {
            SimpleInventory inv = new SimpleInventory(27);
            ItemStack border = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
            trySetCustomName(border, Text.literal(" "));
            for (int i = 0; i < 27; i++) inv.setStack(i, border.copy());

            // Centerpiece item (slot 4) shows the category itself.
            ItemStack header = new ItemStack(iconFor(cat.id()));
            trySetCustomName(header, Text.literal(coloredLabel(cat)));
            List<Text> headerLore = new ArrayList<>();
            headerLore.add(line("§7Effect: §a" + effectName(cat.id())));
            headerLore.add(line("§7Custom reward at: §6" + formatValue(cat.id(), cat.weaponThreshold())));
            headerLore.add(line(" "));
            headerLore.add(line("§8Press ESC to go back"));
            trySetLore(header, headerLore);
            inv.setStack(4, header);

            // Top 10 in slots 9..18 (two rows of 9).
            int[] entrySlots = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
            List<LeaderboardManager.Ranked> ranked = (IceySmp.leaderboard != null) ? IceySmp.leaderboard.top(cat.id()) : List.of();
            int shown = 0;
            for (int i = 0; i < ranked.size() && shown < entrySlots.length; i++) {
                LeaderboardManager.Ranked r = ranked.get(i);
                if (r.value == 0) break;
                inv.setStack(entrySlots[shown], rankedItem(i, r, cat));
                shown++;
            }

            // Viewer's own rank at slot 22 (bottom-middle) if outside top 10.
            int myRank = -1; long myValue = 0;
            for (int i = 0; i < ranked.size(); i++) {
                if (ranked.get(i).uuid.equals(player.getUuid())) { myRank = i; myValue = ranked.get(i).value; break; }
            }
            if (myRank >= entrySlots.length && myValue > 0) {
                ItemStack me = new ItemStack(Items.PLAYER_HEAD);
                trySetCustomName(me, Text.literal("§e§lYou §8— §6#" + (myRank + 1)));
                trySetLore(me, List.of(line("§7" + player.getName().getString() + " §8— §b" + formatValue(cat.id(), myValue))));
                inv.setStack(22, me);
            }

            final ServerPlayerEntity playerRef = player;
            player.openHandledScreen(new NamedScreenHandlerFactory() {
                @Override public Text getDisplayName() {
                    return Text.literal(coloredLabel(cat) + " §8— §6§lTop 10");
                }
                @Override public ScreenHandler createMenu(int syncId, PlayerInventory pInv, PlayerEntity p) {
                    return new ClickableScreenHandler(syncId, pInv, inv, slot -> {
                        // No slot routing on the detail screen — clicks
                        // are ignored, ESC handles the "go back" gesture.
                    }, () -> {
                        // onClosed callback: reopen the picker on the
                        // next tick. Per user: "if you press esc you
                        // go back ok?"
                        var server = playerRef.getServer();
                        if (server != null) server.execute(() -> open(playerRef));
                    });
                }
            });
        } catch (Throwable t) {
            System.out.println("[IceySMP] LeaderboardGui.openCategory failed: " + t);
        }
    }

    /** One stack per leaderboard entry — player head with their name
     *  on top, medal + value in lore. */
    private static ItemStack rankedItem(int idx, LeaderboardManager.Ranked r, LeaderboardManager.Category cat) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        String medal = switch (idx) {
            case 0 -> "§e§l1.§r §6§l";
            case 1 -> "§7§l2.§r §f§l";
            case 2 -> "§6§l3.§r §e§l";
            default -> "§7" + (idx + 1) + ".§r §f";
        };
        trySetCustomName(stack, Text.literal(medal + r.name));
        List<Text> lore = new ArrayList<>();
        lore.add(line("§7" + cat.label() + ": §b§l" + formatValue(cat.id(), r.value)));
        trySetLore(stack, lore);
        // Set the head texture to the player's skin if possible.
        try {
            stack.set(DataComponentTypes.PROFILE,
                    new net.minecraft.component.type.ProfileComponent(
                            new com.mojang.authlib.GameProfile(r.uuid, r.name)));
        } catch (Throwable ignored) {}
        return stack;
    }

    private static ItemStack pickerItem(LeaderboardManager.Category cat, ServerPlayerEntity viewer) {
        ItemStack stack = new ItemStack(iconFor(cat.id()));
        trySetCustomName(stack, Text.literal(coloredLabel(cat)));

        List<LeaderboardManager.Ranked> ranked = (IceySmp.leaderboard != null) ? IceySmp.leaderboard.top(cat.id()) : List.of();
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
        lore.add(line(" "));
        lore.add(line("§e§lClick to see top 10"));
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

    /** Read-only 9×3 chest screen handler that routes slot clicks to a
     *  caller-supplied handler and ignores all item-movement actions.
     *  Optionally fires an onClosed callback when the screen closes. */
    private static final class ClickableScreenHandler extends GenericContainerScreenHandler {
        private final java.util.function.IntConsumer clickHandler;
        private final Runnable closeCallback;

        ClickableScreenHandler(int syncId, PlayerInventory playerInv, Inventory inv,
                               java.util.function.IntConsumer clickHandler) {
            this(syncId, playerInv, inv, clickHandler, null);
        }

        ClickableScreenHandler(int syncId, PlayerInventory playerInv, Inventory inv,
                               java.util.function.IntConsumer clickHandler, Runnable closeCallback) {
            super(net.minecraft.screen.ScreenHandlerType.GENERIC_9X3, syncId, playerInv, inv, 3);
            this.clickHandler = clickHandler;
            this.closeCallback = closeCallback;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            // Route clicks on the container slots (0..26) to the handler.
            // Player-inventory slot clicks (27+) are ignored too — chest
            // is fully read-only.
            if (slotIndex >= 0 && slotIndex < 27 && clickHandler != null) {
                clickHandler.accept(slotIndex);
            }
            // Don't call super — that would let the player pick stuff up.
        }

        @Override
        public void onClosed(PlayerEntity player) {
            super.onClosed(player);
            if (closeCallback != null) {
                try { closeCallback.run(); } catch (Throwable ignored) {}
            }
        }
    }
}
