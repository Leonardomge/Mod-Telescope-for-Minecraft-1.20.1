package com.modtelescopio.client;

import com.modtelescopio.TelescopioMod;
import com.modtelescopio.block.TelescopeBlock;
import com.modtelescopio.block.TelescopeBlockEntity;
import com.modtelescopio.common.Optics;
import com.modtelescopio.common.TelescopeType;
import com.modtelescopio.network.AimMessage;
import com.modtelescopio.network.Network;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * Estado da observação no cliente: zoom (FOV), giro da câmera proporcional ao aumento,
 * ganho de luz, troca de oculares pela roda do mouse e sincronização da mira com o servidor.
 */
@Mod.EventBusSubscriber(modid = TelescopioMod.MODID, value = Dist.CLIENT)
public final class ClientObservation {
    private static final double MAX_DISTANCE_SQ = 4.5 * 4.5;

    /** Elementos da HUD escondidos enquanto se olha pela ocular. */
    private static final Set<ResourceLocation> HIDDEN_OVERLAYS = Set.of(
            VanillaGuiOverlay.VIGNETTE.id(), VanillaGuiOverlay.SPYGLASS.id(), VanillaGuiOverlay.HELMET.id(),
            VanillaGuiOverlay.FROSTBITE.id(), VanillaGuiOverlay.PORTAL.id(), VanillaGuiOverlay.HOTBAR.id(),
            VanillaGuiOverlay.CROSSHAIR.id(), VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(),
            VanillaGuiOverlay.PLAYER_HEALTH.id(), VanillaGuiOverlay.ARMOR_LEVEL.id(), VanillaGuiOverlay.FOOD_LEVEL.id(),
            VanillaGuiOverlay.AIR_LEVEL.id(), VanillaGuiOverlay.MOUNT_HEALTH.id(), VanillaGuiOverlay.JUMP_BAR.id(),
            VanillaGuiOverlay.EXPERIENCE_BAR.id(), VanillaGuiOverlay.ITEM_NAME.id(), VanillaGuiOverlay.POTION_ICONS.id(),
            VanillaGuiOverlay.SCOREBOARD.id());

    private static boolean active;
    private static BlockPos pos = BlockPos.ZERO;
    private static TelescopeType type;
    private static int[] eyepieces = {25};
    private static int eyepieceIndex;
    private static float zoom = 1.0F;
    private static long lastFrameNanos;
    private static CameraType savedCamera = CameraType.FIRST_PERSON;

    private static float lastSentYaw = Float.NaN, lastSentAlt;
    private static int sendCooldown;

    private static boolean tracking;
    private static float lastYRot, lastXRot;

    private static boolean gammaSwapped;
    /** Segurar o botão direito repete o clique a cada 4 ticks: só alterna de novo depois de soltar. */
    private static boolean waitRelease;
    private static double savedGamma;

    private ClientObservation() {
    }

    public static boolean isActive() {
        return active;
    }

    public static TelescopeType type() {
        return type;
    }

    public static int[] eyepieces() {
        return eyepieces;
    }

    public static int eyepieceIndex() {
        return eyepieceIndex;
    }

    public static int currentEyepiece() {
        return eyepieces[eyepieceIndex];
    }

    public static void toggle(BlockPos clicked) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || waitRelease) {
            return;
        }
        waitRelease = true;
        if (active && clicked.equals(pos)) {
            stop();
            return;
        }
        Block block = mc.level.getBlockState(clicked).getBlock();
        if (!(block instanceof TelescopeBlock telescope)) {
            return;
        }
        if (active) {
            stop();
        }
        type = telescope.type();
        pos = clicked.immutable();
        eyepieces = Optics.eyepiecesFor(type);
        eyepieceIndex = 0;
        zoom = 1.0F;
        lastFrameNanos = System.nanoTime();
        lastSentYaw = Float.NaN;
        sendCooldown = 0;
        tracking = false;
        savedCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        active = true;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SPYGLASS_USE, 1.0F));
    }

    public static void stop() {
        if (!active) {
            return;
        }
        active = false;
        tracking = false;
        Minecraft mc = Minecraft.getInstance();
        TelescopeBlockEntity be = blockEntity();
        if (be != null) {
            be.liveAim = false;
            if (mc.player != null) {
                sendAim(mc.player.getYRot(), -mc.player.getXRot());
            }
        }
        mc.options.setCameraType(savedCamera);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SPYGLASS_STOP_USING, 1.0F));
    }

    private static TelescopeBlockEntity blockEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        return mc.level.getBlockEntity(pos) instanceof TelescopeBlockEntity be ? be : null;
    }

    private static void sendAim(float yaw, float alt) {
        lastSentYaw = yaw;
        lastSentAlt = alt;
        Network.CHANNEL.sendToServer(new AimMessage(pos, yaw, alt));
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (waitRelease && !mc.options.keyUse.isDown()) {
            waitRelease = false;
        }
        if (!active) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            active = false;
            return;
        }
        TelescopeBlockEntity be = blockEntity();
        if (be == null || player.isShiftKeyDown() || !player.isAlive()
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQ) {
            stop();
            return;
        }
        be.liveAim = true;
        float yaw = player.getYRot(), alt = -player.getXRot();
        if (--sendCooldown <= 0 && (Float.isNaN(lastSentYaw)
                || Math.abs(Optics.wrapDegrees(yaw - lastSentYaw)) > 0.3F || Math.abs(alt - lastSentAlt) > 0.3F)) {
            sendAim(yaw, alt);
            sendCooldown = 2;
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            LocalPlayer player = mc.player;
            if (!active || player == null) {
                tracking = false;
                return;
            }
            long now = System.nanoTime();
            float dt = Math.min(0.1F, (now - lastFrameNanos) / 1.0e9F);
            lastFrameNanos = now;
            float target = Optics.zoomFor(Optics.magnification(type, currentEyepiece()));
            zoom += (target - zoom) * Math.min(1.0F, dt * 10.0F);

            scaleMouseRotation(player);

            if (mc.screen == null || mc.screen instanceof ChatScreen) {
                savedGamma = mc.options.gamma().get();
                mc.options.gamma().set(Math.min(1.0, savedGamma + Optics.lightGatheringBoost(type)));
                gammaSwapped = true;
            }
        } else if (gammaSwapped) {
            mc.options.gamma().set(savedGamma);
            gammaSwapped = false;
        }
    }

    /**
     * O mouse já girou o jogador antes deste evento; aqui reduzimos esse giro pelo zoom,
     * como uma montagem que se move devagar quando o aumento é alto.
     */
    private static void scaleMouseRotation(LocalPlayer player) {
        float yRot = player.getYRot(), xRot = player.getXRot();
        if (tracking) {
            float dy = yRot - lastYRot, dx = xRot - lastXRot;
            if (Math.abs(dy) < 60.0F && Math.abs(dx) < 60.0F) {
                float k = 1.0F / zoom;
                float newY = lastYRot + dy * k;
                float newX = Mth.clamp(lastXRot + dx * k, -90.0F, 90.0F);
                player.setYRot(newY);
                player.setXRot(newX);
                player.yRotO += newY - yRot;
                player.xRotO += newX - xRot;
                yRot = newY;
                xRot = newX;
            }
        }
        lastYRot = yRot;
        lastXRot = xRot;
        tracking = true;
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (active && event.usedConfiguredFov()) {
            event.setFOV(event.getFOV() / zoom);
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!active || Minecraft.getInstance().screen != null || event.getScrollDelta() == 0) {
            return;
        }
        int next = eyepieceIndex + (event.getScrollDelta() > 0 ? 1 : -1);
        next = Math.max(0, Math.min(eyepieces.length - 1, next));
        if (next != eyepieceIndex) {
            eyepieceIndex = next;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.6F));
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (active) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (active && HIDDEN_OVERLAYS.contains(event.getOverlay().id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (active) {
            active = false;
            tracking = false;
            Minecraft.getInstance().options.setCameraType(savedCamera);
        }
    }
}
