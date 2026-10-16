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
import java.util.function.BooleanSupplier;
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

    public SceneControl control() {
        return control;
    }

    public SceneNode control(SceneControl value) {
        unmounted();
        control = Objects.requireNonNull(value, "control");
        return this;
    }

    private SceneNode parent;
    private SceneRuntime runtime;
    private boolean destroyed;
    private Object logicalOwner;
    private boolean logicalOwned, transferred;
    private final List<AutoCloseable> resources = new ArrayList<>();

    /** 逻辑资源跨卸载保留，仅永久销毁释放；外部共享资源不隐含归属。 */
    public SceneNode own(AutoCloseable resource) {
        writable();
        resources.add(Objects.requireNonNull(resource, "resource"));
        return this;
    }

    public Object logicalOwner() {
        return logicalOwner;
    }

    /** 声明拥有权独立于挂载 Owner，明确移动时转移。 */
    public void frameworkOwnership(SceneRuntime authority, Object value) {
        frameworkOwnership(authority, value, value != null);
    }

    public void frameworkOwnership(SceneRuntime authority, Object value, boolean owned) {
        authority.checkMutation();
        logicalOwner = value;
        logicalOwned = owned;
    }

    public boolean transferred() {
        return transferred;
    }

    public void frameworkTransfer(SceneRuntime authority, SceneNode next) {
        frameworkOwnership(authority, next);
        transferred = true;
    }

    /** 释放卸载保留的逻辑树；挂载树须由正式运行时结束挂载。 */
    public void destroy() {
        if (destroyed) return;
        if (runtime != null) throw new IllegalStateException("挂载节点应经运行时销毁");
        destroyed = true;
        Throwable failure = null;
        for (SceneNode child : new ArrayList<>(children)) {
            child.parent = null;
            if (child.logicalOwned) {
                try {
                    child.destroy();
                } catch (Throwable error) {
                    failure = cleanupFailure(failure, error);
                }
            }
        }
        children.clear();
        if (parent != null) parent.children.remove(this);
        parent = null;
        logicalOwner = null;
        for (int i = resources.size() - 1; i >= 0; i--) {
            try {
                resources.get(i).close();
            } catch (Throwable error) {
                failure = cleanupFailure(failure, error);
            }
        }
        resources.clear();
        control = null;
        initialFocus = null;
        restoreFocus = null;
        declarationState.clear();
        bindings.clear();
        click = null;
        scrollAction = null;
        escapeAction = null;
        text = "";
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure != null) throw new IllegalStateException("节点逻辑资源释放失败: " + id, failure);
    }

    private static Throwable cleanupFailure(Throwable failure, Throwable error) {
        if (failure == null) return error;
        if (failure != error) failure.addSuppressed(error);
        return failure;
    }

    public enum Axis {
        ROW,
        COLUMN
    }

    private Axis axis = Axis.COLUMN;
    private Size width = Size.natural(), height = Size.natural();
    private TextStyle style = new TextStyle("default", 12, 0xFFFFFFFF);
    private club.heiqi.uilib.ui.scene.text.TextPolicy textPolicy =
            club.heiqi.uilib.ui.scene.text.TextPolicy.NORMAL;

    public club.heiqi.uilib.ui.scene.text.TextPolicy textPolicy() {
        return textPolicy;
    }

    public SceneNode textPolicy(club.heiqi.uilib.ui.scene.text.TextPolicy value) {
        writable();
        textPolicy = Objects.requireNonNull(value, "textPolicy");
        changed();
        return this;
    }

    private int padding, gap, scrollOffset, background;
    private int scrollStep = 20;
    private boolean modal;
    private boolean clip, scrollable, visible = true;
    private boolean enabled = true;
    private boolean clickFocus;

    public boolean clickFocus() {
        return clickFocus;
    }

    /** 显式点击聚焦声明；标准按钮启用，普通节点保持原策略。 */
    public SceneNode clickFocus(boolean value) {
        writable();
        clickFocus = value;
        return this;
    }

    private boolean focusable;
    private SceneNode initialFocus, restoreFocus;

    /** 页面根或模态的进入目标；null使用框架逻辑顺序，不在更新声明时抢焦点。 */
    public SceneNode initialFocus() {
        return initialFocus;
    }

    public SceneNode initialFocus(SceneNode target) {
        writable();
        initialFocus = target;
        return this;
    }

    /** 返回该范围时优先核验的声明目标；无效时核验原挂载焦点，再使用初始策略。 */
    public SceneNode restoreFocus() {
        return restoreFocus;
    }

    public SceneNode restoreFocus(SceneNode target) {
        writable();
        restoreFocus = target;
        return this;
    }

    /** 是否声明参与焦点；默认false，资格还受当前挂载及输入范围约束。 */
    public boolean focusable() {
        return focusable;
    }

    /** 挂载后通过绑定更新，不在输入handler直接改声明。 */
    public SceneNode focusable(boolean value) {
        writable();
        focusable = value;
        if (runtime != null) runtime.invalidateInteraction();
        return this;
    }

    private String text = "";
    private Runnable click;
    private IntConsumer scrollAction;
    private BooleanSupplier escapeAction;

    /** 范围/页面关闭策略：true表示已消费；只发布状态或意图，不直接改挂载声明。 */
    public SceneNode onEscape(BooleanSupplier action) {
        writable();
        escapeAction = action;
        return this;
    }

    public BooleanSupplier escapeAction() {
        return escapeAction;
    }

    public UUID id() {
        return id;
    }

    public Axis axis() {
        return axis;
    }

    public Size width() {
        return width;
    }

    public Size height() {
        return height;
    }

    public TextStyle style() {
        return style;
    }

    public int padding() {
        return padding;
    }

    public int gap() {
        return gap;
    }

    public int scrollOffset() {
        return scrollOffset;
    }

    public int scrollStep() {
        return scrollStep;
    }

    public SceneNode scrollStep(int value) {
        writable();
        if (value <= 0) throw new IllegalArgumentException("滚动步长必须为正");
        scrollStep = value;
        changed();
        return this;
    }

    public int background() {
        return background;
    }

    public boolean clip() {
        return clip;
    }

    public boolean scrollable() {
        return scrollable;
    }

    public boolean visible() {
        return visible;
    }

    public boolean modal() {
        return modal;
    }

    public SceneNode modal(boolean value) {
        writable();
        if (modal != value) {
            modal = value;
            if (runtime != null) runtime.invalidateRange(this);
        }
        return this;
    }

    public SceneNode axis(Axis value) {
        writable();
        axis = Objects.requireNonNull(value, "axis");
        changed();
        return this;
    }

    public SceneNode size(Size width, Size height) {
        writable();
        this.width = Objects.requireNonNull(width, "width");
        this.height = Objects.requireNonNull(height, "height");
        changed();
        return this;
    }

    public SceneNode style(TextStyle style) {
        writable();
        Objects.requireNonNull(style, "style");
        TextStyle previous = this.style;
        if (previous.equals(style)) return this;
        this.style = style;
        if (runtime != null && previous.sameLayout(style))
            runtime.invalidatePaint();
        else changed();
        return this;
    }

    public SceneNode padding(int value) {
        writable();
        if (value < 0) throw new IllegalArgumentException("内边距不能为负");
        padding = value;
        changed();
        return this;
    }

    public SceneNode gap(int value) {
        writable();
        if (value < 0) throw new IllegalArgumentException("间距不能为负");
        gap = value;
        changed();
        return this;
    }

    public SceneNode scrollable(boolean value) {
        writable();
        scrollable = value;
        changed();
        return this;
    }

    public SceneNode scrollOffset(int value) {
        writable();
        if (value < 0) throw new IllegalArgumentException("滚动位置不能为负");
        scrollOffset = value;
        changed();
        return this;
    }

    public SceneNode clip(boolean value) {
        writable();
        clip = value;
        changed();
        return this;
    }

    public SceneNode visible(boolean value) {
        writable();
        if (visible != value) {
            visible = value;
            if (runtime != null) runtime.invalidateRange(this);
        }
        return this;
    }

    public SceneNode background(int value) {
        writable();
        background = value;
        if (runtime != null) runtime.invalidatePaint();
        return this;
    }

    public SceneNode parent() {
        return parent;
    }

    public List<SceneNode> children() {
        return Collections.unmodifiableList(children);
    }

    public List<NodeBinding> bindings() {
        return Collections.unmodifiableList(bindings);
    }

    /** 逻辑声明状态属于节点本身，跨挂载保留，永久销毁清除。 */
    public Object declarationState(NodeBinding binding, Supplier<Object> initial) {
        writable();
        return declarationState.computeIfAbsent(binding, key -> initial.get());
    }

    public boolean destroyed() {
        return destroyed;
    }

    public SceneRuntime runtime() {
        return runtime;
    }

    public String text() {
        return text;
    }

    public boolean enabled() {
        return enabled;
    }

    public Runnable clickAction() {
        return click;
    }

    public IntConsumer scrollAction() {
        return scrollAction;
    }

    public SceneNode onScroll(IntConsumer action) {
        writable();
        scrollAction = action;
        return this;
    }

    public SceneNode text(String value) {
        writable();
        Objects.requireNonNull(value, "text");
        if (!text.equals(value)) {
            text = value;
            changed();
        }
        return this;
    }

    public SceneNode enabled(boolean value) {
        writable();
        if (enabled != value) {
            enabled = value;
            if (runtime != null) runtime.invalidateRange(this);
        }
        return this;
    }

    public SceneNode onClick(Runnable action) {
        writable();
        if (click != action) {
            click = action;
            if (runtime != null) runtime.invalidateInteraction();
        }
        return this;
    }

    public SceneNode bind(NodeBinding binding) {
        unmounted();
        bindings.add(Objects.requireNonNull(binding, "binding"));
        return this;
    }

    public SceneNode add(SceneNode child) {
        writable();
        child.writable();
        for (SceneNode cursor = this; cursor != null; cursor = cursor.parent)
            if (cursor == child) throw new IllegalArgumentException("节点不能包含自身");
        if (child.parent != null || child.runtime != null)
            throw new IllegalArgumentException("节点已有归属");
        children.add(child);
        child.parent = this;
        child.logicalOwner = this;
        child.logicalOwned = true;
        changed();
        return this;
    }

    /** 框架写端口受运行时授权约束，业务 handler 无法直接改树。 */
    public void frameworkParent(SceneRuntime authority, SceneNode next) {
        authority.checkMutation();
        if (runtime != null && runtime != authority)
            throw new IllegalArgumentException("节点属于另一运行时");
        if (parent == next) return;
        if (parent != null) parent.children.remove(this);
        parent = next;
        if (next != null) next.children.add(this);
    }

    /** 框架原生装饰沿宿主父链参与输入；不加入页面声明的内容子项或普通流。 */
    public void frameworkDecorationParent(SceneRuntime authority, SceneNode host) {
        authority.checkMutation();
        if (runtime != null && runtime != authority) throw new IllegalArgumentException("装饰属于另一运行时");
        parent = host; logicalOwner = host; logicalOwned = true;
    }

    /** 仅确定全部存续孩子的顺序；父归属、逻辑拥有和挂载身份仍由各自端口维护。 */
    public void frameworkChildrenOrder(SceneRuntime authority, List<SceneNode> ordered) {
        authority.checkMutation();
        if (runtime != authority) throw new IllegalArgumentException("父节点不属于运行时");
        List<SceneNode> next = new ArrayList<>(ordered);
        Map<SceneNode, Boolean> unique = new IdentityHashMap<>();
        for (SceneNode child : next) {
            if (child.parent != this || unique.put(child, Boolean.TRUE) != null)
                throw new IllegalArgumentException("子序列归属或身份冲突: " + id + " -> " + child.id);
        }
        if (next.size() != children.size())
            throw new IllegalArgumentException("子序列必须包含全部存续孩子: " + id);
        for (int i = 0; i < next.size(); i++) children.set(i, next.get(i));
    }

    public void frameworkRuntime(SceneRuntime authority, boolean attached) {
        authority.checkMutation();
        if (runtime != null && runtime != authority)
            throw new IllegalArgumentException("节点属于另一运行时");
        runtime = attached ? authority : null;
    }

    public void frameworkDestroy(SceneRuntime authority) {
        authority.checkMutation();
        destroy();
    }

    private void unmounted() {
        if (destroyed || runtime != null) throw new IllegalStateException("节点不能修改挂载声明");
    }

    private void writable() {
        if (destroyed) throw new IllegalStateException("节点已销毁");
        if (runtime != null) runtime.checkMutation();
    }

    private void changed() {
        if (runtime != null) runtime.invalidate(this);
    }
}
