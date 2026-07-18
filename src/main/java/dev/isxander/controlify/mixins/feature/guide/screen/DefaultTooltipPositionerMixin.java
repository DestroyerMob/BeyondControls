package dev.isxander.controlify.mixins.feature.guide.screen;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.gui.guide.GuideRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DefaultTooltipPositioner.class)
public class DefaultTooltipPositionerMixin {
    @Inject(method = "positionTooltip", at = @At("RETURN"), cancellable = true)
    private void keepControllerGuidesVisible(int screenWidth, int screenHeight, int mouseX, int mouseY,
                                             int tooltipWidth, int tooltipHeight,
                                             CallbackInfoReturnable<Vector2ic> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> containerScreen)) return;
        if (!Controlify.instance().currentInputMode().isController()) return;
        if (Controlify.instance().getCurrentController()
                .map(controller -> !controller.settings().generic.guide.showScreenGuides)
                .orElse(true)) return;

        GuideRenderer.Bounds exclusion = GuideRenderer.belowContainer(containerScreen);
        Vector2ic position = cir.getReturnValue();
        if (!intersects(position.x(), position.y(), tooltipWidth, tooltipHeight, exclusion)) return;

        int above = exclusion.top() - tooltipHeight - 4;
        if (above >= 4) {
            cir.setReturnValue(new Vector2i(position.x(), above));
            return;
        }

        int left = exclusion.left() - tooltipWidth - 4;
        if (left >= 4) {
            cir.setReturnValue(new Vector2i(left, Math.max(4, Math.min(position.y(), screenHeight - tooltipHeight - 4))));
            return;
        }

        int right = exclusion.right() + 4;
        if (right + tooltipWidth <= screenWidth - 4) {
            cir.setReturnValue(new Vector2i(right, Math.max(4, Math.min(position.y(), screenHeight - tooltipHeight - 4))));
            return;
        }

        cir.setReturnValue(new Vector2i(position.x(), 4));
    }

    private static boolean intersects(int x, int y, int width, int height, GuideRenderer.Bounds bounds) {
        return x < bounds.right() && x + width > bounds.left()
                && y < bounds.bottom() && y + height > bounds.top();
    }
}
