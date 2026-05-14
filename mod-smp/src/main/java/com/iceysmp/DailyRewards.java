package com.iceysmp;

import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Random;

/**
 * `/icey daily` — once every 14 hours, roll a random item from a weighted
 * pool. Plays a brief on-screen "rolling" animation (cycling fake item
 * names in the title slot via TitleS2CPacket) before settling on the
 * actual reward.
 *
 * <p>Cooldown is per-player, stored in {@link PlayerStats#lastDailyMs}.
 *
 * <p>Pool excludes blocks/stairs/walls/slabs per user request — only
 * "ok / good / great" items. Tiered by weight (common → very rare).
 */
public final class DailyRewards {

    private static final long COOLDOWN_MS = 14L * 60L * 60L * 1000L; // 14 hours
    private static final Random RNG = new Random();

    private record Reward(String item, int count, int weight, String label) {}

    private static final Reward[] POOL = new Reward[] {
            // ── Common (chunky stacks) ─────────────────────────────────
            new Reward("minecraft:cooked_beef",       32, 50, "Cooked Beef"),
            new Reward("minecraft:cooked_porkchop",   32, 50, "Cooked Porkchop"),
            new Reward("minecraft:iron_ingot",        16, 50, "Iron Ingots"),
            new Reward("minecraft:gold_ingot",         8, 40, "Gold Ingots"),
            new Reward("minecraft:experience_bottle", 16, 35, "Bottles o' Enchanting"),
            new Reward("minecraft:arrow",             64, 30, "Arrows"),
            new Reward("minecraft:bone_meal",         32, 25, "Bone Meal"),
            new Reward("minecraft:string",            32, 20, "String"),
            new Reward("minecraft:gunpowder",         16, 20, "Gunpowder"),
            // ── Uncommon ───────────────────────────────────────────────
            new Reward("minecraft:diamond",            3, 20, "Diamonds"),
            new Reward("minecraft:emerald",            8, 20, "Emeralds"),
            new Reward("minecraft:golden_apple",       4, 15, "Golden Apples"),
            new Reward("minecraft:saddle",             1, 15, "Saddle"),
            new Reward("minecraft:name_tag",           4, 15, "Name Tags"),
            new Reward("minecraft:ender_pearl",        8, 15, "Ender Pearls"),
            new Reward("minecraft:blaze_rod",          8, 12, "Blaze Rods"),
            new Reward("minecraft:eye_of_ender",       4, 10, "Eyes of Ender"),
            new Reward("minecraft:nautilus_shell",     4,  8, "Nautilus Shells"),
            new Reward("minecraft:phantom_membrane",   4,  8, "Phantom Membranes"),
            // ── Rare ────────────────────────────────────────────────────
            new Reward("minecraft:netherite_scrap",    1,  6, "Netherite Scrap"),
            new Reward("minecraft:totem_of_undying",   1,  5, "Totem of Undying"),
            new Reward("minecraft:enchanted_golden_apple", 1, 4, "Enchanted Golden Apple"),
            new Reward("minecraft:beacon",             1,  3, "Beacon"),
            new Reward("minecraft:end_crystal",        4,  4, "End Crystals"),
            new Reward("minecraft:shulker_shell",      2,  3, "Shulker Shells"),
            new Reward("minecraft:trial_key",          1,  3, "Trial Key"),
            new Reward("minecraft:ghast_tear",         4,  3, "Ghast Tears"),
            // ── Very rare ──────────────────────────────────────────────
            new Reward("minecraft:elytra",             1,  1, "Elytra"),
            new Reward("minecraft:heart_of_the_sea",   1,  1, "Heart of the Sea"),
            new Reward("minecraft:nether_star",        1,  1, "Nether Star"),
            new Reward("minecraft:netherite_ingot",    1,  1, "Netherite Ingot"),
            new Reward("minecraft:dragon_head",        1,  1, "Dragon Head"),
            new Reward("minecraft:music_disc_pigstep", 1,  1, "Music Disc — Pigstep"),
    };
    private static final int TOTAL_WEIGHT;
    static {
        int t = 0;
        for (Reward r : POOL) t += r.weight;
        TOTAL_WEIGHT = t;
    }

    private DailyRewards() {}

    /** Returns ms until next roll (0 if available now). */
    public static long cooldownRemainingMs(PlayerStats ps) {
        long elapsed = System.currentTimeMillis() - ps.lastDailyMs;
        return Math.max(0, COOLDOWN_MS - elapsed);
    }

    /** Roll the daily for {@code player}. Returns true on success. The
     *  caller is responsible for the cooldown check (so the command can
     *  format a nicer "Xh Ym remaining" message). */
    public static boolean roll(ServerPlayerEntity player, PlayerStats ps) {
        MinecraftServer server = IceySmp.server;
        if (server == null) return false;
        Reward win = pickWeighted();

        // Deliver FIRST — only set cooldown if /give actually landed.
        // Try with explicit count, then fall back to count=1 (in case the
        // count token parses weird on some version), then bare item.
        String name = player.getName().getString();
        boolean delivered =
                   VersionShim.executeServerCommand(server, "give " + name + " " + win.item + " " + win.count)
                || VersionShim.executeServerCommand(server, "give " + name + " " + win.item + " 1")
                || VersionShim.executeServerCommand(server, "give " + name + " " + win.item);
        if (!delivered) {
            System.out.println("[IceySMP] daily /give failed for " + name + ": item=" + win.item);
            player.sendMessage(net.minecraft.text.Text.literal(
                    "§5§l[§d§lAttribute§7§lSMP§5§l]§r§c Daily roll failed to deliver §f" + win.label + "§c — try again, no cooldown applied"), false);
            return false;
        }
        ps.lastDailyMs = System.currentTimeMillis();

        // Animation + confirmation
        scheduleAnimation(player, win);
        player.sendMessage(net.minecraft.text.Text.literal(
                "§5§l[§d§lAttribute§7§lSMP§5§l]§r §a§l✦ Daily reward: §f§l" + win.label + " §7×" + win.count + " §a§ldelivered to your inventory"), false);

        // Server-wide chat — rare drops get announced, common drops stay quiet
        if (win.weight <= 5) {
            try {
                server.getPlayerManager().broadcast(
                        Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §a§l" + name
                                + " §r§7rolled §b§l" + win.label + "§r§7 on their daily!"),
                        false);
            } catch (Throwable ignored) {}
        }
        return true;
    }

    private static Reward pickWeighted() {
        int roll = RNG.nextInt(TOTAL_WEIGHT);
        int acc = 0;
        for (Reward r : POOL) {
            acc += r.weight;
            if (roll < acc) return r;
        }
        return POOL[0];
    }

    /** Send a fast-cycle sequence of fake titles, then the winner.
     *  Client renders each title for its stay-time before the next packet
     *  overrides it — so we use short stays for the fake ones (5 ticks =
     *  250 ms each) and a long stay (60 ticks = 3s) for the final reveal. */
    private static void scheduleAnimation(ServerPlayerEntity player, Reward win) {
        MinecraftServer server = IceySmp.server;
        if (server == null) return;
        // 8 fake rolls + 1 reveal. Schedule each onto a future server tick.
        for (int i = 0; i < 8; i++) {
            final int step = i;
            // 4 ticks between rolls = 200ms — feels like a slot machine
            scheduleTick(server, step * 4, () -> {
                Reward fake = POOL[RNG.nextInt(POOL.length)];
                try {
                    player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 6, 0));
                    player.networkHandler.sendPacket(new TitleS2CPacket(
                            Text.literal("§e§lROLLING…")));
                    player.networkHandler.sendPacket(new SubtitleS2CPacket(
                            Text.literal("§7" + fake.label)));
                } catch (Throwable ignored) {}
            });
        }
        // Final reveal at tick 36 (≈1.8s after start)
        scheduleTick(server, 36, () -> {
            try {
                player.networkHandler.sendPacket(new TitleFadeS2CPacket(8, 60, 20));
                player.networkHandler.sendPacket(new TitleS2CPacket(
                        Text.literal("§a§l✦ DAILY REWARD ✦")));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(
                        Text.literal("§f§l" + win.label + " §7×" + win.count)));
                // Levelup sound — Yarn drift between SoundEvents.X being a
                // RegistryEntry vs a raw SoundEvent across the matrix, so
                // we dispatch via the vanilla /playsound command instead
                // of crafting a PlaySoundS2CPacket. /playsound is stable
                // across versions.
                VersionShim.executeServerCommand(server,
                        "playsound minecraft:entity.player.levelup player " + player.getName().getString()
                                + " " + player.getX() + " " + player.getY() + " " + player.getZ() + " 1 1");
            } catch (Throwable ignored) {}
        });
    }

    /** Schedule a no-arg runnable to fire {@code ticksFromNow} ticks later. */
    private static void scheduleTick(MinecraftServer server, int ticksFromNow, Runnable r) {
        Scheduler.schedule(server, ticksFromNow, r);
    }
}
