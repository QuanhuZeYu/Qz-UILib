package club.heiqi.uilib.config.modern;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.FieldType;
import club.heiqi.uilib.Config;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.render.BackdropQuality;
import club.heiqi.uilib.ui.render.BackdropQualityService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 背景滤镜档位通路（配置项 → 进程级信号 → 主题/渲染）守卫。
 *
 * <p>本类只碰「配置面 + 进程级信号」两段，<b>不</b>调
 * {@link ConfigValueBridge#applyFromAuthority}（那会写 Config/FontConfig 静态字段，
 * 污染面交给自带完整保存/恢复夹具的 {@code ConfigValueBridgeTest} 覆盖）；因此本类无需静态字段夹具。</p>
 *
 * <p>判据：</p>
 * <ol>
 *   <li>schema 声明 {@code general.backdropQuality} 为 CHOICE，选项恰为 full/eco/solid，
 *       默认 {@code full}（⇒ 旧配置缺省 = 现状液态玻璃，观感零变化）；</li>
 *   <li>档位没有 Config 静态镜像字段：唯一运行时权威是 {@link BackdropQualityService}
 *       的进程级信号（静态镜像会成为「有键、有回灌、零读取者」的假开关，撞既有配置守卫）；</li>
 *   <li>权威源读取 → 回灌入口 → 进程级信号（帧末 flush 后生效），信号是进程级单例、不重建；</li>
 *   <li>缺键 / 非法值回落 FULL 且不崩（走既有失效通道，不改写配置文件）；</li>
 *   <li><b>三档取值必须能手改 YAML 裸词往返</b>——{@code off} 是 YAML 1.1 布尔字面量，
 *       本轮实测踩中（裸 {@code off} → strict type BOOLEAN → bootstrap fail-closed →
 *       配置页打不开），故第三档定名 {@code solid}；本判据是该取值的回归锁。</li>
 * </ol>
 */
public class BackdropQualityConfigTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void resetQuality() {
        clearQuality();
    }

    @After
    public void restoreQuality() {
        clearQuality();
    }

    /** 复位进程级档位信号（本类会写全局状态，必须双向清理）。 */
    private static void clearQuality() {
        BackdropQualityService.getInstance().applyConfigured("full");
        ReactiveScheduler.get().flush();
    }

    /** ① schema 声明：CHOICE + 三档选项 + 默认 full。 */
    @Test
    public void schemaDeclaresBackdropQualityChoiceWithFullDefault() {
        ConfigSchema schema = QzUiLibModernSchema.create();
        FieldSpec field = schema.field(BackdropQualityService.CONFIG_PATH);
        assertNotNull("schema 必须声明 " + BackdropQualityService.CONFIG_PATH, field);
        assertEquals("档位是枚举型字段", FieldType.CHOICE, field.type());
        assertEquals("默认必须是 full（= 现状液态玻璃，观感零变化）", "full", field.defaultValue());
        assertEquals("选项必须恰为三档（顺序即 UI 呈现顺序）",
                Arrays.asList("full", "eco", "solid"), field.constraints().choices());
    }

    /** ② 档位没有 Config 静态镜像字段（本体是进程级信号，不是静态字段）。 */
    @Test
    public void qualityHasNoStaticMirrorOnConfig() {
        try {
            Config.class.getField("backdropQuality");
            fail("Config 不得新增 backdropQuality 静态镜像：本档位唯一运行时权威是 "
                    + "BackdropQualityService 的进程级信号，镜像字段会被既有"
                    + "「有键、有回灌、主源码零读取者 = 假开关」守卫判红");
        } catch (NoSuchFieldException expected) {
            // 预期：字段不存在
        }
    }

    /** ③ 权威源 → 回灌入口 → 进程级信号（帧末口径）；信号单例，不随读取重建。 */
    @Test
    public void configuredQualityReachesProcessSignalWithoutRebuildingIt() throws Exception {
        assertSame("信号必须是进程级单例（订阅方据此建立长期依赖）",
                BackdropQualityService.getInstance().quality(),
                BackdropQualityService.getInstance().quality());
        assertEquals("初始 = FULL（未配置 = 现状）", BackdropQuality.FULL,
                BackdropQualityService.getInstance().current());

        // 走真实保存通道：DraftBuffer → save → 落盘 → 重新 bootstrap 读回
        File file = tempFolder.newFile("qzuilib-modern.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("general.backdropQuality", "solid");
        assertTrue("合法档位必须可保存", manager.save(draft).isSuccess());
        ConfigManager persisted = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        assertEquals("权威源必须读到配置值", "solid",
                persisted.authority().getString(BackdropQualityService.CONFIG_PATH));

        // 回灌入口的唯一调用形态（生产调用点是 ConfigValueBridge.applyGeneral）
        BackdropQualityService.getInstance().applyConfigured(
                persisted.authority().getString(BackdropQualityService.CONFIG_PATH));
        // 帧末口径（守 I9）：写入经调度器批处理，宿主帧末 flush 后生效。
        ReactiveScheduler.get().flush();
        assertEquals("回灌后信号应为配置档位", BackdropQuality.OFF,
                BackdropQualityService.getInstance().current());
        assertSame("回灌不得替换信号实例",
                BackdropQualityService.getInstance().quality(),
                BackdropQualityService.getInstance().quality());

        // 改回 full：同一信号实例再次更新（热更语义）
        BackdropQualityService.getInstance().applyConfigured("full");
        ReactiveScheduler.get().flush();
        assertEquals("改档位只改值、不换信号", BackdropQuality.FULL,
                BackdropQualityService.getInstance().current());
    }

    /** ④ 缺键 / 非法值：回落 FULL 且不崩（走既有失效通道，不改写配置文件）。 */
    @Test
    public void missingOrInvalidConfiguredValueFallsBackToFullWithoutThrowing() throws Exception {
        File missing = tempFolder.newFile("missing.yaml");
        ConfigManager missingManager = ConfigManager.bootstrap(missing, QzUiLibModernSchema.create());
        assertEquals("缺键时权威源给 schema 默认 full", "full",
                missingManager.authority().getString(BackdropQualityService.CONFIG_PATH));
        BackdropQualityService.getInstance().applyConfigured(
                missingManager.authority().getString(BackdropQualityService.CONFIG_PATH));
        ReactiveScheduler.get().flush();
        assertEquals(BackdropQuality.FULL, BackdropQualityService.getInstance().current());

        File invalid = tempFolder.newFile("invalid.yaml");
        writeText(invalid, "general:\n  backdropQuality: bogus\n");
        ConfigManager invalidManager = ConfigManager.bootstrap(invalid, QzUiLibModernSchema.create());
        BackdropQualityService.getInstance().applyConfigured(
                invalidManager.authority().getString(BackdropQualityService.CONFIG_PATH));
        ReactiveScheduler.get().flush();
        assertEquals("非法档位名必须回落 FULL 且不抛", BackdropQuality.FULL,
                BackdropQualityService.getInstance().current());
    }

    /**
     * ⑤ 三档取值必须能手改 YAML 裸词往返。
     *
     * <p>回归锁由来：本档位第三档最初取名 {@code off}，而 {@code off} 是 YAML 1.1 布尔字面量——
     * 手改裸词会让字段被解析成 BOOLEAN，撞 CHOICE/STRING 严格类型校验，bootstrap fail-closed
     * （配置页打不开）。定名 {@code solid} 后三档都不是 YAML 布尔词，裸词手改必须与 UI 写盘行为一致。</p>
     */
    @Test
    public void everyOptionSurvivesUnquotedHandWrittenYaml() throws Exception {
        for (String option : Arrays.asList("full", "eco", "solid")) {
            File file = tempFolder.newFile("quality-" + option + ".yaml");
            writeText(file, String.join("\n", "general:", "  backdropQuality: " + option, ""));
            ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
            assertEquals("档位取值 " + option + " 必须能手改 YAML 裸词读写"
                            + "（不得是 YAML 1.1 布尔字面量，如 off/on/yes/no）",
                    option, manager.authority().getString(BackdropQualityService.CONFIG_PATH));
        }
    }

    private static void writeText(File file, String text) throws Exception {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }
}
