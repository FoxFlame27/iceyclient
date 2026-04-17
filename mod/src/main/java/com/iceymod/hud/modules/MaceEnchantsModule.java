package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

public class MaceEnchantsModule extends HudModule {
    public MaceEnchantsModule() {
        super("maceenchants", "Mace Enchants", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        ItemStack s = client.player.getMainHandStack();
        if (!s.isOf(Items.MACE)) return "\u00A78No mace";
        ItemEnchantmentsComponent ench = s.getEnchantments();
        int density = 0, breach = 0, wind = 0;
        for (RegistryEntry<Enchantment> entry : ench.getEnchantments()) {
            java.util.Optional<RegistryKey<Enchantment>> key = entry.getKey();
            if (!key.isPresent()) continue;
            String path = key.get().getValue().getPath();
            int level = ench.getLevel(entry);
            switch (path) {
                case "density" -> density = level;
                case "breach" -> breach = level;
                case "wind_burst" -> wind = level;
                default -> { /* ignore */ }
            }
        }
        StringBuilder sb = new StringBuilder("\u00A7d");
        if (density > 0) sb.append("D").append(density).append(" ");
        if (breach > 0) sb.append("\u00A7eB").append(breach).append(" ");
        if (wind > 0) sb.append("\u00A7bW").append(wind);
        if (sb.length() <= 2) return "\u00A78No enchants";
        return sb.toString().trim();
    }
}
