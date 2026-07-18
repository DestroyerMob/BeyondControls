package dev.isxander.controlify.compatibility.recipeviewer;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.api.vmousesnapping.SnapPoint;
import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.gui.guide.GuideRenderer;
import dev.isxander.controlify.mixins.feature.guide.screen.AbstractContainerScreenAccessor;
import dev.isxander.controlify.platform.client.PlatformClientUtil;
import dev.isxander.controlify.platform.main.PlatformMainUtil;
import dev.isxander.controlify.screenop.ScreenProcessorProvider;
import dev.isxander.controlify.virtualmouse.VirtualMouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
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
    private static final String JEI_VANILLA_TYPES = "mezz.jei.api.constants.VanillaTypes";
    private static final String JEI_RECIPE_ROLE = "mezz.jei.api.recipe.RecipeIngredientRole";
    private static final String EMI_RECIPE_SCREEN = "dev.emi.emi.screen.RecipeScreen";
    private static final String EMI_SCREEN_MANAGER = "dev.emi.emi.screen.EmiScreenManager";
    private static final String EMI_API = "dev.emi.emi.api.EmiApi";
    private static final String EMI_STACK = "dev.emi.emi.api.stack.EmiStack";
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
        if (isRecipeViewerScreen(screen)) return false;
        int direction = tabDirection(controller);
        if (direction == 0) return false;
        int mouseX = (int) vmouse.getCurrentX(1f);
        int mouseY = (int) vmouse.getCurrentY(1f);

        if (emiLoaded) {
            try {
                Object panel = invokeStatic(EMI_SCREEN_MANAGER, "getHoveredPanel", mouseX, mouseY);
                if (panel != null && booleanMethod(panel, "isVisible", false)) {
                    invoke(panel, "scroll", direction);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (jeiLoaded) {
            try {
                Object contents = hoveredJeiContents(mouseX, mouseY);
                if (contents != null) {
                    Object paged = invoke(contents, "getPageDelegate");
                    Object changed = invoke(paged, direction < 0 ? "previousPage" : "nextPage");
                    return !(changed instanceof Boolean result) || result;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static boolean isHoveringItemPanel(Screen screen, int mouseX, int mouseY) {
        if (isRecipeViewerScreen(screen)) return false;
        if (emiLoaded) {
            try {
                Object panel = invokeStatic(EMI_SCREEN_MANAGER, "getHoveredPanel", mouseX, mouseY);
                if (panel != null && booleanMethod(panel, "isVisible", false)) return true;
            } catch (Throwable ignored) {
            }
        }
        if (jeiLoaded) {
            try {
                return hoveredJeiContents(mouseX, mouseY) != null;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static boolean isStackViewerAvailable() {
        return emiLoaded || jeiLoaded;
    }

    public static boolean openStackViewer(ItemStack stack, boolean uses) {
        if (stack == null || stack.isEmpty()) return false;
        if (emiLoaded) {
            try {
                Object emiStack = invokeStatic(EMI_STACK, "of", stack.copy());
                invokeStatic(EMI_API, uses ? "displayUses" : "displayRecipes", emiStack);
                return true;
            } catch (Throwable ignored) {
            }
        }
        if (jeiLoaded) {
            try {
                Object runtime = invokeStatic(JEI_INTERNAL, "getJeiRuntime");
                Object ingredientManager = invoke(runtime, "getIngredientManager");
                Object itemType = staticField(JEI_VANILLA_TYPES, "ITEM_STACK");
                Object optionalTyped = invoke(ingredientManager, "createTypedIngredient", itemType, stack.copy());
                if (!(optionalTyped instanceof Optional<?> optional) || optional.isEmpty()) return false;
                Object helpers = invoke(runtime, "getJeiHelpers");
                Object focusFactory = invoke(helpers, "getFocusFactory");
                Object role = staticField(JEI_RECIPE_ROLE, uses ? "INPUT" : "OUTPUT");
                Object focus = invoke(focusFactory, "createFocus", role, optional.get());
                invoke(invoke(runtime, "getRecipesGui"), "show", focus);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
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

        if (emiLoaded) {
            try {
                if (isScreen(screen, EMI_RECIPE_SCREEN)) {
                    renderEmiRecipeHints(screen, graphics, activeController);
                } else {
                    renderEmiSidebarHints(graphics, activeController, mouseX, mouseY);
                }
            } catch (Throwable ignored) {
            }
        }
        if (jeiLoaded) {
            try {
                if (isScreen(screen, JEI_RECIPE_SCREEN)) {
                    renderJeiRecipeHints(screen, graphics, activeController);
                } else {
                    renderJeiOverlayHints(graphics, activeController, mouseX, mouseY);
                }
            } catch (Throwable ignored) {
            }
        }
        // EMI and JEI render their tooltips in the post-screen phase. Place the
        // item actions here as well so the tracker sees this frame's exact tooltip.
        renderTooltipAwareItemHints(screen, graphics, activeController, mouseX, mouseY);
    }

    public static void renderTooltipAwareItemHints(Screen screen, GuiGraphics graphics,
                                                   ControllerEntity controller, int mouseX, int mouseY) {
        boolean hoveredViewerItem = false;
        if (emiLoaded) {
            try {
                hoveredViewerItem = hasHoveredEmiStack(mouseX, mouseY);
            } catch (Throwable ignored) {
            }
        }
        if (jeiLoaded) {
            try {
                if (isScreen(screen, JEI_RECIPE_SCREEN)) {
                    hoveredViewerItem |= valuePresent(invoke(
                            screen, "getIngredientUnderMouse", (double) mouseX, (double) mouseY
                    ));
                } else if (hasHoveredJeiIngredient()) {
                    hoveredViewerItem = true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (hoveredViewerItem && !hasHoveredContainerSlot(screen)) {
            renderItemActionGlyphs(graphics, controller, mouseX, mouseY);
        }
    }

    private static boolean hasHoveredContainerSlot(Screen screen) {
        return screen instanceof AbstractContainerScreen<?>
                && ((AbstractContainerScreenAccessor) screen).getHoveredSlot() != null;
    }

    private static void renderEmiSidebarHints(GuiGraphics graphics, ControllerEntity controller,
                                               int mouseX, int mouseY) throws ReflectiveOperationException {
        Object panel = invokeStatic(EMI_SCREEN_MANAGER, "getHoveredPanel", mouseX, mouseY);
        if (panel == null || !booleanMethod(panel, "isVisible", false)
                || !booleanMethod(panel, "hasMultiplePages", false)) return;
        drawBeside(graphics, controller, ControlifyBindings.GUI_PREV_TAB, field(panel, "pageLeft"), true);
        drawBeside(graphics, controller, ControlifyBindings.GUI_NEXT_TAB, field(panel, "pageRight"), false);
    }

    private static void renderJeiOverlayHints(GuiGraphics graphics, ControllerEntity controller,
                                               int mouseX, int mouseY) throws ReflectiveOperationException {
        Object contents = hoveredJeiContents(mouseX, mouseY);
        if (contents == null) return;
        drawBeside(graphics, controller, ControlifyBindings.GUI_PREV_TAB,
                invoke(contents, "getBackButtonArea"), true);
        drawBeside(graphics, controller, ControlifyBindings.GUI_NEXT_TAB,
                invoke(contents, "getNextPageButtonArea"), false);
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

    private static Object hoveredJeiContents(int mouseX, int mouseY) throws ReflectiveOperationException {
        Object runtime = invokeStatic(JEI_INTERNAL, "getJeiRuntime");
        Object overlay = invoke(runtime, "getIngredientListOverlay");
        if (overlay == null || !booleanMethod(overlay, "isListDisplayed", false)) return null;
        Object contents = field(overlay, "contents");
        return Boolean.TRUE.equals(invoke(contents, "isMouseOver", (double) mouseX, (double) mouseY))
                ? contents
                : null;
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
        Component recipes = Component.translatable("controlify.compat.recipe_viewer.action.recipes");
        Component uses = Component.translatable("controlify.compat.recipe_viewer.action.uses");
        int maxWidth = 0;
        int visibleHints = 0;
        if (!leftClick.isUnbound()) {
            maxWidth = Math.max(maxWidth, GuideRenderer.labeledGlyphWidth(
                    Minecraft.getInstance().font, leftClick.inputGlyph(), recipes
            ));
            visibleHints++;
        }
        if (!rightClick.isUnbound()) {
            maxWidth = Math.max(maxWidth, GuideRenderer.labeledGlyphWidth(
                    Minecraft.getInstance().font, rightClick.inputGlyph(), uses
            ));
            visibleHints++;
        }
        if (visibleHints == 0) return;
        int rowHeight = Minecraft.getInstance().font.lineHeight + 5;
        int totalHeight = visibleHints * rowHeight - 2;
        var position = GuideRenderer.placeAboveOrBelowTooltip(
                graphics,
                new GuideRenderer.Bounds(mouseX - 8, mouseY - 8, mouseX + 9, mouseY + 9),
                maxWidth,
                totalHeight
        );
        int x = position.x();
        int y = position.y();
        if (!leftClick.isUnbound()) {
            GuideRenderer.drawLabeledGlyph(
                    graphics, Minecraft.getInstance().font, leftClick.inputGlyph(), recipes, x, y
            );
            y += rowHeight;
        }
        if (!rightClick.isUnbound()) {
            GuideRenderer.drawLabeledGlyph(
                    graphics, Minecraft.getInstance().font, rightClick.inputGlyph(), uses, x, y
            );
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
        GuideRenderer.drawGlyph(graphics, Minecraft.getInstance().font, glyph, x, y);
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
            if (methodCompatible(candidate, method, args)) {
                found = candidate;
                break;
            }
        }
        if (found == null) {
            for (Class<?> cursor = type; cursor != null && found == null; cursor = cursor.getSuperclass()) {
                for (Method candidate : cursor.getDeclaredMethods()) {
                    if (methodCompatible(candidate, method, args)) {
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

    private static boolean methodCompatible(Method candidate, String name, Object[] args) {
        if (!candidate.getName().equals(name) || candidate.getParameterCount() != args.length) return false;
        Class<?>[] parameters = candidate.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            if (args[i] == null) continue;
            Class<?> parameter = wrapPrimitive(parameters[i]);
            if (!parameter.isAssignableFrom(args[i].getClass())) return false;
        }
        return true;
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return type;
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
