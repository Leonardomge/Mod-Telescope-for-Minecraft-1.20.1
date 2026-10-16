package com.modtelescopio.client;

import com.modtelescopio.TelescopioMod;
import com.modtelescopio.block.MountBlock;
import com.modtelescopio.block.TelescopeBlock;
import com.modtelescopio.block.TelescopeBlockEntity;
import com.modtelescopio.client.model.Assembly;
import com.modtelescopio.client.model.ModelBounds;
import com.modtelescopio.common.Optics;
import com.modtelescopio.common.TelescopeType;
import com.modtelescopio.network.AimMessage;
import com.modtelescopio.network.Network;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
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
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * Estado da observação no cliente: zoom (FOV), giro da câmera proporcional ao aumento,
 * ganho de luz, troca de oculares (ou zoom digital da câmera) pela roda do mouse e
 * sincronização da mira com o servidor.
 */
@Mod.EventBusSubscriber(modid = TelescopioMod.MODID, value = Dist.CLIENT)
public final class ClientObservation {
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
    /** Tubo usado na observação (o do telescópio completo ou o instalado na montagem). */
    private static TelescopeType type;
    private static String title = "";
    private static boolean camera;
    private static int[] eyepieces = {25};
    private static int stepIndex;
    private static float zoom = 1.0F;
    private static long lastFrameNanos, startNanos;
    private static double maxDistanceSq = 4.5 * 4.5;
    private static CameraType savedCamera = CameraType.FIRST_PERSON;

    private static float lastSentYaw = Float.NaN, lastSentAlt;
    private static int sendCooldown;

    private static boolean tracking;
    private static float lastYRot, lastXRot;

    private static boolean gammaSwapped;
    private static double savedGamma;
    /** Segurar o botão direito repete o clique a cada 4 ticks: só alterna de novo depois de soltar. */
    private static boolean waitRelease;
    /** Por que a última observação terminou (diagnóstico do teste automático). */
    public static String lastStopReason = "";

    private ClientObservation() {
    }

    public static boolean isActive() {
        return active;
    }

    public static TelescopeType type() {
        return type;
    }

    public static String title() {
        return title;
    }

    /** true = telescópio que só usa câmera (inteligentes e RASA): zoom digital em vez de oculares. */
    public static boolean isCamera() {
        return camera;
    }

    public static int[] eyepieces() {
        return eyepieces;
    }

    public static int stepIndex() {
        return stepIndex;
    }

    public static int currentEyepiece() {
        return eyepieces[Math.min(stepIndex, eyepieces.length - 1)];
    }

    public static float currentDigitalZoom() {
        return Optics.DIGITAL_ZOOM[Math.min(stepIndex, Optics.DIGITAL_ZOOM.length - 1)];
    }

    /** Segundos desde que a observação começou (empilhamento ao vivo das câmeras). */
    public static double secondsObserving() {
        return (System.nanoTime() - startNanos) / 1.0e9;
    }

    /** Só para o teste automático: escolhe a ocular/zoom digital. */
    public static void setStepForTest(int index) {
        int count = camera ? Optics.DIGITAL_ZOOM.length : eyepieces.length;
        stepIndex = Math.max(0, Math.min(count - 1, index));
    }

    /** Só para o teste automático: o teste não segura o botão direito. */
    public static void releaseForTest() {
        waitRelease = false;
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
        if (!(mc.level.getBlockEntity(clicked) instanceof TelescopeBlockEntity be)) {
            return;
        }
        TelescopeType tube = be.opticalTube();
        Assembly a = be.assembly();
        if (tube == null || a == null) {
            return;
        }
        if (active) {
            stop();
        }
        type = tube;
        title = titleOf(be.getBlockState().getBlock(), tube);
        camera = tube.design.usesCamera();
        pos = clicked.immutable();
        eyepieces = Optics.eyepiecesFor(type);
        stepIndex = 0;
        zoom = 1.0F;
        // telescópios gigantes: dá para observar de mais longe (a ocular fica lá no alto)
        double reach = Math.max(4.5, ModelBounds.of(a).height() * 0.6 + 3.0);
        maxDistanceSq = reach * reach;
        lastFrameNanos = startNanos = System.nanoTime();
        lastSentYaw = Float.NaN;
        sendCooldown = 0;
        tracking = false;
        savedCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        active = true;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SPYGLASS_USE, 1.0F));
    }

    private static String titleOf(Block block, TelescopeType tube) {
        String tubeName = I18n.get("block." + TelescopioMod.MODID + "." + tube.id);
        if (block instanceof MountBlock m) {
            return tubeName + " · " + I18n.get("block." + TelescopioMod.MODID + "." + m.mount().id);
        }
        return tubeName;
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

    private static float targetZoom() {
        if (camera) {
            return Optics.cameraZoom(type, currentDigitalZoom());
        }
        return Optics.zoomFor(Optics.magnification(type, currentEyepiece()));
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
        boolean valid = be != null && be.opticalTube() == type;
        if (!valid || player.isShiftKeyDown() || !player.isAlive()
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > maxDistanceSq) {
            lastStopReason = !valid ? "bloco" : player.isShiftKeyDown() ? "agachou" : !player.isAlive() ? "morreu" : "distancia";
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
            zoom += (targetZoom() - zoom) * Math.min(1.0F, dt * 10.0F);

            scaleMouseRotation(player);

            if (mc.screen == null || mc.screen instanceof ChatScreen) {
                savedGamma = mc.options.gamma().get();
                float boost = Optics.lightGatheringBoost(type);
                if (camera) {
                    boost += Optics.stackingBoost(secondsObserving());
                }
                mc.options.gamma().set(Math.min(1.0, savedGamma + boost));
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
        int count = camera ? Optics.DIGITAL_ZOOM.length : eyepieces.length;
        int next = stepIndex + (event.getScrollDelta() > 0 ? 1 : -1);
        next = Math.max(0, Math.min(count - 1, next));
        if (next != stepIndex) {
            stepIndex = next;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.6F));
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBlockHighlight(RenderHighlightEvent.Block event) {
        // o contorno do bloco mirado ficaria enorme na imagem ampliada
        if (active) {
            event.setCanceled(true);
        }
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
