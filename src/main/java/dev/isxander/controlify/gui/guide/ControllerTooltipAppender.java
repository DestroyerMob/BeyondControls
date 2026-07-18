package dev.isxander.controlify.gui.guide;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.compatibility.recipeviewer.RecipeViewerCompat;
import dev.isxander.controlify.screenop.ScreenProcessorProvider;
import dev.isxander.controlify.screenop.compat.vanilla.AbstractContainerScreenProcessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Adds controller actions to the tooltip they describe before it is measured. */
public final class ControllerTooltipAppender {
    private ControllerTooltipAppender() {
    }

    public static void append(List<ClientTooltipComponent> tooltip, int mouseX, int mouseY) {
        Controlify controlify = Controlify.instance();
        if (!controlify.currentInputMode().isController()) return;

        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return;

        controlify.getCurrentController().ifPresent(controller -> {
            if (!controller.settings().generic.guide.showScreenGuides) return;

            List<Component> lines = new ArrayList<>();
            var processor = ScreenProcessorProvider.provide(screen);
            if (processor instanceof AbstractContainerScreenProcessor<?> containerProcessor) {
                lines.addAll(containerProcessor.controllerTooltipLines(controller));
            }
            if (lines.isEmpty()) {
                lines.addAll(RecipeViewerCompat.controllerTooltipLines(
                        screen, controller, mouseX, mouseY
                ));
            }

            for (Component line : lines) {
                tooltip.add(ClientTooltipComponent.create(line.getVisualOrderText()));
            }
        });
    }
}
