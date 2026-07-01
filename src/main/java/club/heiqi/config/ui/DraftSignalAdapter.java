package club.heiqi.config.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * DraftBuffer → signal 适配器：把纯数据 {@link DraftBuffer} 的每字段包成 uilib
 * {@link Signal}，并建 {@link Computed} 派生 dirty / error / canSave。
 *
 * <h3>核心机制</h3>
 * <ul>
 *   <li>每字段一个 {@code Signal<Object>}（初值 = {@code draft.getDraft(path)}），
 *       作为 draft 值的响应式镜像。</li>
 *   <li>{@code signal.set} 的真值落点是 {@link DraftBuffer}——{@link #onFieldEdit}
 *       内部同步 {@code draft.setDraft(path, value)}（核心层持真相，signal 是镜像）。</li>
 *   <li>{@code dirtySignal(path)} 读 {@code draftSignal.get()} 与 {@code draft.getCurrent(path)} 比对。</li>
 *   <li>{@code errorSignal(path)} 调 {@code draft.error(path)}。</li>
 *   <li>{@code canSaveSignal} = {@code isDirtySignal && !hasErrorSignal}。</li>
 * </ul>
 *
 * <h3>revision signal</h3>
 * <p>{@link DraftBuffer} 的 current / draft 内部状态变化（如 {@link #afterSaveSync} 改 current、
 * {@link #onFieldEdit} 改 draft）不会自动触发依赖它的 {@link Computed} 重算。
 * 故维护一个 {@code revisionSignal}，所有读 DraftBuffer 内部状态的 {@link Computed}
 * 都订阅它；任何改 DraftBuffer 内部状态的操作都 bump revision，强制重算。</p>
 *
 * <p>本类软依赖 uilib（reactive 包），不依赖 scene 控件层。</p>
 */
public final class DraftSignalAdapter {

    /** 关联的场景运行时（保留入参以备扩展，当前内部不强制使用，可为 null） */
    private final SceneRuntime runtime;
    /** 纯数据草稿容器，真值落点 */
    private final DraftBuffer draft;
    /** 关联的 schema */
    private final ConfigSchema schema;
    /** 每字段 draft 镜像 signal：path → Signal<Object> */
    private final Map<String, Signal<Object>> draftSignals;
    /** 每字段脏标记派生：path → Computed<Boolean> */
    private final Map<String, Computed<Boolean>> dirtySignals;
    /** 每字段错误派生：path → Computed<String> */
    private final Map<String, Computed<String>> errorSignals;
    /** 全部 Computed 集合，供 dispose 统一释放 */
    private final List<Computed<?>> allComputed;
    /** 修订号 signal：bump 后强制所有读 DraftBuffer 内部状态的 Computed 重算 */
    private final Signal<Integer> revisionSignal;
    /** 聚合脏标记：任一字段 draft != current */
    private final Computed<Boolean> isDirtySignal;
    /** 聚合错误标记：任一字段有校验错误 */
    private final Computed<Boolean> hasErrorSignal;
    /** 可保存派生：isDirty && !hasError */
    private final Computed<Boolean> canSaveSignal;
    /** 脏字段计数派生：遍历 dirtySignals 计 true 数 */
    private final Computed<Integer> dirtyCountSignal;
    /** 错误字段计数派生：遍历 errorSignals 计非空数 */
    private final Computed<Integer> errorCountSignal;
    /** 保存反馈受控源：由 ConfigScreen 在 saveChanges 后 set，UI 消费 */
    private final Signal<SaveFeedback> saveFeedbackSignal;
    /** 当前修订号 */
    private int revision;

    /**
     * 创建适配器，为 DraftBuffer 每字段建立 signal 镜像与派生 Computed。
     *
     * @param runtime 场景运行时，可为 null（当前内部不强制使用，保留以备扩展）
     * @param draft   纯数据草稿容器，不可为 null
     */
    public DraftSignalAdapter(SceneRuntime runtime, DraftBuffer draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft must not be null");
        }
        this.runtime = runtime;
        this.draft = draft;
        this.schema = draft.schema();
        this.draftSignals = new HashMap<String, Signal<Object>>();
        this.dirtySignals = new HashMap<String, Computed<Boolean>>();
        this.errorSignals = new HashMap<String, Computed<String>>();
        this.allComputed = new ArrayList<Computed<?>>();
        this.revisionSignal = Signal.create(Integer.valueOf(0));
        this.revision = 0;

        // 为每字段建 draft 镜像 signal + dirty/error 派生
        for (FieldSpec field : schema.allFields()) {
            final String path = field.path();
            final Signal<Object> sig = Signal.create(draft.getDraft(path));
            draftSignals.put(path, sig);

            final Computed<Boolean> dirty = Computed.create(() -> {
                revisionSignal.get(); // 订阅 revision，bump 后强制重算
                Object d = sig.get();
                Object c = draft.getCurrent(path);
                return Boolean.valueOf(!Objects.equals(d, c));
            });
            dirtySignals.put(path, dirty);
            allComputed.add(dirty);

            final Computed<String> error = Computed.create(() -> {
                revisionSignal.get();
                return draft.error(path);
            });
            errorSignals.put(path, error);
            allComputed.add(error);
        }

        // 聚合派生
        this.isDirtySignal = Computed.create(() -> {
            revisionSignal.get();
            return Boolean.valueOf(draft.isDirtyAny());
        });
        allComputed.add(isDirtySignal);

        this.hasErrorSignal = Computed.create(() -> {
            revisionSignal.get();
            return Boolean.valueOf(draft.hasError());
        });
        allComputed.add(hasErrorSignal);

        this.canSaveSignal = Computed.create(() -> {
            Boolean dirty = isDirtySignal.get();
            Boolean hasErr = hasErrorSignal.get();
            return Boolean.valueOf(Boolean.TRUE.equals(dirty) && !Boolean.TRUE.equals(hasErr));
        });
        allComputed.add(canSaveSignal);

        // 脏字段计数：遍历 dirtySignals 计 true 数（订阅 revision，bump 后重算）
        this.dirtyCountSignal = Computed.create(() -> {
            revisionSignal.get();
            int count = 0;
            for (Computed<Boolean> dirty : dirtySignals.values()) {
                if (Boolean.TRUE.equals(dirty.get())) {
                    count++;
                }
            }
            return Integer.valueOf(count);
        });
        allComputed.add(dirtyCountSignal);

        // 错误字段计数：遍历 errorSignals 计非空数（订阅 revision，bump 后重算）
        this.errorCountSignal = Computed.create(() -> {
            revisionSignal.get();
            int count = 0;
            for (Computed<String> error : errorSignals.values()) {
                String msg = error.get();
                if (msg != null && !msg.isEmpty()) {
                    count++;
                }
            }
            return Integer.valueOf(count);
        });
        allComputed.add(errorCountSignal);

        // 保存反馈受控源：初值 NONE（无反馈），由 ConfigScreen 在 saveChanges 后 set
        this.saveFeedbackSignal = Signal.create(SaveFeedback.NONE);
    }

    /**
     * @return 关联的草稿容器
     */
    public DraftBuffer draft() {
        return draft;
    }

    /**
     * 取某字段的 draft 镜像 signal（只读视图）。
     * 写入请用 {@link #onFieldEdit(String, Object)}，勿直接 set。
     *
     * @param path 字段全路径
     * @return draft 镜像 signal 的只读视图，不存在返回 null
     */
    public ReadableSignal<Object> draftSignal(String path) {
        return draftSignals.get(path);
    }

    /**
     * 取某字段的脏标记派生（只读）。
     *
     * @param path 字段全路径
     * @return 脏标记 Computed，不存在返回 null
     */
    public ReadableSignal<Boolean> dirtySignal(String path) {
        return dirtySignals.get(path);
    }

    /**
     * 取某字段的错误信息派生（只读）。
     *
     * @param path 字段全路径
     * @return 错误信息 Computed，不存在返回 null
     */
    public ReadableSignal<String> errorSignal(String path) {
        return errorSignals.get(path);
    }

    /**
     * @return 聚合脏标记派生（任一字段脏则 true）
     */
    public ReadableSignal<Boolean> isDirtySignal() {
        return isDirtySignal;
    }

    /**
     * @return 聚合错误标记派生（任一字段有错则 true）
     */
    public ReadableSignal<Boolean> hasErrorSignal() {
        return hasErrorSignal;
    }

    /**
     * @return 可保存派生（isDirty && !hasError）
     */
    public ReadableSignal<Boolean> canSaveSignal() {
        return canSaveSignal;
    }

    /**
     * @return 脏字段计数派生（值为 true 的 dirtySignal 数量）
     */
    public ReadableSignal<Integer> dirtyCountSignal() {
        return dirtyCountSignal;
    }

    /**
     * @return 错误字段计数派生（error 非空的字段数量）
     */
    public ReadableSignal<Integer> errorCountSignal() {
        return errorCountSignal;
    }

    /**
     * @return 保存反馈受控源（只读视图），由 {@link #setSaveFeedback} 写入
     */
    public ReadableSignal<SaveFeedback> saveFeedbackSignal() {
        return saveFeedbackSignal;
    }

    /**
     * 写入保存反馈，供 ConfigScreen 在 saveChanges 后调用（守 I1：只经 signal 改 UI）。
     *
     * @param feedback 保存反馈，null 时按 NONE 处理
     */
    public void setSaveFeedback(SaveFeedback feedback) {
        saveFeedbackSignal.set(feedback == null ? SaveFeedback.NONE : feedback);
    }

    /**
     * 字段编辑：同步写回 DraftBuffer 并更新 draft 镜像 signal。
     *
     * <p>真值落点是 {@link DraftBuffer}（{@code draft.setDraft}），signal 是镜像。
     * bump revision 让 errorSignal 重算（error 依赖 draft 内部状态）。</p>
     *
     * @param path  字段全路径
     * @param value 新的草稿值
     */
    public void onFieldEdit(String path, Object value) {
        Signal<Object> sig = draftSignals.get(path);
        if (sig == null) {
            return;
        }
        draft.setDraft(path, value);
        sig.set(value);
        bumpRevision();
    }

    /**
     * 重置全部草稿为当前值：DraftBuffer.resetToCurrent + 逐字段 signal.set(current)。
     */
    public void resetToCurrent() {
        draft.resetToCurrent();
        for (FieldSpec field : schema.allFields()) {
            String path = field.path();
            Signal<Object> sig = draftSignals.get(path);
            if (sig != null) {
                sig.set(draft.getCurrent(path));
            }
        }
        bumpRevision();
    }

    /**
     * 重置单字段草稿为默认值：DraftBuffer.resetFieldToDefault + signal.set(default)。
     *
     * <p>current 不变，故 default != current 时该字段 dirty=true。</p>
     *
     * @param path 字段全路径
     */
    public void resetFieldToDefault(String path) {
        FieldSpec field = schema.field(path);
        if (field == null) {
            return;
        }
        draft.resetFieldToDefault(path);
        Signal<Object> sig = draftSignals.get(path);
        if (sig != null) {
            sig.set(draft.getDraft(path));
        }
        bumpRevision();
    }

    /**
     * 保存成功后刷新 current 派生。
     *
     * <p>保存事务把 current = draft，draft 值未变但 current 变了，
     * bump revision 让 dirtySignal 重算（draftSignal 值 == 新 current → dirty=false）。</p>
     */
    public void afterSaveSync() {
        bumpRevision();
    }

    /**
     * 释放所有 Computed，后续不再重算与传播。
     */
    public void dispose() {
        for (Computed<?> c : allComputed) {
            c.dispose();
        }
    }

    /**
     * bump revision signal，强制所有订阅它的 Computed 重算。
     */
    private void bumpRevision() {
        revision++;
        revisionSignal.set(Integer.valueOf(revision));
    }
}
