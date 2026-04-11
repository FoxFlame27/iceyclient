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
 * Three-column grid of module buttons with category filters.
 */
public class IceyModScreen extends Screen {

    private static HudModule.Category currentFilter = null; // null = ALL

    public IceyModScreen() {
        super(Text.literal("Icey Client"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int filterY = 36;
        int filterBtnW = 60;
        int filterBtnH = 16;
        int filterGap = 3;

        // Filter buttons row
        HudModule.Category[] cats = HudModule.Category.values();
        int filterCount = cats.length + 1; // +1 for ALL
        int filterRowW = filterCount * filterBtnW + (filterCount - 1) * filterGap;
        int filterStartX = centerX - filterRowW / 2;

        addDrawableChild(ButtonWidget.builder(
                Text.literal(currentFilter == null ? "\u00A7b\u00A7lALL" : "ALL"),
                btn -> { currentFilter = null; rebuild(); }
        ).dimensions(filterStartX, filterY, filterBtnW, filterBtnH).build());

        for (int i = 0; i < cats.length; i++) {
            HudModule.Category cat = cats[i];
            int x = filterStartX + (i + 1) * (filterBtnW + filterGap);
            String label = currentFilter == cat ? "\u00A7b\u00A7l" + cat.name() : cat.name();
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(label),
                    btn -> { currentFilter = cat; rebuild(); }
            ).dimensions(x, filterY, filterBtnW, filterBtnH).build());
        }

        // Filtered modules
        List<HudModule> all = HudManager.getModules();
        java.util.List<HudModule> filtered = new java.util.ArrayList<>();
        for (HudModule m : all) {
            if (currentFilter == null || m.getCategory() == currentFilter) {
                filtered.add(m);
            }
        }

        int cols = 3;
        int btnW = 110;
        int btnH = 18;
        int gap = 3;
        int gridW = cols * btnW + (cols - 1) * gap;
        int startX = centerX - gridW / 2;
        int startY = filterY + filterBtnH + 8;

        for (int i = 0; i < filtered.size(); i++) {
            HudModule module = filtered.get(i);
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

        int rows = (filtered.size() + cols - 1) / cols;
        int bottomY = startY + rows * (btnH + gap) + 10;

        // Edit HUD button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u2699 Edit HUD Layout"),
                btn -> client.setScreen(new HudEditScreen(this))
        ).dimensions(centerX - 100, bottomY, 200, 20).build());

        // Done
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.done"),
                btn -> close()
        ).dimensions(centerX - 100, bottomY + 22, 200, 20).build());
    }

    private void rebuild() {
        this.clearChildren();
        this.init();
    }

    private Text getModuleText(HudModule module) {
        String state = module.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
        return Text.literal(module.getName() + ": " + state);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Skip vanilla blur (1.21.11 double-blur crash)
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer,
                "\u00A7b\u00A7lIcey Client", this.width / 2, 8, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                "\u00A77" + HudManager.getModules().size() + " modules", this.width / 2, 20, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        HudManager.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
