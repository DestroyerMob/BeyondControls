package dev.isxander.controlify.gui.guide;

import com.google.common.collect.Lists;
import dev.isxander.controlify.utils.render.Blit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class GuideRenderer {
    private GuideRenderer() {}

    private static Bounds screenBounds(Minecraft minecraft) {
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        return new Bounds(0, 0, width, height);
    }

    public static void render(GuiGraphics graphics, GuideDomain<?> domain, Minecraft minecraft, boolean bottomAligned, boolean textContrast) {
        render(graphics, domain, minecraft, bottomAligned, textContrast, screenBounds(minecraft));
    }

    public static void render(GuiGraphics graphics, GuideDomain<?> domain, Minecraft minecraft,
                              boolean bottomAligned, boolean textContrast, Bounds bounds) {

        Blit.batchDraw(graphics, () -> {
            renderLines(graphics, domain.leftGuides(), minecraft.font, bounds, bottomAligned, false, textContrast);
            renderLines(graphics, domain.rightGuides(), minecraft.font, bounds, bottomAligned, true, textContrast);
        });
    }

    public static int drawGlyph(GuiGraphics graphics, Font font, Component glyph, int x, int y) {
        int width = font.width(glyph);
        renderAtTop(graphics, () -> graphics.drawString(font, glyph, x, y, 0xFFFFFFFF, true));
        return width + 5;
    }

    public static int labeledGlyphWidth(Font font, Component glyph, Component label) {
        return font.width(glyph) + 3 + font.width(label) + 7;
    }

    public static int drawLabeledGlyph(GuiGraphics graphics, Font font, Component glyph,
                                       Component label, int x, int y) {
        Component text = Component.empty().append(glyph).append(" ").append(label);
        int width = font.width(text);
        renderAtTop(graphics, () -> {
            graphics.fill(x - 3, y - 2, x + width + 3, y + font.lineHeight + 2, 0xD0000000);
            graphics.drawString(font, text, x, y, 0xFFFFFFFF, false);
        });
        return width + 7;
    }

    public static HintPosition placeContextHint(GuiGraphics graphics, Bounds target,
                                                int hintWidth, int hintHeight) {
        int gap = 7;
        int centeredY = target.top() + (target.height() - hintHeight) / 2;
        int centeredX = target.left() + (target.width() - hintWidth) / 2;
        List<HintPosition> candidates = List.of(
                new HintPosition(target.right() + gap, centeredY),
                new HintPosition(target.left() - hintWidth - gap, centeredY),
                new HintPosition(centeredX, target.bottom() + gap),
                new HintPosition(centeredX, target.top() - hintHeight - gap)
        );
        List<Bounds> tooltips = new ArrayList<>(
                TooltipTracker.boundsFor(Minecraft.getInstance().screen)
        );
        tooltips.add(likelyTooltipRegion(graphics, target));

        HintPosition best = null;
        int bestOverlap = Integer.MAX_VALUE;
        for (HintPosition candidate : candidates) {
            HintPosition clamped = candidate.clamp(graphics.guiWidth(), graphics.guiHeight(), hintWidth, hintHeight);
            Bounds placed = new Bounds(clamped.x(), clamped.y(), clamped.x() + hintWidth, clamped.y() + hintHeight);
            int overlap = placed.overlapArea(target) * 1000
                    + tooltips.stream().mapToInt(placed::overlapArea).sum();
            if (overlap == 0) return clamped;
            if (overlap < bestOverlap) {
                best = clamped;
                bestOverlap = overlap;
            }
        }
        return best == null ? new HintPosition(2, 2) : best;
    }

    public static HintPosition placeAboveOrBelowTooltip(GuiGraphics graphics, Bounds target,
                                                        int hintWidth, int hintHeight) {
        Bounds tooltip = TooltipTracker.boundsFor(Minecraft.getInstance().screen).stream()
                .min((first, second) -> Integer.compare(
                        first.distanceSquaredTo(target), second.distanceSquaredTo(target)
                ))
                .orElseGet(() -> {
                    int centerY = target.top() + target.height() / 2;
                    int estimatedHalfHeight = Math.min(64, Math.max(32, graphics.guiHeight() / 8));
                    return new Bounds(
                            target.left(), centerY - estimatedHalfHeight,
                            target.right(), centerY + estimatedHalfHeight
                    );
                });

        int gap = 2;
        int topSpace = tooltip.top() - gap;
        int bottomSpace = graphics.guiHeight() - tooltip.bottom() - gap;
        boolean placeBelow = bottomSpace >= hintHeight && (topSpace < hintHeight || bottomSpace >= topSpace);
        int y = placeBelow
                ? tooltip.bottom() + gap
                : tooltip.top() - hintHeight - gap;
        int x = tooltip.left() + (tooltip.width() - hintWidth) / 2;
        return new HintPosition(x, y).clamp(
                graphics.guiWidth(), graphics.guiHeight(), hintWidth, hintHeight
        );
    }

    private static Bounds likelyTooltipRegion(GuiGraphics graphics, Bounds target) {
        int cursorX = target.left() + target.width() / 2;
        int estimatedTooltipWidth = Math.min(320, Math.max(160, graphics.guiWidth() * 2 / 5));
        boolean tooltipFitsOnRight = cursorX + 12 + estimatedTooltipWidth <= graphics.guiWidth();
        return tooltipFitsOnRight
                ? new Bounds(cursorX + 4, 0, graphics.guiWidth(), graphics.guiHeight())
                : new Bounds(0, 0, cursorX - 4, graphics.guiHeight());
    }

    private static void renderAtTop(GuiGraphics graphics, Runnable render) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1000);
        try {
            render.run();
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void renderLines(GuiGraphics graphics, PrecomputedLines lines, Font font, Bounds bounds, boolean bottomAligned, boolean rightAligned, boolean textContrast) {
        int safeAreaX = 2;
        int safeAreaY = 5;
        int betweenLines = 2;

        int allLinesHeight = lines.height() + (lines.lines().size() - 1) * betweenLines;

        int x = rightAligned ? (bounds.right() - safeAreaX) : (bounds.left() + safeAreaX);
        int y = bottomAligned ? (bounds.bottom() - allLinesHeight - safeAreaY) : (bounds.top() + safeAreaY);

        var list = bottomAligned ? Lists.reverse(lines.lines()) : lines.lines();
        for (PrecomputedLines.PrecomputedLine line : list) {
            if (!bottomAligned && y + line.height() > bounds.bottom() - safeAreaY) break;
            int lineX = rightAligned ? (x - line.width()) : x;

            if (textContrast) {
                graphics.fill(
                        lineX + line.backgroundLeft() - 1, y - 1,
                        lineX + line.backgroundRight() + 1, y + font.lineHeight + 1, // use font.lineHeight for the height of the line since we're just contrasting the regular text
                        0x80000000
                );
            }

            graphics.drawString(font, line.text(), lineX, y, 0xFFFFFFFF, !textContrast);

            y += line.height() + betweenLines;
        }
    }

    public record Bounds(int left, int top, int right, int bottom) {
        public Bounds translate(int x, int y) {
            return new Bounds(left + x, top + y, right + x, bottom + y);
        }

        public int width() {
            return Math.max(0, right - left);
        }

        public int height() {
            return Math.max(0, bottom - top);
        }

        public int overlapArea(Bounds other) {
            int width = Math.max(0, Math.min(right, other.right) - Math.max(left, other.left));
            int height = Math.max(0, Math.min(bottom, other.bottom) - Math.max(top, other.top));
            return width * height;
        }

        public int distanceSquaredTo(Bounds other) {
            int x = Math.max(0, Math.max(left - other.right, other.left - right));
            int y = Math.max(0, Math.max(top - other.bottom, other.top - bottom));
            return x * x + y * y;
        }
    }

    public record HintPosition(int x, int y) {
        private HintPosition clamp(int screenWidth, int screenHeight, int width, int height) {
            return new HintPosition(
                    Math.max(2, Math.min(x, screenWidth - width - 2)),
                    Math.max(2, Math.min(y, screenHeight - height - 2))
            );
        }
    }

    public static class Renderable implements net.minecraft.client.gui.components.Renderable {
        private final GuideDomain<?> domain;
        private final Minecraft minecraft;
        private boolean bottomAligned;
        private boolean textContrast;
        private final Supplier<Bounds> boundsSupplier;

        public Renderable(GuideDomain<?> domain, Minecraft minecraft, boolean bottomAligned, boolean textContrast) {
            this(domain, minecraft, bottomAligned, textContrast, null);
        }

        public Renderable(GuideDomain<?> domain, Minecraft minecraft, boolean bottomAligned, boolean textContrast,
                          Supplier<Bounds> boundsSupplier) {
            this.domain = domain;
            this.minecraft = minecraft;
            this.bottomAligned = bottomAligned;
            this.textContrast = textContrast;
            this.boundsSupplier = boundsSupplier;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            if (boundsSupplier == null) {
                GuideRenderer.render(guiGraphics, domain, minecraft, bottomAligned, textContrast);
            } else {
                GuideRenderer.render(guiGraphics, domain, minecraft, bottomAligned, textContrast, boundsSupplier.get());
            }
        }

        public void setBottomAligned(boolean bottomAligned) {
            this.bottomAligned = bottomAligned;
        }

        public void setTextContrast(boolean textContrast) {
            this.textContrast = textContrast;
        }
    }
}
