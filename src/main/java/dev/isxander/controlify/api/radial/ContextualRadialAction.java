package dev.isxander.controlify.api.radial;

import dev.isxander.controlify.controller.ControllerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Mutable context used to resolve a configured radial-menu slot when the menu opens.
 * Integrations can inspect the client state and replace the action, while the user's
 * configured action remains available as a safe fallback.
 */
public final class ContextualRadialAction {
    private final Minecraft minecraft;
    private final ControllerEntity controller;
    private final int slot;
    private final Identifier configuredAction;
    private Identifier resolvedAction;

    public ContextualRadialAction(Minecraft minecraft, ControllerEntity controller, int slot, Identifier configuredAction) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.slot = slot;
        this.configuredAction = Objects.requireNonNull(configuredAction, "configuredAction");
        this.resolvedAction = configuredAction;
    }

    public Minecraft minecraft() {
        return minecraft;
    }

    public ControllerEntity controller() {
        return controller;
    }

    public int slot() {
        return slot;
    }

    public Identifier configuredAction() {
        return configuredAction;
    }

    public Identifier resolvedAction() {
        return resolvedAction;
    }

    public void setResolvedAction(Identifier resolvedAction) {
        this.resolvedAction = Objects.requireNonNull(resolvedAction, "resolvedAction");
    }

    public void resetToConfiguredAction() {
        this.resolvedAction = configuredAction;
    }
}
