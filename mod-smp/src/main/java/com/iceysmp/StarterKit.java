package com.iceysmp;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Hands out a starter kit (iron set + iron tools) the first time a player
 * joins. Detection: PlayerStats only has firstJoinTimestamp set on its
 * very first {@code computeIfAbsent} — and we record a separate flag
 * {@code starterKitGiven} (in-memory) so a server restart doesn't
 * re-grant the kit.
 *
 * <p>Why in-memory and not persisted: a fresh stats.json IS the signal
 * that this player is new. If we ever wipe stats with /icey reset, we
 * also want kits to re-grant — both come from the same "first-event"
 * trigger.
 */
public final class StarterKit {

    private static final java.util.Set<java.util.UUID> givenThisSession = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private StarterKit() {}

    /** Call from a player-join event when their PlayerStats was just
     *  created (i.e. firstJoinTimestamp is within the last few seconds). */
    public static void giveIfFirstJoin(ServerPlayerEntity player, PlayerStats ps, SmpConfig config) {
        if (!config.starterKit()) return;
        if (ps == null) return;
        // Heuristic: this player's firstJoinTimestamp was set very recently
        // AND we haven't given the kit yet this session.
        long ageMs = System.currentTimeMillis() - ps.firstJoinTimestamp;
        if (ageMs > 5_000L) return; // not a freshly-created stats entry
        if (!givenThisSession.add(player.getUuid())) return;
        try {
            // Equip iron armor
            player.equipStack(EquipmentSlot.HEAD,  new ItemStack(Items.IRON_HELMET));
            player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            player.equipStack(EquipmentSlot.LEGS,  new ItemStack(Items.IRON_LEGGINGS));
            player.equipStack(EquipmentSlot.FEET,  new ItemStack(Items.IRON_BOOTS));
            // Tools to inventory
            player.getInventory().insertStack(new ItemStack(Items.IRON_SWORD));
            player.getInventory().insertStack(new ItemStack(Items.IRON_PICKAXE));
            player.getInventory().insertStack(new ItemStack(Items.IRON_AXE));
            player.getInventory().insertStack(new ItemStack(Items.IRON_SHOVEL));
            player.getInventory().insertStack(new ItemStack(Items.COOKED_BEEF, 16));
            player.sendMessage(
                    Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §a§lWelcome! §rHere's your starter kit. You have " +
                            config.noobProtectionMinutes() + " min of noob protection."),
                    false);
        } catch (Throwable t) {
            System.out.println("[IceySMP] StarterKit grant failed: " + t);
        }
    }
}
