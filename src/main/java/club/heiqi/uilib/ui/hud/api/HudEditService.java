package club.heiqi.uilib.ui.hud.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * HUD 编辑服务：可编辑目标注册表 + 编辑意图（候选公共 API）。
 *
 * <p>把「把某个 HUD 交给用户拖动布局」从聊天内部实现提升为公开契约：第三方 Mod 注册
 * {@link HudEditTarget}，当前打开的聊天输入屏（编辑宿主）在编辑态为这些目标渲染预览浮层、
 * 命中拖动并写 {@link HudLayoutService} 草稿。放置真值仍然只有一处（{@link HudLayoutService}），
 * 本服务不保存位置、不做坐标数学。</p>
 *
 * <p>契约：</p>
 * <ul>
 *   <li>同一 hudId 重复注册明确拒绝（不静默覆盖）；注册与注销限客户端主线程；</li>
 *   <li>{@link #revision()} 在增删时 +1，编辑宿主据此重建预览浮层；</li>
 *   <li>{@link #requestEdit(String)} 只发布「进入编辑并聚焦该目标」意图，由当前打开的聊天屏
 *       消费；无活动编辑宿主时静默丢弃（不排队、不抛异常、不改变 {@link #isEditing()})；</li>
 *   <li>{@link #focus()} / {@link #isEditing()} 以编辑宿主为真值：无宿主时分别为 null / false；</li>
 *   <li>聊天屏关闭时摘除宿主，旧屏关闭不得顶掉新屏（{@link #detachHost} 按身份判定）。</li>
 * </ul>
 *
 * <p><b>宿主端口（{@link Host}）</b>属于 UILib 内部接线：由聊天输入屏实现并由其打开/关闭
 * 时注入与摘除，第三方调用方只使用注册表与 {@link #requestEdit(String)} / {@link #focus()}，
 * 不实现该端口。{@link #attachHost} 重复注入同一宿主幂等；{@link #detachHost} 仅当仍是同一
 * 宿主时摘除（旧屏关闭不顶掉新屏）；{@link #clear()} 只清注册表、不动宿主绑定。以上全部
 * 限客户端主线程调用。</p>
 */
public final class HudEditService {

    private static final HudEditService INSTANCE = new HudEditService();

    /** 无宿主时的焦点信号（恒 null，调用方无需判空）。 */
    private static final ReadableSignal<String> NO_FOCUS = new ReadableSignal<String>() {
        @Override
        public String get() {
            return null;
        }
    };

    private final Map<String, HudEditTarget> entries = new LinkedHashMap<String, HudEditTarget>();
    /** 注册表版本（增删时 +1；编辑宿主据此重建预览浮层）。 */
    private final Signal<Integer> revision = Signal.create(Integer.valueOf(0));
    private int revisionValue;
    /** 当前编辑会话宿主（聊天输入屏打开期间注入；读多写少，用 volatile 免持锁调用外部实现）。 */
    private volatile Host host;

    private HudEditService() {
    }

    /** @return 全局注册表单例 */
    public static HudEditService getInstance() {
        return INSTANCE;
    }

    /**
     * 注册可编辑 HUD 目标。
     *
     * @param target 目标（不可为 null）
     * @return 幂等注销句柄
     */
    public synchronized HudRegistration register(HudEditTarget target) {
        Objects.requireNonNull(target, "target");
        final String hudId = target.getHudId();
        if (entries.containsKey(hudId)) {
            throw new IllegalArgumentException("duplicate HUD edit target id: " + hudId);
        }
        entries.put(hudId, target);
        bump();
        return new Registration(new Runnable() {
            @Override
            public void run() {
                remove(target);
            }
        });
    }

    /** @return 该 HUD 是否已注册为可编辑目标 */
    public synchronized boolean hasTarget(String hudId) {
        return hudId != null && entries.containsKey(hudId);
    }

    /** @return 该 HUD 的编辑目标；未注册返回 null */
    public synchronized HudEditTarget target(String hudId) {
        return hudId == null ? null : entries.get(hudId);
    }

    /** @return 注册顺序快照（不可变；编辑宿主装配预览时遍历） */
    public synchronized List<HudEditTarget> targets() {
        return Collections.unmodifiableList(new ArrayList<HudEditTarget>(entries.values()));
    }

    /** @return 注册表版本（增删时 +1） */
    public ReadableSignal<Integer> revision() {
        return revision;
    }

    /**
     * 发布「进入编辑并聚焦该目标」意图。
     *
     * <p>消费端是当前打开的聊天输入屏：无活动编辑宿主时静默丢弃（不排队、不抛异常），
     * 有宿主时由宿主进入编辑子模式（首次进入调用 {@link HudLayoutService#beginEdit()}）
     * 并把焦点切到该目标。</p>
     *
     * @param hudId 目标 HUD id（null/空白 = 静默忽略）
     */
    public void requestEdit(String hudId) {
        if (hudId == null || hudId.trim().isEmpty()) {
            return;
        }
        Host current = host;
        if (current == null) {
            return;
        }
        current.requestEnterEdit(hudId);
    }

    /**
     * @return 当前聚焦目标 hudId；无活动编辑宿主或未聚焦时返回 null
     *
     * <p>信号由编辑宿主提供，聊天屏关闭后引用不再有效——调用方应在 {@link #isEditing()} 为真
     * （或直接调用本方法）时读取，不要把返回对象缓存过屏。</p>
     */
    public ReadableSignal<String> focus() {
        Host current = host;
        return current == null ? NO_FOCUS : current.focus();
    }

    /** @return 是否处于 HUD 编辑子模式（以编辑宿主真值为准；无宿主恒 false） */
    public boolean isEditing() {
        Host current = host;
        return current != null && current.isEditing();
    }

    /** 清空注册表（测试与整体关闭用）；已返回句柄失效。宿主端口不受影响。 */
    public synchronized void clear() {
        if (entries.isEmpty()) {
            return;
        }
        entries.clear();
        bump();
    }

    /**
     * 注入编辑宿主（聊天输入屏装配时调用；内部接线，第三方调用方不实现也不调用）。
     *
     * <p>重复注入同一宿主幂等；限客户端主线程。注入后 {@link #requestEdit(String)} 才会把
     * 意图交给该宿主，{@link #isEditing()} / {@link #focus()} 也以它为真值。</p>
     *
     * @param value 宿主端口（不可为 null）
     */
    public void attachHost(Host value) {
        this.host = Objects.requireNonNull(value, "host");
    }

    /** 摘除编辑宿主（仅当仍是同一宿主时生效，避免旧屏关闭顶掉新屏）；限客户端主线程。 */
    public void detachHost(Host value) {
        if (host == value) {
            host = null;
        }
    }

    /**
     * 编辑会话宿主端口（UILib 内部接线：由聊天输入屏实现，第三方调用方不实现）。
     *
     * <p>三个方法都限客户端主线程：聊天屏进入编辑子模式时保持 {@link #isEditing()} 为真，
     * 并把当前聚焦目标经 {@link #focus()} 暴露给服务。</p>
     */
    public interface Host {
        /** 进入编辑子模式并聚焦该目标（已编辑时只切换聚焦；首次进入调用 {@code HudLayoutService.beginEdit()}）。 */
        void requestEnterEdit(String hudId);

        /** @return 编辑子模式是否进行中（宿主真值，非帧末信号快照） */
        boolean isEditing();

        /** @return 当前聚焦目标信号（无聚焦时值为 null） */
        ReadableSignal<String> focus();
    }

    private synchronized void remove(HudEditTarget target) {
        if (entries.containsValue(target)) {
            entries.values().remove(target);
            bump();
        }
    }

    private void bump() {
        revisionValue++;
        revision.set(Integer.valueOf(revisionValue));
    }

    /** 幂等注销句柄（与 {@link HudRegistration} 同语义）。 */
    private static final class Registration implements HudRegistration {
        private final Runnable closer;
        private final AtomicBoolean closed = new AtomicBoolean();
        Registration(Runnable closer) { this.closer = closer; }
        @Override public void close() {
            if (closed.compareAndSet(false, true)) {
                closer.run();
            }
        }
        @Override public boolean isClosed() { return closed.get(); }
    }
}
