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
        int positionalCapeIdx = -1;
        int identifierCount = 0;

        for (int i = 0; i < comps.length; i++) {
            RecordComponent rc = comps[i];
            paramTypes[i] = rc.getType();
            args[i] = rc.getAccessor().invoke(original);
            boolean isId = isIdentifierType(rc.getType());
            if (dbg != null) {
                dbg.append("  [").append(i).append("] name=").append(rc.getName())
                   .append(" type=").append(rc.getType().getName())
                   .append(" isIdentifier=").append(isId).append('\n');
            }
            if (isId) {
                identifierCount++;
                if (identifierCount == 2 && positionalCapeIdx < 0) {
                    positionalCapeIdx = i;
                }
                if (nameMatchIdx < 0 && rc.getName().toLowerCase().contains("cape")) {
                    nameMatchIdx = i;
                }
            }
        }

        int targetIdx = nameMatchIdx >= 0 ? nameMatchIdx : positionalCapeIdx;
        if (dbg != null) {
            dbg.append("[IceyMod] CapeMixin.swap: identifierCount=").append(identifierCount)
               .append(" nameMatchIdx=").append(nameMatchIdx)
               .append(" positionalCapeIdx=").append(positionalCapeIdx)
               .append(" targetIdx=").append(targetIdx);
            System.out.println(dbg.toString());
            iceymod$swapDiagLogged = true;
        }

        if (targetIdx < 0) return null;
        args[targetIdx] = customCape;

        Constructor<?> ctor = cls.getDeclaredConstructor(paramTypes);
        ctor.setAccessible(true);
        return ctor.newInstance(args);
    }
}
