package dev.isxander.controlify.api.bind;

import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.input.ControllerStateView;
import net.minecraft.client.Minecraft;

/**
 * Live state used to decide whether a controller binding participates in the
 * current contextual input layer.
 */
public record InputBindingActivationContext(
        Minecraft minecraft,
        ControllerEntity controller,
        ControllerStateView state
) {
    /**
     * Reads another binding directly from this tick's controller state. This is
     * intended for modifier/layer bindings and does not depend on binding update order.
     */
    public boolean isPressed(InputBindingSupplier supplier) {
        InputBinding binding = supplier.onOrNull(controller);
        return binding != null
                && binding.boundInput().state(state) >= controller.settings().input.buttonActivationThreshold;
    }
}
