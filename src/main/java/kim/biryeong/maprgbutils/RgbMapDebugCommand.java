package kim.biryeong.maprgbutils;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import eu.pb4.mapcanvas.api.core.CanvasImage;
import eu.pb4.mapcanvas.api.core.DrawableCanvas;
import eu.pb4.mapcanvas.api.core.PlayerCanvas;
import eu.pb4.mapcanvas.api.utils.CanvasUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RgbMapDebugCommand {
    private static final Map<UUID, PlayerCanvas> ACTIVE_CANVASES = new ConcurrentHashMap<>();
    private static final RgbMapCodec CODEC = RgbMapCodec.createDefault();

    private RgbMapDebugCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("rgbmapdebug")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> show(context.getSource()))
                        .then(Commands.literal("clear")
                                .executes(context -> clear(context.getSource())))
        ));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> destroyExisting(handler.getPlayer().getUUID()));
    }

    private static int show(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        destroyExisting(player.getUUID());

        BufferedImage debug64x64 = createDebugImage();
        int[] mapIndexes = CODEC.encodeImageToMapIndexes(debug64x64);

        CanvasImage encodedCanvas = RgbMapCanvasAdapter.mapIndexesToDrawableCanvas(mapIndexes);
        PlayerCanvas playerCanvas = DrawableCanvas.create();
        CanvasUtils.draw(playerCanvas, 0, 0, encodedCanvas);

        playerCanvas.addPlayer(player);
        playerCanvas.sendUpdates();

        ItemStack mapItem = playerCanvas.asStack();
        if (!player.addItem(mapItem)) {
            player.drop(mapItem, false);
        }

        ACTIVE_CANVASES.put(player.getUUID(), playerCanvas);

        source.sendSuccess(
                () -> Component.literal("rgbmapdebug map created (id=" + playerCanvas.getId() + "). Hold the map item to view the encoded canvas."),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int clear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean removed = destroyExisting(player.getUUID());

        if (removed) {
            source.sendSuccess(() -> Component.literal("rgbmapdebug canvas cleared."), false);
        } else {
            source.sendFailure(Component.literal("No active rgbmapdebug canvas found for this player."));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static boolean destroyExisting(UUID playerUuid) {
        PlayerCanvas previous = ACTIVE_CANVASES.remove(playerUuid);
        if (previous == null) {
            return false;
        }
        previous.destroy();
        return true;
    }

    private static BufferedImage createDebugImage() {
        BufferedImage image = new BufferedImage(RgbMapCodec.RGB_WIDTH, RgbMapCodec.RGB_HEIGHT, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < RgbMapCodec.RGB_HEIGHT; y++) {
            for (int x = 0; x < RgbMapCodec.RGB_WIDTH; x++) {
                int r = (x * 4) & 0xFF;
                int g = (y * 4) & 0xFF;
                int b = ((x * 3) ^ (y * 5)) & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        return image;
    }
}
