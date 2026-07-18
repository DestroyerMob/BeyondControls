package dev.isxander.controlify.screenop.compat.vanilla;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.gui.guide.GuideRenderer;
import dev.isxander.controlify.mixins.feature.guide.screen.AbstractContainerScreenAccessor;
import dev.isxander.controlify.mixins.feature.guide.screen.CreativeModeInventoryScreenAccessor;
import dev.isxander.controlify.platform.client.CreativeTabHelper;
import dev.isxander.controlify.platform.client.PlatformClientUtil;
import dev.isxander.controlify.virtualmouse.VirtualMouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class CreativeModeInventoryScreenProcessor extends AbstractContainerScreenProcessor<CreativeModeInventoryScreen> {
    private final CreativeTabHelper tabHelper;

    public CreativeModeInventoryScreenProcessor(CreativeModeInventoryScreen screen, Supplier<Slot> hoveredSlot, ClickSlotFunction clickSlotFunction, Predicate<ControllerEntity> itemSlotAction) {
        super(screen, hoveredSlot, clickSlotFunction, itemSlotAction);
        this.tabHelper = PlatformClientUtil.createCreativeTabHelper(screen);
    }

    @Override
    public void onWidgetRebuild() {
        super.onWidgetRebuild();
        Controlify.instance().virtualMouseHandler().snapToClosestPoint();
    }

    @SuppressWarnings("UnreachableCode")
    @Override
    protected void handleScreenVMouse(ControllerEntity controller, VirtualMouseHandler vmouse) {
        List<CreativeModeTab> tabs = tabHelper.getTabsForPage(tabHelper.getCurrentPage());
        if (ControlifyBindings.GUI_NEXT_TAB.on(controller).justPressed()) {
            int newIndex = tabs.indexOf(tabHelper.getSelectedTab()) + 1;
            if (newIndex >= tabs.size()) {
                newIndex = 0;

                int newPage = tabHelper.getCurrentPage() + 1;
                if (newPage >= tabHelper.getPageCount())
                    newPage = 0;

                tabHelper.setCurrentPage(newPage);
                tabs = tabHelper.getTabsForPage(newPage);
            }

            tabHelper.setSelectedTab(tabs.get(newIndex));
        }
        if (ControlifyBindings.GUI_PREV_TAB.on(controller).justPressed()) {
            int newIndex = tabs.indexOf(tabHelper.getSelectedTab()) - 1;
            if (newIndex < 0) {
                int newPage = tabHelper.getCurrentPage() - 1;
                if (newPage < 0)
                    newPage = tabHelper.getPageCount() - 1;

                tabHelper.setCurrentPage(newPage);
                tabs = tabHelper.getTabsForPage(newPage);
                newIndex = tabs.size() - 1;
            }

            tabHelper.setSelectedTab(tabs.get(newIndex));
        }

        super.handleScreenVMouse(controller, vmouse);
    }

    @Override
    protected void render(ControllerEntity controller, GuiGraphics graphics, float tickDelta,
                          Optional<VirtualMouseHandler> vmouse) {
        super.render(controller, graphics, tickDelta, vmouse);
        if (!controller.settings().generic.guide.showScreenGuides) return;

        CreativeModeTab tab = tabHelper.getSelectedTab();
        if (tab == null) return;
        var container = (AbstractContainerScreenAccessor) screen;
        var creative = (CreativeModeInventoryScreenAccessor) screen;
        int tabX = container.getLeftPos() + creative.invokeGetTabX(tab);
        int tabY = container.getTopPos()
                + (tab.row() == CreativeModeTab.Row.TOP ? -28 : container.getImageHeight() - 4);

        int glyphY = tab.row() == CreativeModeTab.Row.TOP
                ? tabY - minecraft.font.lineHeight - 3
                : tabY + 31;
        drawTabGlyphs(graphics, controller, tabX, glyphY);
    }

    private void drawTabGlyphs(GuiGraphics graphics, ControllerEntity controller, int tabX, int y) {
        var previous = ControlifyBindings.GUI_PREV_TAB.on(controller);
        var next = ControlifyBindings.GUI_NEXT_TAB.on(controller);
        int previousWidth = previous.isUnbound() ? 0 : minecraft.font.width(previous.inputGlyph()) + 5;
        int nextWidth = next.isUnbound() ? 0 : minecraft.font.width(next.inputGlyph()) + 5;
        int x = Math.max(2, Math.min(
                tabX + (28 - previousWidth - nextWidth) / 2,
                graphics.guiWidth() - previousWidth - nextWidth - 2
        ));
        if (!previous.isUnbound()) {
            x += GuideRenderer.drawGlyph(graphics, minecraft.font, previous.inputGlyph(), x, y);
        }
        if (!next.isUnbound()) {
            GuideRenderer.drawGlyph(graphics, minecraft.font, next.inputGlyph(), x, y);
        }
    }
}
