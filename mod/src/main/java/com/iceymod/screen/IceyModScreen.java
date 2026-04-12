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
        int sh = this.height;

        // Filter buttons row
        int filterY = 26;
        int filterBtnW = 52;
        int filterBtnH = 14;
        int filterGap = 2;

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
            String name = cat.name().substring(0, Math.min(cat.name().length(), 6));
            String label = currentFilter == cat ? "\u00A7b\u00A7l" + name : name;
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(label),
                    btn -> { currentFilter = cat; rebuild(); }
            ).dimensions(x, filterY, filterBtnW, filterBtnH).build());
        }

        // Filter modules
        List<HudModule> all = HudManager.getModules();
        java.util.List<HudModule> filtered = new java.util.ArrayList<>();
        for (HudModule m : all) {
            if (currentFilter == null || m.getCategory() == currentFilter) {
                filtered.add(m);
            }
        }

        // Calculate space for grid (between filter row and bottom buttons)
        int bottomReserved = 50; // Edit HUD + Done buttons + padding
        int gridTop = filterY + filterBtnH + 8;
        int availableHeight = sh - gridTop - bottomReserved;

        // Use 5 columns with small buttons to fit 30 modules
        int cols = 5;
        int btnW = 84;
        int btnH = 14;
        int gap = 2;
        int rowsNeeded = (filtered.size() + cols - 1) / cols;
        int rowsThatFit = Math.max(1, availableHeight / (btnH + gap));

        // If rows don't fit, scale down further (this should rarely happen)
        if (rowsNeeded > rowsThatFit) {
            btnH = 12;
            gap = 1;
        }

        int gridW = cols * btnW + (cols - 1) * gap;
        int startX = centerX - gridW / 2;

        for (int i = 0; i < filtered.size(); i++) {
            HudModule module = filtered.get(i);
            int col = i % cols;
            int row = i / cols;
            int x = startX + col * (btnW + gap);
            int y = gridTop + row * (btnH + gap);

            addDrawableChild(ButtonWidget.builder(
                    getModuleText(module),
                    btn -> {
                        module.toggle();
                        btn.setMessage(getModuleText(module));
                    }
            ).dimensions(x, y, btnW, btnH).build());
        }

        // Bottom buttons - anchored to bottom of screen
        int bottomBtnY = sh - bottomReserved + 4;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u2699 Edit HUD Layout"),
                btn -> client.setScreen(new HudEditScreen(this))
        ).dimensions(centerX - 100, bottomBtnY, 200, 18).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.done"),
                btn -> close()
        ).dimensions(centerX - 100, bottomBtnY + 22, 200, 18).build());
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
                "\u00A7b\u00A7lIcey Client \u00A77" + HudManager.getModules().size() + " modules",
                this.width / 2, 8, 0xFFFFFFFF);
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
