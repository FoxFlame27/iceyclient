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
                KeyBinding.Category.MISC
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
            if (screen instanceof TitleScreen) {
                ScreenEvents.afterRender(screen).register((scr, ctx, mouseX, mouseY, delta) -> {
                    int sw = client.getWindow().getScaledWidth();
                    // Use drawTexturedQuad for simple full-texture rendering
                    int logoW = 160;
                    int logoH = 32;
                    int x = (sw - logoW) / 2;
                    int y = 6;
                    ctx.drawTexturedQuad(LOGO_TEXTURE, x, x + logoW, y, y + logoH, 0, 0, 1, 1);
                    String text = "\u00A7b\u00A7lIcey Client \u00A77v1.0";
                    int textW = client.textRenderer.getWidth(text);
                    ctx.drawTextWithShadow(client.textRenderer, text, (sw - textW) / 2, y + logoH + 2, 0xFFFFFFFF);
                });
            }
            if (screen instanceof HandledScreen) {
                ScreenEvents.afterRender(screen).register((scr, ctx, mouseX, mouseY, delta) -> {
                    ctx.drawTextWithShadow(client.textRenderer, "\u00A7b\u00A7lIcey Client", 4, 4, 0xFFFFFFFF);
                });
            }
        });
    }
}
