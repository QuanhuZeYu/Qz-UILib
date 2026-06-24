package club.heiqi.uilib.ui.scene.overlay;

import club.heiqi.uilib.ui.scene.node.SceneNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 场景浮层宿主。
 *
 * <p>本类维护 active overlay roots 的有序栈，列表尾部为栈顶。绘制侧按
 * bottom-first 顺序消费，命中测试侧按 top-first 顺序消费。返回的顺序访问结果
 * 均为不可变快照，不暴露内部可变列表。</p>
 */
public final class SceneOverlayHost {

    private final List<Entry> entries = new ArrayList<>();

    /**
     * 注册一个浮层 root。
     *
     * @param root 浮层根节点
     * @return 可幂等摘除该浮层的句柄
     */
    public OverlayHandle register(SceneNode root) {
        return register(root, OverlayDismissPolicy.DEFAULT, null, null, Collections.emptySet());
    }

    /**
     * 注册一个带关闭策略的浮层 root。
     *
     * @param root 浮层根节点
     * @param dismissPolicy 关闭策略，传入 null 时使用默认策略
     * @param dismissRequest 关闭请求回调，只允许调用方在回调内写 signal，可为 null
     * @return 可幂等摘除该浮层的句柄
     */
    public OverlayHandle register(SceneNode root,
                                  OverlayDismissPolicy dismissPolicy,
                                  Runnable dismissRequest) {
        return register(root, dismissPolicy, dismissRequest, null, Collections.emptySet());
    }

    /**
     * 注册一个带关闭策略和锚点探针的浮层 root。
     *
     * @param root 浮层根节点
     * @param dismissPolicy 关闭策略，传入 null 时使用默认策略
     * @param dismissRequest 关闭请求回调，只允许调用方在回调内写 signal，可为 null
     * @param anchorProvider 只读锚点探针，可为 null 表示按宿主左上角全尺寸布局
     * @return 可幂等摘除该浮层的句柄
     */
    public OverlayHandle register(SceneNode root,
                                  OverlayDismissPolicy dismissPolicy,
                                  Runnable dismissRequest,
                                  AnchorProvider anchorProvider) {
        return register(root, dismissPolicy, dismissRequest, anchorProvider, Collections.emptySet());
    }

    /**
     * 注册一个带关闭策略、锚点探针和保护节点集的浮层 root。
     *
     * @param root 浮层根节点
     * @param dismissPolicy 关闭策略，传入 null 时使用默认策略
     * @param dismissRequest 关闭请求回调，只允许调用方在回调内写 signal，可为 null
     * @param anchorProvider 只读锚点探针，可为 null 表示按宿主左上角全尺寸布局
     * @param protectedNodes 外部点击判定中视为浮层内部的保护节点集，可为 null
     * @return 可幂等摘除该浮层的句柄
     */
    public OverlayHandle register(SceneNode root,
                                  OverlayDismissPolicy dismissPolicy,
                                  Runnable dismissRequest,
                                  AnchorProvider anchorProvider,
                                  Collection<SceneNode> protectedNodes) {
        Entry entry = new Entry(root, dismissPolicy == null ? OverlayDismissPolicy.DEFAULT : dismissPolicy,
                dismissRequest, anchorProvider, protectedNodes);
        entries.add(entry);
        return new OverlayHandle(this, entry);
    }

    /**
     * 按底到顶顺序返回浮层 entry 快照，供绘制叠加消费。
     *
     * @return 不可变快照列表
     */
    public List<Entry> bottomFirst() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * 按顶到底顺序返回浮层 entry 快照，供优先命中消费。
     *
     * @return 不可变快照列表
     */
    public List<Entry> topFirst() {
        List<Entry> snapshot = new ArrayList<>(entries);
        Collections.reverse(snapshot);
        return Collections.unmodifiableList(snapshot);
    }

    /** @return 当前浮层数量 */
    public int size() {
        return entries.size();
    }

    /** @return 当前是否没有浮层 */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    void remove(Entry entry) {
        entries.remove(entry);
    }

    /**
     * 浮层栈中的一项。
     *
     * <p>entry 只保存 scene 数据对象、关闭策略与关闭请求回调；不持有平台上下文、
     * 渲染实现或旧 DOM 对象。</p>
     */
    public static final class Entry {
        private final SceneNode root;
        private final OverlayDismissPolicy dismissPolicy;
        private final Runnable dismissRequest;
        private final AnchorProvider anchorProvider;
        private final Set<SceneNode> protectedNodes;
        private int anchorX;
        private int anchorY;

        private Entry(SceneNode root,
                      OverlayDismissPolicy dismissPolicy,
                      Runnable dismissRequest,
                      AnchorProvider anchorProvider,
                      Collection<SceneNode> protectedNodes) {
            this.root = Objects.requireNonNull(root, "root");
            this.dismissPolicy = Objects.requireNonNull(dismissPolicy, "dismissPolicy");
            this.dismissRequest = dismissRequest;
            this.anchorProvider = anchorProvider;
            this.protectedNodes = protectedNodes == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new HashSet<>(protectedNodes));
        }

        /** @return 浮层根节点 */
        public SceneNode getRoot() {
            return root;
        }

        /** @return 浮层关闭策略 */
        public OverlayDismissPolicy getDismissPolicy() {
            return dismissPolicy;
        }

        /** @return 只读锚点探针，可为 null */
        public AnchorProvider getAnchorProvider() {
            return anchorProvider;
        }

        /** @return 外部点击判定中视为浮层内部的保护节点集 */
        public Set<SceneNode> getProtectedNodes() {
            return protectedNodes;
        }

        /** @return 浮层 root 在 host 局部坐标系下的 X 偏移 */
        public int getAnchorX() {
            return anchorX;
        }

        /** @return 浮层 root 在 host 局部坐标系下的 Y 偏移 */
        public int getAnchorY() {
            return anchorY;
        }

        /** 设置浮层 root 在 host 局部坐标系下的 X 偏移。 */
        public void setAnchorX(int anchorX) {
            this.anchorX = anchorX;
        }

        /** 设置浮层 root 在 host 局部坐标系下的 Y 偏移。 */
        public void setAnchorY(int anchorY) {
            this.anchorY = anchorY;
        }

        /** 请求关闭浮层；实际是否写 signal 由注册方回调决定。 */
        public void requestDismiss() {
            if (dismissRequest != null) {
                dismissRequest.run();
            }
        }
    }
}
