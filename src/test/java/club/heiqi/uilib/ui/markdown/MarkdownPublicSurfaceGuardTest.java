package club.heiqi.uilib.ui.markdown;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * markdown 接缝与 chat3 消费面「公共面守卫」——反射式全成员口径钉死（A1）。
 *
 * <p><b>与 {@code MarkdownLayerGuardTest} 的分工</b>：后者用源码文本扫描锁住「包内不得出现
 * GL11/AWT import、L2 顶层公共类型恰 1 个」；本锁用反射锁住「已经公开的成员到底有几个、是谁」。
 * 文本扫描数不清继承与合成成员，反射读不出注释里的意图——两面合起来才是公共面的完整真相。</p>
 *
 * <h2>口径史实：为什么必须有本锁（两把尺的旧账）</h2>
 *
 * <p>历史记账里同时存在两个数字，且它们是<b>两把不同的尺</b>：</p>
 * <ul>
 *   <li>「样式表公共方法恒 18」——规划《通用Markdown渲染器》§二之七 M7 注记（样式表公共面
 *       零膨胀 {@code 18→18}）与 §二之七·续 第 12 条回归对账（「样式表公共方法恒 18」），
 *       用的都是<b>纯方法数</b>：只数 {@code public} 方法，构造器与字段不进账。</li>
 *   <li>「{@code MarkdownLayoutLine} 公共成员维持 18」——同一条对账行的后半句，加上第 13 条
 *       M10d 的「接缝公共面 +1 读端（用户批准 18→<b>19</b>）」，用的是<b>全成员数</b>：
 *       public 方法 + public 构造器 + public 字段。</li>
 * </ul>
 *
 * <p>两把尺挨在同一行记账里，于是「样式表 18」与「行 19」看着像「行比样式表多一个成员」，
 * 真相却是<b>同一类人数的两把尺</b>：样式表按全成员尺也是 19（18 方法 + 1 构造器 + 0 字段）。
 * 后人拿 18 去对 19 必然打架。现状注记（C3b3）：样式表纯方法尺仍恒 18（零膨胀）；
 * 行按全成员尺 19→<b>20</b>（+{@code getHeadingLevel()} 一个读端）——两数各是各尺。</p>
 *
 * <p><b>本锁把尺统一为「全成员口径」，并逐类钉死分解</b>（方法 / 构造器 / 字段三个数分别相等，
 * 不只钉总数）。锚定值分解（基线 2026-09-06 {@code javap -public} 于 {@code build/classes/java/main}
 * 实测，JDK 8 与 JDK 25 双跑逐位一致；{@code MarkdownLayoutLine} 行已按 C3b3 +1 读端更新为
 * 20=18+1+1，仍为全成员尺——历史「样式表 18」是纯方法尺，两把尺不许混写）：</p>
 *
 * <table border="1">
 *   <caption>五锚定值 = 全成员数（public 方法 + public 构造器 + public 字段）</caption>
 *   <tr><th>类</th><th>全成员</th><th>public 方法</th><th>public 构造器</th><th>public 字段</th></tr>
 *   <tr><td>{@code MarkdownLayoutLine}</td><td>20</td><td>18</td><td>1</td><td>1（NO_BLOCK）
 *       （C3b3 +getHeadingLevel()：19→20，全成员尺）</td></tr>
 *   <tr><td>{@code MarkdownStyleTable}</td><td>19</td><td>18</td><td>1</td><td>0</td></tr>
 *   <tr><td>{@code ChatMessageList}</td><td>9</td><td>5</td><td>4</td><td>0</td></tr>
 *   <tr><td>{@code ChatSceneController}</td><td>26</td><td>22</td><td>4</td><td>0</td></tr>
 *   <tr><td>{@code ChatMarkdownPipeline}</td><td>0</td><td>0</td><td>0</td><td>0（类本身非 public）</td></tr>
 * </table>
 *
 * <p><b>计数细则（改本锁前必读，否则又会造出第三把尺）</b>：</p>
 * <ol>
 *   <li>只数<b>声明</b>成员：{@code getDeclaredMethods()} / {@code getDeclaredFields()} 过滤
 *       {@code Modifier.isPublic}，构造器用 {@code getConstructors()}（本就是 public-only，
 *       且构造器不继承，与「getDeclared + isPublic 过滤」等价）。
 *       <b>类声明行本身不计</b>——{@code public class Foo} 不是一个成员。</li>
 *   <li>{@code getDeclaredClasses()} <b>一律不计</b>（见
 *       {@link #publicNestedTypesMustNotEnterTheCount()}）。{@code MarkdownLayoutLine.Kind}
 *       是 public 嵌套枚举，但 20（全成员尺）里没有它的位置——给枚举加常量（C3b3 的
 *       {@code HEADING}）只动「枚举常量计数」哨兵，不动成员账。</li>
 *   <li>「继承自 Object 的 public 方法如 {@code toString}」只在本类<b>显式覆写</b>时才进账——
 *       {@code getDeclaredMethods()} 只返回本类声明的方法。{@code MarkdownLayoutLine} 覆写了
 *       {@code toString}（18 个方法含它——C3b3 后全成员尺的方法分解）；
 *       {@code ChatMessageList}/{@code ChatSceneController}/
 *       {@code MarkdownStyleTable} 未覆写，故不因其 +1。这与 {@code javap -public} 同口径，
 *       不是第三把尺。</li>
 *   <li>不额外排除 {@code synthetic}/bridge 成员（{@code javap -public} 也不排除）。若将来出现
 *       public 合成成员（泛型桥等），锚定值随之 +1——那确实是一次公共面变化，须独立裁定。</li>
 * </ol>
 *
 * <h2>为什么只有一个测试类、且五个主体全走 {@code Class.forName}</h2>
 *
 * <p>被点的类可见性不齐：{@code ChatMarkdownPipeline} 是 package-private final（其源码头注释明言
 * 「块模型与 L1/L2 类型不外泄」），在别的包里只能按名字反射拿。本锁只<b>列成员、读修饰符</b>，
 * 从不 invoke、从不读字段值，因此不需要 {@code setAccessible}、也不需要与被测类同包。据此：
 * ① 一个测试类 = 一套计数函数 = 一把尺；拆两类必然要么复制计数、要么各自演化，正是上面那段
 * 「两把尺」旧账的成因，不再重演；② 五类统一 {@code Class.forName}，不出现「直引的走 A 路径、
 * 反射的走 B 路径」的口径分叉；③ 落点选 {@code ui/markdown} 守卫族，与既有
 * {@code MarkdownLayerGuardTest} 同处，公共面锁不外散进 chat3 测试包。</p>
 *
 * <p>纯 JVM 反射：零字体注册、零 GL、不启动场景，不掺 {@code Playground*} 的跨类字体顺序耦合。</p>
 */
public class MarkdownPublicSurfaceGuardTest {

    // ==================== 锚定类名 ====================

    /** L1 块身份行接缝（M7 方案乙引入的唯一新增公共类型）。 */
    private static final String LAYOUT_LINE =
            "club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine";

    /** L1 块级样式/排版表（D3 最小公共面落点）。 */
    private static final String STYLE_TABLE =
            "club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable";

    /** L3 聊天气泡列表（chat3 视图）。 */
    private static final String MESSAGE_LIST =
            "club.heiqi.uilib.internal.chat3.view.ChatMessageList";

    /** L3 聊天场景控制器（chat3 视图）。 */
    private static final String SCENE_CONTROLLER =
            "club.heiqi.uilib.internal.chat3.view.ChatSceneController";

    /** chat3 markdown 唯一入口：package-private final，公共面恒 0。 */
    private static final String PIPELINE =
            "club.heiqi.uilib.internal.chat3.view.ChatMarkdownPipeline";

    /** 正对照一（JDK 稳定哨兵）：覆盖方法 + 构造器两条计数路径。 */
    private static final String JDK_SENTINEL = "java.lang.Object";

    /** 正对照二（仓内哨兵）：{@code MarkdownLayoutLine} 的 public 嵌套枚举，覆盖字段计数路径。 */
    private static final String NESTED_ENUM_SENTINEL =
            "club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine$Kind";

    // ==================== 锚定值（全成员口径；分解 = 方法 / 构造器 / 字段）====================

    /** {@code MarkdownLayoutLine} = 20（18 方法 + 1 构造器 + 1 字段 {@code NO_BLOCK}；
     *  C3b3 接缝标题身份 +1 读端 getHeadingLevel()，全成员尺 19→20，用户裁定见任务书 B）。 */
    private static final int LAYOUT_LINE_TOTAL = 20;
    private static final int LAYOUT_LINE_METHODS = 18;
    private static final int LAYOUT_LINE_CONSTRUCTORS = 1;
    private static final int LAYOUT_LINE_FIELDS = 1;

    /** {@code MarkdownStyleTable} = 19（18 方法 + 1 构造器 + 0 字段）；旧账「18」是纯方法尺。 */
    private static final int STYLE_TABLE_TOTAL = 19;
    private static final int STYLE_TABLE_METHODS = 18;
    private static final int STYLE_TABLE_CONSTRUCTORS = 1;
    private static final int STYLE_TABLE_FIELDS = 0;

    /** {@code ChatMessageList} = 9（5 方法 + 4 构造器 + 0 字段）。 */
    private static final int MESSAGE_LIST_TOTAL = 9;
    private static final int MESSAGE_LIST_METHODS = 5;
    private static final int MESSAGE_LIST_CONSTRUCTORS = 4;
    private static final int MESSAGE_LIST_FIELDS = 0;

    /** {@code ChatSceneController} = 26（22 方法 + 4 构造器 + 0 字段）。 */
    private static final int SCENE_CONTROLLER_TOTAL = 26;
    private static final int SCENE_CONTROLLER_METHODS = 22;
    private static final int SCENE_CONTROLLER_CONSTRUCTORS = 4;
    private static final int SCENE_CONTROLLER_FIELDS = 0;

    /** {@code ChatMarkdownPipeline} = 0（类本身 package-private，成员面全为包内/私有）。 */
    private static final int PIPELINE_TOTAL = 0;
    private static final int PIPELINE_METHODS = 0;
    private static final int PIPELINE_CONSTRUCTORS = 0;
    private static final int PIPELINE_FIELDS = 0;

    /**
     * 五类合计地板（反空跑）。实测合计 = 20 + 19 + 9 + 26 + 0 = <b>74</b>（C3b3 后；
     * 均为全成员尺）；地板取 50 不动——地板的唯一职责是「计数函数被写成恒 0 时当场红」，
     * 精确性由各类的相等断言负责。
     */
    private static final int TOTAL_SURFACE_FLOOR = 50;

    /** 公共 10 参构造器的参数个数（M7 起签名冻结，C3b3 加标题级别也未动；带链/带级别写端
     *  构造器 13 参，必须非 public）。 */
    private static final int PUBLIC_CTOR_ARITY = 10;

    /** M10d 新增、经用户批准的接缝公共读端（计数清单里的形态：方法名/参数个数）。 */
    private static final String LIST_MARKER_CHAIN_GETTER = "getListMarkerChain/0";

    /** C3b3 新增、经任务书 B 批准的接缝标题级别读端（19→20 全成员尺的唯一增量）。 */
    private static final String HEADING_LEVEL_GETTER = "getHeadingLevel/0";

    // ==================== 每类精确相等（全成员 + 三项分解）====================

    /** {@code MarkdownLayoutLine} 锚定 20 = 18 方法 + 1 构造器 + 1 字段（全成员尺；
     *  C3b3 起 17→18 方法，多的是 {@code getHeadingLevel()}）。 */
    @Test
    public void markdownLayoutLinePublicSurfaceIsAnchoredAt20() {
        assertSurface(publicSurface(load(LAYOUT_LINE)), LAYOUT_LINE_TOTAL, LAYOUT_LINE_METHODS,
                LAYOUT_LINE_CONSTRUCTORS, LAYOUT_LINE_FIELDS);
    }

    /** {@code MarkdownStyleTable} 锚定 19 = 18 方法 + 1 构造器 + 0 字段（旧账「18」是纯方法尺）。 */
    @Test
    public void markdownStyleTablePublicSurfaceIsAnchoredAt19() {
        assertSurface(publicSurface(load(STYLE_TABLE)), STYLE_TABLE_TOTAL, STYLE_TABLE_METHODS,
                STYLE_TABLE_CONSTRUCTORS, STYLE_TABLE_FIELDS);
    }

    /** {@code ChatMessageList} 锚定 9 = 5 方法 + 4 构造器（重载链 parser / +measurer / +postProcessor / +wrap）。 */
    @Test
    public void chatMessageListPublicSurfaceIsAnchoredAt9() {
        assertSurface(publicSurface(load(MESSAGE_LIST)), MESSAGE_LIST_TOTAL, MESSAGE_LIST_METHODS,
                MESSAGE_LIST_CONSTRUCTORS, MESSAGE_LIST_FIELDS);
    }

    /** {@code ChatSceneController} 锚定 26 = 22 方法 + 4 构造器。 */
    @Test
    public void chatSceneControllerPublicSurfaceIsAnchoredAt26() {
        assertSurface(publicSurface(load(SCENE_CONTROLLER)), SCENE_CONTROLLER_TOTAL,
                SCENE_CONTROLLER_METHODS, SCENE_CONTROLLER_CONSTRUCTORS, SCENE_CONTROLLER_FIELDS);
    }

    /**
     * {@code ChatMarkdownPipeline} 锚定 0，<b>且类本身非 public</b>（结构钉，不只看数）。
     *
     * <p>「0」有两种成因：真相是包内不外泄，或者反射根本没扫到成员（类名写错、拿到空壳）。
     * 本锁同时钉类修饰符与「声明成员地板」，把第二种成因堵死。</p>
     */
    @Test
    public void chatMarkdownPipelineHasZeroPublicMembersAndIsNotPublic() {
        Class<?> type = load(PIPELINE);
        assertSurface(publicSurface(type), PIPELINE_TOTAL, PIPELINE_METHODS,
                PIPELINE_CONSTRUCTORS, PIPELINE_FIELDS);
        Assert.assertFalse("ChatMarkdownPipeline 的类本身必须非 public（源码头注释：块模型与 L1/L2 "
                + "类型不外泄；一旦放开，包外可直接持有唯一入口）: modifiers="
                + Modifier.toString(type.getModifiers()), Modifier.isPublic(type.getModifiers()));
        int declared = type.getDeclaredMethods().length + type.getDeclaredConstructors().length
                + type.getDeclaredFields().length;
        // 实测数随生产码演进（javap -p 逐条数得，非心算）：C6b 时 20 = 15 方法 + 1 构造器 + 4 字段；
        // C7 划界删掉 § → span 输入转换器 toSpanStream 与其 flushSpan 助手 ⇒ 19 = 14 + 1 + 4。
        // 地板 15 不动——它的职责只是「反射没扫到位就当场红」。
        Assert.assertTrue("反空跑：ChatMarkdownPipeline 声明成员（含非 public）实测 19"
                + "（14 方法 + 1 构造器 + 4 字段；C7 删 toSpanStream/flushSpan 前是 20），地板取 15；"
                + "低于地板说明反射没真扫到这个类，上面那个「0 public 成员」就成了假绿: 实到声明成员 "
                + declared, declared >= 15);
    }

    // ==================== 总量地板（防恒真）====================

    /** 五类合计 &gt; 0 且不低于地板：计数函数被写成恒 0 时，本条与上面的相等断言一起红。 */
    @Test
    public void fiveClassPublicSurfaceHasNonZeroFloor() {
        String[] names = { LAYOUT_LINE, STYLE_TABLE, MESSAGE_LIST, SCENE_CONTROLLER, PIPELINE };
        int total = 0;
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            Surface surface = publicSurface(load(names[i]));
            total += surface.total;
            detail.append("\n  ").append(surface.oneLine());
        }
        Assert.assertTrue("五个锚定类的 public 成员合计必须 > 0（恒 0 = 计数函数失效）: 合计 "
                + total + detail, total > 0);
        Assert.assertTrue("合计地板（实测 74，全成员尺，用途见 TOTAL_SURFACE_FLOOR 注释）: 合计 " + total
                + detail, total >= TOTAL_SURFACE_FLOOR);
    }

    // ==================== 正对照：同一把尺在已知非零哨兵上必须报出非零 ====================

    /**
     * 正对照一：{@code java.lang.Object} 走同一计数函数必须是 10 = 9 public 方法 + 1 public
     * 构造器 + 0 public 字段（{@code javap -public} 在 JDK 8 与 JDK 25 双跑逐位相同）。
     *
     * <p>这把「计数函数不是恒 0」钉成「计数函数还数得对」——只断言 &gt;0 的话，
     * 一个「所有成员一律算成 1」的 bug 照样绿。</p>
     */
    @Test
    public void counterMustNotBeHardwiredToZeroOnJdkSentinel() {
        Surface surface = publicSurface(load(JDK_SENTINEL));
        Assert.assertEquals("正对照：java.lang.Object 的 public 成员合计必须恰为 10（9 方法 + 1 构造器）"
                + "，实到 " + surface.describe(), 10, surface.total);
        Assert.assertEquals("正对照：Object public 方法数（getClass/hashCode/equals/toString/notify/"
                + "notifyAll/wait×3；clone 与 finalize 是 protected，不入账）: " + surface.describe(),
                9, surface.methods);
        Assert.assertEquals("正对照：Object public 构造器数: " + surface.describe(), 1,
                surface.constructors);
        Assert.assertEquals("正对照：Object public 字段数必须为 0: " + surface.describe(), 0,
                surface.fields);
    }

    /**
     * 正对照二：{@code MarkdownLayoutLine$Kind}（public 嵌套枚举）字段路径必须报出 &ge; 5 个
     * 常量（TEXT/HEADING/LIST/CODE/THEMATIC_BREAK——C3b3 起含 HEADING），方法路径 &ge; 2
     * （{@code values()}/{@code valueOf(String)}）。
     *
     * <p>Object 的字段数是 0，证明不了字段路径没坏；本哨兵专补这一条。C3b3 起它还兼任
     * <b>枚举常量计数的正对照</b>：Kind 从 4 常量到 5 常量（加 HEADING）是嵌套类型演进、
     * 不进全成员账（细则 2），但字段计数路径必须看得见这次增长——下限随之 4→5。
     * 方法与字段都用下限而非精确值——再加一个常量仍是合法演进，不该让公共面守卫为它红。</p>
     */
    @Test
    public void counterFieldPathMustNotBeHardwiredToZeroOnNestedEnumSentinel() {
        Surface surface = publicSurface(load(NESTED_ENUM_SENTINEL));
        Assert.assertTrue("正对照：字段计数路径必须报出 >= 5 个 public 枚举常量（C3b3 起含 HEADING），实到 "
                + surface.describe(), surface.fields >= 5);
        Assert.assertTrue("正对照：枚举方法路径必须报出 >= 2（values()/valueOf），实到 "
                + surface.describe(), surface.methods >= 2);
        Assert.assertTrue("正对照：哨兵合计必须 > 0，实到 " + surface.describe(), surface.total > 0);
    }

    // ==================== 结构钉（不只数数）====================

    /**
     * 结构钉 a：{@code MarkdownLayoutLine} 的 public 构造器<b>恰好 1 个、参数个数 10</b>
     * （M7 冻结，C3b3 未动）；带链写端（C3b3 起 13 参全字段构造器，多出的三项是
     * {@code blockContentWidthPx}、{@code headingLevel} 与 {@code listMarkerChain}）
     * 必须保持 package-private。
     *
     * <p>这是 M10d 的写端收口（C3b3 同款口径延用到标题级别）：链与级别都是 L1（同包
     * {@code MarkdownDocument}）装配期事实，公共面只加读端 {@code getListMarkerChain()}/
     * {@code getHeadingLevel()}，不为写端扩构造器重载族。哪天有人把 13 参全参构造器放开
     * public，包外即可自造带链/带级别行、绕开 L1 装配与 L2 度量——本钉当场红。</p>
     */
    @Test
    public void layoutLineChainWriteCtorMustStayPackagePrivate() {
        Class<?> type = load(LAYOUT_LINE);
        Constructor<?>[] declared = type.getDeclaredConstructors();
        List<Integer> publicArities = new ArrayList<Integer>();
        List<String> widerThanPublic = new ArrayList<String>();
        for (int i = 0; i < declared.length; i++) {
            int arity = declared[i].getParameterTypes().length;
            boolean isPublic = Modifier.isPublic(declared[i].getModifiers());
            if (isPublic) {
                publicArities.add(Integer.valueOf(arity));
            }
            if (arity > PUBLIC_CTOR_ARITY) {
                widerThanPublic.add(arity + " 参(" + Modifier.toString(declared[i].getModifiers()) + ")");
                Assert.assertFalse("写端收口被破坏：" + arity + " 参构造器宽于公共冻结签名 "
                        + PUBLIC_CTOR_ARITY + " 参却已是 public——包外可自造带链/带块宽的行，"
                        + "绕过 L1 装配与 L2 度量。全部声明构造器 = " + describeConstructors(type),
                        isPublic);
            }
        }
        Assert.assertEquals("MarkdownLayoutLine 的 public 构造器必须恰好 1 个（M7 起冻结的 10 参签名；"
                + "加读端不加构造器，要加构造器重载族须独立裁定）: " + describeConstructors(type),
                1, publicArities.size());
        Assert.assertEquals("唯一的 public 构造器必须是 10 参签名: " + describeConstructors(type),
                PUBLIC_CTOR_ARITY, publicArities.get(0).intValue());
        Assert.assertTrue("反空跑：必须真的存在比公共签名更宽的声明构造器（带链写端），否则上面的"
                + "「写端非 public」是空转: " + describeConstructors(type), !widerThanPublic.isEmpty());
    }

    /**
     * 结构钉 b：public 方法名集合必须含 {@code getListMarkerChain()}——M10d（2026-09-05 追加裁定
     * 「做全」）经用户批准进入公共面的读端。少它 = 读端被悄悄摘掉（18→19 的裁定作废）。
     */
    @Test
    public void layoutLineMustExposeListMarkerChainReadEnd() {
        Surface surface = publicSurface(load(LAYOUT_LINE));
        Assert.assertTrue("public 方法清单必须含 M10d 读端 getListMarkerChain()（列表正文列的唯一"
                + "显式载体：几何不编码进可见文本）: 实到 " + surface.describe(),
                surface.inventory.contains(LIST_MARKER_CHAIN_GETTER));
    }

    /**
     * 结构钉 d（C3b3）：public 方法名集合必须含 {@code getHeadingLevel()}——标题身份进接缝的
     * 唯一级别读端（全成员尺 19→20 的那 +1）。少它 = 标题级别只活在包内块模型、接缝照旧丢身份。
     */
    @Test
    public void layoutLineMustExposeHeadingLevelReadEnd() {
        Surface surface = publicSurface(load(LAYOUT_LINE));
        Assert.assertTrue("public 方法清单必须含 C3b3 读端 getHeadingLevel()（HEADING 行级别 1..6，"
                + "其余 kind 恒 0）: 实到 " + surface.describe(),
                surface.inventory.contains(HEADING_LEVEL_GETTER));
    }

    /** 结构钉 c：public 嵌套类型（{@code Kind}）一律不计入成员数。 */
    @Test
    public void publicNestedTypesMustNotEnterTheCount() {
        Class<?> type = load(LAYOUT_LINE);
        Class<?>[] declaredClasses = type.getDeclaredClasses();
        List<String> publicNested = new ArrayList<String>();
        for (int i = 0; i < declaredClasses.length; i++) {
            if (Modifier.isPublic(declaredClasses[i].getModifiers())) {
                publicNested.add(declaredClasses[i].getSimpleName());
            }
        }
        Assert.assertTrue("反空跑：MarkdownLayoutLine 确有 public 嵌套类型（Kind），"
                + "「不计嵌套类」这条细则才算被检验到: " + publicNested, !publicNested.isEmpty());
        Surface surface = publicSurface(type);
        Assert.assertEquals("全成员口径的 19 里没有嵌套类型的位置（19 = 17 方法 + 1 构造器 + 1 字段）；"
                + "哪天把 getDeclaredClasses 也计入，本条会与锚定值一起红。public 嵌套类 = "
                + publicNested, LAYOUT_LINE_TOTAL, surface.total);
    }

    // ==================== 计数实现（唯一一把尺）====================

    /** 一次反射计数的结果：总数 + 三项分解 + 排序后的成员清单（红了能直接看出多了谁）。 */
    private static final class Surface {
        private final String type;
        private final int methods;
        private final int constructors;
        private final int fields;
        private final int total;
        private final List<String> inventory;

        Surface(String type, int methods, int constructors, int fields, List<String> inventory) {
            this.type = type;
            this.methods = methods;
            this.constructors = constructors;
            this.fields = fields;
            this.total = methods + constructors + fields;
            this.inventory = inventory;
        }

        /** 单行摘要：合计与分解。 */
        String oneLine() {
            return shortName() + " = " + total + "（方法 " + methods + " + 构造器 " + constructors
                    + " + 字段 " + fields + "）";
        }

        /** 完整描述：摘要 + 排序后的成员清单。 */
        String describe() {
            return oneLine() + " 成员[" + String.join(", ", inventory) + "]";
        }

        private String shortName() {
            int dot = type.lastIndexOf('.');
            return dot < 0 ? type : type.substring(dot + 1);
        }
    }

    /**
     * 全成员口径的唯一实现：{@code getDeclaredMethods} 过滤 public + {@code getConstructors}
     * （public-only，构造器不继承）+ {@code getDeclaredFields} 过滤 public。
     * {@code getDeclaredClasses} 与类声明行本身都不计入。
     */
    private static Surface publicSurface(Class<?> type) {
        List<String> inventory = new ArrayList<String>();
        int methods = 0;
        Method[] declaredMethods = type.getDeclaredMethods();
        for (int i = 0; i < declaredMethods.length; i++) {
            if (Modifier.isPublic(declaredMethods[i].getModifiers())) {
                methods++;
                inventory.add(declaredMethods[i].getName() + "/"
                        + declaredMethods[i].getParameterTypes().length);
            }
        }
        int constructors = 0;
        Constructor<?>[] publicConstructors = type.getConstructors();
        for (int i = 0; i < publicConstructors.length; i++) {
            constructors++;
            inventory.add("<init>/" + publicConstructors[i].getParameterTypes().length);
        }
        int fields = 0;
        Field[] declaredFields = type.getDeclaredFields();
        for (int i = 0; i < declaredFields.length; i++) {
            if (Modifier.isPublic(declaredFields[i].getModifiers())) {
                fields++;
                inventory.add(declaredFields[i].getName());
            }
        }
        Collections.sort(inventory);
        return new Surface(type.getName(), methods, constructors, fields, inventory);
    }

    /** 总数与三项分解一起钉：只钉总数会漏掉「方法 +1 同时字段 -1」这种抵消式漂移。 */
    private static void assertSurface(Surface surface, int expectedTotal, int expectedMethods,
            int expectedConstructors, int expectedFields) {
        Assert.assertEquals("[" + surface.type + "] public 成员总数（全成员口径）: "
                + surface.describe(), expectedTotal, surface.total);
        Assert.assertEquals("[" + surface.type + "] public 方法数（分解钉：防总数不变而构成变了）: "
                + surface.describe(), expectedMethods, surface.methods);
        Assert.assertEquals("[" + surface.type + "] public 构造器数: " + surface.describe(),
                expectedConstructors, surface.constructors);
        Assert.assertEquals("[" + surface.type + "] public 字段数: " + surface.describe(),
                expectedFields, surface.fields);
    }

    /** 按名字加载锚定类；改名/删除都属公共面事件，本锁必须红而不是静默跳过。 */
    private static Class<?> load(String className) {
        try {
            return Class.forName(className, false, MarkdownPublicSurfaceGuardTest.class
                    .getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError("锚定类无法加载（被改名/删除/移动，或本锁里名字写错）: " + className
                    + " —— 若属有意的公共面调整，同步改本锁锚定值并在规划文档记一笔", e);
        }
    }

    /** 全部声明构造器（含非 public）的可读清单，供构造器相关断言的失败信息使用。 */
    private static String describeConstructors(Class<?> type) {
        Constructor<?>[] declared = type.getDeclaredConstructors();
        List<String> shapes = new ArrayList<String>();
        for (int i = 0; i < declared.length; i++) {
            shapes.add(Modifier.toString(declared[i].getModifiers()) + " "
                    + type.getSimpleName() + "(" + declared[i].getParameterTypes().length + " 参)");
        }
        Collections.sort(shapes);
        return "[" + String.join(" | ", shapes) + "]";
    }
}
