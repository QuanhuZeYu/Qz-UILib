package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;

/**
 * 新栈 ui.scene TextArea 多行文本输入 demo 宿主 Widget。
 *
 * <p>展示基础多行文本输入：Enter 换行、Backspace 跨行删除、方向键跨行移动 caret、
 * Home/End 行首行尾、点击定位、纵向滚动、placeholder。下方实时回显当前 value。</p>
 */
public class SceneTextAreaHostWidget extends AbstractSceneHostWidget {

    private static final int ROOT_BG = 0xFF0B1424;
    private static final int TITLE_COLOR = 0xFFC9D8F8;
    private static final int MUTED_COLOR = 0xFF8AA0C8;
    private static final int READOUT_BG = 0xFF1E293B;
    private static final int READOUT_TEXT = 0xFFEAF1FF;

    private final SceneNode root;
    private final Signal<String> textValue;

    /**
     * 创建 TextArea demo 宿主 Widget。
     *
     * @param inputSource 平台输入源，可为 null
     */
    public SceneTextAreaHostWidget(PlatformInputSource inputSource) {
        super(inputSource);

        this.root = new SceneNode();
        root.setFillParentHeight(true);
        root.setFlexDirection(FlexDirection.COLUMN);
        root.setGap(12);
        root.setPadding(20);
        root.setBackgroundColor(ROOT_BG);

        // 标题
        SceneNode title = new SceneNode();
        title.setText("TextArea 多行文本输入（Enter 换行 / 方向键跨行 / 滚轮滚动）");
        title.setTextColor(TITLE_COLOR);
        title.setHitTestable(false);
        root.appendChild(title);

        // TextArea
        this.textValue = Signal.create("第一行\n第二行\n第三行");
        SceneTextArea.Props props = new SceneTextArea.Props(
                textValue,
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                "请输入多行文本...",
                1024,
                160,
                next -> textValue.set(next));
        runtime.mount(root, SceneTextArea.create(runtime, props));

        // 实时回显：按行渲染（渲染层不按 \n 自动分行，单文本节点无法显示换行）
        SceneNode readoutLabel = new SceneNode();
        readoutLabel.setText("当前 value（实时回显）：");
        readoutLabel.setTextColor(MUTED_COLOR);
        readoutLabel.setHitTestable(false);
        root.appendChild(readoutLabel);

        SceneNode readout = new SceneNode();
        readout.setBackgroundColor(READOUT_BG);
        readout.setPadding(8);
        readout.setHitTestable(false);
        readout.setFlexDirection(FlexDirection.COLUMN);
        root.appendChild(readout);

        // 行号列表 signal：value 变化时重算行数
        Computed<List<Integer>> readoutRows = Computed.create(() -> {
            String v = nullSafe(textValue.get());
            int lines = 1;
            for (int i = 0; i < v.length(); i++) {
                if (v.charAt(i) == '\n') {
                    lines++;
                }
            }
            List<Integer> list = new ArrayList<>(lines);
            for (int i = 0; i < lines; i++) {
                list.add(Integer.valueOf(i));
            }
            return list;
        });
        runtime.forEach(readout, readoutRows, idx -> idx, rowIdx -> {
            SceneNode line = new SceneNode();
            line.setTextColor(READOUT_TEXT);
            line.setHitTestable(false);
            runtime.bindText(line, Computed.create(() -> rowLine(textValue.get(), rowIdx.intValue())));
            return line;
        });

        runtime.flush();
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    // ==================== 测试探针 ====================

    SceneRuntime __getRuntime() {
        return runtime;
    }

    SceneLayoutEngine __getLayoutEngine() {
        return layoutEngine;
    }

    ScenePaintEngine __getPaintEngine() {
        return paintEngine;
    }

    SceneNode __getRoot() {
        return root;
    }

    Signal<String> __getTextValue() {
        return textValue;
    }

    /** null 安全。 */
    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    /**
     * 取 value 第 rowIdx 行文本（按 \n 切分，保留空行）。
     */
    private static String rowLine(String value, int rowIdx) {
        String t = nullSafe(value);
        if (t.isEmpty()) {
            return "";
        }
        String[] lines = t.split("\n", -1);
        if (rowIdx < 0 || rowIdx >= lines.length) {
            return "";
        }
        return lines[rowIdx];
    }
}
