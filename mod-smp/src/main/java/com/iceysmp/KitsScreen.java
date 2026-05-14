package com.iceysmp;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * /kits — chest GUI for buying tiered SMP gear bundles.
 *
 * <p>5 kits in slots 10..14 (middle row). Click → attempt purchase via
 * {@link Kits#attemptPurchase}. On success, GUI closes and player
 * receives chat / title feedback. On failure, GUI closes and the error
 * is sent in chat ("Chat error + close GUI" path the user chose).
 */
public final class KitsScreen {

    private KitsScreen() {}

    private static final int[] KIT_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    public static void open(ServerPlayerEntity player) {
        if (player == null) return;
        try {
            SimpleInventory inv = new SimpleInventory(27);

            // Border — purple-tinted glass for the AttributeSMP theme.
            ItemStack border = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
            trySetCustomName(border, Text.literal(" "));
            for (int i = 0; i < 27; i++) inv.setStack(i, border.copy());

            // Place kit items in the middle row.
            Kits.Kit[] all = Kits.ALL;
            String[] slotToKit = new String[27];
            for (int i = 0; i < all.length && i < KIT_SLOTS.length; i++) {
                inv.setStack(KIT_SLOTS[i], kitItem(all[i], player));
                slotToKit[KIT_SLOTS[i]] = all[i].id;
            }

            final ServerPlayerEntity playerRef = player;
            player.openHandledScreen(new NamedScreenHandlerFactory() {
                @Override public Text getDisplayName() {
                    return Brand.gradient("✦ Kits ✦", 0xC040FF, 0x44004A, true);
                }
                @Override public ScreenHandler createMenu(int syncId, PlayerInventory pInv, PlayerEntity p) {
                    return new ClickableScreenHandler(syncId, pInv, inv, slot -> {
                        String kitId = (slot >= 0 && slot < slotToKit.length) ? slotToKit[slot] : null;
                        if (kitId == null) return;
                        Kits.Kit kit = Kits.byId(kitId);
                        if (kit == null) return;
                        // Close the GUI then run the purchase on the
                        // server thread so any error message lands in
                        // chat after the player is back in the world.
                        var server = IceySmp.server;
                        if (server != null) server.execute(() -> {
                            playerRef.closeHandledScreen();
                            String error = Kits.attemptPurchase(playerRef, kit);
                            if (error != null) {
                                playerRef.sendMessage(
                                        Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §c" + error), false);
                            }
                        });
                    });
                }
            });
        } catch (Throwable t) {
            System.out.println("[IceySMP] KitsScreen.open failed: " + t);
            player.sendMessage(Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c Couldn't open kits GUI: " + t.getMessage()), false);
        }
    }

    /** Build the display item for one kit — icon + price + cooldown +
     *  short content summary in lore. */
    private static ItemStack kitItem(Kits.Kit kit, ServerPlayerEntity viewer) {
        ItemStack stack = makeItem(kit.iconItem);
        trySetCustomName(stack, Text.literal(kit.iconColor + "§l" + kit.label));

        List<Text> lore = new ArrayList<>();
        lore.add(line("§7Cost: §f" + kit.cost.displayLabel));
        if (viewer != null && IceySmp.stats != null) {
            PlayerStats ps = IceySmp.stats.peek(viewer.getUuid());
            long remain = Kits.cooldownRemainingMs(ps, kit);
            if (remain > 0) {
                long h = remain / 3_600_000L;
                long m = (remain % 3_600_000L) / 60_000L;
                lore.add(line("§cCooldown: §f" + h + "h " + m + "m"));
            } else {
                int have = Kits.countInInventory(viewer, kit.cost.item);
                if (have >= kit.cost.amount) {
                    lore.add(line("§a✓ Ready to buy"));
                } else {
                    lore.add(line("§7You have: §f" + have + "/" + kit.cost.amount));
                }
            }
        }
        lore.add(line(" "));
        if (kit.descLines != null) for (String d : kit.descLines) lore.add(line("§7" + d));
        lore.add(line(" "));
        lore.add(line("§7Contains:"));
        for (Kits.Item it : kit.items) {
            String shortName = it.id.replace("minecraft:", "").replace("_", " ");
            String count = it.count > 1 ? (" §8×" + it.count) : "";
            lore.add(line("§8  • §f" + shortName + count));
        }
        lore.add(line(" "));
        lore.add(line("§e§lClick to buy"));
        trySetLore(stack, lore);
        return stack;
    }

    /** Resolve an "minecraft:elytra"-style ID to an ItemStack via the
     *  registry. Falls back to PAPER on lookup failure. */
    private static ItemStack makeItem(String id) {
        try {
            return new ItemStack(Registries.ITEM.get(Identifier.of(id)));
        } catch (Throwable t) {
            return new ItemStack(Items.PAPER);
        }
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

    /** Read-only 9×3 chest screen handler — routes slot clicks to a
     *  consumer, blocks all item movement. */
    private static final class ClickableScreenHandler extends GenericContainerScreenHandler {
        private final java.util.function.IntConsumer clickHandler;

        ClickableScreenHandler(int syncId, PlayerInventory playerInv, Inventory inv,
                               java.util.function.IntConsumer clickHandler) {
            super(ScreenHandlerType.GENERIC_9X3, syncId, playerInv, inv, 3);
            this.clickHandler = clickHandler;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (slotIndex >= 0 && slotIndex < 27 && clickHandler != null) {
                clickHandler.accept(slotIndex);
            }
            // No super.onSlotClick — chest is read-only.
        }
    }
}
