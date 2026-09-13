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
    public static Camera camera(Minecraft mc) { return mc.gameRenderer.mainCamera(); }
    public static long dayTime(Level level) { return level.getDefaultClockTime(); }
    public static void forceFastGraphics(Minecraft mc) {
        if (mc.options.graphicsPreset().get() != GraphicsPreset.FAST) mc.options.graphicsPreset().set(GraphicsPreset.FAST);
    }
    public static long chunkKey(ChunkPos pos) { return pos.pack(); }
    public static void onEndLevelTick(Consumer<ClientLevel> c) { ClientTickEvents.END_LEVEL_TICK.register(c::accept); }
}
