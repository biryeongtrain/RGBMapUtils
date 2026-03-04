package kim.biryeong.maprgbutils;

import eu.pb4.mapcanvas.api.core.CanvasColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class PaletteCompatibilityDebugTest {
    @Test
    void rgbMapPaletteMatchesMinecraftRenderColors4to131() {
        RgbMapPalette palette = RgbMapPalette.rgbMaps();

        int mismatchCount = 0;
        StringBuilder mismatch = new StringBuilder();
        for (int i = 0; i < 128; i++) {
            int expected = palette.rgbAt(i);
            int raw = i + 4;
            int actual = CanvasColor.getFromRaw((byte) raw).getRgbColor() & 0x00FFFFFF;
            if (expected != actual) {
                mismatchCount++;
                mismatch.append("index=")
                        .append(i)
                        .append(" raw=")
                        .append(raw)
                        .append(" expected=0x")
                        .append(String.format("%06X", expected))
                        .append(" actual=0x")
                        .append(String.format("%06X", actual))
                        .append('\n');
            }
        }

        if (mismatchCount != 0) {
            fail("Palette mismatch count=" + mismatchCount + "\n" + mismatch);
        }
        assertEquals(0, mismatchCount);
    }
}
