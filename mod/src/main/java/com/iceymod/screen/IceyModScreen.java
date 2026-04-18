package com.iceymod.screen;

import com.iceymod.IceyMod;
import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The main Icey Client menu, opened with Y.
 * Paginated grid of module toggles with arrow-key navigation.
 */
public class IceyModScreen extends Screen {

    private static HudModule.Category currentFilter = null; // null = ALL
    private static int page = 0;
    private static int selectedIndex = 0;
    // Instance field so settings mode resets every time the menu is reopened.
    private boolean settingsMode = false;

    private int gridCols = 4;
    private int gridRows = 5;
    private int perPage = 20;
    private List<HudModule> filtered = new ArrayList<>();
    private final List<ButtonWidget> moduleButtons = new ArrayList<>();

    private static final Identifier GEAR_TEXTURE = Identifier.of(IceyMod.MOD_ID, "textures/gui/gear.png");
    private int gearX, gearY, gearW, gearH;

    public IceyModScreen() {
        super(Text.literal("Icey Client"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int sh = this.height;
        moduleButtons.clear();

        // Filter buttons row (slightly bigger)
        int filterY = 30;
        int filterBtnW = 64;
        int filterBtnH = 18;
        int filterGap = 4;

        HudModule.Category[] cats = HudModule.Category.values();
        int filterCount = cats.length + 1; // +1 for ALL
        int filterRowW = filterCount * filterBtnW + (filterCount - 1) * filterGap;
        int filterStartX = centerX - filterRowW / 2;

        addDrawableChild(ButtonWidget.builder(
                Text.literal(currentFilter == null ? "\u00A7b\u00A7lALL" : "ALL"),
                btn -> { currentFilter = null; page = 0; selectedIndex = 0; rebuild(); }
        ).dimensions(filterStartX, filterY, filterBtnW, filterBtnH).build());

        for (int i = 0; i < cats.length; i++) {
            HudModule.Category cat = cats[i];
            int x = filterStartX + (i + 1) * (filterBtnW + filterGap);
            String name = cat.name();
            String label = currentFilter == cat ? "\u00A7b\u00A7l" + name : name;
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(label),
                    btn -> { currentFilter = cat; page = 0; selectedIndex = 0; rebuild(); }
            ).dimensions(x, filterY, filterBtnW, filterBtnH).build());
        }

        // Filter modules (ALL excludes OPTIMIZATION so it only appears in its own tab)
        List<HudModule> all = HudManager.getModules();
        filtered = new ArrayList<>();
        for (HudModule m : all) {
            if (currentFilter == null) {
                if (m.getCategory() != HudModule.Category.OPTIMIZATION) filtered.add(m);
            } else if (m.getCategory() == currentFilter) {
                filtered.add(m);
            }
        }

        // Grid sizing — bigger buttons, paginated so they always fit
        int bottomReserved = 80; // pagination + edit + done
        int gridTop = filterY + filterBtnH + 12;
        int availableH = sh - gridTop - bottomReserved;

        int btnW = 128;
        int btnH = 20;
        int gap = 4;

        gridCols = Math.max(2, Math.min(5, (this.width - 40) / (btnW + gap)));
        gridRows = Math.max(3, availableH / (btnH + gap));
        perPage = gridCols * gridRows;

        int totalPages = Math.max(1, (filtered.size() + perPage - 1) / perPage);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;
        if (selectedIndex >= filtered.size()) selectedIndex = Math.max(0, filtered.size() - 1);

        int startIdx = page * perPage;
        int endIdx = Math.min(filtered.size(), startIdx + perPage);

        int gridW = gridCols * btnW + (gridCols - 1) * gap;
        int startX = centerX - gridW / 2;

        for (int i = startIdx; i < endIdx; i++) {
            final HudModule module = filtered.get(i);
            int rel = i - startIdx;
            int col = rel % gridCols;
            int row = rel / gridCols;
            int x = startX + col * (btnW + gap);
            int y = gridTop + row * (btnH + gap);

            final int thisIdx = i;
            ButtonWidget btn = ButtonWidget.builder(
                    getModuleText(module, thisIdx == selectedIndex),
                    b -> {
                        selectedIndex = thisIdx;
                        if (settingsMode) {
                            client.setScreen(new ModuleSettingsScreen(module, this));
                        } else {
                            module.toggle();
                            b.setMessage(getModuleText(module, true));
                        }
                    }
            ).dimensions(x, y, btnW, btnH).build();
            addDrawableChild(btn);
            moduleButtons.add(btn);
        }

        // Gear icon (settings mode toggle) — top-right corner, renders as a texture
        gearW = 28;
        gearH = 28;
        gearX = this.width - gearW - 10;
        gearY = 10;
        ButtonWidget gear = ButtonWidget.builder(
                Text.literal(""),
                b -> { settingsMode = !settingsMode; rebuild(); }
        ).dimensions(gearX, gearY, gearW, gearH).build();
        addDrawableChild(gear);

        // Pagination row
        int paginationY = sh - bottomReserved + 4;
        int pagBtnW = 80;
        int pagBtnH = 20;
        int pagGap = 6;

        boolean canPrev = page > 0;
        boolean canNext = page < totalPages - 1;

        ButtonWidget lessBtn = ButtonWidget.builder(
                Text.literal(canPrev ? "\u00A7b\u25C0 Less" : "\u00A78\u25C0 Less"),
                btn -> { if (page > 0) { page--; selectedIndex = page * perPage; rebuild(); } }
        ).dimensions(centerX - pagBtnW - pagGap - 50, paginationY, pagBtnW, pagBtnH).build();
        lessBtn.active = canPrev;
        addDrawableChild(lessBtn);

        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u00A77" + (page + 1) + "/" + totalPages),
                btn -> {}
        ).dimensions(centerX - 40, paginationY, 80, pagBtnH).build());

        ButtonWidget moreBtn = ButtonWidget.builder(
                Text.literal(canNext ? "\u00A7bMore \u25B6" : "\u00A78More \u25B6"),
                btn -> { if (page < totalPages - 1) { page++; selectedIndex = page * perPage; rebuild(); } }
        ).dimensions(centerX + 50 + pagGap, paginationY, pagBtnW, pagBtnH).build();
        moreBtn.active = canNext;
        addDrawableChild(moreBtn);

        // Bottom buttons
        int bottomBtnY = sh - 54;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u2699 Edit HUD Layout"),
                btn -> client.setScreen(new HudEditScreen(this))
        ).dimensions(centerX - 110, bottomBtnY, 220, 22).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.done"),
                btn -> close()
        ).dimensions(centerX - 110, bottomBtnY + 26, 220, 22).build());
    }

    private void rebuild() {
        this.clearChildren();
        this.init();
    }

    private Text getModuleText(HudModule module, boolean selected) {
        String prefix = selected ? "\u00A7b\u00BB \u00A7r" : "";
        if (settingsMode) {
            return Text.literal(prefix + module.getName() + " \u00A77\u2699");
        }
        String state = module.isEnabled() ? "\u00A7aON" : "\u00A7cOFF";
        return Text.literal(prefix + module.getName() + ": " + state);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (filtered.isEmpty()) return super.keyPressed(keyCode, scanCode, modifiers);

        int startIdx = page * perPage;
        int localIdx = selectedIndex - startIdx;

        if (keyCode == GLFW.GLFW_KEY_UP) {
            int next = selectedIndex - gridCols;
            if (next < 0) next = selectedIndex;
            moveSelection(next);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            int next = selectedIndex + gridCols;
            if (next >= filtered.size()) next = selectedIndex;
            moveSelection(next);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (localIdx > 0 && selectedIndex > 0) {
                moveSelection(selectedIndex - 1);
            } else if (page > 0) {
                page--;
                selectedIndex = Math.min(page * perPage + perPage - 1, filtered.size() - 1);
                rebuild();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (selectedIndex < filtered.size() - 1) {
                moveSelection(selectedIndex + 1);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
                HudModule m = filtered.get(selectedIndex);
                if (settingsMode) {
                    client.setScreen(new ModuleSettingsScreen(m, this));
                } else {
                    m.toggle();
                    rebuild();
                }
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            int totalPages = Math.max(1, (filtered.size() + perPage - 1) / perPage);
            if (page < totalPages - 1) { page++; selectedIndex = page * perPage; rebuild(); }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            if (page > 0) { page--; selectedIndex = page * perPage; rebuild(); }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int totalPages = Math.max(1, (filtered.size() + perPage - 1) / perPage);
        if (verticalAmount < 0 && page < totalPages - 1) {
            page++; selectedIndex = page * perPage; rebuild();
            return true;
        }
        if (verticalAmount > 0 && page > 0) {
            page--; selectedIndex = page * perPage; rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void moveSelection(int newIdx) {
        if (newIdx < 0 || newIdx >= filtered.size()) return;
        int newPage = newIdx / perPage;
        selectedIndex = newIdx;
        if (newPage != page) {
            page = newPage;
            rebuild();
        } else {
            // just refresh labels
            int startIdx = page * perPage;
            for (int i = 0; i < moduleButtons.size(); i++) {
                HudModule m = filtered.get(startIdx + i);
                moduleButtons.get(i).setMessage(getModuleText(m, startIdx + i == selectedIndex));
            }
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer,
                "\u00A7b\u00A7lIcey Client \u00A77" + HudManager.getModules().size() + " modules",
                this.width / 2, 10, 0xFFFFFFFF);

        // Draw gear icon texture over the invisible button
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                GEAR_TEXTURE,
                gearX, gearY,
                0f, 0f,
                gearW, gearH,
                gearW, gearH
        );
        if (settingsMode) {
            context.drawCenteredTextWithShadow(this.textRenderer, "\u00A7b\u00A7lON",
                    gearX + gearW / 2, gearY + gearH + 2, 0xFFFFFFFF);
        }
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
