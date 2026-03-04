package kim.biryeong.maprgbutils.gametest;

import eu.pb4.mapcanvas.api.core.CanvasImage;
import kim.biryeong.maprgbutils.RgbMapCanvasAdapter;
import kim.biryeong.maprgbutils.RgbMapCodec;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

import java.util.Random;

public class RgbMapCodecGameTest {
    private static final RgbMapCodec CODEC = RgbMapCodec.createDefault();

    @GameTest
    public void rgbRoundTrip(GameTestHelper helper) {
        int[] input = new int[RgbMapCodec.RGB_PIXEL_COUNT];
        Random random = new Random(0x5EEDBEEFL);

        for (int i = 0; i < input.length; i++) {
            input[i] = random.nextInt(0x1000000);
        }

        int[] encoded = CODEC.encodeRgbToMapIndexes(input);
        int[] decoded = CODEC.decodeMapIndexesToRgb(encoded);

        for (int i = 0; i < input.length; i++) {
            if (input[i] != decoded[i]) {
                helper.fail(Component.literal("Round-trip mismatch at pixel " + i + ": in=" + toHex(input[i]) + ", out=" + toHex(decoded[i])));
                return;
            }
        }

        helper.succeed();
    }

    @GameTest
    public void canvasIndexRoundTrip(GameTestHelper helper) {
        int[] indexes = new int[RgbMapCodec.MAP_INDEX_COUNT];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i] = i % 128;
        }

        CanvasImage canvas = RgbMapCanvasAdapter.mapIndexesToDrawableCanvas(indexes);
        int[] decoded = RgbMapCanvasAdapter.drawableCanvasToMapIndexes(canvas);

        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] != decoded[i]) {
                helper.fail(Component.literal("Canvas round-trip mismatch at index " + i + ": in=" + indexes[i] + ", out=" + decoded[i]));
                return;
            }
        }

        helper.succeed();
    }

    private static String toHex(int rgb) {
        return String.format("0x%06X", rgb & 0x00FFFFFF);
    }
}
