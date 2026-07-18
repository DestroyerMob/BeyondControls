package dev.isxander.controlify.api.bind;

import dev.isxander.controlify.bindings.input.ButtonInput;
import dev.isxander.controlify.bindings.input.Input;
import net.minecraft.resources.Identifier;

/** Common controller inputs for programmatic defaults in pack integrations. */
public final class ControllerInputs {
    private ControllerInputs() {
    }

    public static Input button(String path) {
        return new ButtonInput(Identifier.fromNamespaceAndPath("controlify", "button/" + path));
    }
}
