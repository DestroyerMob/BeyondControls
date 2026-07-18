package dev.isxander.controlify.screenop.compat.vanilla;

import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.compatibility.cherishedworlds.CherishedWorldsCompat;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.screenop.ScreenProcessor;
import dev.isxander.controlify.mixins.feature.screenop.impl.outofgame.SelectWorldScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import dev.isxander.controlify.virtualmouse.VirtualMouseHandler;

import java.util.Optional;

public class SelectWorldScreenProcessor extends ScreenProcessor<SelectWorldScreen> {
    public SelectWorldScreenProcessor(SelectWorldScreen screen) {
        super(screen);
    }

    @Override
    protected void render(ControllerEntity controller, GuiGraphics graphics, float tickDelta,
                          Optional<VirtualMouseHandler> vmouse) {
        var binding = ControlifyBindings.GUI_ABSTRACT_ACTION_2.on(controller);
        if (binding.isUnbound()) return;

        CherishedWorldsCompat.selectedWorldControlPosition(screen).ifPresent(position -> {
            var glyph = binding.inputGlyph();
            int width = minecraft.font.width(glyph);
            int x = position.starX() - width - 4;
            int y = position.y();
            graphics.fill(x - 2, y - 1, x + width + 2, y + minecraft.font.lineHeight, 0xB0000000);
            graphics.drawString(minecraft.font, glyph, x, y, 0xFFFFFFFF, false);
        });
    }

    @Override
    protected void handleButtons(ControllerEntity controller) {
        if (ControlifyBindings.GUI_ABSTRACT_ACTION_2.on(controller).guiPressed().get()
                && CherishedWorldsCompat.toggleSelectedWorld(screen)) {
            playClackSound();
            return;
        }

        if (ControlifyBindings.GUI_ABSTRACT_ACTION_1.on(controller).justPressed()) {
            playClackSound();
            var minecraft = Minecraft.getInstance();
            CreateWorldScreen.openFresh(minecraft, /*? if >=1.21.9 {*/() -> minecraft.setScreen(screen) /*?} else {*/ /*screen *//*?}*/);
            return;
        }

        if (screen.getFocused() != null && screen.getFocused() instanceof Button) {
            if (ControlifyBindings.GUI_BACK.on(controller).guiPressed().get()) {
                screen.setFocused(((SelectWorldScreenAccessor) screen).getList());
                return;
            }
        }

        super.handleButtons(controller);
    }
}
