package club.heiqi.uilib.config.modern;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import club.heiqi.config.ConfigException;
import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.FieldType;
import club.heiqi.config.ui.field.PickerDensityPreferenceSource;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.control.search.PickerDensityPreference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 密度偏好通路（配置项 → 进程级信号 → 装配层接线）守卫。
 *
 * <p>本类只碰「配置面 + 信号 + 接线」三段，<b>不</b>调
 * {@link ConfigValueBridge#applyFromAuthority}（那会写 Config/FontConfig 静态字段，
 * 污染面交给自带完整保存/恢复夹具的 {@code ConfigValueBridgeTest} 覆盖）；因此本类无需静态字段夹具。</p>
 *
 * <p>四条判据：</p>
 * <ol>
 *   <li>schema 声明 {@code general.pickerDensity} 为 CHOICE，选项恰为 auto/compact/standard/roomy，
 *       默认 {@code auto}（⇒ 旧配置缺省 = 现状）；</li>
 *   <li>四档解析（大小写/空白容错）与非法值回落 AUTO（失效通道）；</li>
 *   <li>权威源读取 → 回灌入口 → 进程级信号（帧末 flush 后生效），信号是单例、不重建；</li>
 *   <li>读取失败：错型值让 bootstrap fail-closed，启动路径捕获后不崩且偏好停在 AUTO
 *       （接线先于 bootstrap，与成败无关）。</li>
 * </ol>
 */
public class PickerDensityPreferencesTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void resetPreference() {
        clearPreference();
    }

    @After
    public void restorePreference() {
        clearPreference();
    }

    /** 复位进程级信号与装配层接线（本类会写全局状态，必须双向清理）。 */
    private static void clearPreference() {
        PickerDensityPreferences.resetForTest();
        ReactiveScheduler.get().flush();
        PickerDensityPreferenceSource.release();
    }

    /** ① schema 声明：CHOICE + 四档选项 + 默认 auto（旧配置零影响）。 */
    @Test
    public void schemaDeclaresPickerDensityChoiceWithAutoDefault() {
        ConfigSchema schema = QzUiLibModernSchema.create();
        FieldSpec field = schema.field(PickerDensityPreferences.CONFIG_PATH);
        assertNotNull("schema 必须声明 " + PickerDensityPreferences.CONFIG_PATH, field);
        assertEquals("密度档位是枚举型字段", FieldType.CHOICE, field.type());
        assertEquals("默认必须是 auto（= 现状档，旧配置缺省零影响）", "auto", field.defaultValue());
        assertEquals("选项必须恰为四档（顺序即 UI 呈现顺序）",
                Arrays.asList("auto", "compact", "standard", "roomy"), field.constraints().choices());
    }

    /** ② 解析：四档大小写/空白容错，非法与缺失一律 AUTO。 */
    @Test
    public void resolveMapsNamesAndFallsBackToAuto() {
        assertEquals(PickerDensityPreference.AUTO, PickerDensityPreferences.resolve("auto"));
        assertEquals(PickerDensityPreference.COMPACT, PickerDensityPreferences.resolve("compact"));
        assertEquals(PickerDensityPreference.STANDARD, PickerDensityPreferences.resolve("standard"));
        assertEquals(PickerDensityPreference.ROOMY, PickerDensityPreferences.resolve("roomy"));
        assertEquals("大小写不敏感", PickerDensityPreference.ROOMY,
                PickerDensityPreferences.resolve("Roomy"));
        assertEquals("首尾空白容错", PickerDensityPreference.COMPACT,
                PickerDensityPreferences.resolve("  compact  "));
        assertEquals("null = 未配置", PickerDensityPreference.AUTO, PickerDensityPreferences.resolve(null));
        assertEquals("空串回落", PickerDensityPreference.AUTO, PickerDensityPreferences.resolve(""));
        assertEquals("纯空白回落", PickerDensityPreference.AUTO, PickerDensityPreferences.resolve("   "));
        assertEquals("未知名称回落（不抛）", PickerDensityPreference.AUTO,
                PickerDensityPreferences.resolve("bogus"));
        assertEquals("错型哨兵（reload 校验路径）回落", PickerDensityPreference.AUTO,
                PickerDensityPreferences.resolve("-1"));
    }

    /** ③ 权威源 → 回灌 → 进程级信号（帧末口径）；信号单例，不随读取重建。 */
    @Test
    public void configuredTierReachesProcessSignalWithoutRebuildingIt() throws Exception {
        assertSame("信号必须是进程级单例", PickerDensityPreferences.signal(), PickerDensityPreferences.signal());
        assertEquals("初始 = AUTO（未配置 = 现状）", PickerDensityPreference.AUTO,
                PickerDensityPreferences.signal().get());

        File file = tempFolder.newFile("qzuilib-modern.yaml");
        writeText(file, "general:\n  pickerDensity: roomy\n");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        assertEquals("权威源必须读到配置值", "roomy",
                manager.authority().getString(PickerDensityPreferences.CONFIG_PATH));

        // 回灌入口的唯一调用形态（生产调用点是 ConfigValueBridge.applyGeneral）
        PickerDensityPreferences.applyConfigured(
                manager.authority().getString(PickerDensityPreferences.CONFIG_PATH));
        // 帧末口径：写入经调度器批处理，宿主帧末 flush 后生效。
        ReactiveScheduler.get().flush();
        assertEquals("回灌后信号应为配置档位", PickerDensityPreference.ROOMY,
                PickerDensityPreferences.signal().get());
        assertSame("回灌不得替换信号实例", PickerDensityPreferences.signal(), PickerDensityPreferences.signal());

        // 配置改回 auto：同一信号实例再次更新（热更语义）
        PickerDensityPreferences.applyConfigured("auto");
        ReactiveScheduler.get().flush();
        assertEquals("改档位只改值、不换信号", PickerDensityPreference.AUTO,
                PickerDensityPreferences.signal().get());
    }

    /** ④ 缺键 / 非法值：回落 AUTO 且不崩（走既有失效通道，不改写配置文件）。 */
    @Test
    public void missingOrInvalidConfiguredValueFallsBackToAutoWithoutThrowing() throws Exception {
        File missing = tempFolder.newFile("missing.yaml");
        ConfigManager missingManager = ConfigManager.bootstrap(missing, QzUiLibModernSchema.create());
        assertEquals("缺键时权威源给 schema 默认 auto", "auto",
                missingManager.authority().getString(PickerDensityPreferences.CONFIG_PATH));
        PickerDensityPreferences.applyConfigured(
                missingManager.authority().getString(PickerDensityPreferences.CONFIG_PATH));
        ReactiveScheduler.get().flush();
        assertEquals(PickerDensityPreference.AUTO, PickerDensityPreferences.signal().get());

        File invalid = tempFolder.newFile("invalid.yaml");
        writeText(invalid, "general:\n  pickerDensity: bogus\n");
        ConfigManager invalidManager = ConfigManager.bootstrap(invalid, QzUiLibModernSchema.create());
        PickerDensityPreferences.applyConfigured(
                invalidManager.authority().getString(PickerDensityPreferences.CONFIG_PATH));
        ReactiveScheduler.get().flush();
        assertEquals("非法档位名必须回落 AUTO 且不抛", PickerDensityPreference.AUTO,
                PickerDensityPreferences.signal().get());
    }

    /** ④ 读取失败（错型）：fail-closed + 启动路径不崩 + 接线先于 bootstrap 且与成败无关。 */
    @Test
    public void wronglyTypedValueFailsClosedAndStillInstallsSourceWithAuto() throws Exception {
        File file = tempFolder.newFile("wrong-type.yaml");
        writeText(file, "general:\n  pickerDensity: 3\n");
        try {
            ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
            fail("CHOICE 字段错型必须 fail-closed（严格 NodeType）");
        } catch (ConfigException expected) {
            assertNotNull(expected.getMessage());
        }
        assertFalse("测试起始状态必须未接线", PickerDensityPreferenceSource.isInstalled());

        // 启动路径：bootstrap 失败 → log + return，不中断启动
        ModernConfigBootstrap.bootstrapAndApply(file);
        ReactiveScheduler.get().flush();
        assertEquals("读取失败时偏好停在 AUTO", PickerDensityPreference.AUTO,
                PickerDensityPreferences.signal().get());
        assertTrue("接线先于 bootstrap，与成败无关", PickerDensityPreferenceSource.isInstalled());
        assertSame("接线对象必须是进程级偏好信号", PickerDensityPreferences.signal(),
                PickerDensityPreferenceSource.installed());

        PickerDensityPreferenceSource.release();
        assertFalse("撤线后回到未接线（面板按 AUTO）", PickerDensityPreferenceSource.isInstalled());
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
