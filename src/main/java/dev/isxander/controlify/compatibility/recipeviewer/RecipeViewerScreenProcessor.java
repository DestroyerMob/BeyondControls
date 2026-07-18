package dev.isxander.controlify.compatibility.recipeviewer;

import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.screenop.ScreenProcessor;
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour;
import dev.isxander.controlify.virtualmouse.VirtualMouseHandler;
import net.minecraft.client.gui.screens.Screen;

/** Keeps a fully interactive controller cursor on recipe-viewer screens. */
public final class RecipeViewerScreenProcessor extends ScreenProcessor<Screen> {
    public RecipeViewerScreenProcessor(Screen screen) {
        super(screen);
    }

    @Override
    protected void handleScreenVMouse(ControllerEntity controller, VirtualMouseHandler vmouse) {
        if (RecipeViewerCompat.handleRecipeNavigation(screen, controller)) {
            playClackSound();
        }
        super.handleScreenVMouse(controller, vmouse);
    }

    @Override
    public VirtualMouseBehaviour virtualMouseBehaviour() {
        return VirtualMouseBehaviour.ENABLED;
    }
}
