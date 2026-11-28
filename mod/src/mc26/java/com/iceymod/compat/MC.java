package com.iceymod.compat;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** 26.x: small accessors for client state whose home moved between versions. */
public final class MC {
    private MC() {}
    public static Screen screen(Minecraft mc) { return mc.gui.screen(); }
    public static void setScreen(Minecraft mc, Screen s) { mc.gui.setScreen(s); }
    /** One of the game's own toasts (the boxes that slide in at the top right), so a notice looks like Minecraft and follows the resource pack. */
    public static void toast(Minecraft mc, net.minecraft.network.chat.Component title, net.minecraft.network.chat.Component text) {
        net.minecraft.client.gui.components.toasts.SystemToast.add(mc.gui.toastManager(),
                net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, text);
    }
    public static Camera camera(Minecraft mc) { return mc.gameRenderer.mainCamera(); }
    public static long dayTime(Level level) { return level.getDefaultClockTime(); }
    /**
     * "FPS: Fast Graphics". Up to 1.21.10 Fast is one setting and is simply
     * held. From 1.21.11 it is a preset: a bundle that also puts render
     * distance at 8 and simulation distance at 6, and that flips to Custom
     * the moment the player changes any part of it. Holding the preset every
     * tick therefore threw the player's render distance back to 8 each time
     * they raised it. On those versions the preset is applied once per game
     * start, and both distances are put back to what the player had.
     */
    private static boolean fastPresetApplied;
    public static void forceFastGraphics(Minecraft mc) {
        boolean preset = GraphicsPreset.values().length > 3;          // has CUSTOM: the preset kind
        if (mc.options.graphicsPreset().get() == GraphicsPreset.FAST) { fastPresetApplied = true; return; }
        if (preset && fastPresetApplied) return;
        fastPresetApplied = true;
        int render = mc.options.renderDistance().get();
        int simulation = mc.options.simulationDistance().get();
        mc.options.graphicsPreset().set(GraphicsPreset.FAST);
        if (preset) {
            if (mc.options.renderDistance().get() != render) mc.options.renderDistance().set(render);
            if (mc.options.simulationDistance().get() != simulation) mc.options.simulationDistance().set(simulation);
        }
    }
    public static long chunkKey(ChunkPos pos) { return pos.pack(); }
    public static void onEndLevelTick(Consumer<ClientLevel> c) { ClientTickEvents.END_LEVEL_TICK.register(c::accept); }
}
