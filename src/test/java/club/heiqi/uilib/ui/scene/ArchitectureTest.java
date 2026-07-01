package club.heiqi.uilib.ui.scene;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.runner.RunWith;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构守卫测试 —— 把 {@code NORTH_STAR.md} 的层级不变量翻译成 ArchUnit 规则，
 * 防止后续改动隐性破坏 L1/L2/L3 分层。
 *
 * <h3>当前启用规则</h3>
 * <ul>
 *   <li><b>规则 3</b>：L2 layout 纯数学层禁依赖 runtime/input/reactive/paint
 *       （对应 NORTH_STAR I4/I7/I12，package-info 已声明）。</li>
 * </ul>
 *
 * <p>规则 1 / 2 在 B5 批次启用，本类暂只锁规则 3。</p>
 *
 * <h3>包扫描范围</h3>
 * <p>{@code @AnalyzeClasses(packages = "club.heiqi.uilib.ui.scene")} 只导入 scene 子树，
 * 规则 3 的 {@code ..ui.scene.layout..} / {@code ..ui.scene.runtime..} 等包匹配
 * 均落在此范围内。{@link ImportOption.DoNotIncludeJars} 跳过 jar 依赖，加速导入。</p>
 */
@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(
        packages = "club.heiqi.uilib.ui.scene",
        importOptions = ImportOption.DoNotIncludeJars.class)
public class ArchitectureTest {

    /**
     * 规则 3：L2 layout 纯数学层禁依赖 runtime/input/reactive/paint。
     *
     * <p>L2（{@code ui.scene.layout}）按 NORTH_STAR I4/I7/I12 必须保持纯数学语义：
     * 只依赖 LayoutBox / Constraints / SizingCalculator 等几何原语，零 runtime
     * 信号、零 input 事件、零 reactive 流、零 paint 渲染耦合。任何向上耦合都会
     * 破坏「失效级别矩阵」与「重算收敛」的数学可证性。</p>
     *
     * <p>package-info 已显式声明此边界，本规则把它升级为可执行守卫。</p>
     */
    @ArchTest
    static final ArchRule layoutIsPureMath =
            noClasses()
                    .that().resideInAPackage("..ui.scene.layout..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..ui.scene.runtime..",
                            "..ui.scene.input..",
                            "..ui.reactive..",
                            "..ui.scene.paint..")
                    .because("L2 纯数学层零 runtime/signal/input/paint 依赖"
                            + "（package-info 已声明，NORTH_STAR I4/I7/I12）");
}
