package kim.biryeong.maprgbutils;

import eu.pb4.mapcanvas.api.core.DrawableCanvas;

import java.awt.image.BufferedImage;

final class RgbMapValidation {
    private RgbMapValidation() {
    }

    static int[] requireRgbArray(int[] rgb64x64, String argumentName) {
        if (rgb64x64 == null) {
            throw new IllegalArgumentException(argumentName + " must not be null");
        }
        if (rgb64x64.length != RgbMapCodec.RGB_PIXEL_COUNT) {
            throw new IllegalArgumentException(argumentName + " must contain exactly 4096 values (64x64), but was " + rgb64x64.length);
        }
        return rgb64x64;
    }

    static int[] requireMapIndexArray(int[] mapIndexes128x128, String argumentName) {
        if (mapIndexes128x128 == null) {
            throw new IllegalArgumentException(argumentName + " must not be null");
        }
        if (mapIndexes128x128.length != RgbMapCodec.MAP_INDEX_COUNT) {
            throw new IllegalArgumentException(argumentName + " must contain exactly 16384 values (128x128), but was " + mapIndexes128x128.length);
        }
        return mapIndexes128x128;
    }

    static byte[] requireMapIndexByteArray(byte[] mapIndexes128x128, String argumentName) {
        if (mapIndexes128x128 == null) {
            throw new IllegalArgumentException(argumentName + " must not be null");
        }
        if (mapIndexes128x128.length != RgbMapCodec.MAP_INDEX_COUNT) {
            throw new IllegalArgumentException(argumentName + " must contain exactly 16384 values (128x128), but was " + mapIndexes128x128.length);
        }
        return mapIndexes128x128;
    }

    static BufferedImage requireImageSize(BufferedImage image, int expectedWidth, int expectedHeight, String argumentName) {
        if (image == null) {
            throw new IllegalArgumentException(argumentName + " must not be null");
        }
        if (image.getWidth() != expectedWidth || image.getHeight() != expectedHeight) {
            throw new IllegalArgumentException(argumentName + " must be " + expectedWidth + "x" + expectedHeight + ", but was " + image.getWidth() + "x" + image.getHeight());
        }
        return image;
    }

    static DrawableCanvas requireCanvasSize(DrawableCanvas canvas, int expectedWidth, int expectedHeight, String argumentName) {
        if (canvas == null) {
            throw new IllegalArgumentException(argumentName + " must not be null");
        }
        if (canvas.getWidth() != expectedWidth || canvas.getHeight() != expectedHeight) {
            throw new IllegalArgumentException(argumentName + " must be " + expectedWidth + "x" + expectedHeight + ", but was " + canvas.getWidth() + "x" + canvas.getHeight());
        }
        return canvas;
    }

    static int requireMapIndexRange(int value, int x, int y, String sourceName) {
        if (value < 0 || value > 127) {
            throw new IllegalArgumentException(sourceName + " contains out-of-range value " + value + " at (x=" + x + ", y=" + y + "); expected 0..127");
        }
        return value;
    }
}
