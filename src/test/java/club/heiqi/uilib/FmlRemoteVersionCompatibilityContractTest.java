package club.heiqi.uilib;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.VersionRange;

/**
 * FML 远端版本协商的声明契约测试。
 *
 * <p>声明必须是非空左闭右开区间，<b>且必须包含当前构建版本 {@link Tags#VERSION}</b>：区间不含自身时
 * FML 判定「mod 拒绝自身版本」，集成服务器在进入世界的握手阶段 {@code Rejecting connection CLIENT}
 * 并卸载全部维度，表现为「无法进入世界」（2026-09-12 实际事故）。</p>
 *
 * <p><b>勘误（2026-09-12，制品级实跑证伪本类旧注释）</b>：旧注释称「空串 = 开发期精确版本检查」，
 * 与 FML 实现相反 —— {@code NetworkModHolder} 把<b>显式空串</b>原样送入
 * {@code VersionRange.createFromVersionSpec("")}，得到 restrictions=0 的空区间，{@code containsVersion}
 * 恒 false，即空串<b>拒绝一切远端版本（含自身）</b>；「精确版本相等」只属于<b>整条属性不写</b>的形态
 * （FML 从注解元数据取到 null）。反射读到的是 Java 注解默认值 {@code ""}，无法区分「未声明」与
 * 「显式空串」，故本类只能守住「不得为空」这一侧。</p>
 */
public class FmlRemoteVersionCompatibilityContractTest {

    /**
     * 发布态远端版本范围格式：两侧均为 major.minor.patch 三段式版本，左闭右开，
     * <b>且两端 major 必须相同</b> —— 同 major 内互通才是承诺范围；上界允许跨 minor
     * （[4.9.0,4.11.0) 同时覆盖 4.9.x 与 4.10.x），但不允许跨 major（[4.9.0,5.0.0) 等于
     * 多承诺一个 major）。
     * 例：[4.9.0,4.11.0)；拒绝 [4.7.0,4.7.0) 空区间、缺失括号、预发布标签混入与跨 major 形式。
     * 反向引用 {@code \1} 表达「major 相同」，不把 4 写死：发布 5.0.0 时声明 [5.0.0,5.1.0)
     * 同样合法。
     */
    private static final String RELEASE_RANGE_PATTERN = "\\[(\\d+)\\.\\d+\\.\\d+,(?:\\1)\\.\\d+\\.\\d+\\)";

    /**
     * 声明必须显式给出左闭右开区间；显式空串是禁止形态。
     *
     * <p>空串会被 FML 解析成 restrictions=0 的空区间并拒绝一切（含自身），比「区间漏掉自身」更严重；
     * 开发期若确实不做限制，应当整条删掉该属性，而不是写空串。</p>
     */
    @Test
    public void remoteVersionDeclarationMustBeExplicitRange() {
        Mod declaration = MyMod.class.getAnnotation(Mod.class);
        assertNotNull("MyMod 必须保留 @Mod 声明", declaration);
        String range = declaration.acceptableRemoteVersions();
        assertFalse("显式空串构成空区间、拒绝一切远端版本（含自身），禁止使用；"
                + "需要放开限制时应整条删除该属性（FML 取到 null 才走精确版本相等）", range.isEmpty());
        assertTrue("远端版本范围必须为 [x.y.z,x.y.z) 左闭右开三段式且两端 major 相同，实际：" + range,
                range.matches(RELEASE_RANGE_PATTERN));
    }

    /**
     * 自洽守卫：非空远端范围必须包含当前构建版本，否则 FML 判定「mod 拒绝自身版本」。
     *
     * <p>事故背景：P6 制品刷新用 {@code VERSION=4.10.0} 注入构建版本后，遗留的发布态范围
     * {@code [4.9.0,4.10.0)} 上界开区间恰好排除自身版本 —— 启动期报
     * {@code appears to reject its own version number (4.10.0)}，进入世界时集成服务器
     * {@code Rejecting connection CLIENT: [FMLMod:qz_uilib{4.10.0}]} 后卸载全部维度。</p>
     *
     * <p>判定复用运行时同一套 FML 区间语义（{@link VersionRange} + {@link DefaultArtifactVersion}），
     * 不引入第二套版本比较实现。空串形态由 {@link #remoteVersionDeclarationMustBeExplicitRange} 单独拦截
     * —— 空串不是「开发期自洽」而是拒绝一切，此处提前返回只是避免同因重复报错。</p>
     */
    @Test
    public void declaredRangeMustContainOwnBuildVersion() throws Exception {
        Mod declaration = MyMod.class.getAnnotation(Mod.class);
        assertNotNull("MyMod 必须保留 @Mod 声明", declaration);
        String range = declaration.acceptableRemoteVersions();
        if (range.isEmpty()) {
            return;
        }
        VersionRange parsed = VersionRange.createFromVersionSpec(range);
        assertTrue(
                "远端范围必须包含当前构建版本 " + Tags.VERSION + "，否则 FML 判定 mod 拒绝自身版本、进世界时握手被拒；实际范围：" + range,
                parsed.containsVersion(new DefaultArtifactVersion(Tags.VERSION)));
    }

    /** 自定义 handler 会覆盖默认精确检查，MyMod 不得声明它。 */
    @Test
    public void customNetworkCheckHandlerCannotOverrideExactVersionCheck() {
        for (Method method : MyMod.class.getDeclaredMethods()) {
            assertFalse(
                    method.getName() + " 不得使用 @NetworkCheckHandler 覆盖默认精确检查",
                    method.isAnnotationPresent(NetworkCheckHandler.class));
        }
    }
}
