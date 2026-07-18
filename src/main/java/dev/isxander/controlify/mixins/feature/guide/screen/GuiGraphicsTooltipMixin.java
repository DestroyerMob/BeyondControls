package dev.isxander.controlify.mixins.feature.guide.screen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.gui.guide.GuideRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphics.class)
public class GuiGraphicsTooltipMixin {
    @WrapOperation(
            method = "renderTooltipInternal",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"
            )
    )
    private Vector2ic keepControllerGuidesVisible(ClientTooltipPositioner positioner,
                                                  int screenWidth, int screenHeight,
                                                  int mouseX, int mouseY,
                                                  int tooltipWidth, int tooltipHeight,
                                                  Operation<Vector2ic> original) {
        Vector2ic position = original.call(
                positioner, screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight
        );
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> containerScreen)) return position;
        if (!Controlify.instance().currentInputMode().isController()) return position;
        if (Controlify.instance().getCurrentController()
                .map(controller -> !controller.settings().generic.guide.showScreenGuides)
                .orElse(true)) return position;

        GuideRenderer.Bounds exclusion = GuideRenderer.belowContainer(containerScreen);
        if (!intersects(position.x(), position.y(), tooltipWidth, tooltipHeight, exclusion)) return position;

        int above = exclusion.top() - tooltipHeight - 4;
        if (above >= 4) return new Vector2i(position.x(), above);

        int left = exclusion.left() - tooltipWidth - 4;
        if (left >= 4) {
            return new Vector2i(left, clampY(position.y(), screenHeight, tooltipHeight));
        }

        int right = exclusion.right() + 4;
        if (right + tooltipWidth <= screenWidth - 4) {
            return new Vector2i(right, clampY(position.y(), screenHeight, tooltipHeight));
        }

        return new Vector2i(position.x(), 4);
    }

    private static int clampY(int y, int screenHeight, int tooltipHeight) {
        return Math.max(4, Math.min(y, screenHeight - tooltipHeight - 4));
    }

    private static boolean intersects(int x, int y, int width, int height, GuideRenderer.Bounds bounds) {
        return x < bounds.right() && x + width > bounds.left()
                && y < bounds.bottom() && y + height > bounds.top();
    }
}
