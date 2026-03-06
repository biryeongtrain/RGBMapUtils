package kim.biryeong.maprgbutils.impl;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import kim.biryeong.maprgbutils.api.RgbMapPalette;
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
                    vec3 c = color * 255.0;
                    int bestIndex = 0;
                    float bestDistance = 1e20;

                    for (int i = 0; i < 128; i++) {
                        vec3 diff = c - vec3(rgbmap_lookup[i]);
                        float distance = dot(diff, diff);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            bestIndex = i;
                        }
                    }

                    return bestIndex;
                }

                bool rgbmap_is_near_i11(vec4 sampledColor, float maxDistance) {
                    if (sampledColor.a < 0.99) {
                        return false;
                    }

                    vec3 c = sampledColor.rgb * 255.0;
                    int bestIndex = 0;
                    float bestDistance = 1e20;

                    for (int i = 0; i < 8; i++) {
                        vec3 diff = c - vec3(rgbmap_lookup[i]);
                        float distance = dot(diff, diff);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            bestIndex = i;
                        }
                    }

                    return bestIndex < 8 && bestDistance <= maxDistance;
                }

                bool rgbmap_is_opaque(vec4 sampledColor) {
                    return sampledColor.a >= 0.99;
                }

                bool rgbmap_block_is_opaque(sampler2D samplerTex, ivec2 origin, ivec2 texSize) {
                    ivec2 clampedOrigin = clamp(origin, ivec2(0), texSize - ivec2(2));
                    return rgbmap_is_opaque(texelFetch(samplerTex, clampedOrigin, 0))
                            && rgbmap_is_opaque(texelFetch(samplerTex, clampedOrigin + ivec2(1, 0), 0))
                            && rgbmap_is_opaque(texelFetch(samplerTex, clampedOrigin + ivec2(0, 1), 0))
                            && rgbmap_is_opaque(texelFetch(samplerTex, clampedOrigin + ivec2(1, 1), 0));
                }

                int rgbmap_i11_score(sampler2D samplerTex, ivec2 origin, ivec2 texSize, float maxDistance) {
                    ivec2 minCoord = ivec2(0);
                    ivec2 maxOrigin = texSize - ivec2(2);
                    ivec2 o0 = clamp(origin, minCoord, maxOrigin);
                    ivec2 o1 = clamp(origin + ivec2(2, 0), minCoord, maxOrigin);
                    ivec2 o2 = clamp(origin + ivec2(0, 2), minCoord, maxOrigin);

                    int score = 0;
                    score += rgbmap_is_near_i11(texelFetch(samplerTex, o0 + ivec2(1, 1), 0), maxDistance) ? 1 : 0;
                    score += rgbmap_is_near_i11(texelFetch(samplerTex, o1 + ivec2(1, 1), 0), maxDistance) ? 1 : 0;
                    score += rgbmap_is_near_i11(texelFetch(samplerTex, o2 + ivec2(1, 1), 0), maxDistance) ? 1 : 0;
                    return score;
                }

                int rgbmap_i11_available(sampler2D samplerTex, ivec2 origin, ivec2 texSize) {
                    ivec2 minCoord = ivec2(0);
                    ivec2 maxOrigin = texSize - ivec2(2);
                    ivec2 o0 = clamp(origin, minCoord, maxOrigin);
                    ivec2 o1 = clamp(origin + ivec2(2, 0), minCoord, maxOrigin);
                    ivec2 o2 = clamp(origin + ivec2(0, 2), minCoord, maxOrigin);

                    int available = 0;
                    available += rgbmap_is_opaque(texelFetch(samplerTex, o0 + ivec2(1, 1), 0)) ? 1 : 0;
                    available += rgbmap_is_opaque(texelFetch(samplerTex, o1 + ivec2(1, 1), 0)) ? 1 : 0;
                    available += rgbmap_is_opaque(texelFetch(samplerTex, o2 + ivec2(1, 1), 0)) ? 1 : 0;
                    return available;
                }

                int rgbmap_candidate_score(sampler2D samplerTex, ivec2 origin, ivec2 texSize) {
                    ivec2 clampedOrigin = clamp(origin, ivec2(0), texSize - ivec2(2));
                    if (!rgbmap_is_near_i11(texelFetch(samplerTex, clampedOrigin + ivec2(1, 1), 0), 256.0)) {
                        return -1;
                    }

                    int available = rgbmap_i11_available(samplerTex, clampedOrigin, texSize);
                    if (available <= 0) {
                        return -1;
                    }

                    int score = rgbmap_i11_score(samplerTex, clampedOrigin, texSize, 256.0);
                    int requiredScore = min(3, available);
                    return score >= requiredScore ? score : -1;
                }

                bool rgbmap_find_block_origin(sampler2D samplerTex, ivec2 pixel, ivec2 texSize, out ivec2 originOut) {
                    if (texSize.x < 2 || texSize.y < 2) {
                        return false;
                    }

                    int originEvenX = pixel.x - (pixel.x & 1);
                    int originOddX = pixel.x - ((pixel.x + 1) & 1);
                    int originEvenY = pixel.y - (pixel.y & 1);
                    int originOddY = pixel.y - ((pixel.y + 1) & 1);

                    ivec2 candidate0 = ivec2(originEvenX, originEvenY);
                    ivec2 candidate1 = ivec2(originOddX, originEvenY);
                    ivec2 candidate2 = ivec2(originEvenX, originOddY);
                    ivec2 candidate3 = ivec2(originOddX, originOddY);

                    int bestScore = -1;
                    ivec2 bestOrigin = candidate0;

                    int score0 = rgbmap_candidate_score(samplerTex, candidate0, texSize);
                    if (score0 > bestScore) {
                        bestScore = score0;
                        bestOrigin = candidate0;
                    }

                    int score1 = rgbmap_candidate_score(samplerTex, candidate1, texSize);
                    if (score1 > bestScore) {
                        bestScore = score1;
                        bestOrigin = candidate1;
                    }

                    int score2 = rgbmap_candidate_score(samplerTex, candidate2, texSize);
                    if (score2 > bestScore) {
                        bestScore = score2;
                        bestOrigin = candidate2;
                    }

                    int score3 = rgbmap_candidate_score(samplerTex, candidate3, texSize);
                    if (score3 > bestScore) {
                        bestScore = score3;
                        bestOrigin = candidate3;
                    }

                    if (bestScore < 0) {
                        return false;
                    }

                    originOut = clamp(bestOrigin, ivec2(0), texSize - ivec2(2));
                    return true;
                }

                bool rgbmap_is_encoded_map(sampler2D samplerTex) {
                    ivec2 texSize = textureSize(samplerTex, 0).xy;
                    if (texSize.x < 2 || texSize.y < 2) {
                        return false;
                    }

                    ivec2 center = clamp(texSize / 2, ivec2(0), texSize - ivec2(1));
                    ivec2 origin;
                    if (rgbmap_find_block_origin(samplerTex, center, texSize, origin)) {
                        return true;
                    }

                    ivec2 topLeft = ivec2(1, 1);
                    if (rgbmap_find_block_origin(samplerTex, topLeft, texSize, origin)) {
                        return true;
                    }

                    ivec2 topRight = ivec2(texSize.x - 2, 1);
                    if (rgbmap_find_block_origin(samplerTex, topRight, texSize, origin)) {
                        return true;
                    }

                    ivec2 bottomLeft = ivec2(1, texSize.y - 2);
                    return rgbmap_find_block_origin(samplerTex, bottomLeft, texSize, origin);
                }

                vec4 rgbmap_decode_sample(sampler2D samplerTex, vec2 uv) {
                    vec4 sampled = texture(samplerTex, uv);
                    ivec2 texSize = textureSize(samplerTex, 0).xy;

                    if (!rgbmap_is_opaque(sampled)) {
                        return sampled;
                    }

                    if (!rgbmap_is_encoded_map(samplerTex)) {
                        return sampled;
                    }

                    ivec2 pixel = clamp(ivec2(floor(uv * vec2(texSize))), ivec2(0), texSize - ivec2(1));
                    ivec2 coord;
                    if (!rgbmap_find_block_origin(samplerTex, pixel, texSize, coord)) {
                        return sampled;
                    }

                    if (!rgbmap_block_is_opaque(samplerTex, coord, texSize)) {
                        return sampled;
                    }

                    int b1 = rgbmap_decode7u(texelFetch(samplerTex, coord, 0).rgb);
                    int b2 = rgbmap_decode7u(texelFetch(samplerTex, coord + ivec2(1, 0), 0).rgb);
                    int b3 = rgbmap_decode7u(texelFetch(samplerTex, coord + ivec2(0, 1), 0).rgb);
                    int b4 = rgbmap_decode7u(texelFetch(samplerTex, coord + ivec2(1, 1), 0).rgb);

                    b1 |= (b4 & 1) << 7;
                    b2 |= (b4 & 2) << 6;
                    b3 |= (b4 & 4) << 5;

                    return vec4(vec3(b3, b2, b1) / 255.0, 1.0);
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
