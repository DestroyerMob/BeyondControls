package dev.isxander.controlify.api.bind;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A reusable contextual input layer. Bindings in an active layer take ownership
 * of their physical inputs from bindings in lower-priority layers.
 */
public record InputBindingLayer(
        @NotNull Identifier id,
        int priority,
        @NotNull Predicate<InputBindingActivationContext> activationCondition
) {
    public InputBindingLayer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(activationCondition, "activationCondition");
    }

    /**
     * Creates a layer which is active while another binding is physically held.
     * The modifier binding itself does not need to consume or suppress its input.
     */
    public static InputBindingLayer whileHeld(@NotNull Identifier id, int priority,
                                              @NotNull InputBindingSupplier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        return new InputBindingLayer(id, priority, context -> context.isPressed(modifier));
    }

    public boolean isActive(InputBindingActivationContext context) {
        return activationCondition.test(context);
    }
}
