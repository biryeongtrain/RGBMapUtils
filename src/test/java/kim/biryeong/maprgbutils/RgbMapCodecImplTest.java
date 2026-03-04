package kim.biryeong.maprgbutils;

import eu.pb4.mapcanvas.api.core.CanvasImage;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RgbMapCodecImplTest {
    private final RgbMapCodec codec = RgbMapCodec.createDefault();

    @Test
    void encodeDecodeBitLayoutMatchesSpec() {
        int[] input = new int[RgbMapCodec.RGB_PIXEL_COUNT];
        input[0] = 0x80FF01;

        int[] mapIndexes = codec.encodeRgbToMapIndexes(input);

        assertEquals(1, mapIndexes[0]);
        assertEquals(127, mapIndexes[1]);
        assertEquals(0, mapIndexes[RgbMapCodec.MAP_WIDTH]);
        assertEquals(6, mapIndexes[RgbMapCodec.MAP_WIDTH + 1]);

        int[] decoded = codec.decodeMapIndexesToRgb(mapIndexes);
        assertEquals(input[0], decoded[0]);
    }

    @Test
    void roundTripSequentialData() {
        int[] input = new int[RgbMapCodec.RGB_PIXEL_COUNT];
        for (int i = 0; i < input.length; i++) {
            input[i] = i;
        }

        int[] mapIndexes = codec.encodeRgbToMapIndexes(input);
        int[] decoded = codec.decodeMapIndexesToRgb(mapIndexes);

        assertArrayEquals(input, decoded);
    }

    @Test
    void roundTripRandomData() {
        Random random = new Random(0x5EEDBEEFL);

        for (int round = 0; round < 30; round++) {
            int[] input = new int[RgbMapCodec.RGB_PIXEL_COUNT];
            for (int i = 0; i < input.length; i++) {
                input[i] = random.nextInt(0x1000000);
            }

            int[] mapIndexes = codec.encodeRgbToMapIndexes(input);
            int[] decoded = codec.decodeMapIndexesToRgb(mapIndexes);
            assertArrayEquals(input, decoded);
        }
    }

    @Test
    void imageHelperRoundTrip() {
        BufferedImage image = new BufferedImage(RgbMapCodec.RGB_WIDTH, RgbMapCodec.RGB_HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < RgbMapCodec.RGB_HEIGHT; y++) {
            for (int x = 0; x < RgbMapCodec.RGB_WIDTH; x++) {
                int r = (x * 4) & 0xFF;
                int g = (y * 4) & 0xFF;
                int b = ((x + y) * 2) & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        int[] mapIndexes = codec.encodeImageToMapIndexes(image);
        BufferedImage decoded = codec.decodeMapIndexesToImage(mapIndexes);

        for (int y = 0; y < RgbMapCodec.RGB_HEIGHT; y++) {
            for (int x = 0; x < RgbMapCodec.RGB_WIDTH; x++) {
                assertEquals(image.getRGB(x, y) & 0x00FFFFFF, decoded.getRGB(x, y) & 0x00FFFFFF);
            }
        }
    }

    @Test
    void mapCanvasAndPaletteConversionsRoundTrip() {
        int[] mapIndexes = new int[RgbMapCodec.MAP_INDEX_COUNT];
        for (int i = 0; i < mapIndexes.length; i++) {
            mapIndexes[i] = i % 128;
        }

        CanvasImage canvas = RgbMapCanvasAdapter.mapIndexesToDrawableCanvas(mapIndexes);
        int[] fromCanvas = RgbMapCanvasAdapter.drawableCanvasToMapIndexes(canvas);
        assertArrayEquals(mapIndexes, fromCanvas);

        RgbMapPalette palette = RgbMapPalette.rgbMaps();
        BufferedImage paletteImage = RgbMapCanvasAdapter.mapIndexesToPaletteImage(mapIndexes, palette);
        int[] fromPaletteImage = RgbMapCanvasAdapter.paletteImageToMapIndexes(paletteImage, palette);
        assertArrayEquals(mapIndexes, fromPaletteImage);
    }

    @Test
    void validationMessagesAreClear() {
        IllegalArgumentException rgbSize = assertThrows(
                IllegalArgumentException.class,
                () -> codec.encodeRgbToMapIndexes(new int[3])
        );
        assertTrue(rgbSize.getMessage().contains("4096"));

        IllegalArgumentException mapSize = assertThrows(
                IllegalArgumentException.class,
                () -> codec.decodeMapIndexesToRgb(new int[10])
        );
        assertTrue(mapSize.getMessage().contains("16384"));

        int[] invalidIndex = new int[RgbMapCodec.MAP_INDEX_COUNT];
        invalidIndex[42] = 128;
        IllegalArgumentException range = assertThrows(
                IllegalArgumentException.class,
                () -> codec.decodeMapIndexesToRgb(invalidIndex)
        );
        assertTrue(range.getMessage().contains("0..127"));
    }
}
