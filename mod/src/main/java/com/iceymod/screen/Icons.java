package com.iceymod.screen;

import java.util.HashMap;
import java.util.Map;

/**
 * The line icons of the menus: glyphs of the Lucide icon set (ISC licence,
 * see assets/iceymod/font/lucide-LICENSE.txt), shipped as a small font
 * that holds only the icons named here (assets/iceymod/font/lucide.ttf;
 * tools/README-icons.txt says how it is cut down). One per module of the
 * Y menu, and the ones the menus themselves use.
 */
final class Icons {
    private Icons() {}

    static final char SEARCH = '\ue151';
    static final char CLOSE = '\ue1b2';
    static final char SETTINGS = '\ue154';
    static final char MODS = '\ue29a';
    static final char HUD = '\ue1c1';
    static final char CARDS = '\ue0ff';
    static final char PANELS = '\ue099';
    static final char CLASSIC = '\ue58a';
    static final char PALETTE = '\ue1dd';
    static final char STAR = '\ue176';
    static final char CHECK = '\ue06c';
    static final char CHEVRON = '\ue06f';
    static final char ALL = '\ue4fa';
    static final char G_HUD = '\ue11d';
    static final char G_COMBAT = '\ue2b4';
    static final char G_WORLD = '\ue1f3';
    static final char G_PLAYER = '\ue19f';
    static final char G_PERFORMANCE = '\ue1b4';
    static final char FALLBACK = '\ue29c';
    static final char DOTS = '\ue0b6';
    static final char POWER = '\ue140';
    static final char KEYBIND = '\ue284';
    static final char BACK = '\ue048';
    static final char PLUS = '\ue13d';
    static final char PENCIL = '\ue1f9';
    static final char TRASH = '\ue18e';
    static final char REFRESH = '\ue145';
    static final char PLUG = '\ue37f';
    static final char PLAY = '\ue13c';
    static final char SIGNAL0 = '\ue263';
    static final char SIGNAL1 = '\ue261';
    static final char SIGNAL2 = '\ue262';
    static final char SIGNAL3 = '\ue260';
    static final char SIGNAL4 = '\ue25f';
    static final char VOLUME = '\ue1ab';
    static final char MUSIC = '\ue122';
    static final char FULLSCREEN = '\ue112';
    static final char MOUNTAIN = '\ue231';
    static final char BLUR = '\ue59c';
    static final char CONTROLS = '\ue0df';
    static final char LANGUAGE = '\ue0fe';
    static final char SKIN = '\ue1ca';
    static final char CHAT = '\ue117';
    static final char ACCESSIBILITY = '\ue297';
    static final char ONLINE = '\ue1ae';
    static final char FOLDER = '\ue0d7';
    static final char USER_PLUS = '\ue1a2';
    static final char LOGOUT = '\ue10e';
    static final char BELL = '\ue059';
    static final char VIDEO = '\ue0f6';
    static final char INFO = '\ue0f9';
    static final char WARNING = '\ue193';
    static final char ARROW_RIGHT = '\ue049';
    static final char USERS = '\ue1a4';
    static final char GLOBE = '\ue0e8';
    static final char SERVER = '\ue153';
    static final char CLOCK = '\ue087';
    static final char GAUGE = '\ue1bf';
    static final char SUN = '\ue178';
    static final char EYE = '\ue0ba';
    static final char PACKS = '\ue129';
    static final char VSYNC = '\ue258';
    static final char WORLD = '\ue1f3';
    static final char SWORD = '\ue2b3';
    static final char HEART = '\ue0f2';
    static final char HOME = '\ue0f5';
    static final char USER = '\ue19f';
    static final char FRIENDS = '\ue46e';
    static final char SEND = '\ue152';
    static final char MAP_PIN = '\ue111';
    static final char SHUFFLE = '\ue15e';
    static final char SPARKLES = '\ue412';
    static final char LINK = '\ue102';
    static final char COPY = '\ue09e';
    static final char SAVE = '\ue14d';
    static final char HARD_DRIVE = '\ue0ed';
    static final char GAMEMODE = '\ue2b4';
    static final char CALENDAR = '\ue2b9';
    static final char CHEVRON_DOWN = '\ue06d';
    static final char CHEVRON_LEFT = '\ue06e';
    static final char MONITOR = '\ue11d';
    static final char KEYBOARD = '\ue284';
    static final char LAYERS = '\ue529';

    private static final Map<String, Character> MODULE = new HashMap<>();
    static {
        MODULE.put("air", '\ue0b5');
        MODULE.put("antiafk", '\ue096');
        MODULE.put("armor", '\ue158');
        MODULE.put("arrows", '\ue493');
        MODULE.put("automaceswap", '\ue146');
        MODULE.put("autorespawn", '\ue148');
        MODULE.put("autosprint", '\ue1b4');
        MODULE.put("autototem", '\ue1ff');
        MODULE.put("bedcoords", '\ue2c1');
        MODULE.put("biome", '\ue2f5');
        MODULE.put("biomelocator", '\ue110');
        MODULE.put("blocksmined", '\ue5c6');
        MODULE.put("blockunder", '\ue455');
        MODULE.put("chunk", '\ue0e9');
        MODULE.put("combo", '\ue2b4');
        MODULE.put("cooldown", '\ue1e0');
        MODULE.put("coords", '\ue111');
        MODULE.put("cps", '\ue120');
        MODULE.put("crosshair", '\ue0ac');
        MODULE.put("crystalcps", '\ue2d2');
        MODULE.put("crystaltracker", '\ue242');
        MODULE.put("daycounter", '\ue2b9');
        MODULE.put("deaths", '\ue221');
        MODULE.put("direction", '\ue09b');
        MODULE.put("distwalked", '\ue53e');
        MODULE.put("elytra", '\ue0be');
        MODULE.put("entitycount", '\ue4f5');
        MODULE.put("falldist", '\ue042');
        MODULE.put("fps", '\ue1bf');
        MODULE.put("fpsboost_clouds", '\ue08d');
        MODULE.put("fpsboost_graphics", '\ue11d');
        MODULE.put("fpsboost_particles", '\ue47e');
        MODULE.put("fpsboost_shaderguard", '\ue1fe');
        MODULE.put("fpsboost_shadows", '\ue09d');
        MODULE.put("freecam", '\ue1a5');
        MODULE.put("freelook", '\ue0ba');
        MODULE.put("fullbright", '\ue178');
        MODULE.put("gapples", '\ue34e');
        MODULE.put("hitboxes", '\ue1cb');
        MODULE.put("hostilecount", '\ue20e');
        MODULE.put("hotbartext", '\ue129');
        MODULE.put("invslots", '\ue2c8');
        MODULE.put("itemcd", '\ue296');
        MODULE.put("itemglow", '\ue412');
        MODULE.put("keystrokes", '\ue284');
        MODULE.put("killstreak", '\ue373');
        MODULE.put("lastdamage", '\ue2d6');
        MODULE.put("lookat", '\ue537');
        MODULE.put("macedamage", '\ue0e0');
        MODULE.put("memory", '\ue445');
        MODULE.put("mobkills", '\ue2b3');
        MODULE.put("nearesthostile", '\ue497');
        MODULE.put("nearestplayer", '\ue1a4');
        MODULE.put("net_buffer", '\ue0ad');
        MODULE.put("net_dnscache", '\ue0e8');
        MODULE.put("net_ipv4", '\ue125');
        MODULE.put("net_nodelay", '\ue0bd');
        MODULE.put("nethercoords", '\ue0d2');
        MODULE.put("nobob", '\ue37d');
        MODULE.put("nofovchange", '\ue257');
        MODULE.put("nohurtcam", '\ue065');
        MODULE.put("obsidian", '\ue061');
        MODULE.put("offhand", '\ue1d7');
        MODULE.put("pearls", '\ue345');
        MODULE.put("perspective", '\ue064');
        MODULE.put("ping", '\ue25f');
        MODULE.put("pitchyaw", '\ue2ea');
        MODULE.put("potions", '\ue0d5');
        MODULE.put("safewalk", '\ue3b9');
        MODULE.put("saturation", '\ue25b');
        MODULE.put("server", '\ue153');
        MODULE.put("session", '\ue1ad');
        MODULE.put("structurelocator", '\ue3e0');
        MODULE.put("time", '\ue087');
        MODULE.put("toolwarn", '\ue0ec');
        MODULE.put("totempops", '\ue36e');
        MODULE.put("totems", '\ue0f2');
        MODULE.put("tps", '\ue038');
        MODULE.put("velocity", '\ue1b0');
        MODULE.put("waypoints", '\ue0d1');
        MODULE.put("weather", '\ue216');
        MODULE.put("worldtime", '\ue2b2');
        MODULE.put("ylevel", '\ue529');
        MODULE.put("zoom", '\ue1b6');
    }

    /** The icon of a module (by its id); a puzzle piece for one that has none yet. */
    static char of(String moduleId) {
        Character c = MODULE.get(moduleId);
        return c != null ? c : FALLBACK;
    }
}
