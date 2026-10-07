package club.heiqi.uilib.ui.scene.node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.IntConsumer;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 逻辑声明与稳定身份，不持有布局结果；挂载后修改只经呈现运行时。 */
public final class SceneNode {
    private final UUID id = UUID.randomUUID();
    private final List<SceneNode> children = new ArrayList<>();
    private final List<NodeBinding> bindings = new ArrayList<>();
    private final Map<NodeBinding, Object> declarationState = new IdentityHashMap<>();
    private SceneControl control;
    public SceneControl control() { return control; }
    public SceneNode control(SceneControl value) { unmounted(); control = Objects.requireNonNull(value, "control"); return this; }
    private SceneNode parent;
    private SceneRuntime runtime;
    private boolean destroyed;
    public enum Axis { ROW, COLUMN }
    private Axis axis = Axis.COLUMN;
    private Size width = Size.natural(), height = Size.natural();
    private TextStyle style = new TextStyle("default", 12, 0xFFFFFFFF);
    private int padding, gap, scrollOffset, background;
    private boolean modal;
    private boolean clip, scrollable, visible = true;
    private boolean enabled = true;
    private String text = "";
    private Runnable click;
    private IntConsumer scrollAction;
    public UUID id() { return id; }
    public Axis axis() { return axis; }
    public Size width() { return width; }
    public Size height() { return height; }
    public TextStyle style() { return style; }
    public int padding() { return padding; }
    public int gap() { return gap; }
    public int scrollOffset() { return scrollOffset; }
    public int background() { return background; }
    public boolean clip() { return clip; }
    public boolean scrollable() { return scrollable; }
    public boolean visible() { return visible; }
    public boolean modal() { return modal; }
    public SceneNode modal(boolean value) { writable(); modal = value; changed(); return this; }
    public SceneNode axis(Axis value) { writable(); axis = Objects.requireNonNull(value, "axis"); changed(); return this; }
    public SceneNode size(Size width, Size height) { writable(); this.width = Objects.requireNonNull(width, "width"); this.height = Objects.requireNonNull(height, "height"); changed(); return this; }
    public SceneNode style(TextStyle style) { writable(); this.style = Objects.requireNonNull(style, "style"); changed(); return this; }
    public SceneNode padding(int value) { writable(); if (value < 0) throw new IllegalArgumentException("内边距不能为负"); padding = value; changed(); return this; }
    public SceneNode gap(int value) { writable(); if (value < 0) throw new IllegalArgumentException("间距不能为负"); gap = value; changed(); return this; }
    public SceneNode scrollable(boolean value) { writable(); scrollable = value; changed(); return this; }
    public SceneNode scrollOffset(int value) { writable(); if (value < 0) throw new IllegalArgumentException("滚动位置不能为负"); scrollOffset = value; changed(); return this; }
    public SceneNode clip(boolean value) { writable(); clip = value; changed(); return this; }
    public SceneNode visible(boolean value) { writable(); visible = value; changed(); return this; }
    public SceneNode background(int value) { writable(); background = value; if (runtime != null) runtime.invalidatePaint(); return this; }
    public SceneNode parent() { return parent; }
    public List<SceneNode> children() { return Collections.unmodifiableList(children); }
    public List<NodeBinding> bindings() { return Collections.unmodifiableList(bindings); }
    /** 逻辑声明状态属于节点本身，跨挂载保留，永久销毁清除。 */
    public Object declarationState(NodeBinding binding, Supplier<Object> initial) {
        writable(); return declarationState.computeIfAbsent(binding, key -> initial.get());
    }
    public boolean destroyed() { return destroyed; }
    public SceneRuntime runtime() { return runtime; }
    public String text() { return text; }
    public boolean enabled() { return enabled; }
    public Runnable clickAction() { return click; }
    public IntConsumer scrollAction() { return scrollAction; }
    public SceneNode onScroll(IntConsumer action) { writable(); scrollAction = action; return this; }
    public SceneNode text(String value) { writable(); text = Objects.requireNonNull(value, "text"); changed(); return this; }
    public SceneNode enabled(boolean value) { writable(); enabled = value; changed(); return this; }
    public SceneNode onClick(Runnable action) { writable(); click = action; return this; }
    public SceneNode bind(NodeBinding binding) { unmounted(); bindings.add(Objects.requireNonNull(binding, "binding")); return this; }
    public SceneNode add(SceneNode child) {
        writable(); child.writable();
        for (SceneNode cursor = this; cursor != null; cursor = cursor.parent)
            if (cursor == child) throw new IllegalArgumentException("节点不能包含自身");
        if (child.parent != null || child.runtime != null) throw new IllegalArgumentException("节点已有归属");
        children.add(child); child.parent = this; changed(); return this;
    }
    /** 框架写端口受运行时授权约束，业务 handler 无法直接改树。 */
    public void frameworkParent(SceneRuntime authority, SceneNode next) {
        authority.checkMutation();
        if (runtime != null && runtime != authority) throw new IllegalArgumentException("节点属于另一运行时");
        if (parent != null) parent.children.remove(this);
        parent = next;
        if (next != null) next.children.add(this);
    }
    public void frameworkRuntime(SceneRuntime authority, boolean attached) {
        authority.checkMutation();
        if (runtime != null && runtime != authority) throw new IllegalArgumentException("节点属于另一运行时");
        runtime = attached ? authority : null;
    }
    public void frameworkDestroy(SceneRuntime authority) {
        authority.checkMutation(); destroyed = true; control = null; declarationState.clear(); bindings.clear(); click = null; scrollAction = null; text = "";
    }
    private void unmounted() { if (destroyed || runtime != null) throw new IllegalStateException("节点不能修改挂载声明"); }
    private void writable() {
        if (destroyed) throw new IllegalStateException("节点已销毁");
        if (runtime != null) runtime.checkMutation();
    }
    private void changed() { if (runtime != null) runtime.invalidate(); }
}
