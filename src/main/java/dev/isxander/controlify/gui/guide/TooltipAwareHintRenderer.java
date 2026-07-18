package dev.isxander.controlify.gui.guide;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.compatibility.recipeviewer.RecipeViewerCompat;
import dev.isxander.controlify.screenop.ScreenProcessorProvider;
import dev.isxander.controlify.screenop.compat.vanilla.AbstractContainerScreenProcessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Renders hints which need the final, current-frame tooltip bounds for placement. */
public final class TooltipAwareHintRenderer {
    private TooltipAwareHintRenderer() {
    }

    public static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        Controlify controlify = Controlify.instance();
        if (!controlify.currentInputMode().isController()) return;
        controlify.getCurrentController().ifPresent(controller -> {
            if (!controller.settings().generic.guide.showScreenGuides) return;

            var processor = ScreenProcessorProvider.provide(screen);
            if (processor instanceof AbstractContainerScreenProcessor<?> containerProcessor) {
                containerProcessor.renderTooltipAwareHints(controller, graphics);
            }
            RecipeViewerCompat.renderTooltipAwareItemHints(
                    screen, graphics, controller, mouseX, mouseY
            );
        });
    }
}
