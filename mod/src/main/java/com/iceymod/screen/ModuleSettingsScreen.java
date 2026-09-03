package com.iceymod.screen;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import com.iceymod.hud.settings.ColorSetting;
import com.iceymod.hud.settings.DoubleSetting;
import com.iceymod.hud.settings.EnumSetting;
import com.iceymod.hud.settings.IntSetting;
import com.iceymod.hud.settings.Setting;
import com.iceymod.screen.widget.IceyButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Per-module settings editor.
 *
 * <p>Each setting is an {@link IceyButton} row: label on the left, live value
 * on the right (ON/OFF pill for booleans, accent text for numbers and enums,
 * an actual ARGB swatch for colours). Click = cycle/toggle to the next value;
 * colours hand off to {@link ColorPickerScreen}.
 */
public class ModuleSettingsScreen extends Screen {

    private final HudModule module;
    private final Screen parent;

    private int panelX, panelY, panelW, panelH;
    private int headerDividerY, footerDividerY;

    public ModuleSettingsScreen(HudModule module, Screen parent) {
        super(Text.literal(module.getName() + " Settings"));
        this.module = module;
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<Setting<?>> settings = module.getSettings();
        int btnH = 20;
        int gap = 4;
        int centerX = this.width / 2;
        int topY = 58;
        int bottomY = this.height - 52;
        int gridH = bottomY - topY;
        int rowsPerCol = Math.max(1, gridH / (btnH + gap));

        // Adaptive column layout — single wide column for short lists,
        // grid for long ones (X-Ray has ~85 settings; need 3 cols to fit).
        int cols = (int) Math.ceil((double) settings.size() / rowsPerCol);
        if (cols < 1) cols = 1;
        if (cols > 4) cols = 4;
        int gridW = Math.min(this.width - 48, cols * 224 + (cols - 1) * gap);
        int btnW = (gridW - (cols - 1) * gap) / cols;
        int gridX = centerX - gridW / 2;

        for (int i = 0; i < settings.size(); i++) {
            final Setting<?> setting = settings.get(i);
            int col = i / rowsPerCol;
            int row = i % rowsPerCol;
            int x = gridX + col * (btnW + gap);
            int y = topY + row * (btnH + gap);
            IceyButton btn = new IceyButton(x, y, btnW, btnH, Text.literal(setting.label),
                    b -> {
                        onClick(setting);
                        applyRowStyle(b, setting);
                    });
            applyRowStyle(btn, setting);
            addDrawableChild(btn);
        }

        // Footer buttons centered below the grid
        int footerW = Math.min(300, this.width - 80);
        int footerY = this.height - 48;
        footerDividerY = footerY - 8;
        addDrawableChild(new IceyButton(centerX - footerW / 2, footerY, footerW, 20,
                Text.literal("Reset to Defaults"), b -> { resetAll(); rebuild(); })
                .setStyle(IceyButton.Style.DANGER_STYLE));
        footerY += 20 + gap;

        addDrawableChild(new IceyButton(centerX - footerW / 2, footerY, footerW, 20,
                Text.literal("Back"), b -> client.setScreen(parent))
                .setStyle(IceyButton.Style.ACCENT_PRIMARY));

        // Panel chrome
        panelW = Math.min(this.width - 12, Math.max(gridW, footerW) + 28);
        panelX = centerX - panelW / 2;
        panelY = 6;
        panelH = Math.max(60, this.height - 12);
        headerDividerY = 46;
    }

    /** Label left, live value right. */
    private void applyRowStyle(IceyButton btn, Setting<?> setting) {
        btn.setLeftText(setting.label);
        btn.clearRight();
        if (setting instanceof BoolSetting bs) {
            btn.setToggleState(bs.get());
        } else if (setting instanceof IntSetting is) {
            btn.setStyle(IceyButton.Style.NORMAL);
            btn.setRightText(String.valueOf(is.get()), IceyButton.ACCENT);
        } else if (setting instanceof DoubleSetting ds) {
            btn.setStyle(IceyButton.Style.NORMAL);
            btn.setRightText(String.format("%.2f", ds.get()), IceyButton.ACCENT);
        } else if (setting instanceof ColorSetting cs) {
            btn.setStyle(IceyButton.Style.NORMAL);
            btn.setSwatch(cs.get());
            btn.setRightText(cs.colorName(), IceyButton.TEXT_MUTED);
        } else if (setting instanceof EnumSetting es) {
            btn.setStyle(IceyButton.Style.NORMAL);
            btn.setRightText(es.getCurrentOption(), IceyButton.ACCENT);
        } else {
            btn.setStyle(IceyButton.Style.NORMAL);
        }
    }

    private void onClick(Setting<?> setting) {
        if (setting instanceof BoolSetting bs) {
            bs.set(!bs.get());
        } else if (setting instanceof IntSetting is) {
            int next = is.get() + is.step;
            if (next > is.max) next = is.min;
            is.set(next);
        } else if (setting instanceof DoubleSetting ds) {
            double next = ds.get() + ds.step;
            if (next > ds.max + 1e-6) next = ds.min;
            ds.set(next);
        } else if (setting instanceof ColorSetting cs) {
            // Opens the full RGB + hex picker instead of cycling the preset
            // palette — any ARGB value is reachable and the swatch updates live.
            client.setScreen(new ColorPickerScreen(cs, this));
            return; // ColorPickerScreen persists on its own Save click.
        } else if (setting instanceof EnumSetting es) {
            es.cycle();
        }
        // Persist after every mutation — without this the new value
        // lives in memory only and reverts on next MC launch.
        try { com.iceymod.hud.HudManager.save(); } catch (Throwable ignored) {}
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void resetAll() {
        for (Setting s : module.getSettings()) s.set(s.getDefault());
    }

    private void rebuild() {
        this.clearChildren();
        this.init();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Skip vanilla blur (avoids double-blur issues on recent versions)
        IceyButton.drawGradientBackdrop(context, this.width, this.height);
        IceyButton.drawContentPanel(context, panelX, panelY, panelW, panelH);
        IceyButton.drawDivider(context, panelX + 12, headerDividerY, panelW - 24);
        IceyButton.drawDivider(context, panelX + 12, footerDividerY, panelW - 24);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer,
                "§l" + module.getName(), centerX, 15, IceyButton.ACCENT);
        int count = module.getSettings().size();
        context.drawCenteredTextWithShadow(this.textRenderer,
                count == 0 ? "Settings" : count + (count == 1 ? " option" : " options"),
                centerX, 28, IceyButton.TEXT_MUTED);
        if (count == 0) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    "No configurable options",
                    centerX, this.height / 2 - 4, IceyButton.TEXT_MUTED);
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}
