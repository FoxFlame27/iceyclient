package com.iceymod.menu;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;

/**
 * Makes the running game play as another account, from the main menu.
 *
 * The game keeps "who am I" in a handful of fields that are set once at
 * start: the user (name, id, sign-in), the service object that speaks to
 * Mojang as that user, the chat signing keys, and the player's own
 * profile. Their names and even their types moved between game versions,
 * so they are found by type and set by reflection, each on its own: the
 * user is what joining a server needs, the rest follows as far as the
 * version allows.
 */
public final class AccountSwitch {
    private AccountSwitch() {}

    /** @return null when the game now plays as the account, else what went wrong */
    public static String apply(String name, String uuidText, String accessToken) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null || mc.getConnection() != null) return "Leave the world first";
        UUID uuid;
        try {
            String u = uuidText.replace("-", "");
            uuid = new UUID(Long.parseUnsignedLong(u.substring(0, 16), 16), Long.parseUnsignedLong(u.substring(16), 16));
        } catch (Throwable t) { return "Bad account"; }

        User user;
        try { user = newUser(name, uuid, accessToken); }
        catch (Throwable t) { System.out.println("[IceyMod] account switch: no user " + t); return "This game version cannot switch"; }
        if (!set(mc, User.class, user)) return "This game version cannot switch";
        System.out.println("[IceyMod] now playing as " + name);

        // Everything below speaks to Mojang as the new account. Without it
        // the game still joins servers as that account, but chat signing
        // would carry the old account's keys.
        Object api = null;
        try { api = userApi(mc, accessToken); } catch (Throwable t) { System.out.println("[IceyMod] account switch: no api service " + t); }
        if (api != null) {
            try {
                Class<?> apiType = Class.forName("com.mojang.authlib.minecraft.UserApiService");
                set(mc, apiType, api);
                Method create = null;
                for (Method m : ProfileKeyPairManager.class.getMethods())
                    if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 3 && m.getReturnType() == ProfileKeyPairManager.class) create = m;
                if (create != null) set(mc, ProfileKeyPairManager.class, create.invoke(null, api, user, mc.gameDirectory.toPath()));
                for (Method m : ReportingContext.class.getMethods())
                    if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 2 && m.getReturnType() == ReportingContext.class
                            && m.getParameterTypes()[0] == ReportEnvironment.class)
                        set(mc, ReportingContext.class, m.invoke(null, ReportEnvironment.local(), api));
                // The properties (chat allowed, and so on) of the new account.
                final Object apiF = api;
                for (Method m : apiType.getMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType().getName().endsWith("UserProperties")) {
                        setFuture(mc, "UserProperties", CompletableFuture.supplyAsync(() -> { try { return m.invoke(apiF); } catch (Throwable t) { return null; } }));
                        break;
                    }
                }
            } catch (Throwable t) { System.out.println("[IceyMod] account switch: partly done " + t); }
        }
        try { profile(mc, uuid); } catch (Throwable t) { System.out.println("[IceyMod] account switch: profile " + t); }
        return null;
    }

    private static User newUser(String name, UUID uuid, String token) throws Exception {
        Constructor<?> best = null;
        for (Constructor<?> c : User.class.getConstructors()) if (best == null || c.getParameterCount() > best.getParameterCount()) best = c;
        if (best == null) throw new IllegalStateException("no constructor");
        Class<?>[] types = best.getParameterTypes();
        Object[] args = new Object[types.length];
        int strings = 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i] == String.class) args[i] = strings++ == 0 ? name : token;
            else if (types[i] == UUID.class) args[i] = uuid;
            else if (types[i] == Optional.class) args[i] = Optional.empty();
            else if (types[i].isEnum()) {
                Object pick = null;
                for (Object e : types[i].getEnumConstants()) if (((Enum<?>) e).name().equals("MSA")) pick = e;
                args[i] = pick != null ? pick : types[i].getEnumConstants()[types[i].getEnumConstants().length - 1];
            }
        }
        return (User) best.newInstance(args);
    }

    /** The object that speaks to Mojang's services as the account with this sign-in. */
    private static Object userApi(Minecraft mc, String token) throws Exception {
        try {
            Class<?> c = Class.forName("com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService");
            Object service = c.getConstructor(java.net.Proxy.class).newInstance(mc.getProxy());
            return c.getMethod("createUserApiService", String.class).invoke(service, token);
        } catch (ClassNotFoundException | NoSuchMethodException older) {
            Class<?> c = Class.forName("com.mojang.authlib.services.MinecraftServicesDiscoveryService");
            Object service = c.getMethod("create", java.net.Proxy.class).invoke(null, mc.getProxy());
            return c.getMethod("createUserApiService", String.class).invoke(service, token);
        }
    }

    /** The player's own profile (the skin in a singleplayer world comes from it). */
    private static void profile(Minecraft mc, UUID uuid) throws Exception {
        Field target = futureField("ProfileResult");
        if (target == null) return;
        // Find whichever object the game holds that can fetch a profile by id.
        java.util.List<Object> holders = new java.util.ArrayList<>();
        for (Field f : Minecraft.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            String n = f.getType().getName();
            if (!n.startsWith("com.mojang.authlib") && !n.endsWith(".Services")) continue;
            f.setAccessible(true);
            Object o = f.get(mc);
            if (o == null) continue;
            holders.add(o);
            if (n.endsWith(".Services")) for (Method m : o.getClass().getMethods())
                if (m.getParameterCount() == 0 && m.getReturnType().getName().startsWith("com.mojang.authlib")) { try { Object in = m.invoke(o); if (in != null) holders.add(in); } catch (Throwable ignored) {} }
        }
        for (Object h : holders) {
            for (Method m : h.getClass().getMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length == 2 && pt[0] == UUID.class && pt[1] == boolean.class && m.getReturnType().getName().endsWith("ProfileResult")) {
                    m.setAccessible(true);
                    target.setAccessible(true);
                    target.set(mc, CompletableFuture.supplyAsync(() -> { try { return m.invoke(h, uuid, true); } catch (Throwable t) { return null; } }));
                    return;
                }
            }
        }
    }

    private static Field futureField(String generic) {
        for (Field f : Minecraft.class.getDeclaredFields())
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == CompletableFuture.class && f.getGenericType().getTypeName().contains(generic)) return f;
        return null;
    }

    private static void setFuture(Minecraft mc, String generic, Object value) throws Exception {
        Field f = futureField(generic);
        if (f == null) return;
        f.setAccessible(true);
        f.set(mc, value);
    }

    /** Sets the one field of the game object that has exactly this type. */
    private static boolean set(Minecraft mc, Class<?> type, Object value) {
        boolean done = false;
        for (Field f : Minecraft.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() != type) continue;
            try { f.setAccessible(true); f.set(mc, value); done = true; }
            catch (Throwable t) { System.out.println("[IceyMod] account switch: " + type.getSimpleName() + " not set: " + t); }
        }
        return done;
    }
}
