package kim.biryeong.maprgbutils;

import java.awt.image.BufferedImage;

public interface RgbMapCodec {
    int RGB_WIDTH = 64;
    int RGB_HEIGHT = 64;
    int RGB_PIXEL_COUNT = RGB_WIDTH * RGB_HEIGHT;

    int MAP_WIDTH = 128;
    int MAP_HEIGHT = 128;
    int MAP_INDEX_COUNT = MAP_WIDTH * MAP_HEIGHT;

    static RgbMapCodec createDefault() {
        return RgbMapCodecImpl.INSTANCE;
    }

    int[] encodeRgbToMapIndexes(int[] rgb64x64);

    int[] decodeMapIndexesToRgb(int[] mapIndexes128x128);

    default byte[] encodeRgbToMapIndexBytes(int[] rgb64x64) {
        int[] mapIndexes = encodeRgbToMapIndexes(rgb64x64);
        byte[] out = new byte[MAP_INDEX_COUNT];
        for (int i = 0; i < MAP_INDEX_COUNT; i++) {
            out[i] = (byte) mapIndexes[i];
        }
        return out;
    }

    default int[] decodeMapIndexBytesToRgb(byte[] mapIndexes128x128) {
        RgbMapValidation.requireMapIndexByteArray(mapIndexes128x128, "mapIndexes128x128");
        int[] mapIndexes = new int[MAP_INDEX_COUNT];
        for (int i = 0; i < MAP_INDEX_COUNT; i++) {
            mapIndexes[i] = Byte.toUnsignedInt(mapIndexes128x128[i]);
        }
        return decodeMapIndexesToRgb(mapIndexes);
    }

    default int[] encodeImageToMapIndexes(BufferedImage image64x64) {
        RgbMapValidation.requireImageSize(image64x64, RGB_WIDTH, RGB_HEIGHT, "image64x64");
        int[] rgb64x64 = new int[RGB_PIXEL_COUNT];

        int i = 0;
        for (int y = 0; y < RGB_HEIGHT; y++) {
            for (int x = 0; x < RGB_WIDTH; x++) {
                rgb64x64[i++] = image64x64.getRGB(x, y) & 0x00FFFFFF;
            }
        }

        return encodeRgbToMapIndexes(rgb64x64);
    }

    default byte[] encodeImageToMapIndexBytes(BufferedImage image64x64) {
        int[] mapIndexes = encodeImageToMapIndexes(image64x64);
        byte[] out = new byte[MAP_INDEX_COUNT];
        for (int i = 0; i < MAP_INDEX_COUNT; i++) {
            out[i] = (byte) mapIndexes[i];
        }
        return out;
    }

    default BufferedImage decodeMapIndexesToImage(int[] mapIndexes128x128) {
        int[] rgb64x64 = decodeMapIndexesToRgb(mapIndexes128x128);
        BufferedImage image = new BufferedImage(RGB_WIDTH, RGB_HEIGHT, BufferedImage.TYPE_INT_RGB);

        int i = 0;
        for (int y = 0; y < RGB_HEIGHT; y++) {
            for (int x = 0; x < RGB_WIDTH; x++) {
                image.setRGB(x, y, rgb64x64[i++]);
            }
        }

        return image;
    }
}
