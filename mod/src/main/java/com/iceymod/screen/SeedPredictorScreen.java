package com.iceymod.screen;

import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.structure.SeedPredictor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import java.util.List;

/**
 * Seed Predictor — paste a world seed, computes likely End-City
 * candidate locations using the vanilla region-grid algorithm, lists
 * them sorted by distance, lets the user waypoint the closest N.
 *
 * Candidates are not server-verified. Vanilla's biome + terrain checks
 * may reject ~10% so a few entries will be empty when visited; the rest
 * land you on or next to a real city.
 */
public class SeedPredictorScreen extends Screen {

    private TextFieldWidget seedField;
    private TextFieldWidget radiusField;
    private List<ChunkPos> results;
    private String statusLine = "Paste your world seed and press Predict.";

    public SeedPredictorScreen() {
        super(Text.literal("Seed Predictor"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int btnW = 240;
        int btnH = 22;
        int gap = 6;
        int y = 60;

        seedField = new TextFieldWidget(this.textRenderer, cx - btnW / 2, y, btnW, btnH, Text.literal(""));
        seedField.setMaxLength(32);
        seedField.setPlaceholder(Text.literal("World seed (e.g. -1234567890)"));
        addDrawableChild(seedField);
        setInitialFocus(seedField);
        y += btnH + gap;

        radiusField = new TextFieldWidget(this.textRenderer, cx - btnW / 2, y, btnW, btnH, Text.literal(""));
        radiusField.setMaxLength(8);
        radiusField.setText("4000");
        radiusField.setPlaceholder(Text.literal("Search radius (blocks)"));
        addDrawableChild(radiusField);
        y += btnH + gap;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("§a▶ Predict End Cities"),
                b -> doPredict()
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("§b✎ Waypoint Top 10 Closest"),
                b -> waypointTop(10)
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("§b✎ Waypoint All"),
                b -> waypointTop(Integer.MAX_VALUE)
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap * 2;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("← Back"),
                b -> this.close()
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
    }

    private void doPredict() {
        long seed;
        try {
            seed = parseSeed(seedField.getText().trim());
        } catch (NumberFormatException e) {
            statusLine = "§cBad seed.";
            return;
        }
        int radius;
        try {
            radius = Math.max(1024, Math.min(60_000, Integer.parseInt(radiusField.getText().trim())));
        } catch (NumberFormatException e) {
            statusLine = "§cBad radius.";
            return;
        }
        results = SeedPredictor.predictEndCities(seed, radius);
        statusLine = "§a" + results.size() + " candidate End City locations within " + radius + " blocks.";
    }

    /**
     * Java's seed semantics: numeric string → parseLong, anything else →
     * String.hashCode(). Matches what vanilla does for non-numeric seeds.
     */
    private long parseSeed(String s) {
        if (s.isEmpty()) throw new NumberFormatException("empty");
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return s.hashCode();
        }
    }

    private void waypointTop(int max) {
        if (results == null || results.isEmpty()) {
            statusLine = "§cNo predictions yet — press Predict first.";
            return;
        }
        int n = Math.min(max, results.size());
        for (int i = 0; i < n; i++) {
            ChunkPos cp = results.get(i);
            int x = cp.x * 16 + 8;
            int z = cp.z * 16 + 8;
            // Y guess: 60 is roughly where outer-end islands sit
            WaypointManager.addWaypoint("End City Pred " + (i + 1), x, 60, z);
        }
        statusLine = "§aAdded " + n + " waypoints (named 'End City Pred N').";
        MinecraftClient c = MinecraftClient.getInstance();
        if (c != null && c.player != null) {
            c.player.sendMessage(net.minecraft.text.Text.literal(
                    "§b[IceyClient] §aSeed Predictor added " + n + " End City waypoints"), false);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, "§b§lSeed Predictor", cx, 24, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                "§7Predicts End-City candidate coords from your world seed.",
                cx, 38, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer, statusLine, cx, 192, 0xFFFFFFFF);

        if (results != null && !results.isEmpty()) {
            int show = Math.min(8, results.size());
            int yy = 210;
            for (int i = 0; i < show; i++) {
                ChunkPos cp = results.get(i);
                int x = cp.x * 16 + 8;
                int z = cp.z * 16 + 8;
                int chunkDist = Math.max(Math.abs(cp.x), Math.abs(cp.z)) * 16;
                String line = "§7#" + (i + 1) + " §f(" + x + ", " + z + ") §8• §7" + chunkDist + "m from origin";
                context.drawCenteredTextWithShadow(this.textRenderer, line, cx, yy, 0xFFFFFFFF);
                yy += 11;
            }
            if (results.size() > show) {
                context.drawCenteredTextWithShadow(this.textRenderer,
                        "§8…and " + (results.size() - show) + " more", cx, yy, 0xFF888888);
            }
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}
