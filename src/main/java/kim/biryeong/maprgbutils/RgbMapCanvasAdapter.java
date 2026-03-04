package kim.biryeong.maprgbutils;

import eu.pb4.mapcanvas.api.core.CanvasImage;
import eu.pb4.mapcanvas.api.core.CombinedPlayerCanvas;
import eu.pb4.mapcanvas.api.core.DrawableCanvas;
import eu.pb4.mapcanvas.api.utils.CanvasUtils;

import java.awt.image.BufferedImage;
import java.util.Locale;

public final class RgbMapCanvasAdapter {
    private static final int MAP_COLOR_OFFSET = 4;

    private RgbMapCanvasAdapter() {
    }

    public static CanvasImage encodeImageToRgbMapCanvas(BufferedImage image64x64) {
        return encodeImageToRgbMapCanvas(image64x64, RgbMapCodec.createDefault());
    }

    public static CanvasImage encodeImageToRgbMapCanvas(BufferedImage image64x64, RgbMapCodec codec) {
        if (codec == null) {
            throw new IllegalArgumentException("codec must not be null");
        }
        RgbMapValidation.requireImageSize(image64x64, RgbMapCodec.RGB_WIDTH, RgbMapCodec.RGB_HEIGHT, "image64x64");
        int[] mapIndexes = codec.encodeImageToMapIndexes(image64x64);
        return mapIndexesToDrawableCanvas(mapIndexes);
    }

    public static CanvasImage encodeCanvasToRgbMapCanvas(DrawableCanvas canvas64x64) {
        return encodeCanvasToRgbMapCanvas(canvas64x64, RgbMapCodec.createDefault());
    }

    public static CanvasImage encodeCanvasToRgbMapCanvas(DrawableCanvas canvas64x64, RgbMapCodec codec) {
        if (codec == null) {
            throw new IllegalArgumentException("codec must not be null");
        }
        RgbMapValidation.requireCanvasSize(canvas64x64, RgbMapCodec.RGB_WIDTH, RgbMapCodec.RGB_HEIGHT, "canvas64x64");
        return encodeImageToRgbMapCanvas(drawableCanvasToBufferedImage(canvas64x64), codec);
    }

    public static CombinedPlayerCanvas encodeImageToRgbMapCombinedCanvas(BufferedImage sourceImage) {
        return encodeImageToRgbMapCombinedCanvas(sourceImage, RgbMapCodec.createDefault());
    }

    public static CombinedPlayerCanvas encodeImageToRgbMapCombinedCanvas(BufferedImage sourceImage, RgbMapCodec codec) {
        if (codec == null) {
            throw new IllegalArgumentException("codec must not be null");
        }
        if (sourceImage == null) {
            throw new IllegalArgumentException("sourceImage must not be null");
        }

        int sectionsWidth = sectionsFor(sourceImage.getWidth(), RgbMapCodec.MAP_WIDTH);
        int sectionsHeight = sectionsFor(sourceImage.getHeight(), RgbMapCodec.MAP_HEIGHT);
        CombinedPlayerCanvas combinedCanvas = DrawableCanvas.create(sectionsWidth, sectionsHeight);

        for (int sectionY = 0; sectionY < sectionsHeight; sectionY++) {
            for (int sectionX = 0; sectionX < sectionsWidth; sectionX++) {
                BufferedImage tile64x64 = sampleRegionTo64x64(
                        sourceImage,
                        sectionX * RgbMapCodec.MAP_WIDTH,
                        sectionY * RgbMapCodec.MAP_HEIGHT
                );
                CanvasImage encodedTile = encodeImageToRgbMapCanvas(tile64x64, codec);
                CanvasUtils.draw(
                        combinedCanvas,
                        sectionX * RgbMapCodec.MAP_WIDTH,
                        sectionY * RgbMapCodec.MAP_HEIGHT,
                        encodedTile
                );
            }
        }

        return combinedCanvas;
    }

    public static CombinedPlayerCanvas encodeCanvasToRgbMapCombinedCanvas(DrawableCanvas sourceCanvas) {
        return encodeCanvasToRgbMapCombinedCanvas(sourceCanvas, RgbMapCodec.createDefault());
    }

    public static CombinedPlayerCanvas encodeCanvasToRgbMapCombinedCanvas(DrawableCanvas sourceCanvas, RgbMapCodec codec) {
        if (sourceCanvas == null) {
            throw new IllegalArgumentException("sourceCanvas must not be null");
        }
        return encodeImageToRgbMapCombinedCanvas(drawableCanvasToBufferedImage(sourceCanvas), codec);
    }

    public static CanvasImage mapIndexesToDrawableCanvas(int[] mapIndexes128x128) {
        RgbMapValidation.requireMapIndexArray(mapIndexes128x128, "mapIndexes128x128");

        CanvasImage canvas = new CanvasImage(RgbMapCodec.MAP_WIDTH, RgbMapCodec.MAP_HEIGHT);
        int i = 0;
        for (int y = 0; y < RgbMapCodec.MAP_HEIGHT; y++) {
            for (int x = 0; x < RgbMapCodec.MAP_WIDTH; x++) {
                int index = RgbMapValidation.requireMapIndexRange(mapIndexes128x128[i], x, y, "mapIndexes128x128");
                canvas.setRaw(x, y, (byte) (index + MAP_COLOR_OFFSET));
                i++;
            }
        }
        return canvas;
    }

    public static int[] drawableCanvasToMapIndexes(DrawableCanvas canvas128x128) {
        RgbMapValidation.requireCanvasSize(canvas128x128, RgbMapCodec.MAP_WIDTH, RgbMapCodec.MAP_HEIGHT, "canvas128x128");

        int[] out = new int[RgbMapCodec.MAP_INDEX_COUNT];
        int i = 0;
        for (int y = 0; y < RgbMapCodec.MAP_HEIGHT; y++) {
            for (int x = 0; x < RgbMapCodec.MAP_WIDTH; x++) {
                int rawValue = Byte.toUnsignedInt(canvas128x128.getRaw(x, y));
                int mapIndex = rawValue - MAP_COLOR_OFFSET;
                out[i++] = RgbMapValidation.requireMapIndexRange(mapIndex, x, y, "canvas128x128");
            }
        }

        return out;
    }

    public static BufferedImage mapIndexesToPaletteImage(int[] mapIndexes128x128, RgbMapPalette palette) {
        if (palette == null) {
            throw new IllegalArgumentException("palette must not be null");
        }
        RgbMapValidation.requireMapIndexArray(mapIndexes128x128, "mapIndexes128x128");

        BufferedImage image = new BufferedImage(RgbMapCodec.MAP_WIDTH, RgbMapCodec.MAP_HEIGHT, BufferedImage.TYPE_INT_RGB);
        int i = 0;
        for (int y = 0; y < RgbMapCodec.MAP_HEIGHT; y++) {
            for (int x = 0; x < RgbMapCodec.MAP_WIDTH; x++) {
                int index = RgbMapValidation.requireMapIndexRange(mapIndexes128x128[i++], x, y, "mapIndexes128x128");
                image.setRGB(x, y, palette.rgbAt(index));
            }
        }
        return image;
    }

    public static int[] paletteImageToMapIndexes(BufferedImage image128x128, RgbMapPalette palette) {
        if (palette == null) {
            throw new IllegalArgumentException("palette must not be null");
        }
        RgbMapValidation.requireImageSize(image128x128, RgbMapCodec.MAP_WIDTH, RgbMapCodec.MAP_HEIGHT, "image128x128");

        int[] mapIndexes = new int[RgbMapCodec.MAP_INDEX_COUNT];
        int i = 0;
        for (int y = 0; y < RgbMapCodec.MAP_HEIGHT; y++) {
            for (int x = 0; x < RgbMapCodec.MAP_WIDTH; x++) {
                int rgb = image128x128.getRGB(x, y) & 0x00FFFFFF;
                int index = palette.findIndexExact(rgb);
                if (index < 0) {
                    throw new IllegalArgumentException(
                            "image128x128 contains color " + toHex(rgb) + " at (x=" + x + ", y=" + y + ") that is not in the 128-color rgb_maps lookup"
                    );
                }
                mapIndexes[i++] = index;
            }
        }

        return mapIndexes;
    }

    public static CanvasImage bufferedImageToDrawableCanvas(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("image must not be null");
        }
        return CanvasImage.from(image);
    }

    public static BufferedImage drawableCanvasToBufferedImage(DrawableCanvas canvas) {
        if (canvas == null) {
            throw new IllegalArgumentException("canvas must not be null");
        }
        return CanvasUtils.toImage(canvas);
    }

    private static int sectionsFor(int size, int sectionSize) {
        return Math.max(1, (size + sectionSize - 1) / sectionSize);
    }

    private static BufferedImage sampleRegionTo64x64(BufferedImage sourceImage, int sourceStartX, int sourceStartY) {
        BufferedImage sampled = new BufferedImage(RgbMapCodec.RGB_WIDTH, RgbMapCodec.RGB_HEIGHT, BufferedImage.TYPE_INT_RGB);

        int sourceRegionWidth = Math.max(0, Math.min(RgbMapCodec.MAP_WIDTH, sourceImage.getWidth() - sourceStartX));
        int sourceRegionHeight = Math.max(0, Math.min(RgbMapCodec.MAP_HEIGHT, sourceImage.getHeight() - sourceStartY));

        if (sourceRegionWidth == 0 || sourceRegionHeight == 0) {
            return sampled;
        }

        for (int y = 0; y < RgbMapCodec.RGB_HEIGHT; y++) {
            int sourceOffsetY = Math.min(sourceRegionHeight - 1, (y * sourceRegionHeight) / RgbMapCodec.RGB_HEIGHT);
            int sourceY = sourceStartY + sourceOffsetY;

            for (int x = 0; x < RgbMapCodec.RGB_WIDTH; x++) {
                int sourceOffsetX = Math.min(sourceRegionWidth - 1, (x * sourceRegionWidth) / RgbMapCodec.RGB_WIDTH);
                int sourceX = sourceStartX + sourceOffsetX;
                sampled.setRGB(x, y, sourceImage.getRGB(sourceX, sourceY));
            }
        }

        return sampled;
    }

    private static String toHex(int rgb) {
        return String.format(Locale.ROOT, "0x%06X", rgb & 0x00FFFFFF);
    }
}
