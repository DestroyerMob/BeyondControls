package dev.isxander.controlify.mixins.feature.guide.screen;

import dev.isxander.controlify.gui.guide.TooltipTracker;
import dev.isxander.controlify.gui.guide.TooltipAwareHintRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenTooltipTrackerMixin {
    @Inject(
            method = /*? if >=1.21.10 {*/ "renderWithTooltipAndSubtitles" /*?} else {*/ /*"renderWithTooltip" *//*?}*/,
            at = @At("HEAD")
    )
    private void controlify$beginTooltipFrame(GuiGraphics graphics, int mouseX, int mouseY,
                                              float tickDelta, CallbackInfo ci) {
        TooltipTracker.beginFrame((Screen) (Object) this, mouseX, mouseY);
    }

    @Inject(
            method = /*? if >=1.21.10 {*/ "renderWithTooltipAndSubtitles" /*?} else {*/ /*"renderWithTooltip" *//*?}*/,
            at = @At("TAIL")
    )
    private void controlify$renderTooltipAwareHints(GuiGraphics graphics, int mouseX, int mouseY,
                                                     float tickDelta, CallbackInfo ci) {
        TooltipAwareHintRenderer.render((Screen) (Object) this, graphics, mouseX, mouseY);
    }
}
