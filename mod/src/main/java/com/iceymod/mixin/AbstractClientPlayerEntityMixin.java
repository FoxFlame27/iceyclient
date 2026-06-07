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

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(
        method = {"getSkinTextures", "getSkin", "method_52814"},
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void iceymod$injectLocalCape(CallbackInfoReturnable cir) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;
            if (((Object) this) != mc.player) return;

            Identifier customCape = CapeLoader.getCapeIdentifier();
            if (customCape == null) return;

            Object original = cir.getReturnValue();
            if (original == null) return;

            Object modified = swapCapeFieldReflective(original, customCape);
            if (modified != null) {
                cir.setReturnValue(modified);
            }
        } catch (Throwable t) {
            // Silent fall-through — better the original cape than a
            // crashed render loop for the whole session.
        }
    }

    /**
     * Walk the record components of {@code original}, find any
     * {@code Identifier}-typed component named "cape*" (case-
     * insensitive), and construct a new record of the same type with
     * that field swapped for {@code customCape}. All other fields
     * carry over unchanged.
     *
     * <p>Returns null if {@code original} isn't a record, or has no
     * cape-named identifier component, or the canonical constructor
     * isn't accessible — the mixin then leaves the original alone.
     */
    private static Object swapCapeFieldReflective(Object original, Identifier customCape) throws Throwable {
        Class<?> cls = original.getClass();
        RecordComponent[] comps = cls.getRecordComponents();
        if (comps == null || comps.length == 0) return null;

        Object[] args = new Object[comps.length];
        Class<?>[] paramTypes = new Class<?>[comps.length];
        boolean foundCape = false;

        for (int i = 0; i < comps.length; i++) {
            RecordComponent rc = comps[i];
            paramTypes[i] = rc.getType();
            Object val = rc.getAccessor().invoke(original);
            String name = rc.getName().toLowerCase();
            boolean isCapeField = rc.getType() == Identifier.class && name.contains("cape");
            if (isCapeField) {
                args[i] = customCape;
                foundCape = true;
            } else {
                args[i] = val;
            }
        }
        if (!foundCape) return null;

        Constructor<?> ctor = cls.getDeclaredConstructor(paramTypes);
        ctor.setAccessible(true);
        return ctor.newInstance(args);
    }
}
