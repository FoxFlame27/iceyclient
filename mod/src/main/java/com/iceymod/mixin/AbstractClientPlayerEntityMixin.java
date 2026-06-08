package com.iceymod.mixin;

import com.iceymod.cape.CapeLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;

/**
 * Injects the user's locally-uploaded cape PNG into the local
 * player's skin-record return value.
 *
 * <h2>Version portability</h2>
 * The cape-bearing record was renamed across MC versions
 * ({@code SkinTextures} on some, {@code PlayerSkin} on others, with
 * package moves between major versions). Rather than pin to a
 * specific yarn name, this mixin uses:
 *
 * <ol>
 *   <li>Multiple method-name candidates ({@code getSkinTextures},
 *       {@code getSkin}, etc.) so the mixin attaches to whichever
 *       getter exists on the running version. {@code require = 0}
 *       means a missing method is a silent no-op, not a build/load
 *       error.</li>
 *   <li>Raw {@code Object} type for the return value — the type
 *       reference isn't in the source at all, so the file compiles
 *       on every yarn matrix.</li>
 *   <li>Reflection over {@link RecordComponent}s on the returned
 *       record to find any {@code Identifier}-typed field whose name
 *       contains "cape" (case-insensitive). That field gets swapped
 *       to our custom cape identifier; everything else stays. A new
 *       record is constructed via the canonical constructor.</li>
 * </ol>
 *
 * <p>Strictly client-side, strictly local player — the
 * {@code this != mc.player} guard means remote players on a server
 * keep their own cape.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    private static boolean iceymod$mixinFiredLogged = false;
    private static boolean iceymod$swapSuccessLogged = false;
    private static boolean iceymod$recordShapeLogged = false;
    private static boolean iceymod$swapDiagLogged = false;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(
        method = {"getSkinTextures", "getSkin", "getPlayerSkin", "method_52814"},
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void iceymod$injectLocalCape(CallbackInfoReturnable cir) {
        try {
            if (!iceymod$mixinFiredLogged) {
                System.out.println("[IceyMod] CapeMixin: injector firing for the first time");
                iceymod$mixinFiredLogged = true;
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;
            if (((Object) this) != mc.player) return;

            Identifier customCape = CapeLoader.getCapeIdentifier();
            if (customCape == null) return;

            Object original = cir.getReturnValue();
            if (original == null) return;

            if (!iceymod$recordShapeLogged) {
                StringBuilder sb = new StringBuilder();
                sb.append("[IceyMod] CapeMixin: Identifier.class at runtime = ")
                  .append(Identifier.class.getName()).append('\n');
                sb.append("[IceyMod] CapeMixin: original record = ").append(original.getClass().getName());
                RecordComponent[] comps = original.getClass().getRecordComponents();
                if (comps == null) {
                    sb.append(" (not a record!)");
                } else {
                    sb.append(" components=[");
                    for (int i = 0; i < comps.length; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(comps[i].getName()).append(':').append(comps[i].getType().getName());
                    }
                    sb.append("]");
                }
                System.out.println(sb.toString());
                iceymod$recordShapeLogged = true;
            }

            Object modified = swapCapeFieldReflective(original, customCape);
            if (modified != null) {
                cir.setReturnValue(modified);
                if (!iceymod$swapSuccessLogged) {
                    System.out.println("[IceyMod] CapeMixin: cape swap SUCCESS for local player");
                    iceymod$swapSuccessLogged = true;
                }
            } else if (!iceymod$swapSuccessLogged) {
                // Log once if reflection couldn't find a cape field to swap.
                System.out.println("[IceyMod] CapeMixin: reflection found no cape field to swap "
                        + "(record class: " + original.getClass().getName() + ")");
                iceymod$swapSuccessLogged = true;
            }
        } catch (Throwable t) {
            System.out.println("[IceyMod] CapeMixin error: " + t);
        }
    }

    /**
     * Walk the record components of {@code original}, find the cape
     * {@link Identifier} field, and construct a new record of the
     * same type with that field swapped for {@code customCape}.
     *
     * <h3>Strategy</h3>
     * <ol>
     *   <li><b>Name-based</b> — look for any {@code Identifier} field
     *       whose component name contains "cape" (case-insensitive).
     *       Works in dev with yarn mappings.</li>
     *   <li><b>Position-based (runtime fallback)</b> — at runtime MC's
     *       classes carry obfuscated synthetic component names
     *       (e.g. {@code comp_1627}) so the name match fails. The cape
     *       is then identified by its <b>position</b> in the record:
     *       <ul>
     *         <li>1.21.x {@code PlayerSkin}: {@code (Identifier body,
     *             Identifier cape, Identifier elytra, Model, boolean)}
     *             — cape is the 2nd Identifier component.</li>
     *         <li>1.20.5+ {@code SkinTextures}: {@code (Identifier
     *             texture, String url, Identifier capeTexture,
     *             Identifier elytraTexture, Model, boolean)} — cape
     *             is the 2nd Identifier component.</li>
     *       </ul>
     *       Either shape, the second {@code Identifier} = cape. We use
     *       that as the fallback.</li>
     * </ol>
     */
    /**
     * Defensive {@code Identifier}-type check. Class identity (==)
     * should work in a remapped production jar, but we ALSO match by
     * full name and simple name as a safety net for any classloader
     * weirdness or unmapped-jar edge case.
     */
    private static boolean isIdentifierType(Class<?> t) {
        if (t == null) return false;
        if (t == Identifier.class) return true;
        String fq = t.getName();
        if (fq.equals(Identifier.class.getName())) return true;
        // Common intermediary + yarn forms.
        if (fq.equals("net.minecraft.class_12081")) return true;
        if (fq.equals("net.minecraft.util.Identifier")) return true;
        return false;
    }

    /**
     * Heuristic: is {@code t} "asset-shaped"? In MC 1.21.10+ the skin
     * record carries texture-wrapper objects (e.g.
     * {@code class_12079$class_12081}) instead of raw Identifiers.
     * Anything that isn't a primitive, enum, String, or boxed type is
     * a candidate wrapper.
     */
    private static boolean isAssetCandidate(Class<?> t) {
        if (t == null) return false;
        if (t.isPrimitive() || t.isEnum() || t == String.class) return false;
        if (t == Boolean.class || t == Integer.class || t == Long.class
            || t == Float.class || t == Double.class) return false;
        if (isIdentifierType(t)) return true;       // legacy direct Identifier
        return true;                                // anything else: probably a texture wrapper
    }

    /**
     * Given a wrapper class (the cape's container in the skin record)
     * and the existing wrapper instance, build a NEW wrapper instance
     * that carries our custom cape {@link Identifier}.
     *
     * <h3>Strategies (in order)</h3>
     * <ol>
     *   <li>{@code Identifier} itself — just return {@code customCape}.</li>
     *   <li>Single-arg ctor taking an {@code Identifier} — call it.</li>
     *   <li>The wrapper is a record — copy components, swap any
     *       {@code Identifier} field for our cape, rebuild.</li>
     *   <li>Walk fields and swap the first {@code Identifier} field
     *       via {@code Field.setAccessible} (records are usually
     *       immutable, but worth a try as a last resort — wrapped in
     *       try/catch so we don't crash if it's truly final).</li>
     * </ol>
     */
    private static Object buildWrapperWithIdentifier(Class<?> wrapperType,
                                                     Object existingWrapper,
                                                     Identifier customCape,
                                                     StringBuilder dbg) {
        // Always log the runtime type — declared type might be an
        // abstract/sealed supertype while the instance is a concrete
        // subclass. Subsequent reflection should target the concrete
        // class, not the declared one.
        Class<?> runtimeType = existingWrapper != null ? existingWrapper.getClass() : wrapperType;

        if (dbg != null) {
            dbg.append("    Wrapper introspection:\n");
            dbg.append("      declaredType=").append(wrapperType.getName()).append('\n');
            dbg.append("      runtimeType=").append(runtimeType.getName()).append('\n');
            dbg.append("      isRecord=").append(runtimeType.isRecord())
               .append(" isInterface=").append(runtimeType.isInterface())
               .append(" superclass=").append(runtimeType.getSuperclass() != null ? runtimeType.getSuperclass().getName() : "null").append('\n');

            dbg.append("      constructors:\n");
            for (Constructor<?> c : runtimeType.getDeclaredConstructors()) {
                dbg.append("        ").append(c.toString()).append('\n');
            }

            dbg.append("      declared fields:\n");
            for (java.lang.reflect.Field f : runtimeType.getDeclaredFields()) {
                String val;
                try {
                    f.setAccessible(true);
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        val = "(static)";
                    } else {
                        Object v = f.get(existingWrapper);
                        val = v == null ? "null" : (v.getClass().getName() + ":" + String.valueOf(v));
                    }
                } catch (Throwable t) {
                    val = "(inaccessible: " + t.getClass().getSimpleName() + ")";
                }
                dbg.append("        ").append(f.getName())
                   .append(": ").append(f.getType().getName())
                   .append(" = ").append(val).append('\n');
            }

            dbg.append("      declared methods (public, returning Identifier-like or string):\n");
            for (java.lang.reflect.Method m : runtimeType.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    Class<?> rt = m.getReturnType();
                    if (isIdentifierType(rt) || rt == String.class || rt.getName().contains("class_2960")) {
                        dbg.append("        ").append(m.getName()).append("():").append(rt.getName()).append('\n');
                    }
                }
            }
        }

        // Strategy 1: it's already an Identifier — just hand it back.
        if (isIdentifierType(wrapperType) || isIdentifierType(runtimeType)) {
            if (dbg != null) dbg.append("    -> strategy: Identifier passthrough\n");
            return customCape;
        }

        // Strategy 2: ctor(Identifier).
        for (Constructor<?> ctor : runtimeType.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 1 && isIdentifierType(params[0])) {
                try {
                    ctor.setAccessible(true);
                    Object wrapper = ctor.newInstance(customCape);
                    if (dbg != null) dbg.append("    -> strategy: ctor(Identifier)\n");
                    return wrapper;
                } catch (Throwable t) {
                    if (dbg != null) dbg.append("    -> strategy ctor(Identifier) failed: ").append(t).append('\n');
                }
            }
        }

        // Strategy 3: it's a record — copy + swap.
        RecordComponent[] wcomps = runtimeType.getRecordComponents();
        if (wcomps != null && wcomps.length > 0) {
            if (dbg != null) {
                dbg.append("    -> wrapper is a record with ").append(wcomps.length)
                   .append(" components, attempting copy+swap\n");
                for (int k = 0; k < wcomps.length; k++) {
                    dbg.append("      [").append(k).append("] ").append(wcomps[k].getName())
                       .append(':').append(wcomps[k].getType().getName()).append('\n');
                }
            }
            try {
                Object[] wargs = new Object[wcomps.length];
                Class<?>[] wparams = new Class<?>[wcomps.length];
                int idIdx = -1;
                for (int j = 0; j < wcomps.length; j++) {
                    wparams[j] = wcomps[j].getType();
                    wargs[j] = wcomps[j].getAccessor().invoke(existingWrapper);
                    if (idIdx < 0 && isIdentifierType(wcomps[j].getType())) {
                        idIdx = j;
                    }
                }
                if (idIdx >= 0) {
                    wargs[idIdx] = customCape;
                    Constructor<?> ctor = runtimeType.getDeclaredConstructor(wparams);
                    ctor.setAccessible(true);
                    Object wrapper = ctor.newInstance(wargs);
                    if (dbg != null) dbg.append("    -> strategy: record copy+swap at idx ").append(idIdx).append("\n");
                    return wrapper;
                } else if (dbg != null) {
                    dbg.append("    -> record has no Identifier component, falling through\n");
                }
            } catch (Throwable t) {
                if (dbg != null) dbg.append("    -> record copy+swap failed: ").append(t).append('\n');
            }
        }

        // Strategy 4: walk declared fields, find one of Identifier type,
        // try to overwrite via reflection. Last resort.
        try {
            for (java.lang.reflect.Field f : runtimeType.getDeclaredFields()) {
                if (isIdentifierType(f.getType()) && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    f.set(existingWrapper, customCape);
                    if (dbg != null) dbg.append("    -> strategy: field overwrite at ").append(f.getName()).append("\n");
                    return existingWrapper;
                }
            }
        } catch (Throwable t) {
            if (dbg != null) dbg.append("    -> field overwrite failed: ").append(t).append('\n');
        }

        // Strategy 5: multi-arg constructor where at least one param is
        // Identifier. Fill the other args by type-matching against the
        // existing wrapper's declared fields (works whether the wrapper
        // is a record, a regular class, or a Mojang-style data carrier).
        for (Constructor<?> ctor : runtimeType.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length < 1 || params.length > 8) continue;
            int idIdx = -1;
            for (int p = 0; p < params.length; p++) {
                if (isIdentifierType(params[p])) { idIdx = p; break; }
            }
            if (idIdx < 0) continue;
            try {
                Object[] args = new Object[params.length];
                args[idIdx] = customCape;
                // Fill other params by looking for declared fields with
                // matching types on the existing wrapper.
                for (int p = 0; p < params.length; p++) {
                    if (p == idIdx) continue;
                    args[p] = findFieldValueByType(existingWrapper, params[p]);
                }
                ctor.setAccessible(true);
                Object wrapper = ctor.newInstance(args);
                if (dbg != null) {
                    dbg.append("    -> strategy: multi-arg ctor(arity=").append(params.length)
                       .append(", idIdx=").append(idIdx).append(")\n");
                }
                return wrapper;
            } catch (Throwable t) {
                if (dbg != null) dbg.append("    -> multi-arg ctor failed: ").append(t).append('\n');
            }
        }

        // Strategy 6: static factory method on the wrapper class
        // returning the same type and taking an Identifier as first
        // param. Mojang's "Resource.of(...)" pattern.
        for (java.lang.reflect.Method m : runtimeType.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
            if (!runtimeType.isAssignableFrom(m.getReturnType())) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length < 1 || params.length > 8) continue;
            if (!isIdentifierType(params[0])) continue;
            try {
                Object[] args = new Object[params.length];
                args[0] = customCape;
                for (int p = 1; p < params.length; p++) {
                    args[p] = findFieldValueByType(existingWrapper, params[p]);
                }
                m.setAccessible(true);
                Object wrapper = m.invoke(null, args);
                if (dbg != null) {
                    dbg.append("    -> strategy: static factory ").append(m.getName())
                       .append("(arity=").append(params.length).append(")\n");
                }
                return wrapper;
            } catch (Throwable t) {
                if (dbg != null) dbg.append("    -> static factory ").append(m.getName()).append(" failed: ").append(t).append('\n');
            }
        }

        // Strategy 7: walk superclass fields too (the wrapper might
        // inherit the Identifier field from a base class).
        try {
            Class<?> c = runtimeType.getSuperclass();
            while (c != null && c != Object.class) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (isIdentifierType(f.getType()) && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        f.set(existingWrapper, customCape);
                        if (dbg != null) dbg.append("    -> strategy: inherited field overwrite at ").append(c.getName()).append('.').append(f.getName()).append("\n");
                        return existingWrapper;
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable t) {
            if (dbg != null) dbg.append("    -> inherited field overwrite failed: ").append(t).append('\n');
        }

        // Strategy 8: enclosing-class static factory. The wrapper is
        // a nested type (class_12079$class_12081 — inner of class_12079);
        // the outer often hosts the constructor / factory. Look for any
        // static method on the ENCLOSING class returning something
        // assignable to wrapperType, taking an Identifier as first arg.
        Class<?> enclosing = wrapperType.getEnclosingClass();
        if (enclosing != null) {
            for (java.lang.reflect.Method m : enclosing.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                if (!wrapperType.isAssignableFrom(m.getReturnType())) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length < 1 || params.length > 8) continue;
                if (!isIdentifierType(params[0])) continue;
                try {
                    Object[] args = new Object[params.length];
                    args[0] = customCape;
                    for (int p = 1; p < params.length; p++) {
                        args[p] = findFieldValueByType(existingWrapper, params[p]);
                    }
                    m.setAccessible(true);
                    Object wrapper = m.invoke(null, args);
                    if (dbg != null) dbg.append("    -> strategy: enclosing factory ").append(enclosing.getName()).append('.').append(m.getName()).append("\n");
                    return wrapper;
                } catch (Throwable t) {
                    if (dbg != null) dbg.append("    -> enclosing factory ").append(m.getName()).append(" failed: ").append(t).append('\n');
                }
            }
        }

        // Strategy 9: Java dynamic Proxy on the interface. The
        // introspection log showed class_12079$class_12081 is an
        // interface with a single Identifier-returning accessor
        // (comp_NNNN). A proxy implementing the interface that returns
        // customCape from that accessor is a valid wrapper at the
        // record-canonical-constructor's API surface.
        if (wrapperType.isInterface()) {
            try {
                final Object delegate = existingWrapper;
                Object wrapper = java.lang.reflect.Proxy.newProxyInstance(
                    wrapperType.getClassLoader(),
                    new Class<?>[]{ wrapperType },
                    (proxy, method, invokeArgs) -> {
                        // Any zero-arg method returning Identifier: hand
                        // back the custom cape. Other methods: delegate
                        // to the existing wrapper if we have one; else
                        // return a sensible default.
                        if (method.getParameterCount() == 0 && isIdentifierType(method.getReturnType())) {
                            return customCape;
                        }
                        if (delegate != null) {
                            try { return method.invoke(delegate, invokeArgs); }
                            catch (Throwable ignored) {}
                        }
                        Class<?> rt = method.getReturnType();
                        if (rt == void.class) return null;
                        if (rt == boolean.class) return Boolean.FALSE;
                        if (rt == int.class) return 0;
                        if (rt == long.class) return 0L;
                        if (rt == float.class) return 0f;
                        if (rt == double.class) return 0d;
                        return null;
                    }
                );
                if (dbg != null) dbg.append("    -> strategy: dynamic Proxy on interface\n");
                return wrapper;
            } catch (Throwable t) {
                if (dbg != null) dbg.append("    -> dynamic Proxy failed: ").append(t).append('\n');
            }
        }

        if (dbg != null) dbg.append("    -> all strategies exhausted, returning null\n");
        return null;
    }

    /**
     * Find any declared field on {@code obj} whose type is assignable
     * to {@code paramType}, return its value. Used by multi-arg ctor
     * strategies to recover the existing wrapper's other args.
     */
    private static Object findFieldValueByType(Object obj, Class<?> paramType) {
        if (obj == null) return null;
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (!paramType.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    return f.get(obj);
                } catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }
        // Type fallback for primitives — return defaults.
        if (paramType == boolean.class) return Boolean.FALSE;
        if (paramType == int.class) return 0;
        if (paramType == long.class) return 0L;
        if (paramType == float.class) return 0f;
        if (paramType == double.class) return 0d;
        return null;
    }

    private static Object swapCapeFieldReflective(Object original, Identifier customCape) throws Throwable {
        Class<?> cls = original.getClass();
        RecordComponent[] comps = cls.getRecordComponents();
        if (comps == null || comps.length == 0) {
            if (!iceymod$swapDiagLogged) {
                System.out.println("[IceyMod] CapeMixin.swap: NOT a record (comps null/empty), bailing");
                iceymod$swapDiagLogged = true;
            }
            return null;
        }

        StringBuilder dbg = iceymod$swapDiagLogged ? null : new StringBuilder();
        if (dbg != null) {
            dbg.append("[IceyMod] CapeMixin.swap: scanning ").append(comps.length).append(" components\n");
        }

        Object[] args = new Object[comps.length];
        Class<?>[] paramTypes = new Class<?>[comps.length];

        int nameMatchIdx = -1;
        int idPositionalIdx = -1;       // 2nd Identifier
        int assetPositionalIdx = -1;    // 2nd asset-shaped wrapper (1.21.10+ shape)
        int idCount = 0;
        int assetCount = 0;
        Class<?> firstAssetType = null;

        for (int i = 0; i < comps.length; i++) {
            RecordComponent rc = comps[i];
            paramTypes[i] = rc.getType();
            args[i] = rc.getAccessor().invoke(original);
            Class<?> t = rc.getType();
            boolean isId = isIdentifierType(t);
            boolean isAsset = !isId && isAssetCandidate(t);
            if (dbg != null) {
                dbg.append("  [").append(i).append("] name=").append(rc.getName())
                   .append(" type=").append(t.getName())
                   .append(" isIdentifier=").append(isId)
                   .append(" isAsset=").append(isAsset).append('\n');
            }
            if (isId) {
                idCount++;
                if (idCount == 2 && idPositionalIdx < 0) idPositionalIdx = i;
                if (nameMatchIdx < 0 && rc.getName().toLowerCase().contains("cape")) nameMatchIdx = i;
            } else if (isAsset) {
                if (firstAssetType == null) firstAssetType = t;
                // Only count consecutive same-typed wrappers — they're the
                // body/cape/elytra trio. A differently-typed field (Model,
                // boolean) breaks the streak naturally because isAsset is
                // false for those.
                if (t.equals(firstAssetType)) {
                    assetCount++;
                    if (assetCount == 2 && assetPositionalIdx < 0) assetPositionalIdx = i;
                }
                if (nameMatchIdx < 0 && rc.getName().toLowerCase().contains("cape")) nameMatchIdx = i;
            }
        }

        // Pick target: explicit "cape" name wins; else 2nd Identifier
        // (legacy shapes); else 2nd asset-wrapper (1.21.10+ shape).
        int targetIdx = nameMatchIdx >= 0 ? nameMatchIdx
                      : idPositionalIdx >= 0 ? idPositionalIdx
                      : assetPositionalIdx;
        if (dbg != null) {
            dbg.append("[IceyMod] CapeMixin.swap: idCount=").append(idCount)
               .append(" assetCount=").append(assetCount)
               .append(" nameMatchIdx=").append(nameMatchIdx)
               .append(" idPositionalIdx=").append(idPositionalIdx)
               .append(" assetPositionalIdx=").append(assetPositionalIdx)
               .append(" -> targetIdx=").append(targetIdx).append('\n');
        }

        if (targetIdx < 0) {
            if (dbg != null) { System.out.println(dbg.toString()); iceymod$swapDiagLogged = true; }
            return null;
        }

        // Build the replacement value for the chosen slot.
        // The cape slot is null for accounts without a Mojang cape —
        // we can't introspect what we don't have. Fall back to a
        // sibling slot of the same type (body is always populated)
        // so buildWrapperWithIdentifier has a concrete runtime class
        // to clone from.
        Class<?> targetType = comps[targetIdx].getType();
        Object existingForBuild = args[targetIdx];
        if (existingForBuild == null) {
            for (int i = 0; i < comps.length; i++) {
                if (i == targetIdx) continue;
                if (comps[i].getType() == targetType && args[i] != null) {
                    existingForBuild = args[i];
                    if (dbg != null) dbg.append("[IceyMod] CapeMixin.swap: cape slot null, using sibling [").append(i).append("] as template\n");
                    break;
                }
            }
        }
        Object newValue = buildWrapperWithIdentifier(targetType, existingForBuild, customCape, dbg);
        if (newValue == null) {
            if (dbg != null) {
                dbg.append("[IceyMod] CapeMixin.swap: buildWrapperWithIdentifier returned null\n");
                System.out.println(dbg.toString());
                iceymod$swapDiagLogged = true;
            }
            return null;
        }
        args[targetIdx] = newValue;

        if (dbg != null) {
            System.out.println(dbg.toString());
            iceymod$swapDiagLogged = true;
        }

        Constructor<?> ctor = cls.getDeclaredConstructor(paramTypes);
        ctor.setAccessible(true);
        return ctor.newInstance(args);
    }
}
