package club.heiqi.config.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.DraftValidator;
import club.heiqi.config.runtime.SaveOutcome;
import club.heiqi.config.runtime.ValidationResult;
import club.heiqi.config.runtime.ValueCopy;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.FieldType;
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
 *       （同 path 内置优先）；冲突态不注入字段 error。</li>
 *   <li>{@code canSaveSignal} = {@code isDirtySignal && !hasErrorSignal && !requiresReload}。</li>
 *   <li>提交失败后由 {@link #setSubmitValidation(ValidationResult)} 写入错误；
 *       任意字段编辑 / 成功保存 / 重置时 {@link #clearSubmitValidation()}。</li>
 *   <li>{@link #replaceDraft(DraftBuffer)} 在保持 Signal/Computed identity 下替换底层草稿引用
 *       （stale 显式恢复用），schema 路径/类型不兼容时拒绝且旧状态不变。</li>
 * </ul>
 *
 * <h3>revision signal</h3>
 * <p>{@link DraftBuffer} 的 current / draft 内部状态变化不会自动触发依赖它的 {@link Computed}
 * 重算。故维护一个 {@code revisionSignal}，所有读 DraftBuffer 内部状态的 {@link Computed}
 * 都订阅它；任何改 DraftBuffer 内部状态的操作都 bump revision，强制重算。</p>
 *
 * <p>本类软依赖 uilib（reactive 包），不依赖 scene 控件层。</p>
 */
public final class DraftSignalAdapter {

    /** 关联的场景运行时（保留入参以备扩展，当前内部不强制使用，可为 null） */
    private final SceneRuntime runtime;
    /** 纯数据草稿容器，真值落点（可被 replaceDraft 安全替换引用） */
    private volatile DraftBuffer draft;
    /** 关联的 schema（replace 时路径/类型必须兼容） */
    private final ConfigSchema schema;
    /** 每字段 UI draft 真值 signal：path → Signal&lt;Object&gt;，容器值深度只读 */
    private final Map<String, Signal<Object>> draftSignals;
    /** 每字段脏标记派生：path → Computed&lt;Boolean&gt; */
    private final Map<String, Computed<Boolean>> dirtySignals;
    /** 每字段错误派生：path → Computed&lt;String&gt; */
    private final Map<String, Computed<String>> errorSignals;
    /** 全部 Computed 集合，供 dispose 统一释放 */
    private final List<Computed<?>> allComputed;
    /** 修订号 signal：bump 后强制所有读 DraftBuffer 内部状态的 Computed 重算 */
    private final Signal<Integer> revisionSignal;
    /**
     * 最近一次提交校验错误（含 custom DraftValidator 与内置合并结果）。
     * 字段编辑或成功保存后清空，避免永久禁用保存。
     * 冲突态下不得用字段错误注入；冲突走 {@link #conflictTypeSignal}。
     */
    private final Signal<ValidationResult> submitValidationSignal;
    /** 最近一次结构化冲突类型（NONE 表示无冲突） */
    private final Signal<SaveOutcome.ConflictType> conflictTypeSignal;
    /** 聚合脏标记：任一字段 draft != current */
    private final Computed<Boolean> isDirtySignal;
    /** 聚合错误标记：任一字段有校验错误（内置 ∪ 提交；不含纯冲突） */
    private final Computed<Boolean> hasErrorSignal;
    /** 可保存派生：isDirty && !hasError && !requiresReload */
    private final Computed<Boolean> canSaveSignal;
    /** 脏字段计数派生：遍历 dirtySignals 计 true 数 */
    private final Computed<Integer> dirtyCountSignal;
    /** 错误计数派生：schema 字段错误 + 全局 {@code _config} 提交错误（冲突时不计 _config） */
    private final Computed<Integer> errorCountSignal;
    /** 保存反馈受控源：由 ConfigScreen 在 saveChanges 后 set，UI 消费 */
    private final Signal<SaveFeedback> saveFeedbackSignal;
    /**
     * presentation-only 展示种子：Authority 空列表时 UI 预填充可见值，但不写 DraftBuffer；
     * dirty 对照仍读 current（保持 false）。用户首次真正编辑后清除。
     */
    private final Map<String, Object> presentationSeeds;
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
        this.presentationSeeds = new HashMap<String, Object>();
        this.revisionSignal = Signal.create(Integer.valueOf(0));
        this.revision = 0;
        this.submitValidationSignal = Signal.create(ValidationResult.ok());
        this.conflictTypeSignal = Signal.create(SaveOutcome.ConflictType.NONE);

        // 为每字段建 draft 镜像 signal + dirty/error 派生
        for (FieldSpec field : schema.allFields()) {
            final String path = field.path();
            final Signal<Object> sig = Signal.create(observableDraftValue(path));
            draftSignals.put(path, sig);

            final Computed<Boolean> dirty = Computed.create(() -> {
                revisionSignal.get(); // 订阅 revision，bump 后强制重算
                // presentation seed 期间 draft 未写入：对用户不可见「脏」，dirty=false
                if (presentationSeeds.containsKey(path) && !draft().isDirty(path)) {
                    return Boolean.FALSE;
                }
                Object d = sig.get();
                Object c = draft().getCurrent(path);
                return Boolean.valueOf(!Objects.equals(d, c));
            });
            dirtySignals.put(path, dirty);
            allComputed.add(dirty);

            final Computed<String> error = Computed.create(() -> {
                revisionSignal.get();
                submitValidationSignal.get();
                conflictTypeSignal.get();
                // 冲突态不注入字段 error（errorCount 不因冲突膨胀）
                if (isConflictActive()) {
                    return draft().error(path); // 仅内置字段错误，不含 _config 冲突
                }
                return mergedFieldError(path);
            });
            errorSignals.put(path, error);
            allComputed.add(error);
        }

        // 聚合派生
        this.isDirtySignal = Computed.create(() -> {
            revisionSignal.get();
            return Boolean.valueOf(draft().isDirtyAny());
        });
        allComputed.add(isDirtySignal);

        this.hasErrorSignal = Computed.create(() -> {
            revisionSignal.get();
            if (draft().hasError()) {
                return Boolean.TRUE;
            }
            // 冲突态：不把 _config 冲突算作字段校验错误（保存由 requiresReload 禁用）
            if (isConflictActive()) {
                return Boolean.FALSE;
            }
            ValidationResult submit = submitValidationSignal.get();
            return Boolean.valueOf(submit != null && submit.hasErrors());
        });
        allComputed.add(hasErrorSignal);

        this.canSaveSignal = Computed.create(() -> {
            revisionSignal.get();
            conflictTypeSignal.get();
            Boolean dirty = isDirtySignal.get();
            Boolean hasErr = hasErrorSignal.get();
            if (requiresReloadActive()) {
                return Boolean.FALSE;
            }
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

        // 错误计数：schema 字段（内置∪提交）+ submit 中非 schema path（如 _config），冲突时不计 _config
        this.errorCountSignal = Computed.create(() -> {
            revisionSignal.get();
            conflictTypeSignal.get();
            ValidationResult submit = submitValidationSignal.get();
            int count = 0;
            for (Computed<String> error : errorSignals.values()) {
                String msg = error.get();
                if (msg != null && !msg.isEmpty()) {
                    count++;
                }
            }
            if (!isConflictActive() && submit != null && submit.hasErrors()) {
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
     * @return 关联的草稿容器（replace 后为新引用）
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
     * @return 可保存派生（isDirty && !hasError && !requiresReload）
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
     * @return 错误字段计数派生（error 非空的字段数量；冲突不计 _config）
     */
    public ReadableSignal<Integer> errorCountSignal() {
        return errorCountSignal;
    }

    /**
     * @return 结构化冲突类型受控源（只读）
     */
    public ReadableSignal<SaveOutcome.ConflictType> conflictTypeSignal() {
        return conflictTypeSignal;
    }

    /**
     * @return 是否需要丢弃编辑并重新加载（STALE_DRAFT_BASE / AUTHORITY_MODIFIED）
     */
    public boolean requiresReload() {
        SaveOutcome.ConflictType t = conflictTypeSignal.get();
        return t == SaveOutcome.ConflictType.STALE_DRAFT_BASE
                || t == SaveOutcome.ConflictType.AUTHORITY_MODIFIED_DURING_SAVE;
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
     * 写入最近一次保存结局中的冲突类型（由 ConfigScreen 在 save 后调用）。
     *
     * <p>普通字段编辑<strong>不会</strong>清除 requiresReload 冲突；
     * 仅 {@link #replaceDraft} / 成功保存 / 显式 clear 可清。</p>
     *
     * @param type 冲突类型，null 按 NONE
     */
    public void setConflictType(SaveOutcome.ConflictType type) {
        conflictTypeSignal.set(type == null ? SaveOutcome.ConflictType.NONE : type);
        bumpRevision();
    }

    /**
     * 写入最近一次提交校验结果（INVALID 时由 ConfigScreen 调用）。
     *
     * <p>先从 DraftBuffer 回读全部字段，确保 validator 或并发编辑引发的冲突结果不会让 UI
     * Signal 停留在旧镜像；随后再设置字段/全局错误。</p>
     *
     * <p>若 outcome 为冲突，请改用 {@link #applySaveFailure(SaveOutcome)}，
     * 避免把冲突诊断注入字段 errorCount。</p>
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
     * 按 {@link SaveOutcome} 统一接入失败态：冲突走 conflictType + 友好反馈，不注入字段 error；
     * 普通 INVALID 写 submitValidation。
     *
     * @param outcome 保存结局，非 null
     */
    public void applySaveFailure(SaveOutcome outcome) {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (outcome.isConflict()) {
            // 保留用户编辑供查看：resync 从 DraftBuffer 回读（draft 未变）
            resyncAllDraftSignals();
            // 不写 submitValidation（避免 _config 进 errorCount / 字段红字）
            submitValidationSignal.set(ValidationResult.ok());
            conflictTypeSignal.set(outcome.conflictType());
            setSaveFeedback(SaveFeedback.forConflict(outcome.conflictType()));
            bumpRevision();
            return;
        }
        if (outcome.status() == SaveOutcome.Status.INVALID) {
            conflictTypeSignal.set(SaveOutcome.ConflictType.NONE);
            ValidationResult validation = outcome.validation();
            if (validation == null) {
                validation = ValidationResult.ok();
            }
            setSubmitValidation(validation);
            String reason = validation.hasErrors()
                    ? validation.summary(48)
                    : "校验未通过";
            if (reason == null || reason.isEmpty()) {
                reason = "校验未通过";
            }
            setSaveFeedback(new SaveFeedback(SaveFeedback.Status.INVALID, "保存失败：" + reason));
            return;
        }
        // IO_FAILED
        conflictTypeSignal.set(SaveOutcome.ConflictType.NONE);
        clearSubmitStateQuiet();
        String reason = outcome.errorMessage();
        if (reason == null || reason.isEmpty()) {
            reason = "保存失败";
        }
        setSaveFeedback(new SaveFeedback(SaveFeedback.Status.IO_FAILED, "保存失败：" + reason));
        bumpRevision();
    }

    /**
     * 清空提交校验错误（编辑字段 / 保存成功 / 取消时调用）。
     *
     * <p>同时将 {@link #saveFeedbackSignal()} 置 {@link SaveFeedback#NONE}。
     * <b>不</b>清除 requiresReload 冲突——普通编辑不能清冲突。</p>
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
     * 同时清空上一轮提交错误与保存失败反馈（非 requiresReload 冲突），
     * 避免 custom INVALID 永久禁用保存；bump revision 让 errorSignal 重算。</p>
     *
     * <p>若存在 presentation seed，首次编辑后清除 seed（值已进入 draft）。</p>
     *
     * @param path  字段全路径
     * @param value 新的草稿值
     */
    public void onFieldEdit(String path, Object value) {
        Signal<Object> sig = draftSignals.get(path);
        if (sig == null) {
            return;
        }
        presentationSeeds.remove(path);
        draft().setDraft(path, value);
        // 从 DraftBuffer 回读深度只读副本，禁止调用方原对象或 Signal 读值反向污染
        sig.set(observableDraftValue(path));
        // 普通编辑可清提交校验与可重试冲突反馈；requiresReload 冲突保留（保存仍禁用）
        if (!requiresReloadActive()) {
            clearSubmitStateQuiet();
            conflictTypeSignal.set(SaveOutcome.ConflictType.NONE);
        } else {
            // 仍清普通 submit validation，但保留 conflictType
            submitValidationSignal.set(ValidationResult.ok());
        }
        bumpRevision();
    }

    /**
     * 展示态预填充：只更新 UI signal 镜像，不写 DraftBuffer（不进 candidate / YAML），dirty=false。
     *
     * <p>用于 SIMPLE_LIST / FontSort 等 Authority 为空时「打开即展示发现列表」：
     * 用户首次编辑/删除/拖拽时再经 {@link #onFieldEdit} 把完整可见列表写入 draft 并 dirty=true。</p>
     *
     * @param path  字段全路径
     * @param value 展示值
     */
    public void seedPresentation(String path, Object value) {
        Signal<Object> sig = draftSignals.get(path);
        if (sig == null) {
            return;
        }
        Object frozen = ValueCopy.freeze(ValueCopy.copyOf(value));
        presentationSeeds.put(path, frozen);
        sig.set(frozen);
        if (!requiresReloadActive()) {
            clearSubmitStateQuiet();
        }
        bumpRevision();
    }

    /**
     * 是否存在 presentation-only 展示种子（draft 尚未被用户编辑写入）。
     *
     * @param path 字段 path
     * @return 有 seed 时 true
     */
    public boolean hasPresentationSeed(String path) {
        return presentationSeeds.containsKey(path);
    }

    /**
     * 取 presentation seed 的防御只读副本；无 seed 时 null。
     *
     * @param path 字段 path
     * @return 只读展示值或 null
     */
    public Object getPresentationSeed(String path) {
        Object seed = presentationSeeds.get(path);
        return seed == null ? null : ValueCopy.freeze(ValueCopy.copyOf(seed));
    }

    /**
     * 预填充字段基线：旧实现会写 draft+current 破坏事务 base。
     *
     * <p><b>已弃用</b>：现委托 {@link #seedPresentation}，仅更新展示，不写 DraftBuffer。</p>
     *
     * @param path  字段全路径
     * @param value 预填充值
     * @deprecated 使用 {@link #seedPresentation(String, Object)}
     */
    @Deprecated
    public void seedFieldBaseline(String path, Object value) {
        seedPresentation(path, value);
    }

    /**
     * 安全替换底层 {@link DraftBuffer} 引用，保持本 adapter 的 Signal/Computed identity。
     *
     * <p>校验 schema 路径集合与每字段 {@link FieldType} 兼容；不兼容时抛
     * {@link IllegalArgumentException} 且旧状态完全不变。</p>
     *
     * <p>成功后：全字段 signal 同步为新 draft、清 presentation seed、
     * 清 submit validation / conflict / feedback、dirty 按新 draft 自然归零、
     * 按既有事务规则 bump revision 一次。</p>
     *
     * @param newDraft 新草稿（通常 {@code manager.openDraft()}），非 null
     * @throws IllegalArgumentException schema 不兼容
     */
    public void replaceDraft(DraftBuffer newDraft) {
        if (newDraft == null) {
            throw new IllegalArgumentException("newDraft must not be null");
        }
        ConfigSchema nextSchema = newDraft.schema();
        if (nextSchema == null) {
            throw new IllegalArgumentException("newDraft.schema must not be null");
        }
        // 兼容性：路径集合 + 每字段类型必须一致
        for (FieldSpec field : schema.allFields()) {
            FieldSpec other = nextSchema.field(field.path());
            if (other == null) {
                throw new IllegalArgumentException(
                        "replaceDraft rejected: missing path " + field.path());
            }
            if (other.type() != field.type()) {
                throw new IllegalArgumentException(
                        "replaceDraft rejected: type mismatch at " + field.path()
                                + " expected " + field.type() + " got " + other.type());
            }
        }
        for (FieldSpec field : nextSchema.allFields()) {
            if (schema.field(field.path()) == null) {
                throw new IllegalArgumentException(
                        "replaceDraft rejected: unexpected path " + field.path());
            }
        }

        this.draft = newDraft;
        presentationSeeds.clear();
        resyncAllDraftSignals();
        submitValidationSignal.set(ValidationResult.ok());
        conflictTypeSignal.set(SaveOutcome.ConflictType.NONE);
        saveFeedbackSignal.set(SaveFeedback.NONE);
        bumpRevision();
    }

    /**
     * 重置全部草稿为当前值：DraftBuffer.resetToCurrent + 逐字段 signal.set(current)。
     *
     * <p>同时清空提交错误与保存失败反馈（与 {@link #onFieldEdit} 一致）。
     * requiresReload 冲突下取消仍保留冲突态（须显式 reload）。</p>
     */
    public void resetToCurrent() {
        draft().resetToCurrent();
        presentationSeeds.clear();
        resyncAllDraftSignals();
        if (!requiresReloadActive()) {
            clearSubmitStateQuiet();
            conflictTypeSignal.set(SaveOutcome.ConflictType.NONE);
        } else {
            submitValidationSignal.set(ValidationResult.ok());
        }
        bumpRevision();
    }

    /**
     * 重置单字段草稿为默认值：DraftBuffer.resetFieldToDefault + signal.set(default)。
     *
     * <p>current 不变，故 default != current 时该字段 dirty=true。
     * 同时清空提交错误与保存失败反馈（非 requiresReload）。</p>
     *
     * @param path 字段全路径
     */
    public void resetFieldToDefault(String path) {
        FieldSpec field = schema.field(path);
        if (field == null) {
            return;
        }
        presentationSeeds.remove(path);
        draft().resetFieldToDefault(path);
        Signal<Object> sig = draftSignals.get(path);
        if (sig != null) {
            sig.set(observableDraftValue(path));
        }
        if (!requiresReloadActive()) {
            clearSubmitStateQuiet();
            conflictTypeSignal.set(SaveOutcome.ConflictType.NONE);
        } else {
            submitValidationSignal.set(ValidationResult.ok());
        }
        bumpRevision();
    }

    /**
     * 保存成功后刷新 current 派生。
     *
     * <p>保存事务可能把 NUMBER 字符串规范化为 Double；因此成功后也必须全字段回读，
     * 再清空提交错误/冲突并 bump revision，让 UI、draft 与 current 使用同一规范化值。</p>
     *
     * <p>presentation seed：仅清除已写入 draft 的字段 seed；Authority 仍空的列表展示 seed 保留，
     * 避免「只保存其他字段」后发现态列表从 UI 消失。</p>
     */
    public void afterSaveSync() {
        // 仅清除 draft 已有实质内容的 seed
        List<String> drop = new ArrayList<String>();
        for (String path : presentationSeeds.keySet()) {
            if (hasCommittedContent(path)) {
                drop.add(path);
            }
        }
        for (String path : drop) {
            presentationSeeds.remove(path);
        }
        resyncAllDraftSignals();
        submitValidationSignal.set(ValidationResult.ok());
        conflictTypeSignal.set(SaveOutcome.ConflictType.NONE);
        bumpRevision();
    }

    /**
     * draft 是否已有实质内容（非空 list / 非空标量）——用于决定是否丢弃 presentation seed。
     */
    private boolean hasCommittedContent(String path) {
        Object d = draft().getDraft(path);
        if (d == null) {
            return false;
        }
        if (d instanceof List) {
            return !((List<?>) d).isEmpty();
        }
        if (d instanceof String) {
            return !((String) d).isEmpty();
        }
        return true;
    }

    /**
     * 释放所有 Computed，后续不再重算与传播。
     */
    public void dispose() {
        for (Computed<?> c : allComputed) {
            c.dispose();
        }
        presentationSeeds.clear();
    }

    /**
     * 单字段错误：内置优先，其次提交校验。
     */
    private String mergedFieldError(String path) {
        String builtIn = draft().error(path);
        if (builtIn != null && !builtIn.isEmpty()) {
            return builtIn;
        }
        ValidationResult submit = submitValidationSignal.get();
        if (submit == null) {
            return null;
        }
        return submit.errorFor(path);
    }

    private boolean isConflictActive() {
        SaveOutcome.ConflictType t = conflictTypeSignal.get();
        return t != null && t != SaveOutcome.ConflictType.NONE;
    }

    private boolean requiresReloadActive() {
        SaveOutcome.ConflictType t = conflictTypeSignal.get();
        return t == SaveOutcome.ConflictType.STALE_DRAFT_BASE
                || t == SaveOutcome.ConflictType.AUTHORITY_MODIFIED_DURING_SAVE;
    }

    /**
     * 无条件排队清空提交错误 Signal + saveFeedback=NONE（不碰 conflictType）。
     */
    private void clearSubmitStateQuiet() {
        submitValidationSignal.set(ValidationResult.ok());
        saveFeedbackSignal.set(SaveFeedback.NONE);
    }

    /**
     * 读取适合暴露给 UI 的深度只读 draft 值。
     * 若存在 presentation seed 且 draft 仍等于 current（未用户编辑），优先返回 seed。
     */
    private Object observableDraftValue(String path) {
        Object seed = presentationSeeds.get(path);
        if (seed != null && !draft().isDirty(path)) {
            return ValueCopy.freeze(ValueCopy.copyOf(seed));
        }
        return ValueCopy.freeze(draft().getDraft(path));
    }

    /** 从 DraftBuffer 回读全部字段到只读 Signal 镜像（尊重 presentation seed）。 */
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
