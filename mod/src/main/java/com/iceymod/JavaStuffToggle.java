package com.iceymod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * U key: switch the Java &amp; Stuff pack off/on.
 *
 * Resource packs can be toggled while the game runs, so those go off
 * immediately (and the game reloads resources). Mods can't be unloaded at
 * runtime, so for them we write config/iceymod-request.json, which the
 * launcher applies to its settings on the next launch. The launcher tells
 * us what's on and which pack entries belong to Java &amp; Stuff through
 * config/iceymod-launcher.json.
 */
public final class JavaStuffToggle {

    private static final String STATUS_FILE = "iceymod-launcher.json";
    private static final String REQUEST_FILE = "iceymod-request.json";
    private static final String STATE_FILE = "iceymod-javastuff-state.json";

    private JavaStuffToggle() {}

    /** True when launched by Icey Client with the pack turned on (or off but present). */
    public static boolean isAvailable() {
        return readStatus() != null;
    }

    public static void toggle(MinecraftClient client) {
        JsonObject status = readStatus();
        if (status == null) {
            say(client, "§cJava & Stuff isn't managed here (launch from Icey Client).");
            return;
        }
        boolean launcherOn = status.has("javaStuffEnabled") && status.get("javaStuffEnabled").getAsBoolean();
        if (!launcherOn) {
            say(client, "§7Java & Stuff is off. Turn it on in Icey Client → Settings.");
            return;
        }
        List<String> packEntries = new ArrayList<>();
        if (status.has("javaStuffPacks") && status.get("javaStuffPacks").isJsonArray()) {
            for (JsonElement e : status.getAsJsonArray("javaStuffPacks")) packEntries.add(e.getAsString());
        }

        boolean visualsOff = readVisualsOff();
        boolean nextOff = !visualsOff;
        try {
            setPacksEnabled(client, packEntries, !nextOff);
        } catch (Throwable t) {
            System.out.println("[IceyMod] resource pack toggle failed: " + t);
        }
        writeVisualsOff(nextOff);
        writeRequest(!nextOff);
        if (nextOff) {
            say(client, "§bJava & Stuff §cOFF§r: resource packs disabled now, its mods are removed next time you launch from Icey Client.");
        } else {
            say(client, "§bJava & Stuff §aON§r: resource packs re-enabled, mods come back on next launch.");
        }
    }

    private static void setPacksEnabled(MinecraftClient client, List<String> entries, boolean enable) {
        ResourcePackManager manager = client.getResourcePackManager();
        manager.scanPacks();
        Set<String> enabled = new LinkedHashSet<>(manager.getEnabledIds());
        Set<String> targets = new LinkedHashSet<>(entries);
        if (enable) {
            // Re-add in the pack's order, after whatever is already on.
            for (String id : entries) if (manager.getProfile(id) != null) enabled.add(id);
        } else {
            enabled.removeIf(targets::contains);
        }
        manager.setEnabledProfiles(enabled);
        client.options.refreshResourcePacks(manager);
    }

    private static void say(MinecraftClient client, String msg) {
        try {
            if (client.player != null) client.player.sendMessage(Text.literal(msg), false);
            else System.out.println("[IceyMod] " + msg);
        } catch (Throwable ignored) {}
    }

    private static Path configPath(String name) {
        return FabricLoader.getInstance().getConfigDir().resolve(name);
    }

    private static JsonObject readStatus() {
        try {
            Path p = configPath(STATUS_FILE);
            if (!Files.exists(p)) return null;
            return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Throwable t) { return null; }
    }

    private static boolean readVisualsOff() {
        try {
            Path p = configPath(STATE_FILE);
            if (!Files.exists(p)) return false;
            JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has("visualsOff") && o.get("visualsOff").getAsBoolean();
        } catch (Throwable t) { return false; }
    }

    private static void writeVisualsOff(boolean off) {
        try {
            Files.createDirectories(configPath(STATE_FILE).getParent());
            Files.writeString(configPath(STATE_FILE), "{\n  \"visualsOff\": " + off + "\n}\n", StandardCharsets.UTF_8);
        } catch (Throwable ignored) {}
    }

    private static void writeRequest(boolean enabled) {
        try {
            Files.createDirectories(configPath(REQUEST_FILE).getParent());
            Files.writeString(configPath(REQUEST_FILE), "{\n  \"javaStuffEnabled\": " + enabled + "\n}\n", StandardCharsets.UTF_8);
        } catch (Throwable ignored) {}
    }
}
