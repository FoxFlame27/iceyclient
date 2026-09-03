package com.iceymod.screen;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import com.iceymod.screen.widget.IceyButton;
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
        addDrawableChild(new IceyButton(this.width / 2 - 50, this.height - 28, 100, 20,
                Text.translatable("gui.done"), btn -> close())
                .setStyle(IceyButton.Style.ACCENT_PRIMARY));
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

        IceyButton.drawGradientBackdrop(context, this.width, this.height);

        context.drawCenteredTextWithShadow(textRenderer,
                "\u00A7lEdit HUD Layout", this.width / 2, 10, IceyButton.ACCENT);
        context.drawCenteredTextWithShadow(textRenderer,
                "Click and drag any module to reposition it", this.width / 2, 23,
                IceyButton.TEXT_MUTED);

        for (HudModule module : HudManager.getModules()) {
            if (!module.isEnabled()) continue;

            try { module.render(context, client); } catch (Throwable ignored) {}

            int x = module.getX() - 3;
            int y = module.getY() - 3;
            int w = module.getWidth() + 6;
            int h = module.getHeight() + 6;

            boolean isHovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
            boolean isDragged = module == dragging;
            int borderColor = isDragged ? IceyButton.ACCENT : isHovered ? 0xCC8FD8F0 : 0x55FFFFFF;
            int fillColor = isDragged ? 0x305BC8F5 : isHovered ? 0x28FFFFFF : 0x20FFFFFF;

            context.fill(x, y, x + w, y + h, fillColor);
            IceyButton.drawBorder(context, x, y, w, h, borderColor);

            // name tag in a small dark pill just above the outline
            int labelColor = isDragged ? IceyButton.ACCENT : 0xFFCBD5E1;
            IceyButton.drawTag(context, module.getName(), x, y - 14, labelColor,
                    isDragged ? 0xE0102630 : 0xD00D1219);
        }

        context.drawCenteredTextWithShadow(textRenderer,
                "Drag to move \u2022 Done to save", this.width / 2, this.height - 42,
                IceyButton.TEXT_MUTED);

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
