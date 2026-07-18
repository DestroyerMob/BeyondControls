package dev.isxander.controlify.screenop.compat.vanilla;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.compatibility.recipeviewer.RecipeViewerCompat;
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
        int mouseX = (int) vmouse.getCurrentX(1f);
        int mouseY = (int) vmouse.getCurrentY(1f);
        if (!RecipeViewerCompat.isHoveringItemPanel(screen, mouseX, mouseY)) {
            int tabDirection = triggerDirection(controller);
            if (tabDirection != 0) changeTab(tabDirection);

            int pageDirection = bumperDirection(controller);
            if (pageDirection != 0) changePage(pageDirection);
        }

        super.handleScreenVMouse(controller, vmouse);
    }

    private int triggerDirection(ControllerEntity controller) {
        if (ControlifyBindings.VMOUSE_PAGE_UP.on(controller).justPressed()) return -1;
        if (ControlifyBindings.VMOUSE_PAGE_DOWN.on(controller).justPressed()) return 1;
        return 0;
    }

    private int bumperDirection(ControllerEntity controller) {
        if (ControlifyBindings.VMOUSE_PAGE_PREV.on(controller).justPressed()) return -1;
        if (ControlifyBindings.VMOUSE_PAGE_NEXT.on(controller).justPressed()) return 1;
        return 0;
    }

    private void changeTab(int direction) {
        List<CreativeModeTab> tabs = tabHelper.getTabsForPage(tabHelper.getCurrentPage());
        if (tabs.isEmpty()) return;
        int currentIndex = Math.max(0, tabs.indexOf(tabHelper.getSelectedTab()));
        tabHelper.setSelectedTab(tabs.get(Math.floorMod(currentIndex + direction, tabs.size())));
    }

    private void changePage(int direction) {
        int pageCount = tabHelper.getPageCount();
        if (pageCount <= 1) return;
        List<CreativeModeTab> oldTabs = tabHelper.getTabsForPage(tabHelper.getCurrentPage());
        int selectedIndex = Math.max(0, oldTabs.indexOf(tabHelper.getSelectedTab()));
        int newPage = Math.floorMod(tabHelper.getCurrentPage() + direction, pageCount);
        tabHelper.setCurrentPage(newPage);
        List<CreativeModeTab> newTabs = tabHelper.getTabsForPage(newPage);
        if (!newTabs.isEmpty()) {
            tabHelper.setSelectedTab(newTabs.get(Math.min(selectedIndex, newTabs.size() - 1)));
        }
    }

    @Override
    protected void render(ControllerEntity controller, GuiGraphics graphics, float tickDelta,
                          Optional<VirtualMouseHandler> vmouse) {
        super.render(controller, graphics, tickDelta, vmouse);
        if (!controller.settings().generic.guide.showScreenGuides) return;
        if (vmouse.isPresent() && RecipeViewerCompat.isHoveringItemPanel(
                screen,
                (int) vmouse.get().getCurrentX(tickDelta),
                (int) vmouse.get().getCurrentY(tickDelta)
        )) return;

        CreativeModeTab tab = tabHelper.getSelectedTab();
        if (tab == null) return;
        var container = (AbstractContainerScreenAccessor) screen;
        var creative = (CreativeModeInventoryScreenAccessor) screen;
        int tabX = container.getLeftPos() + creative.invokeGetTabX(tab);
        boolean topRow = tabHelper.isTabOnTop(tab);
        int tabY = container.getTopPos()
                + (topRow ? -28 : container.getImageHeight() - 4);

        int glyphY = topRow
                ? tabY - minecraft.font.lineHeight - 3
                : tabY + 31;
        drawTabGlyphs(graphics, controller, tabX, glyphY);
        drawPageGlyphs(graphics, controller, container);
    }

    private void drawTabGlyphs(GuiGraphics graphics, ControllerEntity controller, int tabX, int y) {
        var previous = ControlifyBindings.VMOUSE_PAGE_UP.on(controller);
        var next = ControlifyBindings.VMOUSE_PAGE_DOWN.on(controller);
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

    private void drawPageGlyphs(GuiGraphics graphics, ControllerEntity controller,
                                AbstractContainerScreenAccessor container) {
        if (tabHelper.getPageCount() <= 1) return;
        var previous = ControlifyBindings.VMOUSE_PAGE_PREV.on(controller);
        var next = ControlifyBindings.VMOUSE_PAGE_NEXT.on(controller);
        int buttonY = container.getTopPos() - 50;
        int glyphY = buttonY + Math.max(0, (20 - minecraft.font.lineHeight) / 2);
        if (!previous.isUnbound()) {
            int width = minecraft.font.width(previous.inputGlyph());
            GuideRenderer.drawGlyph(
                    graphics, minecraft.font, previous.inputGlyph(),
                    Math.max(2, container.getLeftPos() - width - 4), glyphY
            );
        }
        if (!next.isUnbound()) {
            GuideRenderer.drawGlyph(
                    graphics, minecraft.font, next.inputGlyph(),
                    Math.min(graphics.guiWidth() - minecraft.font.width(next.inputGlyph()) - 2,
                            container.getLeftPos() + container.getImageWidth() + 4),
                    glyphY
            );
        }
    }
}
