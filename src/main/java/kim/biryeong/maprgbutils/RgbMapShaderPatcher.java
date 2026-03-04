package kim.biryeong.maprgbutils;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RgbMapShaderPatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(RgbMapShaderPatcher.class);

    private static final String TARGET_SHADER_PATH = "assets/minecraft/shaders/core/rendertype_text.fsh";

    private static final String MARKER_BEGIN = "// rgbmaplibs:rgb_maps begin";
    private static final Pattern MAIN_SIGNATURE_PATTERN = Pattern.compile("\\bvoid\\s+main\\s*\\(");
    private static final Pattern SAMPLE_ASSIGNMENT_PATTERN = Pattern.compile(
            "(vec4\\s+[A-Za-z_][A-Za-z0-9_]*\\s*=\\s*)texture\\s*\\(\\s*Sampler0\\s*,\\s*texCoord0\\s*\\)\\s*;"
    );
    private static final Pattern SAMPLE_CALL_PATTERN = Pattern.compile(
            "texture\\s*\\(\\s*Sampler0\\s*,\\s*texCoord0\\s*\\)"
    );

    private static final String RGBMAP_SAMPLE_CALL = "rgbmap_decode_sample(Sampler0, texCoord0)";

    private RgbMapShaderPatcher() {
    }

    public static void register() {
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder ->
                builder.addPreFinishTask(RgbMapShaderPatcher::patchShader)
        );
    }

    private static void patchShader(ResourcePackBuilder builder) {
        String original = builder.getStringDataOrSource(TARGET_SHADER_PATH);
        if (original == null || original.isBlank()) {
            String bundled = loadBundledShader();
            if (bundled == null || bundled.isBlank()) {
                LOGGER.warn("Could not read shader source and no bundled fallback found: {}", TARGET_SHADER_PATH);
                return;
            }

            builder.addStringData(TARGET_SHADER_PATH, bundled);
            LOGGER.warn("Shader source was unavailable, wrote bundled fallback shader to {}", TARGET_SHADER_PATH);
            return;
        }

        PatchResult patch = applyPatch(original);
        if (!patch.patched()) {
            if (patch.alreadyPatched()) {
                LOGGER.debug("rgb_maps shader patch already present in {}", TARGET_SHADER_PATH);
            } else {
                LOGGER.warn("Unable to patch shader '{}': {}", TARGET_SHADER_PATH, patch.reason());
            }
            return;
        }

        builder.addStringData(TARGET_SHADER_PATH, patch.shader());
        LOGGER.info("Applied rgb_maps shader patch to {}", TARGET_SHADER_PATH);
    }

    private static PatchResult applyPatch(String source) {
        if (source.contains(MARKER_BEGIN) || source.contains(RGBMAP_SAMPLE_CALL)) {
            return PatchResult.alreadyPatched(source);
        }

        MainBlock mainBlock = findMainBlock(source);
        if (mainBlock == null) {
            return PatchResult.skipped(source, "Could not locate main() block");
        }

        String patchedMain = replaceMainSample(source.substring(mainBlock.bodyStart(), mainBlock.bodyEnd()));
        if (patchedMain == null) {
            return PatchResult.skipped(source, "Could not locate texture(Sampler0, texCoord0) in main()");
        }

        String withPatchedMain = source.substring(0, mainBlock.bodyStart())
                + patchedMain
                + source.substring(mainBlock.bodyEnd());
        String helper = buildHelperBlock();
        String shader = withPatchedMain.substring(0, mainBlock.signatureStart())
                + helper
                + "\n"
                + withPatchedMain.substring(mainBlock.signatureStart());
        return PatchResult.patched(shader);
    }

    private static MainBlock findMainBlock(String source) {
        Matcher mainMatcher = MAIN_SIGNATURE_PATTERN.matcher(source);
        if (!mainMatcher.find()) {
            return null;
        }

        int signatureStart = mainMatcher.start();
        int openBrace = source.indexOf('{', mainMatcher.end());
        if (openBrace < 0) {
            return null;
        }

        int braceDepth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
                if (braceDepth == 0) {
                    return new MainBlock(signatureStart, openBrace + 1, i);
                }
            }
        }

        return null;
    }

    private static String replaceMainSample(String mainBody) {
        Matcher assignmentMatcher = SAMPLE_ASSIGNMENT_PATTERN.matcher(mainBody);
        if (assignmentMatcher.find()) {
            StringBuffer out = new StringBuffer();
            String replacement = assignmentMatcher.group(1) + RGBMAP_SAMPLE_CALL + ";";
            assignmentMatcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
            assignmentMatcher.appendTail(out);
            return out.toString();
        }

        Matcher sampleMatcher = SAMPLE_CALL_PATTERN.matcher(mainBody);
        if (sampleMatcher.find()) {
            return sampleMatcher.replaceFirst(RGBMAP_SAMPLE_CALL);
        }

        return null;
    }

    private static String loadBundledShader() {
        try (InputStream input = RgbMapShaderPatcher.class.getClassLoader().getResourceAsStream(TARGET_SHADER_PATH)) {
            if (input == null) {
                return null;
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to read bundled fallback shader: {}", TARGET_SHADER_PATH, e);
            return null;
        }
    }

    private static String buildHelperBlock() {
        return """
                // rgbmaplibs:rgb_maps begin
                const ivec3 rgbmap_lookup[] = ivec3[128](
                %s
                );

                int rgbmap_decode7u(vec3 color) {
                    ivec3 d = ivec3(color * 255.0);
                    for (int i = 0; i < 128; i++) {
                        if (rgbmap_lookup[i] == d) {
                            return i;
                        }
                    }

                    return 0;
                }

                vec4 rgbmap_decode_sample(sampler2D samplerTex, vec2 uv) {
                    vec4 sampled = texture(samplerTex, uv);
                    ivec2 texSize = textureSize(samplerTex, 0).xy;

                    if (texSize == ivec2(128, 128)) {
                        ivec2 coord = (ivec2(floor(uv * vec2(texSize))) / 2) * 2;
                        int b1 = rgbmap_decode7u(texelFetch(samplerTex, coord, 0).rgb);
                        int b2 = rgbmap_decode7u(texelFetch(samplerTex, coord + ivec2(1, 0), 0).rgb);
                        int b3 = rgbmap_decode7u(texelFetch(samplerTex, coord + ivec2(0, 1), 0).rgb);
                        int b4 = rgbmap_decode7u(texelFetch(samplerTex, coord + ivec2(1, 1), 0).rgb);

                        b1 |= (b4 & 1) << 7;
                        b2 |= (b4 & 2) << 6;
                        b3 |= (b4 & 4) << 5;

                        sampled = vec4(vec3(b3, b2, b1) / 255.0, 1.0);
                    }

                    return sampled;
                }
                // rgbmaplibs:rgb_maps end
                """.formatted(buildLookupTable());
    }

    private static String buildLookupTable() {
        StringBuilder builder = new StringBuilder();
        RgbMapPalette palette = RgbMapPalette.rgbMaps();

        for (int i = 0; i < palette.size(); i++) {
            int rgb = palette.rgbAt(i);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            builder.append("        ivec3(")
                    .append(r)
                    .append(", ")
                    .append(g)
                    .append(", ")
                    .append(b)
                    .append(")");

            if (i + 1 < palette.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }

        return builder.toString().trim();
    }

    private record MainBlock(int signatureStart, int bodyStart, int bodyEnd) {
    }

    private record PatchResult(String shader, boolean patched, boolean alreadyPatched, String reason) {
        static PatchResult patched(String shader) {
            return new PatchResult(shader, true, false, "");
        }

        static PatchResult alreadyPatched(String shader) {
            return new PatchResult(shader, false, true, "");
        }

        static PatchResult skipped(String shader, String reason) {
            return new PatchResult(shader, false, false, reason);
        }
    }
}
