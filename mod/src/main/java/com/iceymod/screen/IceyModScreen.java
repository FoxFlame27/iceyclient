package com.iceymod.screen;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The main Icey Client menu, opened with Y.
 * Styled like a vanilla Minecraft options screen — native buttons, dark overlay.
 */
public class IceyModScreen extends Screen {

    public IceyModScreen() {
        super(Text.literal("Icey Client"));
    }

    @Override
    protected void init() {
        List<HudModule> modules = HudManager.getModules();
        int centerX = this.width / 2;
        int cols = 3;
        int btnW = 100;
        int btnH = 20;
        int gap = 3;
        int gridW = cols * btnW + (cols - 1) * gap;
        int startX = centerX - gridW / 2;
        int startY = 36;

        // Three-column grid of module toggle buttons (vanilla ButtonWidget style)
        for (int i = 0; i < modules.size(); i++) {
            HudModule module = modules.get(i);
            int col = i % cols;
            int row = i / cols;
            int x = startX + col * (btnW + gap);
            int y = startY + row * (btnH + gap);

            addDrawableChild(ButtonWidget.builder(
                    getModuleText(module),
                    btn -> {
                        module.toggle();
                        btn.setMessage(getModuleText(module));
                    }
            ).dimensions(x, y, btnW, btnH).build());
        }

        // "Edit HUD" button — opens the drag editor
        int rows = (modules.size() + cols - 1) / cols;
        int bottomY = startY + rows * (btnH + gap) + 12;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u2699 Edit HUD Layout"),
                btn -> client.setScreen(new HudEditScreen(this))
        ).dimensions(centerX - 100, bottomY, 200, 20).build());

        // "Done" button
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.done"),
                btn -> close()
        ).dimensions(centerX - 100, bottomY + 24, 200, 20).build());
    }

    private Text getModuleText(HudModule module) {
        String state = module.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
        return Text.literal(module.getName() + ": " + state);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dark semi-transparent background
        this.renderBackground(context, mouseX, mouseY, delta);

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer,
                "\u00A7b\u00A7lIcey Client", this.width / 2, 10, 0xFFFFFFFF);

        // Subtitle
        context.drawCenteredTextWithShadow(this.textRenderer,
                "\u00A77Toggle modules and customize your HUD", this.width / 2, 22, 0xFFFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        HudManager.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false; // don't pause the game
    }
}
