package dev.isxander.controlify.gui.screen;

import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.bindings.input.Input;
import dev.isxander.controlify.config.settings.profile.InputSettings;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.gui.controllers.BindController;
import dev.isxander.controlify.screenop.ScreenProcessor;
import dev.isxander.controlify.screenop.ScreenProcessorProvider;
import dev.isxander.controlify.utils.render.CGuiPose;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Optional;

public class BindConsumerScreen extends Screen implements ScreenProcessorProvider {
    private static final int ACTION_WHEEL_HOLD_TICKS = 8;

    private final BindConsumer bindConsumer;
    private final Option<Input> option;
    private final Screen backgroundScreen;
    private final BindController.BindControllerElement widgetToFocus;
    private final ControllerEntity controller;
    private final InputBinding targetBinding;
    private final ScreenProcessorImpl screenProcessor = new ScreenProcessorImpl(this);

    private int ticksTillClose;
    private int ticksTillInput;
    private Input pendingWheelInput;
    private int pendingWheel = -1;
    private int pendingWheelTicks;

    public BindConsumerScreen(BindConsumer bindConsumer, Option<Input> option,
                              BindController.BindControllerElement widgetToFocus,
                              Screen backgroundScreen, ControllerEntity controller,
                              InputBinding targetBinding) {
        super(Component.empty());
        this.bindConsumer = bindConsumer;
        this.option = option;
        this.widgetToFocus = widgetToFocus;
        this.backgroundScreen = backgroundScreen;
        this.controller = controller;
        this.targetBinding = targetBinding;
        this.ticksTillInput = 5;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) {
        Dimension<Integer> dim = widgetToFocus.getDimension();

        var pose = CGuiPose.ofPush(guiGraphics);
        // text renders with z > 0 so push everything back so text doesn't pop through fill
        //? if <1.21.6
        //guiGraphics.pose().translate(0, 0, -20);

        //? if >=1.21.10 {
        backgroundScreen.renderWithTooltipAndSubtitles(guiGraphics, dim.centerX(), dim.centerY(), tickDelta);
        //?} else {
        /*backgroundScreen.renderWithTooltip(guiGraphics, dim.centerX(), dim.centerY(), tickDelta);
        *///?}

        pose.pop();

        pose.push();
        pose.nextLayer(1000f);

        // darken everything except the widget
        guiGraphics.fill(0, 0, width, dim.y() - 1, 0x80000000);
        guiGraphics.fill(0, dim.y(), dim.x() - 1, height, 0x80000000);
        guiGraphics.fill(dim.xLimit() + 1, dim.y() - 1, width, height, 0x80000000);
        guiGraphics.fill(dim.x(), dim.yLimit() + 1, dim.xLimit(), height, 0x80000000);

        pose.pop();

        super.render(guiGraphics, mouseX, mouseY, tickDelta);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
        // do not render background
    }

    @Override
    public void tick() {
        if (ticksTillClose > 0) {
            ticksTillClose--;
            if (ticksTillClose == 0) {
                widgetToFocus.awaitingControllerInput = false;
                // don't call setScreen because will cause background to re-init
                minecraft.screen = backgroundScreen;
            }
        }

        if (ticksTillInput > 0) {
            ticksTillInput--;
            if (ticksTillInput > 0) {
                return;
            }
        }

        // tick runs after all controller input ticks

        if (pendingWheelInput != null) {
            float threshold = controller.input().orElseThrow().settings().buttonActivationThreshold;
            if (pendingWheelInput.state(controller.input().orElseThrow().stateNow()) >= threshold) {
                pendingWheelTicks++;
                if (pendingWheelTicks >= ACTION_WHEEL_HOLD_TICKS) {
                    openWheelAssignment();
                }
            } else {
                option.requestSet(pendingWheelInput);
                clearPendingWheel();
                returnToBackground();
            }
            return;
        }

        Optional<Input> pressedBind = bindConsumer.getPressedBind();
        if (pressedBind.isPresent()) {
            int wheel = getWheelForInput(pressedBind.get());
            if (wheel >= 0) {
                pendingWheelInput = pressedBind.get();
                pendingWheel = wheel;
                pendingWheelTicks = 1;
            } else {
                option.requestSet(pressedBind.get());
                returnToBackground();
            }
        }
    }

    @Override
    //? if >=1.21.9 {
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
        boolean consumed = super.keyPressed(keyEvent);
    //?} else {
    /*public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean consumed = super.keyPressed(keyCode, scanCode, modifiers);
    *///?}
        if (consumed) return true;

        if (ticksTillInput > 0) return false;
        returnToBackground();
        return true;
    }

    @Override
    //? if >=1.21.9 {
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        boolean consumed = super.mouseClicked(mouseButtonEvent, doubleClick);
    //?} else {
    /*public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean consumed = super.mouseClicked(mouseX, mouseY, button);
    *///?}
        if (consumed) return true;

        if (ticksTillInput > 0) return false;
        returnToBackground();
        return true;
    }

    @Override
    //? if >=1.21.9 {
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent, double dx, double dy) {
        boolean consumed = super.mouseDragged(mouseButtonEvent, dx, dy);
    //?} else {
    /*public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        boolean consumed = super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    *///?}
        if (consumed) return true;

        if (ticksTillInput > 0) return false;
        returnToBackground();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount, double d) {
        boolean consumed = super.mouseScrolled(mouseX, mouseY, amount, d);
        if (consumed) return true;

        if (ticksTillInput > 0) return false;
        returnToBackground();
        return true;
    }

    private void returnToBackground() {
        ticksTillClose = 5;
    }

    private int getWheelForInput(Input input) {
        for (int wheel = 0; wheel < InputSettings.RadialMenuSettings.WHEEL_COUNT; wheel++) {
            if (wheelBinding(wheel).boundInput().equals(input)) {
                return wheel;
            }
        }
        return -1;
    }

    private InputBinding wheelBinding(int wheel) {
        return switch (wheel) {
            case InputSettings.RadialMenuSettings.UP -> ControlifyBindings.RADIAL_MENU_UP.on(controller);
            case InputSettings.RadialMenuSettings.DOWN -> ControlifyBindings.RADIAL_MENU_DOWN.on(controller);
            case InputSettings.RadialMenuSettings.LEFT -> ControlifyBindings.RADIAL_MENU_LEFT.on(controller);
            case InputSettings.RadialMenuSettings.RIGHT -> ControlifyBindings.RADIAL_MENU.on(controller);
            default -> throw new IllegalArgumentException("Unknown action wheel " + wheel);
        };
    }

    private void openWheelAssignment() {
        InputBinding openBinding = wheelBinding(pendingWheel);
        widgetToFocus.awaitingControllerInput = false;
        minecraft.setScreen(new RadialMenuScreen(
                controller,
                openBinding,
                RadialItems.createSlotAssignment(controller, pendingWheel, targetBinding),
                Component.translatable("controlify.radial.assign_binding", targetBinding.name()),
                null,
                backgroundScreen
        ));
        clearPendingWheel();
    }

    private void clearPendingWheel() {
        pendingWheelInput = null;
        pendingWheel = -1;
        pendingWheelTicks = 0;
    }

    @Override
    public ScreenProcessor<?> screenProcessor() {
        return screenProcessor;
    }

    public interface BindConsumer {
        Optional<Input> getPressedBind();
    }

    private static class ScreenProcessorImpl extends ScreenProcessor<BindConsumerScreen> {
        public ScreenProcessorImpl(BindConsumerScreen screen) {
            super(screen);
        }

        @Override
        public void onControllerUpdate(ControllerEntity controller) {
            // prevent all other controller input logic
        }
    }
}
