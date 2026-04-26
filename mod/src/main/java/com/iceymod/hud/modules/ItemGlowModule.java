package com.iceymod.hud.modules;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

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
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(DrawContext context, MinecraftClient client) {}

    /**
     * Called from EntityIsGlowingMixin to decide whether a given dropped
     * ItemEntity should appear glowing client-side.
     */
    public static boolean shouldGlow(ItemEntity entity) {
        if (entity == null) return false;
        ItemGlowModule mod = find();
        if (mod == null || !mod.isEnabled()) return false;
        return mod.shouldGlow(entity.getStack());
    }

    public boolean shouldGlow(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (mace.get() && stack.isOf(Items.MACE)) return true;
        if (totem.get() && stack.isOf(Items.TOTEM_OF_UNDYING)) return true;
        if (netherite.get() && (
                stack.isOf(Items.NETHERITE_INGOT)
             || stack.isOf(Items.NETHERITE_SCRAP)
             || stack.isOf(Items.NETHERITE_SWORD)
             || stack.isOf(Items.NETHERITE_AXE)
             || stack.isOf(Items.NETHERITE_PICKAXE)
             || stack.isOf(Items.NETHERITE_SHOVEL)
             || stack.isOf(Items.NETHERITE_HOE)
             || stack.isOf(Items.NETHERITE_HELMET)
             || stack.isOf(Items.NETHERITE_CHESTPLATE)
             || stack.isOf(Items.NETHERITE_LEGGINGS)
             || stack.isOf(Items.NETHERITE_BOOTS))) return true;
        if (netheriteBlocks.get() && (
                stack.isOf(Items.NETHERITE_BLOCK)
             || stack.isOf(Items.ANCIENT_DEBRIS))) return true;
        if (elytra.get() && stack.isOf(Items.ELYTRA)) return true;
        if (beacon.get() && stack.isOf(Items.BEACON)) return true;
        if (netherStar.get() && stack.isOf(Items.NETHER_STAR)) return true;
        if (dragonEgg.get() && stack.isOf(Items.DRAGON_EGG)) return true;
        if (heartOfSea.get() && stack.isOf(Items.HEART_OF_THE_SEA)) return true;
        if (trident.get() && stack.isOf(Items.TRIDENT)) return true;
        if (shulkerShell.get() && stack.isOf(Items.SHULKER_SHELL)) return true;
        if (enchantedBook.get() && stack.isOf(Items.ENCHANTED_BOOK)) return true;
        return false;
    }

    private static ItemGlowModule find() {
        for (HudModule m : HudManager.getModules()) {
            if (m instanceof ItemGlowModule ig) return ig;
        }
        return null;
    }
}
