package com.iceymod.screen;

import com.google.gson.JsonObject;
import com.iceymod.compat.Gfx;
import com.iceymod.compat.IceyScreen;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * A quick message back to the friend who just wrote, without leaving the
 * game. The game is not signed in to the friends service, the launcher is:
 * so the reply is left as a small file in the config folder
 * (iceymod-reply-<time>.json) and the launcher sends it and answers with a
 * notice ("Sent to ...").
 */
public class ReplyScreen extends IceyScreen {

    private final String toUuid;
    private final String toName;
    private EditBox input;
    private final long openedAt = System.currentTimeMillis();
    private boolean prevEnterDown = true;   // Enter held while opening does not send
    private boolean sent;

    public ReplyScreen(String toUuid, String toName) {
        super(Component.literal("Reply"));
        this.toUuid = toUuid;
        this.toName = toName;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int w = 260, h = 22, gap = 6;
        int y = this.height / 2 - 20;
        String keep = input != null ? input.getValue() : "";
        input = new EditBox(this.font, cx - w / 2, y, w, h, Component.literal(""));
        input.setMaxLength(200);
        input.setValue(keep);
        input.setFocused(true);
        addRenderableWidget(input);
        setInitialFocus(input);
        y += h + gap;
        int half = (w - gap) / 2;
        addRenderableWidget(Button.builder(Component.literal("§aSend"), b -> send())
                .bounds(cx - w / 2, y, half, h).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(cx - w / 2 + half + gap, y, half, h).build());
    }

    private void send() {
        if (sent || input == null) return;
        String text = input.getValue().trim();
        if (text.isEmpty()) return;
        sent = true;
        try {
            long id = System.currentTimeMillis();
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("to", toUuid);
            o.addProperty("name", toName);
            o.addProperty("text", text);
            Path dir = FabricLoader.getInstance().getConfigDir();
            Path tmp = dir.resolve("iceymod-reply-" + id + ".tmp");
            Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, dir.resolve("iceymod-reply-" + id + ".json"));
        } catch (Throwable t) {
            System.out.println("[IceyMod] reply not written: " + t);
        }
        onClose();
    }

    @Override
    protected void renderScreenBackground(Gfx g, int mouseX, int mouseY, float delta) {
        Glass.backdrop(g, this.width, this.height);
    }

    @Override
    protected void renderScreen(Gfx g, int mouseX, int mouseY, float delta) {
        // The key that opened this screen must not end up as the first letter.
        if (input != null && System.currentTimeMillis() - openedAt < 120 && !input.getValue().isEmpty()) input.setValue("");
        try {
            boolean down = com.iceymod.compat.Input.keyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
            if (down && !prevEnterDown) send();
            prevEnterDown = down;
        } catch (Throwable ignored) {}
        superRender(g, mouseX, mouseY, delta);
        g.drawCenteredString(this.font, "§b§lReply to " + toName, this.width / 2, this.height / 2 - 48, 0xFFFFFFFF);
        g.drawCenteredString(this.font, "§7Enter sends, Esc closes", this.width / 2, this.height / 2 - 36, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
