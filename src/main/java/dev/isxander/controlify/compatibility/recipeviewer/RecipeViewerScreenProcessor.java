package dev.isxander.controlify.compatibility.recipeviewer;

import dev.isxander.controlify.screenop.ScreenProcessor;
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour;
import net.minecraft.client.gui.screens.Screen;

/** Keeps a fully interactive controller cursor on recipe-viewer screens. */
public final class RecipeViewerScreenProcessor extends ScreenProcessor<Screen> {
    public RecipeViewerScreenProcessor(Screen screen) {
        super(screen);
    }

    @Override
    public VirtualMouseBehaviour virtualMouseBehaviour() {
        return VirtualMouseBehaviour.ENABLED;
    }
}
