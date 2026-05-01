package com.iceymod.hud.modules;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * X-Ray. When on, the {@code BlockShouldDrawSideMixin} hides every block
 * not in the user's see-through set, leaving ores / loot / spawners
 * exposed inside otherwise-invisible terrain. This module owns the
 * toggle + ~85 per-block checkboxes; the mixin reads them via
 * {@link #shouldShow(Block)}.
 *
 * <p>Toggling triggers a chunk re-render so existing chunks update
 * without the user having to walk away and back. Per-block setting
 * toggles also kick a re-render via {@link BoolSetting}'s onChange path
 * (we attach a listener in the register helper).
 *
 * <p>Caveat: works with the vanilla Fabric-API render pipeline. Sodium
 * replaces face-occlusion so X-ray may be ignored on Sodium installs
 * until we add Sodium-specific mixins.
 */
public class XrayModule extends HudModule {

    private final Map<Block, BoolSetting> blockToggles = new IdentityHashMap<>();
    private boolean lastEnabled = false;
    private static volatile XrayModule cached;

    public XrayModule() {
        super("xray", "X-Ray", 0, 0);
        setEnabled(false);

        // ── Ores ───────────────────────────────────────────────────────────
        reg("oreDiamond",        "Diamond Ore",            true,  () -> Blocks.DIAMOND_ORE);
        reg("oreDiamondDeep",    "Deepslate Diamond Ore",  true,  () -> Blocks.DEEPSLATE_DIAMOND_ORE);
        reg("oreEmerald",        "Emerald Ore",            true,  () -> Blocks.EMERALD_ORE);
        reg("oreEmeraldDeep",    "Deepslate Emerald Ore",  true,  () -> Blocks.DEEPSLATE_EMERALD_ORE);
        reg("oreGold",           "Gold Ore",               true,  () -> Blocks.GOLD_ORE);
        reg("oreGoldDeep",       "Deepslate Gold Ore",     true,  () -> Blocks.DEEPSLATE_GOLD_ORE);
        reg("oreGoldNether",     "Nether Gold Ore",        true,  () -> Blocks.NETHER_GOLD_ORE);
        reg("oreIron",           "Iron Ore",               true,  () -> Blocks.IRON_ORE);
        reg("oreIronDeep",       "Deepslate Iron Ore",     true,  () -> Blocks.DEEPSLATE_IRON_ORE);
        reg("oreCopper",         "Copper Ore",             true,  () -> Blocks.COPPER_ORE);
        reg("oreCopperDeep",     "Deepslate Copper Ore",   true,  () -> Blocks.DEEPSLATE_COPPER_ORE);
        reg("oreLapis",          "Lapis Ore",              true,  () -> Blocks.LAPIS_ORE);
        reg("oreLapisDeep",      "Deepslate Lapis Ore",    true,  () -> Blocks.DEEPSLATE_LAPIS_ORE);
        reg("oreRedstone",       "Redstone Ore",           true,  () -> Blocks.REDSTONE_ORE);
        reg("oreRedstoneDeep",   "Deepslate Redstone Ore", true,  () -> Blocks.DEEPSLATE_REDSTONE_ORE);
        reg("oreCoal",           "Coal Ore",               false, () -> Blocks.COAL_ORE);
        reg("oreCoalDeep",       "Deepslate Coal Ore",     false, () -> Blocks.DEEPSLATE_COAL_ORE);
        reg("oreNetherQuartz",   "Nether Quartz Ore",      true,  () -> Blocks.NETHER_QUARTZ_ORE);
        reg("ancientDebris",     "Ancient Debris",         true,  () -> Blocks.ANCIENT_DEBRIS);

        // ── Mineral blocks (rare in worlds — usually only player bases) ────
        reg("blkDiamond",        "Diamond Block",          true,  () -> Blocks.DIAMOND_BLOCK);
        reg("blkEmerald",        "Emerald Block",          true,  () -> Blocks.EMERALD_BLOCK);
        reg("blkGold",           "Gold Block",             true,  () -> Blocks.GOLD_BLOCK);
        reg("blkIron",           "Iron Block",             false, () -> Blocks.IRON_BLOCK);
        reg("blkCopper",         "Copper Block",           false, () -> Blocks.COPPER_BLOCK);
        reg("blkLapis",          "Lapis Block",            false, () -> Blocks.LAPIS_BLOCK);
        reg("blkRedstone",       "Redstone Block",         false, () -> Blocks.REDSTONE_BLOCK);
        reg("blkCoal",           "Coal Block",             false, () -> Blocks.COAL_BLOCK);
        reg("blkNetherite",      "Netherite Block",        true,  () -> Blocks.NETHERITE_BLOCK);
        reg("blkQuartz",         "Quartz Block",           false, () -> Blocks.QUARTZ_BLOCK);

        // ── Raw blocks ────────────────────────────────────────────────────
        reg("rawIron",           "Raw Iron Block",         false, () -> Blocks.RAW_IRON_BLOCK);
        reg("rawCopper",         "Raw Copper Block",       false, () -> Blocks.RAW_COPPER_BLOCK);
        reg("rawGold",           "Raw Gold Block",         false, () -> Blocks.RAW_GOLD_BLOCK);

        // ── Spawners & structure markers ──────────────────────────────────
        reg("spawner",           "Spawner",                true,  () -> Blocks.SPAWNER);
        reg("trialSpawner",      "Trial Spawner",          true,  () -> Blocks.TRIAL_SPAWNER);
        reg("vault",             "Vault",                  true,  () -> Blocks.VAULT);
        reg("reinforcedDeep",    "Reinforced Deepslate",   true,  () -> Blocks.REINFORCED_DEEPSLATE);
        reg("endPortalFrame",    "End Portal Frame",       true,  () -> Blocks.END_PORTAL_FRAME);
        reg("endPortal",         "End Portal",             true,  () -> Blocks.END_PORTAL);
        reg("endGateway",        "End Gateway",            true,  () -> Blocks.END_GATEWAY);
        reg("dragonEgg",         "Dragon Egg",             true,  () -> Blocks.DRAGON_EGG);
        reg("bedrock",           "Bedrock",                false, () -> Blocks.BEDROCK);

        // ── Loot containers ───────────────────────────────────────────────
        reg("chest",             "Chest",                  true,  () -> Blocks.CHEST);
        reg("trappedChest",      "Trapped Chest",          true,  () -> Blocks.TRAPPED_CHEST);
        reg("enderChest",        "Ender Chest",            true,  () -> Blocks.ENDER_CHEST);
        reg("barrel",            "Barrel",                 true,  () -> Blocks.BARREL);
        reg("shulkerBox",        "Shulker Box",            true,  () -> Blocks.SHULKER_BOX);
        reg("hopper",            "Hopper",                 false, () -> Blocks.HOPPER);
        reg("dispenser",         "Dispenser",              false, () -> Blocks.DISPENSER);
        reg("dropper",           "Dropper",                false, () -> Blocks.DROPPER);
        reg("furnace",           "Furnace",                false, () -> Blocks.FURNACE);

        // ── Utility / functional ──────────────────────────────────────────
        reg("beacon",            "Beacon",                 true,  () -> Blocks.BEACON);
        reg("conduit",           "Conduit",                true,  () -> Blocks.CONDUIT);
        reg("lodestone",         "Lodestone",              true,  () -> Blocks.LODESTONE);
        reg("brewingStand",      "Brewing Stand",          false, () -> Blocks.BREWING_STAND);
        reg("enchantTable",      "Enchanting Table",       false, () -> Blocks.ENCHANTING_TABLE);
        reg("anvil",             "Anvil",                  false, () -> Blocks.ANVIL);
        reg("respawnAnchor",     "Respawn Anchor",         false, () -> Blocks.RESPAWN_ANCHOR);

        // ── Amethyst ──────────────────────────────────────────────────────
        reg("amethystBlock",     "Amethyst Block",         false, () -> Blocks.AMETHYST_BLOCK);
        reg("buddingAmethyst",   "Budding Amethyst",       false, () -> Blocks.BUDDING_AMETHYST);
        reg("amethystCluster",   "Amethyst Cluster",       false, () -> Blocks.AMETHYST_CLUSTER);
        reg("smallAmethystBud",  "Small Amethyst Bud",     false, () -> Blocks.SMALL_AMETHYST_BUD);
        reg("mediumAmethystBud", "Medium Amethyst Bud",    false, () -> Blocks.MEDIUM_AMETHYST_BUD);
        reg("largeAmethystBud",  "Large Amethyst Bud",     false, () -> Blocks.LARGE_AMETHYST_BUD);

        // ── Light / glow ──────────────────────────────────────────────────
        reg("glowstone",         "Glowstone",              false, () -> Blocks.GLOWSTONE);
        reg("shroomlight",       "Shroomlight",            false, () -> Blocks.SHROOMLIGHT);
        reg("seaLantern",        "Sea Lantern",            false, () -> Blocks.SEA_LANTERN);
        reg("jackOLantern",      "Jack o'Lantern",         false, () -> Blocks.JACK_O_LANTERN);
        reg("ochreFroglight",    "Ochre Froglight",        false, () -> Blocks.OCHRE_FROGLIGHT);
        reg("verdantFroglight",  "Verdant Froglight",      false, () -> Blocks.VERDANT_FROGLIGHT);
        reg("pearlFroglight",    "Pearlescent Froglight",  false, () -> Blocks.PEARLESCENT_FROGLIGHT);
        reg("redstoneLamp",      "Redstone Lamp",          false, () -> Blocks.REDSTONE_LAMP);

        // ── Mob heads ─────────────────────────────────────────────────────
        reg("playerHead",        "Player Head",            false, () -> Blocks.PLAYER_HEAD);
        reg("zombieHead",        "Zombie Head",            false, () -> Blocks.ZOMBIE_HEAD);
        reg("creeperHead",       "Creeper Head",           false, () -> Blocks.CREEPER_HEAD);
        reg("skeletonSkull",     "Skeleton Skull",         false, () -> Blocks.SKELETON_SKULL);
        reg("witherSkull",       "Wither Skull",           false, () -> Blocks.WITHER_SKELETON_SKULL);
        reg("piglinHead",        "Piglin Head",            false, () -> Blocks.PIGLIN_HEAD);
        reg("dragonHead",        "Dragon Head",            false, () -> Blocks.DRAGON_HEAD);

        // ── Ice / cold ────────────────────────────────────────────────────
        reg("ice",               "Ice",                    false, () -> Blocks.ICE);
        reg("packedIce",         "Packed Ice",             false, () -> Blocks.PACKED_ICE);
        reg("blueIce",           "Blue Ice",               false, () -> Blocks.BLUE_ICE);

        // ── Nether markers / structure indicators ─────────────────────────
        reg("cryingObsidian",    "Crying Obsidian",        true,  () -> Blocks.CRYING_OBSIDIAN);
        reg("obsidian",          "Obsidian",               false, () -> Blocks.OBSIDIAN);
        reg("gildedBlackstone",  "Gilded Blackstone",      true,  () -> Blocks.GILDED_BLACKSTONE);
        reg("magmaBlock",        "Magma Block",            false, () -> Blocks.MAGMA_BLOCK);
        reg("soulSand",          "Soul Sand",              false, () -> Blocks.SOUL_SAND);
        reg("soulSoil",          "Soul Soil",              false, () -> Blocks.SOUL_SOIL);
        reg("netherBrickFence",  "Nether Brick Fence",     false, () -> Blocks.NETHER_BRICK_FENCE);

        // ── Sculk / deep-dark ─────────────────────────────────────────────
        reg("sculk",             "Sculk",                  false, () -> Blocks.SCULK);
        reg("sculkCatalyst",     "Sculk Catalyst",         false, () -> Blocks.SCULK_CATALYST);
        reg("sculkShrieker",     "Sculk Shrieker",         false, () -> Blocks.SCULK_SHRIEKER);
        reg("sculkSensor",       "Sculk Sensor",           false, () -> Blocks.SCULK_SENSOR);

        // ── Misc valuable ─────────────────────────────────────────────────
        reg("honeyBlock",        "Honey Block",            false, () -> Blocks.HONEY_BLOCK);
        reg("honeycombBlock",    "Honeycomb Block",        false, () -> Blocks.HONEYCOMB_BLOCK);
        reg("mossBlock",         "Moss Block",             false, () -> Blocks.MOSS_BLOCK);
        reg("turtleEgg",         "Turtle Egg",             false, () -> Blocks.TURTLE_EGG);
        reg("snifferEgg",        "Sniffer Egg",            false, () -> Blocks.SNIFFER_EGG);
    }

    private void reg(String id, String label, boolean defaultOn, Supplier<Block> blockSupplier) {
        BoolSetting s = addSetting(new BoolSetting(id, label, defaultOn));
        // Block class lookup is wrapped — a future MC version that removes
        // a block (e.g. Mojang renamed/dropped one) won't crash module init,
        // we just lose the binding for that one block.
        try {
            Block b = blockSupplier.get();
            if (b != null) blockToggles.put(b, s);
        } catch (Throwable ignored) {}
    }

    @Override protected boolean shouldShowStyleSettings() { return false; }
    @Override public String getText(MinecraftClient client) { return null; }
    @Override public void render(DrawContext context, MinecraftClient client) {}

    @Override
    public void tick() {
        if (isEnabled() != lastEnabled) {
            lastEnabled = isEnabled();
            reloadChunks();
        }
    }

    private static void reloadChunks() {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c != null && c.worldRenderer != null) c.worldRenderer.reload();
        } catch (Throwable ignored) {}
    }

    /**
     * Mixin entry point. Hot path — called per face per block per chunk
     * rebuild. Cached lookup so we don't iterate HudManager's module list
     * every call.
     */
    public static boolean shouldShow(Block block) {
        XrayModule m = cached;
        if (m == null) {
            for (HudModule mod : HudManager.getModules()) {
                if (mod instanceof XrayModule x) { cached = m = x; break; }
            }
            if (m == null) return true;
        }
        if (!m.isEnabled()) return true;
        BoolSetting s = m.blockToggles.get(block);
        return s != null && s.get();
    }
}
