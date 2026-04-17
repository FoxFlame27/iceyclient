package com.iceymod.hud;

import com.iceymod.hud.modules.*;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class HudManager {
    private static final List<HudModule> modules = new ArrayList<>();
    private static Path configPath;
    private static boolean positionsClamped = false;

    public static void init() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("iceymod.json");

        // Core modules
        modules.add(new FpsModule());
        modules.add(new PingModule());
        modules.add(new CoordsModule());
        modules.add(new CpsModule());
        modules.add(new KeystrokesModule());
        modules.add(new ArmorModule());
        // Info
        modules.add(new HitboxModule());
        modules.add(new DirectionModule());
        modules.add(new DayCounterModule());
        modules.add(new ServerModule());
        modules.add(new PotionModule());
        modules.add(new MemoryModule());
        modules.add(new SaturationModule());
        modules.add(new ComboCounterModule());
        modules.add(new TimeModule());
        modules.add(new BiomeModule());
        modules.add(new BlockAboveModule());
        modules.add(new CrosshairModule());
        modules.add(new HotbarTextModule());
        modules.add(new YLevelModule());
        modules.add(new PitchYawModule());
        modules.add(new VelocityModule());
        modules.add(new SessionTimeModule());
        modules.add(new AirModule());
        modules.add(new WorldTimeModule());
        modules.add(new EntityCountModule());
        modules.add(new ArrowCountModule());
        // New HUD info modules
        modules.add(new WeatherModule());
        modules.add(new TpsModule());
        modules.add(new BlocksMinedModule());
        modules.add(new DistanceWalkedModule());
        modules.add(new InventorySlotsModule());
        // New combat modules (mace PvP / crystal PvP / general PvP)
        modules.add(new CrystalTrackerModule());
        modules.add(new TotemPopsModule());
        modules.add(new TargetHealthModule());
        modules.add(new MaceDamageModule());
        modules.add(new GappleCountModule());
        modules.add(new EnderPearlCountModule());
        modules.add(new CrystalCpsModule());
        modules.add(new AttackCooldownModule());
        modules.add(new ElytraDurabilityModule());
        modules.add(new TotemCountModule());
        modules.add(new ObsidianCountModule());
        modules.add(new KillStreakModule());
        modules.add(new DeathCounterModule());
        modules.add(new LastDamageModule());
        modules.add(new NearestPlayerModule());
        modules.add(new ItemCooldownModule());
        modules.add(new FallDistanceModule());
        // New useful replacements
        modules.add(new OffhandItemModule());
        modules.add(new ToolBreakWarnModule());
        modules.add(new HostileMobsModule());
        modules.add(new NearestHostileModule());
        modules.add(new LastDeathModule());
        modules.add(new BedCoordsModule());
        modules.add(new ChunkModule());
        modules.add(new LookingAtModule());
        modules.add(new MobKillsModule());
        modules.add(new NetherCoordsModule());
        // Quality-of-life features
        modules.add(new ZoomModule());
        modules.add(new PerspectiveModule());
        modules.add(new WaypointsModule());
        // Mace-focused combat modules + small cheats
        modules.add(new WindBurstCooldownModule());
        modules.add(new MaceEnchantsModule());
        modules.add(new SmashKillsModule());
        modules.add(new PredictedFallModule());
        modules.add(new FallImmunityWarnModule());
        modules.add(new WindChargeCountModule());
        modules.add(new HeavyCoreCountModule());
        modules.add(new AutoMaceSwapModule());
        modules.add(new AutoRespawnModule());
        modules.add(new NoHurtCamModule());
        // Optimization modules (invisible, enabled by default)
        modules.add(new FpsBoostParticlesModule());
        modules.add(new FpsBoostCloudsModule());
        modules.add(new FpsBoostShadowsModule());
        modules.add(new FpsBoostGraphicsModule());
        modules.add(new NetDnsCacheModule());
        modules.add(new NetIpv4Module());
        modules.add(new NetNoDelayModule());
        modules.add(new NetBufferModule());

        load();
        applyCenterDefaults();
    }

    /**
     * Place modules in a clean stack near screen center on first launch (no saved config).
     * Also fixes any saved positions that are off-screen.
     */
    private static void applyCenterDefaults() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        if (sw <= 0 || sh <= 0) return;

        boolean configExists = Files.exists(configPath);
        int colWidth = 120;
        int rowHeight = 16;
        int perColumn = Math.max(1, (sh - 40) / rowHeight);
        int startX = sw / 2 - colWidth;
        int startY = sh / 2 - (perColumn * rowHeight) / 2;

        int visibleIdx = 0;
        for (HudModule m : modules) {
            if (m.getCategory() == HudModule.Category.OPTIMIZATION) continue;
            int col = visibleIdx / perColumn;
            int row = visibleIdx % perColumn;
            int defaultX = startX + col * colWidth;
            int defaultY = startY + row * rowHeight;

            if (!configExists) {
                m.setX(defaultX);
                m.setY(defaultY);
            } else {
                if (m.getX() < 0 || m.getX() > sw - 20) m.setX(defaultX);
                if (m.getY() < 0 || m.getY() > sh - 10) m.setY(defaultY);
            }
            visibleIdx++;
        }
        positionsClamped = true;
    }

    public static List<HudModule> getModules() {
        return modules;
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!positionsClamped) applyCenterDefaults();
        for (HudModule module : modules) {
            if (module.isEnabled()) {
                module.render(context, client);
            }
        }
    }

    public static void tick() {
        for (HudModule module : modules) {
            if (module.isEnabled()) {
                module.tick();
            }
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        JsonObject modulesObj = new JsonObject();
        for (HudModule module : modules) {
            JsonObject m = new JsonObject();
            m.addProperty("enabled", module.isEnabled());
            m.addProperty("x", module.getX());
            m.addProperty("y", module.getY());
            modulesObj.add(module.getId(), m);
        }
        root.add("modules", modulesObj);

        try {
            Files.writeString(configPath, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        if (!Files.exists(configPath)) return;
        try {
            String json = Files.readString(configPath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject modulesObj = root.getAsJsonObject("modules");
            if (modulesObj == null) return;

            for (HudModule module : modules) {
                JsonObject m = modulesObj.getAsJsonObject(module.getId());
                if (m != null) {
                    if (m.has("enabled")) module.setEnabled(m.get("enabled").getAsBoolean());
                    if (m.has("x")) module.setX(m.get("x").getAsInt());
                    if (m.has("y")) module.setY(m.get("y").getAsInt());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
