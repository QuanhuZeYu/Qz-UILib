package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.MountHandle;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.control.SceneDataTable;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 新栈 ui.scene DataTable demo 宿主 Widget。
 *
 * <p>该宿主只负责独立页面壳、标题说明和 render pipeline，表格行数据由 {@link Signal} 受控驱动，
 * 可编辑单元格经 {@link SceneDataTable} 内部回调写回同一行列表。</p>
 */
public class SceneDataTableHostWidget extends AbstractSceneHostWidget {

    /** 表格视口固定高度（像素），用于真机验收纵向滚动、裁剪与 overlay anchor 跟随滚动。 */
    private static final int VIEWPORT_HEIGHT = 168;
    /** 表格固定行高（像素）。 */
    private static final int ROW_HEIGHT = 30;
    /** 标题文字颜色。 */
    private static final int TITLE_TEXT_COLOR = 0xFFC9D8F8;
    /** 帮助文字颜色。 */
    private static final int HELP_TEXT_COLOR = 0xFF8AA0C8;

    private final SceneNode root;
    private final SceneNode tableRoot;
    private final SceneNode viewport;
    private final SceneNode content;
    private final Signal<List<SceneDataTable.Row>> rows;
    private final MountHandle tableHandle;

    /**
     * 创建 DataTable demo 宿主 Widget，注入平台输入源。
     *
     * @param inputSource 平台输入源，可为 null（退化模式，无真机滚轮）
     */
    public SceneDataTableHostWidget(PlatformInputSource inputSource) {
        super(inputSource);
        this.rows = Signal.create(createRows());

        this.root = new SceneNode();
        root.setFillParentHeight(true);
        root.setFlexDirection(FlexDirection.COLUMN);
        root.setGap(12);
        root.setPadding(20);

        SceneNode title = new SceneNode();
        title.setText("Scene DataTable demo：text / textInput / select 混合列 + 行内编辑 + 滚动锚点");
        title.setTextColor(TITLE_TEXT_COLOR);
        title.setHitTestable(false);
        root.appendChild(title);

        SceneNode helper = new SceneNode();
        helper.setText("点击名称/描述编辑文本，点击类型下拉选择 A/B/C；滚动视口后展开 Select 可验证 overlay anchor 跟随。");
        helper.setTextColor(HELP_TEXT_COLOR);
        helper.setHitTestable(false);
        root.appendChild(helper);

        this.tableHandle = runtime.mount(root, SceneDataTable.create(runtime, createTableProps()));
        this.tableRoot = tableHandle.getRoot();
        this.viewport = tableRoot.__getChildren().get(0);
        this.content = viewport.__getChildren().get(0);

        runtime.flush();
    }

    /**
     * 创建 DataTable demo 输入数据。
     *
     * @return DataTable 输入契约
     */
    private SceneDataTable.Props createTableProps() {
        List<SceneDataTable.Column> columns = Arrays.asList(
                SceneDataTable.Column.text("序号", 56),
                SceneDataTable.Column.textInput("名称", 112),
                SceneDataTable.Column.select("类型", 82, Arrays.asList("A", "B", "C")),
                SceneDataTable.Column.textInput("描述", 220));
        return new SceneDataTable.Props(rows, columns, ROW_HEIGHT, VIEWPORT_HEIGHT);
    }

    /**
     * 创建受控行示例数据。
     *
     * @return 不可变行列表
     */
    private static List<SceneDataTable.Row> createRows() {
        List<SceneDataTable.Row> demoRows = new ArrayList<SceneDataTable.Row>();
        demoRows.add(row("#1", "主武器", "A", "点击编辑名称或描述"));
        demoRows.add(row("#2", "副武器", "B", "滚动后展开类型，观察下拉锚点"));
        demoRows.add(row("#3", "护甲", "C", "这是一段较长描述，用于观察输入列固定宽度内的裁剪"));
        demoRows.add(row("#4", "药水", "A", "Select 选项：A / B / C"));
        demoRows.add(row("#5", "材料", "B", "行数据由 Signal<List<Row>> 受控驱动"));
        demoRows.add(row("#6", "宝石", "C", "编辑后会写回同一 rowId 的新 Row"));
        demoRows.add(row("#7", "卷轴", "A", "用于撑出滚动范围"));
        demoRows.add(row("#8", "钥匙", "B", "最后一行边界与裁剪检查"));
        return Collections.unmodifiableList(demoRows);
    }

    /**
     * 创建单行数据。
     *
     * @param index 序号文本
     * @param name 名称文本
     * @param type 类型文本
     * @param description 描述文本
     * @return DataTable 行数据
     */
    private static SceneDataTable.Row row(String index, String name, String type, String description) {
        return new SceneDataTable.Row(Arrays.asList(index, name, type, description));
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** 回收资源：卸载 DataTable 组件并释放 runtime 作用域。 */
    @Override
    public void dispose() {
        tableHandle.dispose();
        super.dispose();
    }

    /** @return 内部场景运行时 */
    SceneRuntime __getRuntime() {
        return runtime;
    }

    /** @return 内部布局引擎 */
    SceneLayoutEngine __getLayoutEngine() {
        return layoutEngine;
    }

    /** @return 场景树根节点 */
    SceneNode __getRoot() {
        return root;
    }

    /** @return SceneDataTable 组件根节点 */
    SceneNode __getTableRoot() {
        return tableRoot;
    }

    /** @return DataTable 视口节点 */
    SceneNode __getViewport() {
        return viewport;
    }

    /** @return DataTable 内容容器节点 */
    SceneNode __getContent() {
        return content;
    }

    /** @return 受控行数据源 */
    Signal<List<SceneDataTable.Row>> __getRows() {
        return rows;
    }

    /** @return 视口固定高度常量 */
    static int __getViewportHeight() {
        return VIEWPORT_HEIGHT;
    }
}
