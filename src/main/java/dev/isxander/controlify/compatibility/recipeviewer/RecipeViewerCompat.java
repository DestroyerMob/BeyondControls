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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
        // Register late so contextual glyphs render after JEI/EMI and their tooltips.
        PlatformClientUtil.registerPostScreenRender(RecipeViewerCompat::renderContextualHints);
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

    private static void renderContextualHints(Screen screen, GuiGraphics graphics,
                                              int mouseX, int mouseY, float tickDelta) {
        if (!Controlify.instance().currentInputMode().isController()) return;
        Optional<ControllerEntity> controller = Controlify.instance().getCurrentController();
        if (controller.isEmpty() || !controller.get().settings().generic.guide.showScreenGuides) return;
        ControllerEntity activeController = controller.get();
        boolean hoveredViewerItem = false;

        if (emiLoaded) {
            try {
                if (isScreen(screen, EMI_RECIPE_SCREEN)) {
                    renderEmiRecipeHints(screen, graphics, activeController);
                } else {
                    renderEmiSidebarHints(graphics, activeController, mouseX, mouseY);
                }
                hoveredViewerItem = hasHoveredEmiStack(mouseX, mouseY);
            } catch (Throwable ignored) {
            }
        }
        if (jeiLoaded) {
            try {
                if (isScreen(screen, JEI_RECIPE_SCREEN)) {
                    renderJeiRecipeHints(screen, graphics, activeController);
                    hoveredViewerItem |= valuePresent(invoke(
                            screen, "getIngredientUnderMouse", (double) mouseX, (double) mouseY
                    ));
                } else if (hasHoveredJeiIngredient()) {
                    hoveredViewerItem = true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (hoveredViewerItem) {
            renderItemActionGlyphs(graphics, activeController, mouseX, mouseY);
        }
    }

    private static void renderEmiSidebarHints(GuiGraphics graphics, ControllerEntity controller,
                                               int mouseX, int mouseY) throws ReflectiveOperationException {
        Object panel = invokeStatic(EMI_SCREEN_MANAGER, "getHoveredPanel", mouseX, mouseY);
        if (panel == null || !booleanMethod(panel, "isVisible", false)) {
            panel = invokeStatic(EMI_SCREEN_MANAGER, "getSearchPanel");
        }
        if (panel == null || !booleanMethod(panel, "isVisible", false)
                || !booleanMethod(panel, "hasMultiplePages", false)) return;
        drawBeside(graphics, controller, ControlifyBindings.VMOUSE_PAGE_UP, field(panel, "pageLeft"), true);
        drawBeside(graphics, controller, ControlifyBindings.VMOUSE_PAGE_DOWN, field(panel, "pageRight"), false);
    }

    private static void renderEmiRecipeHints(Screen screen, GuiGraphics graphics, ControllerEntity controller)
            throws ReflectiveOperationException {
        Object arrows = field(screen, "arrows");
        if (!(arrows instanceof List<?> list) || list.size() < 6) return;
        drawBeside(graphics, controller, ControlifyBindings.GUI_PREV_TAB, list.get(2), true);
        drawBeside(graphics, controller, ControlifyBindings.GUI_NEXT_TAB, list.get(3), false);
        drawBeside(graphics, controller, ControlifyBindings.VMOUSE_PAGE_UP, list.get(4), true);
        drawBeside(graphics, controller, ControlifyBindings.VMOUSE_PAGE_DOWN, list.get(5), false);
    }

    private static void renderJeiRecipeHints(Screen screen, GuiGraphics graphics, ControllerEntity controller)
            throws ReflectiveOperationException {
        drawBeside(graphics, controller, ControlifyBindings.GUI_PREV_TAB,
                field(screen, "previousRecipeCategory"), true);
        drawBeside(graphics, controller, ControlifyBindings.GUI_NEXT_TAB,
                field(screen, "nextRecipeCategory"), false);
        drawBeside(graphics, controller, ControlifyBindings.VMOUSE_PAGE_UP,
                field(screen, "previousPage"), true);
        drawBeside(graphics, controller, ControlifyBindings.VMOUSE_PAGE_DOWN,
                field(screen, "nextPage"), false);
    }

    private static boolean hasHoveredEmiStack(int mouseX, int mouseY) throws ReflectiveOperationException {
        Object interaction = invokeStatic(EMI_SCREEN_MANAGER, "getHoveredStack", mouseX, mouseY, true);
        return interaction != null && !booleanMethod(interaction, "isEmpty", true);
    }

    private static boolean hasHoveredJeiIngredient() throws ReflectiveOperationException {
        Object runtime = invokeStatic(JEI_INTERNAL, "getJeiRuntime");
        return valuePresent(invoke(invoke(runtime, "getIngredientListOverlay"), "getIngredientUnderMouse"))
                || valuePresent(invoke(invoke(runtime, "getBookmarkOverlay"), "getIngredientUnderMouse"));
    }

    private static boolean valuePresent(Object value) {
        if (value instanceof Optional<?> optional) return optional.isPresent();
        if (value instanceof Stream<?> stream) {
            try (stream) {
                return stream.findAny().isPresent();
            }
        }
        return value != null;
    }

    private static void renderItemActionGlyphs(GuiGraphics graphics, ControllerEntity controller,
                                               int mouseX, int mouseY) {
        var leftClick = ControlifyBindings.VMOUSE_LCLICK.on(controller);
        var rightClick = ControlifyBindings.VMOUSE_RCLICK.on(controller);
        int totalWidth = 0;
        if (!leftClick.isUnbound()) totalWidth += Minecraft.getInstance().font.width(leftClick.inputGlyph()) + 5;
        if (!rightClick.isUnbound()) totalWidth += Minecraft.getInstance().font.width(rightClick.inputGlyph()) + 5;
        int x = Math.max(3, mouseX - totalWidth - 8);
        int y = Math.max(3, mouseY - Minecraft.getInstance().font.lineHeight / 2);
        if (!leftClick.isUnbound()) {
            x += GuideRenderer.drawGlyphBadge(
                    graphics, Minecraft.getInstance().font, leftClick.inputGlyph(), x, y
            );
        }
        if (!rightClick.isUnbound()) {
            GuideRenderer.drawGlyphBadge(graphics, Minecraft.getInstance().font, rightClick.inputGlyph(), x, y);
        }
    }

    private static void drawBeside(GuiGraphics graphics, ControllerEntity controller,
                                   InputBindingSupplier supplier, Object targetObject, boolean leftSide)
            throws ReflectiveOperationException {
        if (targetObject == null || !widgetVisible(targetObject)) return;
        Rect target = readRect(targetObject);
        if (target == null || target.width() <= 0 || target.height() <= 0) return;
        var binding = supplier.on(controller);
        if (binding.isUnbound()) return;
        var glyph = binding.inputGlyph();
        int glyphWidth = Minecraft.getInstance().font.width(glyph);
        int x = leftSide ? target.x() - glyphWidth - 4 : target.right() + 4;
        x = Math.max(2, Math.min(x, graphics.guiWidth() - glyphWidth - 2));
        int y = target.y() + Math.max(0, (target.height() - Minecraft.getInstance().font.lineHeight) / 2);
        GuideRenderer.drawGlyphBadge(graphics, Minecraft.getInstance().font, glyph, x, y);
    }

    private static boolean widgetVisible(Object target) throws ReflectiveOperationException {
        try {
            Object value = field(target, "visible");
            return !(value instanceof Boolean visible) || visible;
        } catch (NoSuchFieldException ignored) {
            return booleanMethod(target, "isVisible", true);
        }
    }

    private static boolean isRecipeViewerScreen(Screen screen) {
        return isScreen(screen, JEI_RECIPE_SCREEN) || isScreen(screen, EMI_RECIPE_SCREEN);
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

}
