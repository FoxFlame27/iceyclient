package com.iceymod.screen;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * HUD module-position editor.
 *
 * History note: this screen previously overrode {@code mouseClicked /
 * mouseDragged / mouseReleased} on Screen. In 1.21.11 those methods
 * were re-signatured to take a {@code Click} object instead of
 * {@code (double, double, int)}. Loom remaps method descriptors at
 * jar build time, so on 1.21.11 the @Override methods stop overriding
 * anything — they sit there as dead private methods, and dragging the
 * HUD silently no-ops.
 *
 * Fix: poll mouse state inside {@link #render(DrawContext, int, int, float)}
 * (whose signature didn't change) and run a small click/drag/release
 * state machine ourselves. Works identically on 1.21.8 and 1.21.11.
 */
public class HudEditScreen extends Screen {
    private final Screen parent;
    private HudModule dragging = null;
    private int dragOffsetX, dragOffsetY;
    private boolean prevLeftDown = false;

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
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Skip vanilla blur (1.21.11 double-blur crash) - we draw our own overlay in render().
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Polled drag state machine — works regardless of version-specific
        // mouseClicked / mouseDragged / mouseReleased signature changes.
        try {
            updateDrag(mouseX, mouseY);
        } catch (Throwable ignored) {}

        context.fill(0, 0, this.width, this.height, 0x80000000);

        context.drawCenteredTextWithShadow(textRenderer,
                "§b§lDrag modules to reposition", this.width / 2, 8, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                "§7Click and drag any module below", this.width / 2, 20, 0xFFFFFFFF);

        for (HudModule module : HudManager.getModules()) {
            if (!module.isEnabled()) continue;

            try { module.render(context, client); } catch (Throwable ignored) {}

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

    /**
     * Mouse drag state machine, polled each frame from render(). Reads
     * the left mouse button via raw GLFW (works on every MC version)
     * and the cursor via the mouseX/mouseY render args.
     */
    private void updateDrag(int mouseX, int mouseY) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.getWindow() == null) return;
        long handle = c.getWindow().getHandle();
        boolean leftDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        // Just-pressed → look for a module under cursor and begin drag.
        if (leftDown && !prevLeftDown && dragging == null) {
            // Only skip clicks landing on the actual Done button rect
            // (centered at bottom, 100x20). The previous "anywhere in
            // the bottom 32 px" skip blocked dragging modules positioned
            // near the bottom of the screen — including the default
            // Waypoints position on tall windows.
            int doneX = this.width / 2 - 50;
            int doneY = this.height - 28;
            boolean onDoneButton =
                    mouseX >= doneX && mouseX <= doneX + 100
                 && mouseY >= doneY && mouseY <= doneY + 20;
            if (!onDoneButton) {
                var modules = HudManager.getModules();
                for (int i = modules.size() - 1; i >= 0; i--) {
                    HudModule module = modules.get(i);
                    if (!module.isEnabled()) continue;
                    int x = module.getX() - 2;
                    int y = module.getY() - 2;
                    int w = module.getWidth() + 4;
                    int h = module.getHeight() + 4;
                    if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                        dragging = module;
                        dragOffsetX = mouseX - module.getX();
                        dragOffsetY = mouseY - module.getY();
                        break;
                    }
                }
            }
        }

        // Held → keep dragging.
        if (leftDown && dragging != null) {
            int newX = mouseX - dragOffsetX;
            int newY = mouseY - dragOffsetY;
            newX = Math.max(0, Math.min(newX, this.width - dragging.getWidth()));
            newY = Math.max(0, Math.min(newY, this.height - dragging.getHeight()));
            dragging.setX(newX);
            dragging.setY(newY);
        }

        // Just-released → drop.
        if (!leftDown && prevLeftDown) {
            dragging = null;
        }

        prevLeftDown = leftDown;
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
