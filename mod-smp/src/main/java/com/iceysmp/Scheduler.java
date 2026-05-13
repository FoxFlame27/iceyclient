package com.iceysmp;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tiny tick-counter scheduler. Tasks are queued with a target absolute
 * tick; {@link #tick(MinecraftServer)} fires anything that's reached its
 * deadline. Single-threaded — all callbacks run on the server tick
 * thread, same place we already do everything else.
 *
 * <p>Lighter than spawning {@code ScheduledExecutorService} threads and
 * keeps us off the off-thread danger of touching ServerPlayerEntity from
 * outside the tick thread.
 */
public final class Scheduler {

    private record Pending(long fireAtTick, Runnable r) {}

    private static final AtomicLong tickCounter = new AtomicLong(0);
    private static final Deque<Pending> pending = new ArrayDeque<>();

    private Scheduler() {}

    /** Schedule a runnable to fire {@code ticksFromNow} server ticks later. */
    public static synchronized void schedule(MinecraftServer server, int ticksFromNow, Runnable r) {
        long t = tickCounter.get() + Math.max(0, ticksFromNow);
        pending.add(new Pending(t, r));
    }

    public static synchronized void tick(MinecraftServer server) {
        long now = tickCounter.incrementAndGet();
        if (pending.isEmpty()) return;
        // Drain everything whose deadline has been reached. Order isn't
        // guaranteed to be sorted, so we walk every entry — fine since
        // the queue is small in practice (animations ≤ 10 pending).
        var it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.fireAtTick <= now) {
                try { p.r.run(); }
                catch (Throwable t) { System.out.println("[IceySMP] scheduled task failed: " + t); }
                it.remove();
            }
        }
    }
}
