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

    public static void init() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("iceymod.json");

        // Core modules (enabled by default)
        modules.add(new FpsModule());
        modules.add(new PingModule());
        modules.add(new CoordsModule());
        modules.add(new CpsModule());
        modules.add(new KeystrokesModule());
        modules.add(new ArmorModule());
        // Extra modules (disabled by default, user toggles on)
        modules.add(new HitboxModule());
        modules.add(new DirectionModule());
        modules.add(new DayCounterModule());
        modules.add(new ServerModule());
        modules.add(new ReachModule());
        modules.add(new PotionModule());

        load();
    }

    public static List<HudModule> getModules() {
        return modules;
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
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
