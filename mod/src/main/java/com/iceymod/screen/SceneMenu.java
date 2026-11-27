package com.iceymod.screen;

import com.iceymod.MenuPrefs;
import com.iceymod.compat.MC;
import com.iceymod.menu.Scenes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Decides what the player sees where the title screen would be: the
 * question "which main menu?" at the very first start, the scene menu if
 * that is the choice, else the title screen as it is.
 */
public final class SceneMenu {
    private SceneMenu() {}

    /** Set when the scene menu threw: the normal title screen takes over for the rest of the session. */
    static volatile boolean failed;
    /** The question has been shown (or answered) in this session. */
    static volatile boolean asked;
    private static int titleTicks;

    public static void onTick(Minecraft mc) {
        Screen cur = MC.screen(mc);
        if (!(cur instanceof TitleScreen)) { titleTicks = 0; return; }
        if (MenuPrefs.isSceneTitle()) {
            if (!failed && com.iceymod.menu.SceneModel.of(Scenes.current()) != null) MC.setScreen(mc, new SceneMenuScreen());
            return;
        }
        if (MenuPrefs.titleMenu().isEmpty() && !asked) {
            // A moment after the title screen is up, so the loading screen has faded.
            if (++titleTicks < 30) return;
            asked = true;
            MC.setScreen(mc, new MenuChooserScreen());
        }
    }
}
