package dev.isxander.controlify.screenop.compat.vanilla;

import dev.isxander.controlify.api.ControlifyApi;
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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

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

        var accessor = (AbstractContainerScreenAccessor) screen;
        int totalWidth = glyphWidth(controller, ControlifyBindings.INV_SELECT);
        if (slot.hasItem()) {
            totalWidth += glyphWidth(controller, ControlifyBindings.INV_TAKE_HALF);
            totalWidth += glyphWidth(controller, ControlifyBindings.INV_QUICK_MOVE);
        }
        int x = accessor.getLeftPos() + slot.x + (18 - totalWidth) / 2;
        x = Math.max(2, Math.min(x, graphics.guiWidth() - totalWidth - 2));
        int belowSlot = accessor.getTopPos() + slot.y + 20;
        int y = belowSlot + minecraft.font.lineHeight + 2 <= graphics.guiHeight()
                ? belowSlot
                : accessor.getTopPos() + slot.y - minecraft.font.lineHeight - 3;

        x += drawSlotGlyph(graphics, controller, ControlifyBindings.INV_SELECT, x, y);
        if (slot.hasItem()) {
            x += drawSlotGlyph(graphics, controller, ControlifyBindings.INV_TAKE_HALF, x, y);
            drawSlotGlyph(graphics, controller, ControlifyBindings.INV_QUICK_MOVE, x, y);
        }
    }

    private int drawSlotGlyph(GuiGraphics graphics, ControllerEntity controller,
                              dev.isxander.controlify.api.bind.InputBindingSupplier supplier,
                              int x, int y) {
        var binding = supplier.on(controller);
        if (binding.isUnbound()) return 0;
        return GuideRenderer.drawGlyphBadge(graphics, minecraft.font, binding.inputGlyph(), x, y);
    }

    private int glyphWidth(ControllerEntity controller,
                           dev.isxander.controlify.api.bind.InputBindingSupplier supplier) {
        var binding = supplier.on(controller);
        return binding.isUnbound() ? 0 : minecraft.font.width(binding.inputGlyph()) + 5;
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
