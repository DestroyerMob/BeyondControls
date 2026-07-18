package dev.isxander.controlify.mixins.feature.guide.screen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.isxander.controlify.gui.guide.TooltipTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphics.class)
public class GuiGraphicsTooltipTrackerMixin {
    @WrapOperation(
            method = "renderTooltipInternal",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"
            )
    )
    private Vector2ic controlify$recordTooltipPosition(ClientTooltipPositioner positioner,
                                                       int screenWidth, int screenHeight,
                                                       int mouseX, int mouseY,
                                                       int tooltipWidth, int tooltipHeight,
                                                       Operation<Vector2ic> original) {
        Vector2ic position = original.call(
                positioner, screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight
        );
        TooltipTracker.record(
                Minecraft.getInstance().screen,
                position.x(), position.y(), tooltipWidth, tooltipHeight
        );
        return position;
    }
}
