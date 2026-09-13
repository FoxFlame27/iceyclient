package com.iceymod.hud.modules;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import net.minecraft.client.Minecraft;
import com.iceymod.compat.Gfx;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Forces a vanilla glow outline on dropped items the user cares about,
 * so a mace dropped from an Ominous Vault under a pile of gold or a
 * totem of undying after a raid stands out instantly. The actual
 * "isGlowing → true" override is done in
 * {@link com.iceymod.mixin.EntityIsGlowingMixin} which calls
 * {@link #shouldGlow(ItemStack)} below.
 */
public class ItemGlowModule extends HudModule {
    public final BoolSetting mace        = addSetting(new BoolSetting("mace",        "Mace",                true));
    public final BoolSetting totem       = addSetting(new BoolSetting("totem",       "Totem of Undying",    true));
    public final BoolSetting netherite   = addSetting(new BoolSetting("netherite",   "Netherite Items",     true));
    public final BoolSetting netheriteBlocks = addSetting(new BoolSetting("netheriteBlocks", "Netherite Block / Ancient Debris", true));
    public final BoolSetting elytra      = addSetting(new BoolSetting("elytra",      "Elytra",              true));
    public final BoolSetting beacon      = addSetting(new BoolSetting("beacon",      "Beacon",              true));
    public final BoolSetting netherStar  = addSetting(new BoolSetting("netherStar",  "Nether Star",         true));
    public final BoolSetting dragonEgg   = addSetting(new BoolSetting("dragonEgg",   "Dragon Egg",          true));
    public final BoolSetting heartOfSea  = addSetting(new BoolSetting("heartOfSea",  "Heart of the Sea",    true));
    public final BoolSetting trident     = addSetting(new BoolSetting("trident",     "Trident",             true));
    public final BoolSetting shulkerShell= addSetting(new BoolSetting("shulkerShell","Shulker Shell",       true));
    public final BoolSetting enchantedBook = addSetting(new BoolSetting("enchantedBook", "Enchanted Book",  false));

    public ItemGlowModule() {
        super("itemglow", "Item Glow", 0, 0);
        setEnabled(true);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(Gfx context, Minecraft client) {}

    /**
     * Called from EntityIsGlowingMixin to decide whether a given dropped
     * ItemEntity should appear glowing client-side.
     */
    public static boolean shouldGlow(ItemEntity entity) {
        if (entity == null) return false;
        ItemGlowModule mod = find();
        if (mod == null || !mod.isEnabled()) return false;
        return mod.shouldGlow(entity.getItem());
    }

    public boolean shouldGlow(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (mace.get() && stack.is(Items.MACE)) return true;
        if (totem.get() && stack.is(Items.TOTEM_OF_UNDYING)) return true;
        if (netherite.get() && (
                stack.is(Items.NETHERITE_INGOT)
             || stack.is(Items.NETHERITE_SCRAP)
             || stack.is(Items.NETHERITE_SWORD)
             || stack.is(Items.NETHERITE_AXE)
             || stack.is(Items.NETHERITE_PICKAXE)
             || stack.is(Items.NETHERITE_SHOVEL)
             || stack.is(Items.NETHERITE_HOE)
             || stack.is(Items.NETHERITE_HELMET)
             || stack.is(Items.NETHERITE_CHESTPLATE)
             || stack.is(Items.NETHERITE_LEGGINGS)
             || stack.is(Items.NETHERITE_BOOTS))) return true;
        if (netheriteBlocks.get() && (
                stack.is(Items.NETHERITE_BLOCK)
             || stack.is(Items.ANCIENT_DEBRIS))) return true;
        if (elytra.get() && stack.is(Items.ELYTRA)) return true;
        if (beacon.get() && stack.is(Items.BEACON)) return true;
        if (netherStar.get() && stack.is(Items.NETHER_STAR)) return true;
        if (dragonEgg.get() && stack.is(Items.DRAGON_EGG)) return true;
        if (heartOfSea.get() && stack.is(Items.HEART_OF_THE_SEA)) return true;
        if (trident.get() && stack.is(Items.TRIDENT)) return true;
        if (shulkerShell.get() && stack.is(Items.SHULKER_SHELL)) return true;
        if (enchantedBook.get() && stack.is(Items.ENCHANTED_BOOK)) return true;
        return false;
    }

    private static ItemGlowModule find() {
        for (HudModule m : HudManager.getModules()) {
            if (m instanceof ItemGlowModule ig) return ig;
        }
        return null;
    }
}
