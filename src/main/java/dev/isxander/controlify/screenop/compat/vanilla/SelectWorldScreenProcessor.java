package dev.isxander.controlify.screenop.compat.vanilla;

import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.compatibility.cherishedworlds.CherishedWorldsCompat;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.screenop.ScreenProcessor;
import dev.isxander.controlify.mixins.feature.screenop.impl.outofgame.SelectWorldScreenAccessor;
import dev.isxander.controlify.utils.ToastUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class SelectWorldScreenProcessor extends ScreenProcessor<SelectWorldScreen> {
    private boolean favoriteHintShown;

    public SelectWorldScreenProcessor(SelectWorldScreen screen) {
        super(screen);
    }

    @Override
    public void onControllerUpdate(ControllerEntity controller) {
        if (!favoriteHintShown && CherishedWorldsCompat.isAvailable()) {
            favoriteHintShown = true;
            var binding = ControlifyBindings.GUI_ABSTRACT_ACTION_2.on(controller);
            if (!binding.isUnbound()) {
                ToastUtils.sendToast(
                        Component.translatable("controlify.toast.favorite_world.title"),
                        Component.empty()
                                .append(binding.inputGlyph())
                                .append(CommonComponents.SPACE)
                                .append(Component.translatable("controlify.toast.favorite_world.description")),
                        false
                );
            }
        }
        super.onControllerUpdate(controller);
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
