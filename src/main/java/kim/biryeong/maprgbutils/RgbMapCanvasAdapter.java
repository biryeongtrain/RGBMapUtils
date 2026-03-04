package kim.biryeong.maprgbutils;

import eu.pb4.mapcanvas.api.core.CanvasImage;
import eu.pb4.mapcanvas.api.core.DrawableCanvas;
import eu.pb4.mapcanvas.api.utils.CanvasUtils;

import java.awt.image.BufferedImage;
import java.util.Locale;

public final class RgbMapCanvasAdapter {
    private RgbMapCanvasAdapter() {
    }

    public static CanvasImage mapIndexesToDrawableCanvas(int[] mapIndexes128x128) {
        RgbMapValidation.requireMapIndexArray(mapIndexes128x128, "mapIndexes128x128");

        CanvasImage canvas = new CanvasImage(RgbMapCodec.MAP_WIDTH, RgbMapCodec.MAP_HEIGHT);
        int i = 0;
        for (int y = 0; y < RgbMapCodec.MAP_HEIGHT; y++) {
            for (int x = 0; x < RgbMapCodec.MAP_WIDTH; x++) {
                int index = RgbMapValidation.requireMapIndexRange(mapIndexes128x128[i], x, y, "mapIndexes128x128");
                canvas.setRaw(x, y, (byte) index);
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
                int value = Byte.toUnsignedInt(canvas128x128.getRaw(x, y));
                out[i++] = RgbMapValidation.requireMapIndexRange(value, x, y, "canvas128x128");
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

    private static String toHex(int rgb) {
        return String.format(Locale.ROOT, "0x%06X", rgb & 0x00FFFFFF);
    }
}
