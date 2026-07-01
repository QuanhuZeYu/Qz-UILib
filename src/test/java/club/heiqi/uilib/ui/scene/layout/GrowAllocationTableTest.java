package club.heiqi.uilib.ui.scene.layout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * ROW/COLUMN 主轴 grow 分配表驱动参数化测试。
 *
 * <p>覆盖 24 个对称场景（COLUMN 高度 C1-C12 + ROW 宽度 R1-R12），逐场景断言
 * {@link ConstraintResolver#computeColumnGrowHeights} /
 * {@link ConstraintResolver#computeRowGrowWidths} 的 freeze do-while 撞顶撞底、
 * 余数末位吸收、percent 作固定子、margin/gap/padding 扣减与位置摆放。</p>
 *
 * <p>度量用 {@link FixedTextMeasurer}(8,16) 桩保证纯 JUnit 可断言。装饰叶
 * 用 {@code new SceneNode()}（无子无文本，content=0），除非 case 注明。</p>
 *
 * <p>COLUMN 容器靠 {@code setFillParentHeight(true)} 让 priorKnownInnerHeight
 * 返回确定值（约束驱动高度）；ROW 容器靠父级下传确定宽约束驱动主轴分配。</p>
 *
 * <p>期望值经 Oracle 手算并用现有 grow 测试交叉校验，逐字填入。</p>
 */
@RunWith(Parameterized.class)
public class GrowAllocationTableTest {

    /** 被测场景。 */
    private final Case c;

    public GrowAllocationTableTest(Case c) {
        this.c = c;
    }

    /**
     * 24 个场景：COLUMN 组（C1-C12，主轴=高）+ ROW 组（R1-R12，主轴=宽）。
     * 每场景的期望值逐字填 Oracle 手算结果。
     */
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        List<Object[]> list = new ArrayList<>();
        // ============ COLUMN 组（断言 height）============
        addAll(list,
            // C1 单grow吃满：header(prefH=30) 固定 + g(grow1) 吃剩余
            col("C1 单grow吃满")
                .size(200, 100)
                .child(new Spec("header").pref(30).expect(30))
                .child(new Spec("g").grow(1).expect(70)),
            // C2 多grow按权重1:2:3：freeH=120,Σw=6,a=20,b=40,c末位=60
            col("C2 多grow1:2:3")
                .size(200, 120)
                .child(new Spec("a").grow(1).expect(20))
                .child(new Spec("b").grow(2).expect(40))
                .child(new Spec("c").grow(3).expect(60)),
            // C3 权重2:3:1余数末位吸收：freeH=100,a=33,b=50,c末位=17
            col("C3 权重2:3:1余数末位吸收")
                .size(200, 100)
                .child(new Spec("a").grow(2).expect(33))
                .child(new Spec("b").grow(3).expect(50))
                .child(new Spec("c").grow(1).expect(17)),
            // C4 grow撞max回流：b(maxH=50) freeze，剩余回流 a/c 各 125
            col("C4 grow撞max回流")
                .size(200, 300)
                .child(new Spec("a").grow(1).expect(125))
                .child(new Spec("b").grow(1).max(50).expect(50))
                .child(new Spec("c").grow(1).expect(125)),
            // C5 grow撞min(prefH)freeze：a(prefH=80) 撞底 freeze，b 吃剩余 20
            col("C5 grow撞prefreeze")
                .size(200, 100)
                .child(new Spec("a").grow(1).pref(80).expect(80))
                .child(new Spec("b").grow(1).expect(20)),
            // C6 全grow容器溢出freeH→0：header(prefH=120 超容器)固定,a freeH=0→0
            col("C6 grow容器溢出freeH归零")
                .size(200, 100)
                .child(new Spec("header").pref(120).expect(120))
                .child(new Spec("a").grow(1).expect(0)),
            // C7 grow+margin：a(marginTop10+marginBottom6),freeH=84,a/b 各 42
            col("C7 grow+margin")
                .size(200, 100)
                .child(new Spec("a").grow(1).margin(10, 6).expect(42).pos(10))
                .child(new Spec("b").grow(1).expect(42).pos(58)),
            // C8 grow+gap+padding全开：pad(5,0,7,0) gap3,innerH=108,header20,a/b 各 41
            col("C8 grow+gap+padding全开")
                .size(200, 120)
                .padding(5, 0, 7, 0)
                .gap(3)
                .child(new Spec("header").pref(20).expect(20).pos(5))
                .child(new Spec("a").grow(1).expect(41).pos(28))
                .child(new Spec("b").grow(1).expect(41).pos(72)),
            // C9 grow混percent：p(percentH=40)作固定子,a/b 各 30
            col("C9 grow混percent")
                .size(200, 100)
                .child(new Spec("p").percent(40).expect(40))
                .child(new Spec("a").grow(1).expect(30))
                .child(new Spec("b").grow(1).expect(30)),
            // C10 freeze多轮3轮：a(max50)/b(max80) 依次撞顶,c/d 各 85
            col("C10 freeze多轮3轮")
                .size(200, 300)
                .child(new Spec("a").grow(1).max(50).expect(50))
                .child(new Spec("b").grow(1).max(80).expect(80))
                .child(new Spec("c").grow(1).max(100).expect(85))
                .child(new Spec("d").grow(1).expect(85)),
            // C11 混合上下界同轮双冻：a(max30)撞顶,b(pref80)撞底 同轮,c 吃 90
            col("C11 混合上下界同轮双冻")
                .size(200, 200)
                .child(new Spec("a").grow(1).max(30).expect(30))
                .child(new Spec("b").grow(1).pref(80).expect(80))
                .child(new Spec("c").grow(1).expect(90)),
            // C12 不等权重撞顶：a(grow2,max80) freeze,b/c 各 110
            col("C12 不等权重撞顶")
                .size(200, 300)
                .child(new Spec("a").grow(2).max(80).expect(80))
                .child(new Spec("b").grow(1).expect(110))
                .child(new Spec("c").grow(1).expect(110))
        );
        // ============ ROW 组（断言 width）============
        addAll(list,
            // R1 单grow吃满（对称C1）
            row("R1 单grow吃满")
                .size(100, 100)
                .child(new Spec("side").pref(30).expect(30))
                .child(new Spec("g").grow(1).expect(70)),
            // R2 多grow1:2:3（对称C2）
            row("R2 多grow1:2:3")
                .size(120, 100)
                .child(new Spec("a").grow(1).expect(20))
                .child(new Spec("b").grow(2).expect(40))
                .child(new Spec("c").grow(3).expect(60)),
            // R3 权重2:3:1余数（对称C3）
            row("R3 权重2:3:1余数")
                .size(100, 100)
                .child(new Spec("a").grow(2).expect(33))
                .child(new Spec("b").grow(3).expect(50))
                .child(new Spec("c").grow(1).expect(17)),
            // R4 grow撞max回流（对称C4）
            row("R4 grow撞max回流")
                .size(300, 100)
                .child(new Spec("a").grow(1).expect(125))
                .child(new Spec("b").grow(1).max(50).expect(50))
                .child(new Spec("c").grow(1).expect(125)),
            // R5 grow撞prefreeze（对称C5）
            row("R5 grow撞prefreeze")
                .size(100, 100)
                .child(new Spec("a").grow(1).pref(80).expect(80))
                .child(new Spec("b").grow(1).expect(20)),
            // R6 全grow溢出freeW→0（对称C6）
            row("R6 grow溢出freeW归零")
                .size(100, 100)
                .child(new Spec("side").pref(120).expect(120))
                .child(new Spec("a").grow(1).expect(0)),
            // R7 grow+margin（对称C7）：a(marginLeft10+marginRight6)
            row("R7 grow+margin")
                .size(100, 100)
                .child(new Spec("a").grow(1).margin(10, 6).expect(42).pos(10))
                .child(new Spec("b").grow(1).expect(42).pos(58)),
            // R8 grow+gap+padding（对称C8）：pad(0,7,0,5)左5右7 gap3
            row("R8 grow+gap+padding")
                .size(120, 100)
                .padding(0, 7, 0, 5)
                .gap(3)
                .child(new Spec("side").pref(20).expect(20).pos(5))
                .child(new Spec("a").grow(1).expect(41).pos(28))
                .child(new Spec("b").grow(1).expect(41).pos(72)),
            // R9 grow混percent（对称C9）⚠ 可能 red：ROW percentWidth 疑似二次应用百分比
            row("R9 grow混percent")
                .size(100, 100)
                .child(new Spec("p").percent(40).expect(40))
                .child(new Spec("a").grow(1).expect(30))
                .child(new Spec("b").grow(1).expect(30)),
            // R10 freeze多轮3轮（对称C10）
            row("R10 freeze多轮3轮")
                .size(300, 100)
                .child(new Spec("a").grow(1).max(50).expect(50))
                .child(new Spec("b").grow(1).max(80).expect(80))
                .child(new Spec("c").grow(1).max(100).expect(85))
                .child(new Spec("d").grow(1).expect(85)),
            // R11 混合上下界（对称C11）
            row("R11 混合上下界")
                .size(200, 100)
                .child(new Spec("a").grow(1).max(30).expect(30))
                .child(new Spec("b").grow(1).pref(80).expect(80))
                .child(new Spec("c").grow(1).expect(90)),
            // R12 fillParentWidth隐式桥+显式flexGrow：a(fill隐式1)/b(grow3),freeW=100,a=25,b末位=75
            row("R12 fillParentWidth隐式桥+flexGrow")
                .size(100, 100)
                .child(new Spec("a").fill().expect(25))
                .child(new Spec("b").grow(3).expect(75))
        );
        return list;
    }

    /**
     * 主轴分配断言：COLUMN 断 height，ROW 断 width；C7/C8/R7/R8 额外断位置（y/x）。
     *
     * <p>per-test 新建引擎避免跨用例缓存污染。</p>
     */
    @Test
    public void mainAxisAllocation() {
        SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
        SceneNode root = c.build();
        engine.layout(root, c.toConstraints());
        for (Expectation e : c.expectations) {
            LayoutBox box = (LayoutBox) e.node.getCachedLayout();
            Assert.assertNotNull(c.name + " / " + e.label + " 应已布局", box);
            int actualMain = c.isColumn ? box.getHeight() : box.getWidth();
            Assert.assertEquals(c.name + " / " + e.label + " 主轴尺寸",
                    e.expectedMain, actualMain);
            if (e.expectedPos != Spec.UNSET) {
                int actualPos = c.isColumn ? box.getY() : box.getX();
                Assert.assertEquals(c.name + " / " + e.label + " 主轴位置",
                        e.expectedPos, actualPos);
            }
        }
    }

    // ============================================================
    // 辅助工厂
    // ============================================================

    /** 批量添加 Case 到参数列表。 */
    private static void addAll(List<Object[]> list, Case... cases) {
        for (Case c : cases) {
            list.add(new Object[]{c});
        }
    }

    /** COLUMN 场景工厂。 */
    private static Case col(String name) {
        return new Case(name, true);
    }

    /** ROW 场景工厂。 */
    private static Case row(String name) {
        return new Case(name, false);
    }

    // ============================================================
    // 内部数据类
    // ============================================================

    /** 单个场景：容器约束 + padding/gap + 子规格列表 +（build 后）期望列表。 */
    static final class Case {
        final String name;
        final boolean isColumn;
        int availableWidth;
        int availableHeight;
        int padTop, padRight, padBottom, padLeft;
        int gap = 0;
        final List<Spec> specs = new ArrayList<>();
        final List<Expectation> expectations = new ArrayList<>();

        Case(String name, boolean isColumn) {
            this.name = name;
            this.isColumn = isColumn;
        }

        /** 设置约束可用宽高。 */
        Case size(int w, int h) {
            this.availableWidth = w;
            this.availableHeight = h;
            return this;
        }

        /** 设置容器 padding（top, right, bottom, left）。 */
        Case padding(int top, int right, int bottom, int left) {
            this.padTop = top;
            this.padRight = right;
            this.padBottom = bottom;
            this.padLeft = left;
            return this;
        }

        /** 设置主轴 gap。 */
        Case gap(int g) {
            this.gap = g;
            return this;
        }

        /** 追加一个子规格。 */
        Case child(Spec s) {
            this.specs.add(s);
            return this;
        }

        /** {@inheritDoc} 用于 @Parameterized.Parameters(name="{0}") 显示。 */
        @Override
        public String toString() {
            return name;
        }

        /**
         * 按规格建 SceneNode 树并填充 expectations。
         *
         * <p>COLUMN 容器自动 {@code setFillParentHeight(true)} 让容器吃约束高
         * （priorKnownInnerHeight 返回确定值）；ROW 容器靠约束下传宽驱动主轴分配。
         * 子节点为装饰叶（new SceneNode，无子无文本 content=0）。</p>
         */
        SceneNode build() {
            SceneNode container = isColumn ? SceneNode.column() : SceneNode.row();
            if (gap != 0) {
                container.setGap(gap);
            }
            if (padTop != 0 || padRight != 0 || padBottom != 0 || padLeft != 0) {
                container.setPadding(padTop, padRight, padBottom, padLeft);
            }
            // COLUMN 容器靠 fillParentHeight 吃约束高，驱动 grow 分配
            if (isColumn) {
                container.setFillParentHeight(true);
            }
            for (Spec s : specs) {
                SceneNode child = new SceneNode();
                if (s.flexGrow > 0) {
                    child.setFlexGrow(s.flexGrow);
                }
                if (s.fillParent) {
                    // COLUMN=fillParentHeight, ROW=fillParentWidth（隐式桥 effectiveGrow=1）
                    if (isColumn) {
                        child.setFillParentHeight(true);
                    } else {
                        child.setFillParentWidth(true);
                    }
                }
                if (s.preferredMain > 0) {
                    if (isColumn) {
                        child.setPreferredHeight(s.preferredMain);
                    } else {
                        child.setPreferredWidth(s.preferredMain);
                    }
                }
                if (s.maxMain > 0) {
                    if (isColumn) {
                        child.setMaxHeight(s.maxMain);
                    } else {
                        child.setMaxWidth(s.maxMain);
                    }
                }
                if (s.percentMain > 0) {
                    if (isColumn) {
                        child.setPercentHeight(s.percentMain);
                    } else {
                        child.setPercentWidth(s.percentMain);
                    }
                }
                if (s.marginStart != 0 || s.marginEnd != 0) {
                    // COLUMN: marginStart=marginTop, marginEnd=marginBottom
                    // ROW:    marginStart=marginLeft, marginEnd=marginRight
                    // setMargin(top, right, bottom, left)
                    if (isColumn) {
                        child.setMargin(s.marginStart, 0, s.marginEnd, 0);
                    } else {
                        child.setMargin(0, s.marginEnd, 0, s.marginStart);
                    }
                }
                container.appendChild(child);
                Expectation e = new Expectation();
                e.node = child;
                e.label = s.label;
                e.expectedMain = s.expectedMain;
                e.expectedPos = s.expectedPos;
                expectations.add(e);
            }
            return container;
        }

        /** 构造本场景的布局约束。 */
        Constraints toConstraints() {
            return new Constraints(availableWidth, availableHeight);
        }
    }

    /** 子节点规格 + 期望（链式构造）。主轴尺寸字段按 isColumn 自动映射到 Height/Width。 */
    static final class Spec {
        /** expectedPos 未设置哨兵。 */
        static final int UNSET = Integer.MIN_VALUE;

        final String label;
        int flexGrow = 0;
        boolean fillParent = false;
        int preferredMain = 0;
        int maxMain = 0;
        int percentMain = 0;
        int marginStart = 0;
        int marginEnd = 0;
        int expectedMain;
        int expectedPos = UNSET;

        Spec(String label) {
            this.label = label;
        }

        /** 设 flexGrow（显式权重）。 */
        Spec grow(int g) {
            this.flexGrow = g;
            return this;
        }

        /** 标记隐式 grow（COLUMN=fillParentHeight / ROW=fillParentWidth，effectiveGrow=1）。 */
        Spec fill() {
            this.fillParent = true;
            return this;
        }

        /** 设主轴 preferred（COLUMN=prefH / ROW=prefW，作 freeze 下界）。 */
        Spec pref(int p) {
            this.preferredMain = p;
            return this;
        }

        /** 设主轴 max（COLUMN=maxH / ROW=maxW，作 freeze 上界）。 */
        Spec max(int m) {
            this.maxMain = m;
            return this;
        }

        /** 设主轴 percent（COLUMN=percentH / ROW=percentW，作固定子）。 */
        Spec percent(int p) {
            this.percentMain = p;
            return this;
        }

        /** 设主轴 margin 起止（COLUMN=top/bottom, ROW=left/right）。 */
        Spec margin(int start, int end) {
            this.marginStart = start;
            this.marginEnd = end;
            return this;
        }

        /** 期望主轴尺寸。 */
        Spec expect(int main) {
            this.expectedMain = main;
            return this;
        }

        /** 期望主轴位置（COLUMN=y / ROW=x），用于 C7/C8/R7/R8 附加位置断言。 */
        Spec pos(int p) {
            this.expectedPos = p;
            return this;
        }
    }

    /** build 后填入的期望（持有 node 引用，供断言读 cachedLayout）。 */
    static final class Expectation {
        SceneNode node;
        String label;
        int expectedMain;
        int expectedPos;
    }
}
