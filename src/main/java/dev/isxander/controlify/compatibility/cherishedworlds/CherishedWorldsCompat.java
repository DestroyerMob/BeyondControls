package dev.isxander.controlify.compatibility.cherishedworlds;

import dev.isxander.controlify.mixins.feature.screenop.impl.outofgame.SelectWorldScreenAccessor;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.world.level.storage.LevelSummary;

import java.lang.reflect.Method;
import java.util.Optional;

/** Optional controller actions for Cherished Worlds without a hard dependency. */
public final class CherishedWorldsCompat {
    private static final String ENTRY_ACCESSOR =
            "com.illusivesoulworks.cherishedworlds.mixin.core.AccessorWorldSelectionListEntry";
    private static final String FAVORITES_LIST =
            "com.illusivesoulworks.cherishedworlds.client.favorites.FavoritesList";

    private CherishedWorldsCompat() {
    }

    public static boolean isAvailable() {
        try {
            Class.forName(FAVORITES_LIST);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean toggleSelectedWorld(SelectWorldScreen screen) {
        try {
            SelectWorldScreenAccessor screenAccessor = (SelectWorldScreenAccessor) screen;
            var list = screenAccessor.getList();
            var selected = list == null ? null : list.getSelected();
            if (selected == null) return false;

            Class<?> entryAccessor = Class.forName(ENTRY_ACCESSOR);
            if (!entryAccessor.isInstance(selected)) return false;

            Method getWorldSummary = entryAccessor.getMethod("getWorldSummary");
            LevelSummary summary = (LevelSummary) getWorldSummary.invoke(selected);
            if (summary == null) return false;

            Class<?> favorites = Class.forName(FAVORITES_LIST);
            String levelId = summary.getLevelId();
            boolean wasFavorite = (boolean) favorites.getMethod("contains", String.class).invoke(null, levelId);
            favorites.getMethod(wasFavorite ? "remove" : "add", String.class).invoke(null, levelId);
            favorites.getMethod("save").invoke(null);

            var searchBox = screenAccessor.getSearchBox();
            list.updateFilter(searchBox == null ? "" : searchBox.getValue());
            var deleteButton = screenAccessor.getDeleteButton();
            if (deleteButton != null) deleteButton.active = wasFavorite;
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    public static Optional<FavoriteControlPosition> selectedWorldControlPosition(SelectWorldScreen screen) {
        try {
            SelectWorldScreenAccessor screenAccessor = (SelectWorldScreenAccessor) screen;
            var list = screenAccessor.getList();
            var selected = list == null ? null : list.getSelected();
            if (selected == null || !Class.forName(ENTRY_ACCESSOR).isInstance(selected)) return Optional.empty();

            int index = list.children().indexOf(selected);
            if (index < 0) return Optional.empty();
            int rowY = list.getY() + 15 + 36 * index - (int) list.getScrollAmount();
            if (rowY + 9 < list.getY() || rowY > list.getBottom()) return Optional.empty();

            return Optional.of(new FavoriteControlPosition(screen.width / 2 - 148, rowY));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    public record FavoriteControlPosition(int starX, int y) {
    }
}
