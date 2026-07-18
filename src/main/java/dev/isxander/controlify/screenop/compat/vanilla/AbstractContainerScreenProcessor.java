package dev.isxander.controlify.screenop.compat.vanilla;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.haptic.HapticEffects;
import dev.isxander.controlify.compatibility.recipeviewer.RecipeViewerCompat;
import dev.isxander.controlify.screenop.ScreenProcessor;
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour;
import dev.isxander.controlify.virtualmouse.VirtualMouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class AbstractContainerScreenProcessor<T extends AbstractContainerScreen<?>> extends ScreenProcessor<T> {
    private static final int STACK_VIEWER_HOLD_TICKS = 10;

    private final Supplier<Slot> hoveredSlot;
    private final ClickSlotFunction clickSlotFunction;

    private final Predicate<ControllerEntity> doItemSlotActions;
    private final StackPressState selectPress = new StackPressState(ControlifyBindings.INV_SELECT, 0, false);
    private final StackPressState halfPress = new StackPressState(ControlifyBindings.INV_TAKE_HALF, 1, true);

    public AbstractContainerScreenProcessor(
            T screen,
            Supplier<Slot> hoveredSlot,
            ClickSlotFunction clickSlotFunction,
            Predicate<ControllerEntity> doItemSlotActions
    ) {
        super(screen);
        this.hoveredSlot = hoveredSlot;
        this.clickSlotFunction = clickSlotFunction;
        this.doItemSlotActions = doItemSlotActions;
    }

    @Override
    protected void handleScreenVMouse(ControllerEntity controller, VirtualMouseHandler vmouse) {
        if (RecipeViewerCompat.handleContainerPaging(screen, controller, vmouse)) {
            playClackSound();
        }

        Slot hoveredSlot = this.hoveredSlot.get();
        if (hoveredSlot != null) {
            if (hoveredSlot.hasItem()) {
                if (doItemSlotActions.test(controller)) {
                    return;
                }
            }

            if (selectPress.handle(controller, hoveredSlot)) return;
            if (halfPress.handle(controller, hoveredSlot)) return;

            if (ControlifyBindings.INV_QUICK_MOVE.on(controller).justPressed()) {
                clickSlotFunction.clickSlot(hoveredSlot, hoveredSlot.index, 0, ClickType.QUICK_MOVE);
                hapticNavigate();
            }

//            if (ControlifyBindings.SWAP_HANDS.on(controller).justPressed()) {
//                clickSlotFunction.clickSlot(hoveredSlot, hoveredSlot.index, 40, ClickType.SWAP);
//                hapticNavigate();
//            }
        } else {
            selectPress.reset();
            halfPress.reset();
            vmouse.handleCompatibilityBinds(controller);
        }

        if (!screen.getMenu().getCarried().isEmpty()) {
            if (ControlifyBindings.DROP_INVENTORY.on(controller).justPressed()) {
                clickSlotFunction.clickSlot(null, -999, 0, ClickType.PICKUP);
                hapticNavigate();
            }
        }
    }

    public List<Component> controllerTooltipLines(ControllerEntity controller) {
        Slot slot = hoveredSlot.get();
        if (slot == null) return List.of();

        List<StackHint> hints = stackHints(slot);
        if (hints.isEmpty()) return List.of();

        List<Component> lines = new ArrayList<>(hints.size());
        for (StackHint hint : hints) {
            var binding = hint.binding().on(controller);
            if (binding.isUnbound()) continue;
            lines.add(Component.empty()
                    .append(binding.inputGlyph())
                    .append(Component.literal(" "))
                    .append(hint.label()));
        }
        return List.copyOf(lines);
    }

    private List<StackHint> stackHints(Slot slot) {
        List<StackHint> hints = new ArrayList<>(3);
        ItemStack carried = screen.getMenu().getCarried();
        boolean viewerActions = slot.hasItem() && RecipeViewerCompat.isStackViewerAvailable();
        if (carried.isEmpty()) {
            if (!slot.hasItem()) return hints;
            hints.add(new StackHint(
                    ControlifyBindings.INV_SELECT,
                    withHoldAction(
                            Component.translatable("controlify.guide.container.take"),
                            Component.translatable("controlify.compat.recipe_viewer.action.recipes"),
                            viewerActions
                    )
            ));
            if (slot.getItem().getCount() > 1 || viewerActions) {
                hints.add(new StackHint(
                        ControlifyBindings.INV_TAKE_HALF,
                        withHoldAction(
                                Component.translatable(slot.getItem().getCount() > 1
                                        ? "controlify.guide.container.take_half"
                                        : "controlify.guide.container.take"),
                                Component.translatable("controlify.compat.recipe_viewer.action.uses"),
                                viewerActions
                        )
                ));
            }
            hints.add(new StackHint(
                    ControlifyBindings.INV_QUICK_MOVE,
                    Component.translatable("controlify.guide.container.quick_move")
            ));
            return hints;
        }

        if (!slot.mayPlace(carried)) {
            if (viewerActions) {
                hints.add(new StackHint(
                        ControlifyBindings.INV_SELECT,
                        Component.translatable(
                                "controlify.guide.hold_only",
                                Component.translatable("controlify.compat.recipe_viewer.action.recipes")
                        )
                ));
                hints.add(new StackHint(
                        ControlifyBindings.INV_TAKE_HALF,
                        Component.translatable(
                                "controlify.guide.hold_only",
                                Component.translatable("controlify.compat.recipe_viewer.action.uses")
                        )
                ));
            }
            return hints;
        }
        boolean combines = !slot.hasItem() || ItemStack.isSameItemSameComponents(slot.getItem(), carried);
        hints.add(new StackHint(
                ControlifyBindings.INV_SELECT,
                withHoldAction(
                        Component.translatable(combines
                                ? "controlify.guide.container.place_all"
                                : "controlify.guide.container.swap"),
                        Component.translatable("controlify.compat.recipe_viewer.action.recipes"),
                        viewerActions
                )
        ));
        hints.add(new StackHint(
                ControlifyBindings.INV_TAKE_HALF,
                withHoldAction(
                        Component.translatable("controlify.guide.container.take_one"),
                        Component.translatable("controlify.compat.recipe_viewer.action.uses"),
                        viewerActions
                )
        ));
        return hints;
    }

    private Component withHoldAction(Component tapAction, Component holdAction, boolean enabled) {
        return enabled
                ? Component.translatable("controlify.guide.tap_hold", tapAction, holdAction)
                : tapAction;
    }

    private record StackHint(InputBindingSupplier binding, Component label) {}

    private final class StackPressState {
        private final InputBindingSupplier bindingSupplier;
        private final int mouseButton;
        private final boolean uses;
        private Slot pressedSlot;
        private int heldTicks;
        private boolean holdTriggered;

        private StackPressState(InputBindingSupplier bindingSupplier, int mouseButton, boolean uses) {
            this.bindingSupplier = bindingSupplier;
            this.mouseButton = mouseButton;
            this.uses = uses;
        }

        private boolean handle(ControllerEntity controller, Slot currentSlot) {
            var binding = bindingSupplier.on(controller);
            boolean canHold = currentSlot.hasItem() && RecipeViewerCompat.isStackViewerAvailable();
            if (!canHold) {
                reset();
                if (binding.justPressed()) {
                    clickSlotFunction.clickSlot(currentSlot, currentSlot.index, mouseButton, ClickType.PICKUP);
                    hapticNavigate();
                }
                return false;
            }

            if (binding.justPressed()) {
                pressedSlot = currentSlot;
                heldTicks = 0;
                holdTriggered = false;
            }
            if (pressedSlot != null && currentSlot != pressedSlot) {
                reset();
                return false;
            }
            if (pressedSlot != null && binding.digitalNow() && !holdTriggered) {
                heldTicks++;
                if (heldTicks >= STACK_VIEWER_HOLD_TICKS
                        && RecipeViewerCompat.openStackViewer(pressedSlot.getItem(), uses)) {
                    holdTriggered = true;
                    playClackSound();
                    return true;
                }
            }
            if (binding.justReleased() && pressedSlot != null) {
                if (!holdTriggered) {
                    clickSlotFunction.clickSlot(pressedSlot, pressedSlot.index, mouseButton, ClickType.PICKUP);
                    hapticNavigate();
                }
                reset();
            }
            return false;
        }

        private void reset() {
            pressedSlot = null;
            heldTicks = 0;
            holdTriggered = false;
        }
    }

    public void onHoveredSlotChanged(Slot newSlot, Slot oldSlot) {
        if (ControlifyApi.get().currentInputMode().isController()) {
            hapticNavigate();
        }
    }

    private void hapticNavigate() {
        ControlifyApi.get().getCurrentController().flatMap(ControllerEntity::hdHaptics).ifPresent(hh -> {
            hh.playHaptic(HapticEffects.NAVIGATE);
        });
    }

    @Override
    public VirtualMouseBehaviour virtualMouseBehaviour() {
        return VirtualMouseBehaviour.CURSOR_SCROLL;
    }

    @FunctionalInterface
    public interface ClickSlotFunction {
        void clickSlot(Slot slot, int slotId, int button, ClickType clickType);
    }
}
