package kim.biryeong.maprgbutils.api;

import eu.pb4.mapcanvas.api.core.CanvasImage;
import eu.pb4.mapcanvas.api.core.CombinedPlayerCanvas;
import eu.pb4.mapcanvas.api.core.DrawableCanvas;
import eu.pb4.mapcanvas.api.utils.CanvasUtils;

import java.awt.image.BufferedImage;

/**
 * High-level adapter for encoding arbitrary-size images or canvases into
 * RGB-map encoded {@link CombinedPlayerCanvas}.
 * <p>
 * The source is split into {@code 128x128} map regions, each region is sampled to
 * {@code 64x64}, then encoded through {@link RgbMapCodec} into a map-compatible tile.
 * For edge regions smaller than {@code 128x128}, missing pixels are padded with black
 * instead of stretching the remaining source area.
 */
public final class RgbMapCombinedCanvasAdapter {
    private RgbMapCombinedCanvasAdapter() {
    }

    /**
     * Encodes an image into a multi-section RGB-map combined canvas using the default codec.
     *
     * @param sourceImage source image, any non-null size
     * @return encoded combined canvas sized to cover the full source image
     */
    public static CombinedPlayerCanvas encodeImageToRgbMapCombinedCanvas(BufferedImage sourceImage) {
        return encodeImageToRgbMapCombinedCanvas(sourceImage, RgbMapCodec.createDefault());
    }

    /**
     * Encodes an image into a multi-section RGB-map combined canvas.
     *
     * @param sourceImage source image, any non-null size
     * @param codec codec implementation to use
     * @return encoded combined canvas sized to cover the full source image
     */
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
                CanvasImage encodedTile = RgbMapCanvasAdapter.encodeImageToRgbMapCanvas(tile64x64, codec);
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

    /**
     * Encodes a canvas into a multi-section RGB-map combined canvas using the default codec.
     *
     * @param sourceCanvas source canvas, any non-null size
     * @return encoded combined canvas sized to cover the full source canvas
     */
    public static CombinedPlayerCanvas encodeCanvasToRgbMapCombinedCanvas(DrawableCanvas sourceCanvas) {
        return encodeCanvasToRgbMapCombinedCanvas(sourceCanvas, RgbMapCodec.createDefault());
    }

    /**
     * Encodes a canvas into a multi-section RGB-map combined canvas.
     *
     * @param sourceCanvas source canvas, any non-null size
     * @param codec codec implementation to use
     * @return encoded combined canvas sized to cover the full source canvas
     */
    public static CombinedPlayerCanvas encodeCanvasToRgbMapCombinedCanvas(DrawableCanvas sourceCanvas, RgbMapCodec codec) {
        if (sourceCanvas == null) {
            throw new IllegalArgumentException("sourceCanvas must not be null");
        }
        return encodeImageToRgbMapCombinedCanvas(RgbMapCanvasAdapter.drawableCanvasToBufferedImage(sourceCanvas), codec);
    }

    private static int sectionsFor(int size, int sectionSize) {
        return Math.max(1, (size + sectionSize - 1) / sectionSize);
    }

    private static BufferedImage sampleRegionTo64x64(BufferedImage sourceImage, int sourceStartX, int sourceStartY) {
        BufferedImage sampled = new BufferedImage(RgbMapCodec.RGB_WIDTH, RgbMapCodec.RGB_HEIGHT, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < RgbMapCodec.RGB_HEIGHT; y++) {
            int sourceY = sourceStartY + ((y * RgbMapCodec.MAP_HEIGHT) / RgbMapCodec.RGB_HEIGHT);
            if (sourceY < 0 || sourceY >= sourceImage.getHeight()) {
                continue;
            }

            for (int x = 0; x < RgbMapCodec.RGB_WIDTH; x++) {
                int sourceX = sourceStartX + ((x * RgbMapCodec.MAP_WIDTH) / RgbMapCodec.RGB_WIDTH);
                if (sourceX < 0 || sourceX >= sourceImage.getWidth()) {
                    continue;
                }
                sampled.setRGB(x, y, sourceImage.getRGB(sourceX, sourceY));
            }
        }

        return sampled;
    }
}
