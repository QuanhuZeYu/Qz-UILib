package club.heiqi.config.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.DraftValidator;
import club.heiqi.config.runtime.ValidationResult;
import club.heiqi.config.runtime.ValueCopy;
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
 *   <li>每字段一个 {@code Signal<Object>}（初值 = {@code draft.getDraft(path)} 的只读副本），
 *       作为 UI 对 draft 值的唯一响应式真值。</li>
 *   <li>{@code signal.set} 的真值落点是 {@link DraftBuffer}——{@link #onFieldEdit}
 *       内部同步 {@code draft.setDraft(path, value)}（核心层持真相，signal 是镜像）。</li>
 *   <li>{@code dirtySignal(path)} 读 {@code draftSignal.get()} 与 {@code draft.getCurrent(path)} 比对。</li>
 *   <li>{@code errorSignal(path)} = 内置 {@code draft.error(path)} 与最近一次提交校验错误合并
 *       （同 path 内置优先）。</li>
 *   <li>{@code canSaveSignal} = {@code isDirtySignal && !hasErrorSignal}（含提交错误）。</li>
 *   <li>提交失败后由 {@link #setSubmitValidation(ValidationResult)} 写入错误；
 *       任意字段编辑 / 成功保存 / 重置时 {@link #clearSubmitValidation()}。</li>
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
    /** 每字段 UI draft 真值 signal：path → Signal<Object>，容器值深度只读 */
    private final Map<String, Signal<Object>> draftSignals;
    /** 每字段脏标记派生：path → Computed<Boolean> */
    private final Map<String, Computed<Boolean>> dirtySignals;
    /** 每字段错误派生：path → Computed<String> */
    private final Map<String, Computed<String>> errorSignals;
    /** 全部 Computed 集合，供 dispose 统一释放 */
    private final List<Computed<?>> allComputed;
    /** 修订号 signal：bump 后强制所有读 DraftBuffer 内部状态的 Computed 重算 */
    private final Signal<Integer> revisionSignal;
    /**
     * 最近一次提交校验错误（含 custom DraftValidator 与内置合并结果）。
     * 字段编辑或成功保存后清空，避免永久禁用保存。
     */
    private final Signal<ValidationResult> submitValidationSignal;
    /** 聚合脏标记：任一字段 draft != current */
    private final Computed<Boolean> isDirtySignal;
    /** 聚合错误标记：任一字段有校验错误（内置 ∪ 提交） */
    private final Computed<Boolean> hasErrorSignal;
    /** 可保存派生：isDirty && !hasError */
    private final Computed<Boolean> canSaveSignal;
    /** 脏字段计数派生：遍历 dirtySignals 计 true 数 */
    private final Computed<Integer> dirtyCountSignal;
    /** 错误计数派生：schema 字段错误 + 全局 {@code _config} 提交错误 */
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
        this.submitValidationSignal = Signal.create(ValidationResult.ok());

        // 为每字段建 draft 镜像 signal + dirty/error 派生
        for (FieldSpec field : schema.allFields()) {
            final String path = field.path();
            final Signal<Object> sig = Signal.create(observableDraftValue(path));
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
                submitValidationSignal.get();
                return mergedFieldError(path);
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
            ValidationResult submit = submitValidationSignal.get();
            if (draft.hasError()) {
                return Boolean.TRUE;
            }
            return Boolean.valueOf(submit != null && submit.hasErrors());
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

        // 错误计数：schema 字段（内置∪提交）+ submit 中非 schema path（如 _config），不重复计
        this.errorCountSignal = Computed.create(() -> {
            revisionSignal.get();
            ValidationResult submit = submitValidationSignal.get();
            int count = 0;
            for (Computed<String> error : errorSignals.values()) {
                String msg = error.get();
                if (msg != null && !msg.isEmpty()) {
                    count++;
                }
            }
            if (submit != null && submit.hasErrors()) {
                for (String path : submit.errors().keySet()) {
                    if (!errorSignals.containsKey(path)) {
                        String msg = submit.errorFor(path);
                        if (msg != null && !msg.isEmpty()) {
                            count++;
                        }
                    }
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
     * 写入最近一次提交校验结果（INVALID 时由 ConfigScreen 调用）。
     *
     * <p>先从 DraftBuffer 回读全部字段，确保 validator 或并发编辑引发的冲突结果不会让 UI
     * Signal 停留在旧镜像；随后再设置字段/全局错误。</p>
     *
     * @param result 校验结果，null 等价清空
     */
    public void setSubmitValidation(ValidationResult result) {
        ValidationResult next = result == null ? ValidationResult.ok() : result;
        resyncAllDraftSignals();
        submitValidationSignal.set(next);
        bumpRevision();
    }

    /**
     * 清空提交校验错误（编辑字段 / 保存成功 / 取消时调用）。
     *
     * <p>同时将 {@link #saveFeedbackSignal()} 置 {@link SaveFeedback#NONE}。
     * 同帧多次写入由中央调度器合并，提交校验 Signal 是唯一真值。</p>
     */
    public void clearSubmitValidation() {
        clearSubmitStateQuiet();
        bumpRevision();
    }

    /**
     * @return 最近一次提交校验结果（只读），无错误时为 {@link ValidationResult#ok()}
     */
    public ReadableSignal<ValidationResult> submitValidationSignal() {
        return submitValidationSignal;
    }

    /**
     * 字段编辑：同步写回 DraftBuffer 并更新 draft 镜像 signal。
     *
     * <p>真值落点是 {@link DraftBuffer}（{@code draft.setDraft}），signal 是镜像。
     * 同时清空上一轮提交错误与保存失败反馈，避免 custom INVALID 永久禁用保存；
     * bump revision 让 errorSignal 重算。</p>
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
        // 从 DraftBuffer 回读深度只读副本，禁止调用方原对象或 Signal 读值反向污染
        sig.set(observableDraftValue(path));
        clearSubmitStateQuiet();
        bumpRevision();
    }

    /**
     * 预填充字段基线：同时把 value 写入 draft 与 current，使该字段 dirty=false。
     *
     * <p>用于"发现态预填充"场景——UI 展示派生值但不视为用户编辑，不触发保存写盘。
     * 典型用法：fontSort 字段首次打开时 draft 为空（Authority/yaml 空 list），
     * 用 {@code FontConfig.getFontSortSnapshot()} 预填充，让 UI 立即看到所有已发现字体，
     * 同时抹平 dirty——保存按钮不因预填充点亮，用户不显式编辑就不写盘。</p>
     *
     * <p>与 {@link #onFieldEdit} 的区别：</p>
     * <ul>
     *   <li>{@code onFieldEdit} 只改 draft（→dirty=true），代表用户显式编辑意图。</li>
     *   <li>本方法改 draft + current（→dirty=false），代表"以此为基线种子"，
     *       后续用户编辑只要偏离 prefill 即 dirty，触发正常保存。</li>
     * </ul>
     *
     * <p>三个动作：写 draft + current（抹平 dirty）→ 同步 draft 镜像 signal（让 UI 读到新值）
     * → bump revision（让 dirty Computed 重算）。</p>
     *
     * @param path  字段全路径
     * @param value 预填充值（与 draft 值同类型）
     */
    public void seedFieldBaseline(String path, Object value) {
        Signal<Object> sig = draftSignals.get(path);
        if (sig == null) {
            return;
        }
        draft.setDraftAndCurrent(path, value);
        sig.set(observableDraftValue(path));
        clearSubmitStateQuiet();
        bumpRevision();
    }

    /**
     * 重置全部草稿为当前值：DraftBuffer.resetToCurrent + 逐字段 signal.set(current)。
     *
     * <p>同时清空提交错误与保存失败反馈（与 {@link #onFieldEdit} 一致）。</p>
     */
    public void resetToCurrent() {
        draft.resetToCurrent();
        resyncAllDraftSignals();
        clearSubmitStateQuiet();
        bumpRevision();
    }

    /**
     * 重置单字段草稿为默认值：DraftBuffer.resetFieldToDefault + signal.set(default)。
     *
     * <p>current 不变，故 default != current 时该字段 dirty=true。
     * 同时清空提交错误与保存失败反馈。</p>
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
            sig.set(observableDraftValue(path));
        }
        clearSubmitStateQuiet();
        bumpRevision();
    }

    /**
     * 保存成功后刷新 current 派生。
     *
     * <p>保存事务可能把 NUMBER 字符串规范化为 Double；因此成功后也必须全字段回读，
     * 再清空提交错误并 bump revision，让 UI、draft 与 current 使用同一规范化值。</p>
     */
    public void afterSaveSync() {
        // 成功路径：ConfigScreen 另写 OK 反馈
        resyncAllDraftSignals();
        submitValidationSignal.set(ValidationResult.ok());
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
     * 单字段错误：内置优先，其次提交校验。
     */
    private String mergedFieldError(String path) {
        String builtIn = draft.error(path);
        if (builtIn != null && !builtIn.isEmpty()) {
            return builtIn;
        }
        ValidationResult submit = submitValidationSignal.get();
        if (submit == null) {
            return null;
        }
        return submit.errorFor(path);
    }

    /**
     * 无条件排队清空提交错误 Signal + saveFeedback=NONE。
     */
    private void clearSubmitStateQuiet() {
        submitValidationSignal.set(ValidationResult.ok());
        saveFeedbackSignal.set(SaveFeedback.NONE);
    }

    /**
     * 读取适合暴露给 UI 的深度只读 draft 值。
     */
    private Object observableDraftValue(String path) {
        return ValueCopy.freeze(draft.getDraft(path));
    }

    /** 从 DraftBuffer 回读全部字段到只读 Signal 镜像。 */
    private void resyncAllDraftSignals() {
        for (FieldSpec field : schema.allFields()) {
            String path = field.path();
            Signal<Object> signal = draftSignals.get(path);
            if (signal != null) {
                signal.set(observableDraftValue(path));
            }
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
