package com.modtelescopio.autotest;

import com.modtelescopio.block.TelescopeBlockBase;
import com.modtelescopio.block.TelescopeBlockEntity;
import com.modtelescopio.client.ClientObservation;
import com.modtelescopio.client.model.ModelBounds;
import com.modtelescopio.common.MountBlockType;
import com.modtelescopio.common.Optics;
import com.modtelescopio.common.TelescopeType;
import com.modtelescopio.registry.ModRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Teste automático (só desenvolvimento, não vai para o .jar): cria um mundo plano, monta
 * todos os telescópios e montagens, tira capturas de tela, testa a observação (ocular e
 * câmera) e fecha o jogo. Ativado por {@code gradlew runClient -Pautotest=true}.
 */
public final class AutoTest {
    private static final String WORLD = "telescopio_autotest";

    private record Step(int delay, Runnable action) {
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final List<Step> steps = new ArrayList<>();
    private final Map<String, BlockPos> placed = new HashMap<>();
    private int stage;
    private int stepIndex;
    private int wait;
    private int shot;
    private int ox, oy, oz;

    // estado imposto a cada tick (protege o roteiro de cliques/teclas na janela)
    private double[] hold;
    private CameraType view = CameraType.FIRST_PERSON;
    private boolean hideGui;
    private BlockPos observing;

    public static void start() {
        MinecraftForge.EVENT_BUS.register(new AutoTest());
        System.out.println("[AutoTest] ativado");
    }

    private void log(String s) {
        System.out.println("[AutoTest] " + s);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            tick();
        } catch (Throwable t) {
            log("ERRO: " + t);
            t.printStackTrace();
            mc.stop();
        }
    }

    private void enforce() {
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        // solta o mouse: movimentos do mouse no PC não giram a câmera do teste
        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }
        if (mc.options.getCameraType() != view) {
            mc.options.setCameraType(view);
        }
        mc.options.hideGui = hideGui;
        if (hold != null && mc.player != null) {
            mc.player.getAbilities().flying = true;
            float yaw = (float) hold[3], pitch = (float) hold[4];
            mc.player.moveTo(hold[0], hold[1], hold[2], yaw, pitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
            mc.player.yHeadRot = yaw;
            mc.player.yBodyRot = yaw;
            mc.player.setDeltaMovement(Vec3.ZERO);
        }
        if (observing != null && !ClientObservation.isActive()) {
            log("iniciando observacao (parada anterior: " + ClientObservation.lastStopReason + ")");
            ClientObservation.releaseForTest();
            ClientObservation.toggle(observing);
            ClientObservation.releaseForTest();
        }
    }

    private void tick() throws IOException {
        switch (stage) {
            case 0 -> {
                if (mc.screen instanceof TitleScreen) {
                    deleteDir(mc.getLevelSource().getBaseDir().resolve(WORLD));
                    LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT);
                    log("criando mundo");
                    mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(4242L, false, false),
                            ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                                    .createWorldDimensions());
                    stage = 1;
                    wait = 0;
                }
            }
            case 1 -> {
                if (mc.player != null && mc.level != null) {
                    if (mc.screen instanceof PauseScreen) {
                        mc.setScreen(null);
                    }
                    if (++wait > 60) {
                        BlockPos sp = mc.level.getSharedSpawnPos();
                        ox = sp.getX();
                        oz = sp.getZ();
                        oy = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, ox, oz);
                        log("no mundo, origem " + ox + " " + oy + " " + oz);
                        buildScript();
                        stage = 2;
                        wait = 0;
                    }
                }
            }
            case 2 -> {
                enforce();
                if (stepIndex >= steps.size()) {
                    log("fim, fechando o jogo");
                    stage = 3;
                    mc.stop();
                    return;
                }
                Step s = steps.get(stepIndex);
                if (++wait >= s.delay()) {
                    wait = 0;
                    stepIndex++;
                    s.action().run();
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ roteiro

    private void step(int delay, Runnable action) {
        steps.add(new Step(delay, action));
    }

    private void server(Runnable r) {
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv != null) {
            srv.execute(() -> {
                try {
                    r.run();
                } catch (Throwable t) {
                    log("ERRO no servidor: " + t);
                    t.printStackTrace();
                }
            });
        }
    }

    private ServerLevel world() {
        return mc.getSingleplayerServer().overworld();
    }

    private void place(BlockPos pos, Block block, Direction facing, float alt, TelescopeType tube) {
        server(() -> {
            ServerLevel w = world();
            w.setBlock(pos, block.defaultBlockState().setValue(TelescopeBlockBase.FACING, facing), 3);
            if (w.getBlockEntity(pos) instanceof TelescopeBlockEntity be) {
                be.setAim(facing.toYRot(), alt);
                be.setTube(tube);
            }
        });
    }

    private void setTime(long time) {
        server(() -> world().setDayTime(time));
    }

    /** Agenda: posiciona a câmera (mantida a cada tick), espera e tira uma captura. */
    private void shotAt(double x, double y, double z, float yaw, float pitch, CameraType cam, boolean gui, String name) {
        step(5, () -> {
            hold = new double[]{x, y, z, yaw, pitch};
            view = cam;
            hideGui = gui;
        });
        step(70, () -> screenshot(name));
    }

    private void screenshot(String name) {
        shot++;
        String file = String.format("telescopio_%02d_%s.png", shot, name);
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), c -> {
        });
        log("captura " + file);
    }

    private void buildScript() {
        int y = oy;
        server(() -> {
            ServerLevel w = world();
            w.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, w.getServer());
            w.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, w.getServer());
            w.setWeatherParameters(12000, 0, false, false);
        });
        setTime(6000);

        // fileiras de telescópios virados para leste (+X), vistos de lado a partir do sul
        List<TelescopeType> small = new ArrayList<>(), big = new ArrayList<>(), giant = new ArrayList<>();
        for (TelescopeType t : TelescopeType.values()) {
            float h = ModelBounds.of(t).height();
            if (h > 4.5F) {
                giant.add(t);
            } else if (h > 2.05F) {
                big.add(t);
            } else {
                small.add(t);
            }
        }
        row(small, oz, y, 1.5F, 16F, "pequenos");
        row(big, oz + 14, y, 2.0F, 20F, "grandes");
        row(giant, oz + 36, y, 4.0F, 60F, "gigantes");

        // escala: o jogador (visto em terceira pessoa) ao lado do Dobsoniano de 600 mm e do CDK2000
        BlockPos dob = placed.get("dob_flextube_600");
        BlockPos cdk = placed.get("planewave_cdk2000");
        // jogador ao lado do dobsoniano, câmera de frente (terceira pessoa invertida)
        shotAt(dob.getX() + 0.5 - 2.6, y, dob.getZ() + 0.5, 0F, 8F, CameraType.THIRD_PERSON_FRONT, true,
                "escala_dobsoniano_600");
        // coluna de 2 blocos (altura de um jogador) ao lado do CDK2000
        BlockPos marker = cdk.offset(-6, 0, 0);
        server(() -> {
            world().setBlock(marker, Blocks.RED_WOOL.defaultBlockState(), 3);
            world().setBlock(marker.above(), Blocks.WHITE_WOOL.defaultBlockState(), 3);
        });
        shotAt(cdk.getX() - 1.5, y + 3.0, cdk.getZ() + 19.0, 180F, -6F, CameraType.FIRST_PERSON, true, "escala_cdk2000");

        // closes
        BlockPos pds = placed.get("skywatcher_250pds_eq6r");
        shotAt(pds.getX() + 0.5, y + 1.6, pds.getZ() + 3.2, 180F, 10F, CameraType.FIRST_PERSON, true, "close_250pds");
        BlockPos cdk24 = placed.get("planewave_cdk24");
        shotAt(cdk24.getX() + 0.5, y + 2.4, cdk24.getZ() + 5.0, 180F, 8F, CameraType.FIRST_PERSON, true, "close_cdk24");

        // montagens: vazias (atrás) e com tubo (na frente)
        String[][] combos = {
                {"mount_az_gti", "skywatcher_skymax_127"}, {"mount_star_adventurer_gti", "william_optics_redcat_51"},
                {"mount_eq5", "skywatcher_evostar_80ed"}, {"mount_heq5_pro", "gso_rc6"},
                {"mount_eq6r_pro", "skywatcher_250pds_eq6r"}, {"mount_eq8r_pro", "newton_carbon_300_eq8r"},
                {"mount_avx", "celestron_rasa8"}, {"mount_cgx", "celestron_c11_cgx"},
                {"mount_cgx_l", "celestron_c14_cgxl"}, {"mount_planewave_l350", "planewave_cdk14"},
                {"mount_planewave_l600", "planewave_cdk24"}};
        int mz = oz - 14;
        int x = ox - 20;
        for (String[] c : combos) {
            Block block = ModRegistry.MOUNT_BLOCKS.get(MountBlockType.byId(c[0])).get();
            place(new BlockPos(x, y, mz), block, Direction.EAST, Optics.DEFAULT_ALTITUDE, TelescopeType.byId(c[1]));
            place(new BlockPos(x, y, mz - 4), block, Direction.EAST, Optics.DEFAULT_ALTITUDE, null);
            x += 4;
        }
        for (int i = 0; i < 3; i++) {
            shotAt(ox - 16.5 + i * 15, y + 1.2, mz + 6.0, 180F, 14F, CameraType.FIRST_PERSON, true, "montagens_" + i);
        }

        // observação: alvo xadrez a 84 blocos ao norte
        int tx = ox + 40, tz = oz - 90;
        server(() -> {
            ServerLevel w = world();
            for (int dx = -6; dx <= 6; dx++) {
                for (int dy = 0; dy <= 12; dy++) {
                    Block b = ((dx + 6) / 2 + dy / 2) % 2 == 0 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL;
                    if (Math.abs(dx) <= 1 || dy == 6) {
                        b = Blocks.YELLOW_WOOL;
                    }
                    w.setBlock(new BlockPos(tx + dx, y + dy, tz), b.defaultBlockState(), 3);
                }
            }
        });
        int px = ox + 40, pz = oz - 6;
        place(new BlockPos(px, y, pz), ModRegistry.TELESCOPE_BLOCKS.get(TelescopeType.SKYWATCHER_250PDS_EQ6R).get(),
                Direction.NORTH, 10F, null);
        place(new BlockPos(px + 6, y, pz), ModRegistry.TELESCOPE_BLOCKS.get(TelescopeType.ZWO_SEESTAR_S50).get(),
                Direction.NORTH, 10F, null);
        place(new BlockPos(px + 12, y, pz), ModRegistry.MOUNT_BLOCKS.get(MountBlockType.MOUNT_EQ6R_PRO).get(),
                Direction.NORTH, 10F, TelescopeType.CELESTRON_C11_CGX);
        observe(new BlockPos(px, y, pz), tx, y + 6.5, tz, "ocular", new int[]{0, 2, 4});
        observe(new BlockPos(px + 6, y, pz), tx, y + 6.5, tz, "camera", new int[]{0, 3});
        observe(new BlockPos(px + 12, y, pz), tx, y + 6.5, tz, "montagem_com_tubo", new int[]{1});

        // noite: a Lua no zênite pelo dobsoniano
        BlockPos night = new BlockPos(px - 8, y, pz);
        place(night, ModRegistry.TELESCOPE_BLOCKS.get(TelescopeType.SKYWATCHER_FLEXTUBE_400P).get(),
                Direction.NORTH, 60F, null);
        step(10, () -> setTime(18000));
        observe(night, Double.NaN, 0, 0, "noite_lua", new int[]{0, 3});
        step(10, () -> setTime(6000));

        // ícones 3D no inventário criativo
        step(20, () -> {
            hideGui = false;
            creativeTab(ModRegistry.TAB.get());
        });
        step(40, () -> {
            screenshot("inventario_telescopios");
            creativeTab(ModRegistry.TAB_PARTS.get());
        });
        step(40, () -> {
            screenshot("inventario_pecas");
            mc.setScreen(null);
        });
        step(10, () -> log("roteiro concluido"));
    }

    /** Coloca uma fileira e agenda capturas de tela em trechos de até {@code segment} blocos. */
    private void row(List<TelescopeType> list, int z, int y, float gap, float segment, String name) {
        float x = ox - 30;
        float segStart = x;
        List<float[]> segments = new ArrayList<>();
        float maxH = 0;
        for (TelescopeType t : list) {
            ModelBounds b = ModelBounds.of(t);
            float w = Math.max(1F, b.width());
            if (x + w - segStart > segment && x > segStart) {
                segments.add(new float[]{segStart, x - gap, maxH});
                segStart = x;
                maxH = 0;
            }
            BlockPos pos = new BlockPos((int) Math.floor(x + w * 0.5F), y, z);
            place(pos, ModRegistry.TELESCOPE_BLOCKS.get(t).get(), Direction.EAST, Optics.DEFAULT_ALTITUDE, null);
            placed.put(t.id, pos);
            maxH = Math.max(maxH, b.height());
            x += w + gap;
        }
        segments.add(new float[]{segStart, x - gap, maxH});
        for (int i = 0; i < segments.size(); i++) {
            float[] s = segments.get(i);
            float width = s[1] - s[0];
            double dist = Math.max(width / 2.2, s[2] * 1.35) + 3;
            shotAt((s[0] + s[1]) / 2.0, y + s[2] * 0.55 + 1.0 - 1.62, z + dist, 180F, 12F, CameraType.FIRST_PERSON,
                    true, name + "_" + i);
        }
    }

    /**
     * Observa pelo telescópio em {@code pos}, mirando o ponto (tx,ty,tz) - ou o zênite quando tx é NaN -
     * e tira uma captura para cada passo de zoom.
     */
    private void observe(BlockPos pos, double tx, double ty, double tz, String name, int[] zoomSteps) {
        double ex = pos.getX() + 0.5, ey = pos.getY(), ez = pos.getZ() + 2.5;
        float yaw = 180F, pitch = -89.9F;
        if (!Double.isNaN(tx)) {
            double dx = tx + 0.5 - ex, dy = ty - (ey + 1.62), dz = tz + 0.5 - ez;
            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        }
        float fy = yaw, fp = pitch;
        step(10, () -> {
            hold = new double[]{ex, ey, ez, fy, fp};
            view = CameraType.FIRST_PERSON;
            hideGui = false;
            observing = pos;
        });
        for (int zs : zoomSteps) {
            step(40, () -> ClientObservation.setStepForTest(zs));
            step(50, () -> screenshot(name + "_passo" + zs));
        }
        step(5, () -> {
            observing = null;
            ClientObservation.stop();
            ClientObservation.releaseForTest();
        });
    }

    private void creativeTab(CreativeModeTab tab) {
        CreativeModeInventoryScreen screen = new CreativeModeInventoryScreen(mc.player,
                mc.player.connection.enabledFeatures(), true);
        mc.setScreen(screen);
        try {
            Method m = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
            m.setAccessible(true);
            m.invoke(screen, tab);
        } catch (Exception e) {
            log("nao consegui trocar a aba: " + e);
        }
    }

    private static void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
