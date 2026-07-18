package dev.isxander.controlify.compatibility.recipeviewer;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.api.vmousesnapping.SnapPoint;
import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.gui.guide.GuideRenderer;
import dev.isxander.controlify.platform.client.PlatformClientUtil;
import dev.isxander.controlify.platform.main.PlatformMainUtil;
import dev.isxander.controlify.screenop.ScreenProcessorProvider;
import dev.isxander.controlify.virtualmouse.VirtualMouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.joml.Vector2i;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Optional JEI and EMI controller support, isolated from their binary APIs. */
public final class RecipeViewerCompat {
    private static final String JEI_RECIPE_SCREEN = "mezz.jei.gui.recipes.RecipesGui";
    private static final String JEI_INTERNAL = "mezz.jei.common.Internal";
    private static final String EMI_RECIPE_SCREEN = "dev.emi.emi.screen.RecipeScreen";
    private static final String EMI_SCREEN_MANAGER = "dev.emi.emi.screen.EmiScreenManager";
    private static boolean jeiLoaded;
    private static boolean emiLoaded;
    private static final int EDGE_PADDING = 4;

    private RecipeViewerCompat() {
    }

    public static void preInit() {
        jeiLoaded = PlatformMainUtil.isModLoaded("jei");
        emiLoaded = PlatformMainUtil.isModLoaded("emi");
        if (jeiLoaded) registerRecipeScreen(JEI_RECIPE_SCREEN);
        if (emiLoaded) registerRecipeScreen(EMI_RECIPE_SCREEN);
        VirtualMouseHandler.registerSnapPointProvider(RecipeViewerCompat::collectSnapPoints);
    }

    public static void init() {
        // Register late so this legend renders after JEI/EMI's item overlays.
        PlatformClientUtil.registerPostScreenRender(RecipeViewerCompat::renderControllerGuide);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerRecipeScreen(String className) {
        try {
            Class<? extends Screen> screenClass = (Class<? extends Screen>) Class.forName(className);
            ScreenProcessorProvider.registerProvider((Class) screenClass,
                    screen -> new RecipeViewerScreenProcessor((Screen) screen));
        } catch (ClassNotFoundException ignored) {
        }
    }

    private static void collectSnapPoints(Screen screen, Consumer<SnapPoint> consumer) {
        if (jeiLoaded) {
            try {
                collectJeiOverlayPoints(consumer);
                if (isScreen(screen, JEI_RECIPE_SCREEN)) collectJeiRecipePoints(screen, consumer);
            } catch (Throwable ignored) {
            }
        }
        if (emiLoaded) {
            try {
                collectEmiSidebarPoints(consumer);
                if (isScreen(screen, EMI_RECIPE_SCREEN)) collectEmiRecipePoints(screen, consumer);
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean handleContainerPaging(Screen screen, ControllerEntity controller,
                                                VirtualMouseHandler vmouse) {
        if (!emiLoaded || isRecipeViewerScreen(screen)) return false;
        int direction = pageDirection(controller);
        if (direction == 0) return false;

        try {
            Object panel = invokeStatic(
                    EMI_SCREEN_MANAGER,
                    "getHoveredPanel",
                    (int) vmouse.getCurrentX(1f),
                    (int) vmouse.getCurrentY(1f)
            );
            if (panel == null || !booleanMethod(panel, "isVisible", false)) {
                panel = invokeStatic(EMI_SCREEN_MANAGER, "getSearchPanel");
            }
            if (panel == null || !booleanMethod(panel, "isVisible", false)) {
                Object panels = staticField(EMI_SCREEN_MANAGER, "panels");
                if (panels instanceof Collection<?> collection) {
                    panel = collection.stream()
                            .filter(candidate -> {
                                try {
                                    return booleanMethod(candidate, "isVisible", false);
                                } catch (ReflectiveOperationException ignored) {
                                    return false;
                                }
                            })
                            .findFirst()
                            .orElse(null);
                }
            }
            if (panel == null) return false;
            invoke(panel, "scroll", direction);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static GuideRenderer.Bounds containerGuideBounds(AbstractContainerScreen<?> screen) {
        GuideRenderer.Bounds bounds = GuideRenderer.belowContainer(screen);
        if (!hasVisibleOverlay()) return bounds;
        ViewerArea area = viewerArea(screen, screen.width, screen.height);
        int reservedBottom = Math.max(bounds.top(), Math.min(bounds.bottom(), area.bottom()) - 31);
        return new GuideRenderer.Bounds(bounds.left(), bounds.top(), bounds.right(), reservedBottom);
    }

    public static boolean handleRecipeNavigation(Screen screen, ControllerEntity controller) {
        int tabDirection = tabDirection(controller);
        int pageDirection = pageDirection(controller);
        if (tabDirection == 0 && pageDirection == 0) return false;

        try {
            if (isScreen(screen, EMI_RECIPE_SCREEN)) {
                int tabPage = number(field(screen, "tabPage"));
                int tab = number(field(screen, "tab"));
                int page = number(field(screen, "page"));
                if (tabDirection != 0) {
                    invoke(screen, "setPage", tabPage, tab + tabDirection, 0);
                } else {
                    invoke(screen, "setPage", tabPage, tab, page + pageDirection);
                }
                return true;
            }
            if (isScreen(screen, JEI_RECIPE_SCREEN)) {
                Object logic = field(screen, "logic");
                if (tabDirection < 0) invoke(logic, "previousRecipeCategory");
                else if (tabDirection > 0) invoke(logic, "nextRecipeCategory");
                else if (pageDirection < 0) invoke(logic, "previousPage");
                else invoke(logic, "nextPage");
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static int tabDirection(ControllerEntity controller) {
        if (ControlifyBindings.GUI_PREV_TAB.on(controller).justPressed()) return -1;
        if (ControlifyBindings.GUI_NEXT_TAB.on(controller).justPressed()) return 1;
        return 0;
    }

    private static int pageDirection(ControllerEntity controller) {
        if (ControlifyBindings.VMOUSE_PAGE_UP.on(controller).justPressed()) return -1;
        if (ControlifyBindings.VMOUSE_PAGE_DOWN.on(controller).justPressed()) return 1;
        return 0;
    }

    private static void collectJeiOverlayPoints(Consumer<SnapPoint> consumer) throws ReflectiveOperationException {
        Object runtime = invokeStatic(JEI_INTERNAL, "getJeiRuntime");
        collectJeiOverlay(invoke(runtime, "getIngredientListOverlay"), consumer);
        collectJeiOverlay(invoke(runtime, "getBookmarkOverlay"), consumer);
    }

    private static void collectJeiOverlay(Object overlay, Consumer<SnapPoint> consumer) throws ReflectiveOperationException {
        if (overlay == null || !booleanMethod(overlay, "isListDisplayed", true)) return;
        Object contents = field(overlay, "contents");
        if (contents == null) return;

        Object slots = invoke(contents, "getSlots");
        if (slots instanceof Stream<?> stream) {
            try (stream) {
                stream.forEach(slot -> {
                    try {
                        Object element = invoke(slot, "getOptionalElement");
                        if (element instanceof Optional<?> optional && optional.isEmpty()) return;
                        addBounds(invoke(slot, "getRenderArea"), consumer, 16);
                    } catch (ReflectiveOperationException ignored) {
                    }
                });
            }
        }
        addBounds(invoke(contents, "getNextPageButtonArea"), consumer, 14);
        addBounds(invoke(contents, "getBackButtonArea"), consumer, 14);
    }

    private static void collectJeiRecipePoints(Screen screen, Consumer<SnapPoint> consumer) throws ReflectiveOperationException {
        Object layouts = field(screen, "layouts");
        Object wrappedLayouts = layouts == null ? null : field(layouts, "recipeLayoutsWithButtons");
        if (!(wrappedLayouts instanceof Collection<?> collection)) return;

        for (Object wrapped : collection) {
            Object layout = invoke(wrapped, "getRecipeLayout");
            Object slotsView = invoke(layout, "getRecipeSlots");
            Object slots = invoke(slotsView, "getSlots");
            if (slots instanceof Collection<?> slotCollection) {
                for (Object slot : slotCollection) {
                    addBounds(invoke(slot, "getAreaIncludingBackground"), consumer, 16);
                }
            }
            addBounds(invoke(layout, "getRecipeTransferButtonArea"), consumer, 14);
            addBounds(invoke(layout, "getRecipeBookmarkButtonArea"), consumer, 14);
        }
    }

    private static void collectEmiRecipePoints(Screen screen, Consumer<SnapPoint> consumer) throws ReflectiveOperationException {
        Object currentPage = field(screen, "currentPage");
        if (!(currentPage instanceof Collection<?> groups)) return;
        for (Object group : groups) {
            Object widgets = field(group, "widgets");
            if (!(widgets instanceof Collection<?> widgetCollection)) continue;
            for (Object widget : widgetCollection) {
                addBounds(invoke(widget, "getBounds"), consumer, 16);
            }
        }
    }

    private static void collectEmiSidebarPoints(Consumer<SnapPoint> consumer) throws ReflectiveOperationException {
        Object panels = staticField(EMI_SCREEN_MANAGER, "panels");
        if (!(panels instanceof Collection<?> panelCollection)) return;
        for (Object panel : panelCollection) {
            if (!booleanMethod(panel, "isVisible", false)) continue;
            int page = number(field(panel, "page"));
            Object spaces = invoke(panel, "getSpaces");
            if (!(spaces instanceof Collection<?> spaceCollection)) continue;
            for (Object space : spaceCollection) {
                Object entries = invoke(space, "getPage", page);
                if (!(entries instanceof List<?> list)) continue;
                for (int index = 0; index < list.size(); index++) {
                    int x = number(invoke(space, "getRawX", index));
                    int y = number(invoke(space, "getRawY", index));
                    consumer.accept(new SnapPoint(new Vector2i(x + 9, y + 9), 16));
                }
            }
        }
    }

    private static void renderControllerGuide(Screen screen, GuiGraphics graphics,
                                              int mouseX, int mouseY, float tickDelta) {
        if (!Controlify.instance().currentInputMode().isController()) return;
        Optional<ControllerEntity> controller = Controlify.instance().getCurrentController();
        if (controller.isEmpty() || !controller.get().settings().generic.guide.showScreenGuides) return;
        if (!isRecipeViewerScreen(screen)
                && (!(screen instanceof AbstractContainerScreen<?>) || !hasVisibleOverlay())) return;

        Component primary = Component.translatable(
                "controlify.compat.recipe_viewer.guide.primary",
                glyph(ControlifyBindings.VMOUSE_SNAP_UP, controller.get()),
                glyph(ControlifyBindings.VMOUSE_LCLICK, controller.get()),
                glyph(ControlifyBindings.VMOUSE_RCLICK, controller.get())
        );
        Component secondary = isRecipeViewerScreen(screen)
                ? Component.translatable(
                        "controlify.compat.recipe_viewer.guide.recipe_secondary",
                        glyph(ControlifyBindings.GUI_PREV_TAB, controller.get()),
                        glyph(ControlifyBindings.GUI_NEXT_TAB, controller.get()),
                        glyph(ControlifyBindings.VMOUSE_PAGE_UP, controller.get()),
                        glyph(ControlifyBindings.VMOUSE_PAGE_DOWN, controller.get()),
                        glyph(ControlifyBindings.GUI_BACK, controller.get())
                )
                : Component.translatable(
                        "controlify.compat.recipe_viewer.guide.sidebar_secondary",
                        glyph(ControlifyBindings.VMOUSE_SCROLL_UP, controller.get()),
                        glyph(ControlifyBindings.VMOUSE_PAGE_UP, controller.get()),
                        glyph(ControlifyBindings.VMOUSE_PAGE_DOWN, controller.get()),
                        glyph(ControlifyBindings.GUI_BACK, controller.get())
                );
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            GuideRenderer.Bounds bounds = GuideRenderer.belowContainer(containerScreen);
            ViewerArea area = viewerArea(screen, graphics.guiWidth(), graphics.guiHeight());
            int bottom = Math.max(bounds.top() + 28, Math.min(bounds.bottom(), area.bottom()));
            drawGuideLine(graphics, primary, bounds.left(), bounds.right(), bottom - 27);
            drawGuideLine(graphics, secondary, bounds.left(), bounds.right(), bottom - 15);
        } else {
            ViewerArea area = viewerArea(screen, graphics.guiWidth(), graphics.guiHeight());
            drawGuideLine(graphics, primary, area.left(), area.right(), area.bottom() - 27);
            drawGuideLine(graphics, secondary, area.left(), area.right(), area.bottom() - 15);
        }
    }

    private static Component glyph(InputBindingSupplier supplier, ControllerEntity controller) {
        return supplier.on(controller).inputGlyph();
    }

    private static void drawGuideLine(GuiGraphics graphics, Component line, int left, int right, int y) {
        Font font = Minecraft.getInstance().font;
        int width = font.width(line);
        int x = left + Math.max(0, (right - left - width) / 2);
        graphics.fill(x - 3, y - 2, x + width + 3, y + font.lineHeight + 2, 0xC0000000);
        graphics.drawString(font, line, x, y, 0xFFFFFFFF, false);
    }

    private static ViewerArea viewerArea(Screen screen, int width, int height) {
        MutableViewerArea area = new MutableViewerArea(width, height);
        if (jeiLoaded) {
            try {
                Object runtime = invokeStatic(JEI_INTERNAL, "getJeiRuntime");
                reserveJeiOverlay(invoke(runtime, "getIngredientListOverlay"), area);
                reserveJeiOverlay(invoke(runtime, "getBookmarkOverlay"), area);
            } catch (Throwable ignored) {
            }
        }
        if (emiLoaded) {
            boolean emiVisible = false;
            try {
                Object panels = staticField(EMI_SCREEN_MANAGER, "panels");
                if (panels instanceof Collection<?> collection) {
                    for (Object panel : collection) {
                        try {
                            if (booleanMethod(panel, "isVisible", false)) {
                                emiVisible = true;
                                reserveEdge(readRect(invoke(panel, "getBounds")), area);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            // Keep this separate: one incompatible panel must not prevent the search bar being reserved.
            boolean searchReserved = false;
            try {
                Object search = staticField(EMI_SCREEN_MANAGER, "search");
                Rect searchBounds = readRect(search);
                if (searchBounds != null && searchBounds.width() > 0 && searchBounds.height() > 0) {
                    reserveEdge(searchBounds, area);
                    searchReserved = true;
                }
            } catch (Throwable ignored) {
            }
            if (emiVisible && !searchReserved) {
                area.bottom = Math.min(area.bottom, height - 52);
            }
        }
        return area.freeze();
    }

    private static void reserveJeiOverlay(Object overlay, MutableViewerArea area)
            throws ReflectiveOperationException {
        if (overlay == null || !booleanMethod(overlay, "isListDisplayed", false)) return;
        Object contents = field(overlay, "contents");
        if (contents != null) reserveEdge(readRect(invoke(contents, "getBackgroundArea")), area);
        try {
            Object search = field(overlay, "searchField");
            reserveEdge(readRect(search), area);
        } catch (NoSuchFieldException ignored) {
        }
    }

    private static void reserveEdge(Rect rect, MutableViewerArea area) {
        if (rect == null || rect.width() <= 0 || rect.height() <= 0) return;
        int leftDistance = Math.max(0, rect.x());
        int rightDistance = Math.max(0, area.screenWidth - rect.right());
        int topDistance = Math.max(0, rect.y());
        int bottomDistance = Math.max(0, area.screenHeight - rect.bottom());
        int nearest = Math.min(Math.min(leftDistance, rightDistance), Math.min(topDistance, bottomDistance));

        if (nearest == bottomDistance) {
            area.bottom = Math.min(area.bottom, rect.y() - EDGE_PADDING);
        } else if (nearest == topDistance) {
            area.top = Math.max(area.top, rect.bottom() + EDGE_PADDING);
        } else if (nearest == leftDistance) {
            area.left = Math.max(area.left, rect.right() + EDGE_PADDING);
        } else {
            area.right = Math.min(area.right, rect.x() - EDGE_PADDING);
        }
    }

    private static boolean isRecipeViewerScreen(Screen screen) {
        return isScreen(screen, JEI_RECIPE_SCREEN) || isScreen(screen, EMI_RECIPE_SCREEN);
    }

    private static boolean hasVisibleOverlay() {
        try {
            if (jeiLoaded) {
                Object runtime = invokeStatic(JEI_INTERNAL, "getJeiRuntime");
                if (booleanMethod(invoke(runtime, "getIngredientListOverlay"), "isListDisplayed", false)
                        || booleanMethod(invoke(runtime, "getBookmarkOverlay"), "isListDisplayed", false)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            if (emiLoaded) {
                Object panels = staticField(EMI_SCREEN_MANAGER, "panels");
                if (panels instanceof Collection<?> collection) {
                    for (Object panel : collection) {
                        if (booleanMethod(panel, "isVisible", false)) return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isScreen(Screen screen, String className) {
        return screen != null && screen.getClass().getName().equals(className);
    }

    private static void addBounds(Object bounds, Consumer<SnapPoint> consumer, int range)
            throws ReflectiveOperationException {
        Rect rect = readRect(bounds);
        if (rect == null || rect.width() <= 0 || rect.height() <= 0) return;
        consumer.accept(new SnapPoint(new Vector2i(rect.x() + rect.width() / 2, rect.y() + rect.height() / 2), range));
    }

    private static Rect readRect(Object bounds) throws ReflectiveOperationException {
        if (bounds == null) return null;
        return new Rect(
                coordinate(bounds, "x", "getX"),
                coordinate(bounds, "y", "getY"),
                coordinate(bounds, "width", "getWidth"),
                coordinate(bounds, "height", "getHeight")
        );
    }

    private static int coordinate(Object target, String recordMethod, String beanMethod)
            throws ReflectiveOperationException {
        try {
            return number(invoke(target, recordMethod));
        } catch (NoSuchMethodException ignored) {
            return number(invoke(target, beanMethod));
        }
    }

    private static boolean booleanMethod(Object target, String name, boolean fallback)
            throws ReflectiveOperationException {
        if (target == null) return fallback;
        try {
            return Boolean.TRUE.equals(invoke(target, name));
        } catch (NoSuchMethodException ignored) {
            return fallback;
        }
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static Object invokeStatic(String className, String method, Object... args)
            throws ReflectiveOperationException {
        return invoke(Class.forName(className), null, method, args);
    }

    private static Object invoke(Object target, String method, Object... args)
            throws ReflectiveOperationException {
        return invoke(target.getClass(), target, method, args);
    }

    private static Object invoke(Class<?> type, Object target, String method, Object... args)
            throws ReflectiveOperationException {
        Method found = null;
        for (Method candidate : type.getMethods()) {
            if (candidate.getName().equals(method) && candidate.getParameterCount() == args.length) {
                found = candidate;
                break;
            }
        }
        if (found == null) {
            for (Class<?> cursor = type; cursor != null && found == null; cursor = cursor.getSuperclass()) {
                for (Method candidate : cursor.getDeclaredMethods()) {
                    if (candidate.getName().equals(method) && candidate.getParameterCount() == args.length) {
                        found = candidate;
                        break;
                    }
                }
            }
        }
        if (found == null) throw new NoSuchMethodException(type.getName() + "." + method);
        found.setAccessible(true);
        return found.invoke(target, args);
    }

    private static Object staticField(String className, String name) throws ReflectiveOperationException {
        return field(Class.forName(className), null, name);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        return field(target.getClass(), target, name);
    }

    private static Object field(Class<?> type, Object target, String name) throws ReflectiveOperationException {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
    }

    private record ViewerArea(int left, int top, int right, int bottom) {
        int width() { return Math.max(0, right - left); }
    }

    private static final class MutableViewerArea {
        private final int screenWidth;
        private final int screenHeight;
        private int left;
        private int top;
        private int right;
        private int bottom;

        private MutableViewerArea(int width, int height) {
            this.screenWidth = width;
            this.screenHeight = height;
            this.right = width;
            this.bottom = height;
        }

        private ViewerArea freeze() {
            if (right <= left || bottom <= top) return new ViewerArea(0, 0, screenWidth, screenHeight);
            return new ViewerArea(left, top, right, bottom);
        }
    }
}
