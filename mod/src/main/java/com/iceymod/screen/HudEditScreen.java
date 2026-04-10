package com.iceymod.screen;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class HudEditScreen extends Screen {
    private final Screen parent;
    private HudModule dragging = null;
    private int dragOffsetX, dragOffsetY;

    public HudEditScreen(Screen parent) {
        super(Text.literal("Edit HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.done"),
                btn -> close()
        ).dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x80000000);

        context.drawCenteredTextWithShadow(textRenderer,
                "\u00A7b\u00A7lDrag modules to reposition", this.width / 2, 8, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                "\u00A77Click and drag any module below", this.width / 2, 20, 0xFFFFFFFF);

        for (HudModule module : HudManager.getModules()) {
            if (!module.isEnabled()) continue;

            module.render(context, client);

            int x = module.getX() - 2;
            int y = module.getY() - 2;
            int w = module.getWidth() + 4;
            int h = module.getHeight() + 4;

            boolean isHovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
            boolean isDragged = module == dragging;
            int borderColor = isDragged ? 0xFF5BC8F5 : isHovered ? 0xAAFFFFFF : 0x60FFFFFF;

            context.fill(x, y, x + w, y + 1, borderColor);
            context.fill(x, y + h - 1, x + w, y + h, borderColor);
            context.fill(x, y, x + 1, y + h, borderColor);
            context.fill(x + w - 1, y, x + w, y + h, borderColor);

            int labelColor = isDragged ? 0xFF5BC8F5 : 0xFFAAAAAA;
            context.drawTextWithShadow(textRenderer, module.getName(), x + 2, y - 11, labelColor);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    // 1.21.11: mouseClicked uses Click object
    @Override
    public boolean mouseClicked(Click click, boolean forward) {
        int mx = (int) click.x();
        int my = (int) click.y();
        var modules = HudManager.getModules();
        for (int i = modules.size() - 1; i >= 0; i--) {
            HudModule module = modules.get(i);
            if (!module.isEnabled()) continue;
            if (mx >= module.getX() - 2 && mx <= module.getX() + module.getWidth() + 2
                    && my >= module.getY() - 2 && my <= module.getY() + module.getHeight() + 2) {
                dragging = module;
                dragOffsetX = mx - module.getX();
                dragOffsetY = my - module.getY();
                return true;
            }
        }
        return super.mouseClicked(click, forward);
    }

    // 1.21.11: mouseDragged uses Click object
    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging != null) {
            int newX = (int) click.x() - dragOffsetX;
            int newY = (int) click.y() - dragOffsetY;
            newX = Math.max(0, Math.min(newX, this.width - dragging.getWidth()));
            newY = Math.max(0, Math.min(newY, this.height - dragging.getHeight()));
            dragging.setX(newX);
            dragging.setY(newY);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    // 1.21.11: mouseReleased uses Click object
    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != null) {
            dragging = null;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        HudManager.save();
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
