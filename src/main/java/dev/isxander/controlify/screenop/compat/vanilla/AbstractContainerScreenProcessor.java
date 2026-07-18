package dev.isxander.controlify.screenop.compat.vanilla;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.haptic.HapticEffects;
import dev.isxander.controlify.compatibility.recipeviewer.RecipeViewerCompat;
import dev.isxander.controlify.gui.guide.GuideRenderer;
import dev.isxander.controlify.mixins.feature.guide.screen.AbstractContainerScreenAccessor;
import dev.isxander.controlify.screenop.ScreenProcessor;
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour;
import dev.isxander.controlify.virtualmouse.VirtualMouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class AbstractContainerScreenProcessor<T extends AbstractContainerScreen<?>> extends ScreenProcessor<T> {

    private final Supplier<Slot> hoveredSlot;
    private final ClickSlotFunction clickSlotFunction;

    private final Predicate<ControllerEntity> doItemSlotActions;

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

            if (ControlifyBindings.INV_SELECT.on(controller).justPressed()) {
                clickSlotFunction.clickSlot(hoveredSlot, hoveredSlot.index, 0, ClickType.PICKUP);
                hapticNavigate();
            }

            if (ControlifyBindings.INV_QUICK_MOVE.on(controller).justPressed()) {
                clickSlotFunction.clickSlot(hoveredSlot, hoveredSlot.index, 0, ClickType.QUICK_MOVE);
                hapticNavigate();
            }

            if (ControlifyBindings.INV_TAKE_HALF.on(controller).justPressed()) {
                clickSlotFunction.clickSlot(hoveredSlot, hoveredSlot.index, 1, ClickType.PICKUP);
                hapticNavigate();
            }

//            if (ControlifyBindings.SWAP_HANDS.on(controller).justPressed()) {
//                clickSlotFunction.clickSlot(hoveredSlot, hoveredSlot.index, 40, ClickType.SWAP);
//                hapticNavigate();
//            }
        } else {
            vmouse.handleCompatibilityBinds(controller);
        }

        if (!screen.getMenu().getCarried().isEmpty()) {
            if (ControlifyBindings.DROP_INVENTORY.on(controller).justPressed()) {
                clickSlotFunction.clickSlot(null, -999, 0, ClickType.PICKUP);
                hapticNavigate();
            }
        }
    }

    @Override
    protected void render(ControllerEntity controller, GuiGraphics graphics, float tickDelta,
                          Optional<VirtualMouseHandler> vmouse) {
        if (!controller.settings().generic.guide.showScreenGuides) return;
        Slot slot = hoveredSlot.get();
        if (slot == null) return;

        List<StackHint> hints = stackHints(slot);
        if (hints.isEmpty()) return;

        var accessor = (AbstractContainerScreenAccessor) screen;
        int maxWidth = 0;
        int visibleHints = 0;
        for (StackHint hint : hints) {
            var binding = hint.binding().on(controller);
            if (binding.isUnbound()) continue;
            maxWidth = Math.max(maxWidth, GuideRenderer.labeledGlyphWidth(
                    minecraft.font, binding.inputGlyph(), hint.label()
            ));
            visibleHints++;
        }
        if (visibleHints == 0) return;

        int slotX = accessor.getLeftPos() + slot.x;
        int slotY = accessor.getTopPos() + slot.y;
        int x = slotX - maxWidth - 5;
        if (x < 2) x = slotX + 23;
        x = Math.max(2, Math.min(x, graphics.guiWidth() - maxWidth - 2));
        int rowHeight = minecraft.font.lineHeight + 5;
        int totalHeight = visibleHints * rowHeight - 2;
        int y = Math.max(2, Math.min(
                slotY + (18 - totalHeight) / 2,
                graphics.guiHeight() - totalHeight - 2
        ));

        for (StackHint hint : hints) {
            int drawn = drawSlotHint(graphics, controller, hint, x, y);
            if (drawn > 0) y += rowHeight;
        }
    }

    private List<StackHint> stackHints(Slot slot) {
        List<StackHint> hints = new ArrayList<>(3);
        ItemStack carried = screen.getMenu().getCarried();
        if (carried.isEmpty()) {
            if (!slot.hasItem()) return hints;
            hints.add(new StackHint(
                    ControlifyBindings.INV_SELECT,
                    Component.translatable("controlify.guide.container.take")
            ));
            if (slot.getItem().getCount() > 1) {
                hints.add(new StackHint(
                        ControlifyBindings.INV_TAKE_HALF,
                        Component.translatable("controlify.guide.container.take_half")
                ));
            }
            hints.add(new StackHint(
                    ControlifyBindings.INV_QUICK_MOVE,
                    Component.translatable("controlify.guide.container.quick_move")
            ));
            return hints;
        }

        if (!slot.mayPlace(carried)) return hints;
        boolean combines = !slot.hasItem() || ItemStack.isSameItemSameComponents(slot.getItem(), carried);
        hints.add(new StackHint(
                ControlifyBindings.INV_SELECT,
                Component.translatable(combines
                        ? "controlify.guide.container.place_all"
                        : "controlify.guide.container.swap")
        ));
        hints.add(new StackHint(
                ControlifyBindings.INV_TAKE_HALF,
                Component.translatable("controlify.guide.container.take_one")
        ));
        return hints;
    }

    private int drawSlotHint(GuiGraphics graphics, ControllerEntity controller,
                             StackHint hint, int x, int y) {
        var binding = hint.binding().on(controller);
        if (binding.isUnbound()) return 0;
        return GuideRenderer.drawLabeledGlyph(
                graphics, minecraft.font, binding.inputGlyph(), hint.label(), x, y
        );
    }

    private record StackHint(InputBindingSupplier binding, Component label) {}

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
