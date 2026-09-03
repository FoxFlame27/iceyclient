package com.iceymod.screen;

import com.iceymod.IceyMod;
import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.screen.widget.IceyButton;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The main Icey Client menu, opened with Y.
 * Paginated grid of module toggles with arrow-key navigation.
 *
 * <p>All chrome is drawn by hand ({@link IceyButton}) rather than by vanilla
 * widgets so the menu keeps a consistent dark/accent look and stays
 * compilable against both 1.21.8 and 1.21.11 mappings.
 */
public class IceyModScreen extends Screen {

    private static HudModule.Category currentFilter = null; // null = ALL
    private static int page = 0;
    private static int selectedIndex = 0;
    // Persisted across rebuilds during typing so the field doesn't lose
    // text when the screen re-inits on each keystroke.
    private static String searchQuery = "";
    private TextFieldWidget searchField;
    // Instance field so settings mode resets every time the menu is reopened.
    private boolean settingsMode = false;

    private int gridCols = 4;
    private int gridRows = 5;
    private int perPage = 20;
    private List<HudModule> filtered = new ArrayList<>();
    private final List<IceyButton> moduleButtons = new ArrayList<>();

    private static final Identifier GEAR_TEXTURE = Identifier.of(IceyMod.MOD_ID, "textures/gui/gear.png");
    private int gearX, gearY, gearW, gearH;

    // Chrome geometry, computed in init() and consumed by renderBackground().
    private int panelX, panelY, panelW, panelH;
    private int searchFrameX, searchFrameY, searchFrameW, searchFrameH;
    private int headerDividerY, footerDividerY;
    private int totalPages = 1;

    public IceyModScreen() {
        super(Text.literal("Icey Client"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int sh = this.height;
        moduleButtons.clear();

        // ---- header band -----------------------------------------------------
        // title y=13, subtitle y=25, divider under the header, then search.
        headerDividerY = 40;

        // Search bar — custom frame drawn in renderBackground(), the vanilla
        // TextFieldWidget sits inset inside it.
        int searchW = 240;
        int searchH = 20;
        searchFrameX = centerX - searchW / 2;
        searchFrameY = 50;
        searchFrameW = searchW;
        searchFrameH = searchH;

        searchField = new TextFieldWidget(this.textRenderer, searchFrameX + 7, searchFrameY + 6,
                searchW - 14, 12, Text.literal(""));
        searchField.setMaxLength(32);
        searchField.setPlaceholder(Text.literal("§8Search modules…"));
        searchField.setText(searchQuery);
        hideFieldBackground(searchField);
        searchField.setChangedListener(s -> {
            if (!s.equals(searchQuery)) {
                searchQuery = s;
                page = 0;
                selectedIndex = 0;
                rebuild();
            }
        });
        addDrawableChild(searchField);
        // If the user is mid-search, the rebuild triggered by typing
        // would otherwise leave focus on nothing — re-grab it.
        if (searchQuery != null && !searchQuery.isEmpty()) {
            setInitialFocus(searchField);
            searchField.setFocused(true);
        }

        // ---- category chips --------------------------------------------------
        int filterY = searchFrameY + searchFrameH + 8;
        int filterBtnW = 68;
        int filterBtnH = 20;
        int filterGap = 5;

        HudModule.Category[] cats = HudModule.Category.values();
        int filterCount = cats.length + 1; // +1 for ALL
        int filterRowW = filterCount * filterBtnW + (filterCount - 1) * filterGap;
        int filterStartX = centerX - filterRowW / 2;

        addDrawableChild(IceyButton.of(filterStartX, filterY, filterBtnW, filterBtnH, "ALL",
                        btn -> { currentFilter = null; page = 0; selectedIndex = 0; rebuild(); })
                .setStyle(IceyButton.Style.CHIP)
                .setHighlighted(currentFilter == null));

        for (int i = 0; i < cats.length; i++) {
            final HudModule.Category cat = cats[i];
            int x = filterStartX + (i + 1) * (filterBtnW + filterGap);
            addDrawableChild(IceyButton.of(x, filterY, filterBtnW, filterBtnH, cat.name(),
                            btn -> { currentFilter = cat; page = 0; selectedIndex = 0; rebuild(); })
                    .setStyle(IceyButton.Style.CHIP)
                    .setHighlighted(currentFilter == cat));
        }

        // Filter modules: category gate + free-text name search.
        // ALL excludes OPTIMIZATION so it only appears in its own tab.
        String q = searchQuery == null ? "" : searchQuery.toLowerCase().trim();
        List<HudModule> all = HudManager.getModules();
        filtered = new ArrayList<>();
        for (HudModule m : all) {
            boolean catOk = currentFilter == null
                    ? m.getCategory() != HudModule.Category.OPTIMIZATION
                    : m.getCategory() == currentFilter;
            if (!catOk) continue;
            if (!q.isEmpty() && !m.getName().toLowerCase().contains(q)) continue;
            filtered.add(m);
        }

        // ---- module grid -----------------------------------------------------
        int bottomReserved = 88; // pagination + edit + done
        int gridTop = filterY + filterBtnH + 14;
        int availableH = sh - gridTop - bottomReserved;

        int btnW = 132;
        int btnH = 22;
        int gap = 5;

        gridCols = Math.max(2, Math.min(5, (this.width - 56) / (btnW + gap)));
        // Never more rows than actually fit above the footer — at large GUI
        // scales the old minimum of 3 rows spilled over the pagination row.
        gridRows = Math.max(1, availableH / (btnH + gap));
        perPage = gridCols * gridRows;

        totalPages = Math.max(1, (filtered.size() + perPage - 1) / perPage);
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
            IceyButton btn = new IceyButton(x, y, btnW, btnH, Text.literal(module.getName()),
                    b -> {
                        selectedIndex = thisIdx;
                        if (settingsMode) {
                            client.setScreen(new ModuleSettingsScreen(module, this));
                        } else {
                            module.toggle();
                            styleModuleTile(b, module, true);
                        }
                    });
            styleModuleTile(btn, module, thisIdx == selectedIndex);
            addDrawableChild(btn);
            moduleButtons.add(btn);
        }

        // ---- content panel geometry -----------------------------------------
        int contentW = Math.max(Math.max(gridW, filterRowW), 300) + 28;
        panelW = Math.min(this.width - 12, contentW);
        panelX = centerX - panelW / 2;
        panelY = 6;
        panelH = Math.max(60, sh - 12);

        // ---- gear (settings mode toggle), inside the panel's top-right ------
        gearW = 24;
        gearH = 24;
        gearX = panelX + panelW - gearW - 10;
        gearY = panelY + 8;
        addDrawableChild(new IceyButton(gearX, gearY, gearW, gearH, Text.literal(""),
                b -> { settingsMode = !settingsMode; rebuild(); })
                .setStyle(IceyButton.Style.NORMAL)
                .setHighlighted(settingsMode));

        // ---- footer ----------------------------------------------------------
        int paginationY = sh - 82;
        footerDividerY = paginationY - 8;
        int pagBtnW = 80;
        int pagBtnH = 20;
        int pagGap = 6;

        boolean canPrev = page > 0;
        boolean canNext = page < totalPages - 1;

        IceyButton lessBtn = IceyButton.of(centerX - pagBtnW - pagGap - 50, paginationY, pagBtnW, pagBtnH,
                "◀ Less",
                btn -> { if (page > 0) { page--; selectedIndex = page * perPage; rebuild(); } });
        lessBtn.active = canPrev;
        addDrawableChild(lessBtn);

        IceyButton pageLabel = IceyButton.of(centerX - 40, paginationY, 80, pagBtnH,
                (page + 1) + " / " + totalPages, btn -> {});
        pageLabel.setStyle(IceyButton.Style.PLAIN);
        addDrawableChild(pageLabel);

        IceyButton moreBtn = IceyButton.of(centerX + 50 + pagGap, paginationY, pagBtnW, pagBtnH,
                "More ▶",
                btn -> { if (page < totalPages - 1) { page++; selectedIndex = page * perPage; rebuild(); } });
        moreBtn.active = canNext;
        addDrawableChild(moreBtn);

        int bottomBtnY = sh - 58;
        addDrawableChild(IceyButton.of(centerX - 110, bottomBtnY, 220, 22,
                "⚙ Edit HUD Layout",
                btn -> client.setScreen(new HudEditScreen(this))));

        addDrawableChild(new IceyButton(centerX - 110, bottomBtnY + 26, 220, 22,
                Text.translatable("gui.done"), btn -> close())
                .setStyle(IceyButton.Style.ACCENT_PRIMARY));
    }

    /** Applies name / ON-OFF pill / selection highlight to a module tile. */
    private void styleModuleTile(IceyButton btn, HudModule module, boolean selected) {
        btn.setLeftText(module.getName());
        btn.setHighlighted(selected);
        btn.clearRight();
        if (settingsMode) {
            // The gear already says we're in settings mode — just a chevron.
            btn.setStyle(IceyButton.Style.TOGGLE_OFF);
            btn.setRightText("›", IceyButton.ACCENT);
        } else {
            btn.setToggleState(module.isEnabled());
        }
    }

    /**
     * 1.21.8 exposes {@code TextFieldWidget#setDrawsBackground(boolean)}; if a
     * future mapping renames or drops it we simply keep the vanilla frame,
     * which still sits inside our own panel.
     */
    private static void hideFieldBackground(TextFieldWidget field) {
        try {
            field.getClass().getMethod("setDrawsBackground", boolean.class).invoke(field, false);
            return;
        } catch (Throwable ignored) {}
        try {
            for (java.lang.reflect.Method m : field.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class
                        && m.getName().toLowerCase().contains("drawsbackground")) {
                    m.invoke(field, false);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private void rebuild() {
        this.clearChildren();
        this.init();
    }

    // GLFW key polling: 1.21.11 changed Screen.keyPressed's signature to
    // take a KeyInput object, so our (int, int, int) override stops
    // overriding anything at runtime → arrow-key navigation silently
    // dies. We poll inside render() instead — works on both 1.21.8 and
    // 1.21.11 since GLFW key state is independent of MC version.
    private final java.util.HashMap<Integer, Boolean> prevKeyState = new java.util.HashMap<>();

    private boolean keyEdge(int glfwKey) {
        try {
            long handle = client.getWindow().getHandle();
            boolean down = GLFW.glfwGetKey(handle, glfwKey) == GLFW.GLFW_PRESS;
            boolean wasDown = prevKeyState.getOrDefault(glfwKey, false);
            prevKeyState.put(glfwKey, down);
            return down && !wasDown;
        } catch (Throwable t) { return false; }
    }

    private void pollNavigationKeys() {
        if (filtered.isEmpty()) return;
        // Skip nav-key polling when the search field is focused so
        // typing arrow keys / Enter / Space don't double-fire as
        // grid navigation while the user is editing the query.
        if (searchField != null && searchField.isFocused()) return;
        int startIdx = page * perPage;
        int localIdx = selectedIndex - startIdx;

        if (keyEdge(GLFW.GLFW_KEY_UP)) {
            int next = selectedIndex - gridCols;
            if (next < 0) next = selectedIndex;
            moveSelection(next);
        }
        if (keyEdge(GLFW.GLFW_KEY_DOWN)) {
            int next = selectedIndex + gridCols;
            if (next >= filtered.size()) next = selectedIndex;
            moveSelection(next);
        }
        if (keyEdge(GLFW.GLFW_KEY_LEFT)) {
            if (localIdx > 0 && selectedIndex > 0) {
                moveSelection(selectedIndex - 1);
            } else if (page > 0) {
                page--;
                selectedIndex = Math.min(page * perPage + perPage - 1, filtered.size() - 1);
                rebuild();
            }
        }
        if (keyEdge(GLFW.GLFW_KEY_RIGHT)) {
            if (selectedIndex < filtered.size() - 1) moveSelection(selectedIndex + 1);
        }
        if (keyEdge(GLFW.GLFW_KEY_ENTER) || keyEdge(GLFW.GLFW_KEY_SPACE)) {
            if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
                HudModule m = filtered.get(selectedIndex);
                if (settingsMode) {
                    client.setScreen(new ModuleSettingsScreen(m, this));
                } else {
                    m.toggle();
                    rebuild();
                }
            }
        }
        if (keyEdge(GLFW.GLFW_KEY_PAGE_DOWN)) {
            int tp = Math.max(1, (filtered.size() + perPage - 1) / perPage);
            if (page < tp - 1) { page++; selectedIndex = page * perPage; rebuild(); }
        }
        if (keyEdge(GLFW.GLFW_KEY_PAGE_UP)) {
            if (page > 0) { page--; selectedIndex = page * perPage; rebuild(); }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int tp = Math.max(1, (filtered.size() + perPage - 1) / perPage);
        if (verticalAmount < 0 && page < tp - 1) {
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
            // just refresh tile state
            int startIdx = page * perPage;
            for (int i = 0; i < moduleButtons.size(); i++) {
                HudModule m = filtered.get(startIdx + i);
                styleModuleTile(moduleButtons.get(i), m, startIdx + i == selectedIndex);
            }
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        IceyButton.drawGradientBackdrop(context, this.width, this.height);
        IceyButton.drawContentPanel(context, panelX, panelY, panelW, panelH);
        // header / footer rules inside the panel
        IceyButton.drawDivider(context, panelX + 12, headerDividerY, panelW - 24);
        IceyButton.drawDivider(context, panelX + 12, footerDividerY, panelW - 24);
        // search field frame
        IceyButton.drawPanel(context, searchFrameX, searchFrameY, searchFrameW, searchFrameH,
                0xE00E141C, 0xFF2A3440);
        context.fill(searchFrameX + 1, searchFrameY + 1, searchFrameX + searchFrameW - 1,
                searchFrameY + 2, 0x10FFFFFF);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        try { pollNavigationKeys(); } catch (Throwable ignored) {}
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer,
                "§lIcey Client", centerX, 13, IceyButton.ACCENT);

        String subtitle = HudManager.getModules().size() + " modules  •  "
                + filtered.size() + " shown  •  page " + (page + 1) + "/" + totalPages
                + (settingsMode ? "  •  configure mode" : "");
        context.drawCenteredTextWithShadow(this.textRenderer, subtitle,
                centerX, 26, IceyButton.TEXT_MUTED);

        // Gear icon texture on top of its card
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                GEAR_TEXTURE,
                gearX + 4, gearY + 4,
                0f, 0f,
                gearW - 8, gearH - 8,
                gearW - 8, gearH - 8
        );
        // Clear ON/OFF label directly below the gear
        context.drawCenteredTextWithShadow(this.textRenderer,
                settingsMode ? "CONFIG" : "TOGGLE",
                gearX + gearW / 2, gearY + gearH + 4,
                settingsMode ? IceyButton.ACCENT : IceyButton.TEXT_MUTED);

        if (filtered.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    "No modules match that search",
                    centerX, this.height / 2 - 4, IceyButton.TEXT_MUTED);
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
