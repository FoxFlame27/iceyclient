package com.iceymod;

import com.iceymod.hud.HudManager;
import com.iceymod.screen.IceyModScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class IceyMod implements ClientModInitializer {
    public static final String MOD_ID = "iceymod";
    public static final Identifier LOGO_TEXTURE = Identifier.of(MOD_ID, "textures/gui/logo.png");

    private static KeyBinding menuKeyBinding;

    @Override
    public void onInitializeClient() {
        HudManager.init();

        menuKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.iceymod.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                KeyBinding.MISC_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKeyBinding.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new IceyModScreen());
                }
            }
            HudManager.tick();
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && (client.currentScreen == null
                    || client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) {
                HudManager.render(drawContext);
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // Title screen: logo in bottom-left corner, no text
            if (screen instanceof TitleScreen) {
                ScreenEvents.afterRender(screen).register((scr, ctx, mouseX, mouseY, delta) -> {
                    int sh = client.getWindow().getScaledHeight();
                    int logoW = 200;
                    int logoH = 40;
                    int x = 8;
                    int y = sh - logoH - 8;
                    ctx.drawTexturedQuad(LOGO_TEXTURE, x, x + logoW, y, y + logoH, 0, 0, 1, 1);
                });
            }

            // Inventory, crafting table, and all container screens: logo in top-left
            if (screen instanceof HandledScreen) {
                ScreenEvents.afterRender(screen).register((scr, ctx, mouseX, mouseY, delta) -> {
                    int logoW = 140;
                    int logoH = 28;
                    ctx.drawTexturedQuad(LOGO_TEXTURE, 4, 4 + logoW, 4, 4 + logoH, 0, 0, 1, 1);
                });
            }
        });
    }
}
