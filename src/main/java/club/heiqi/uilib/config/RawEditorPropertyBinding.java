package club.heiqi.uilib.config;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.ConfigSerializer;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentCodeEditorChangeHandler;
import club.heiqi.uilib.ui.control.DocumentCodeEditorControl;
import club.heiqi.uilib.ui.control.DocumentCodeEditorErrorHandler;
import club.heiqi.uilib.ui.control.DocumentCodeEditorSyntaxSupport;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 源码编辑器字段绑定。
 *
 * <p>针对声明为 {@code raw} / {@code json} / {@code yaml} 等源码 hint 的字段，整段子树以
 * JSON 或 YAML 文本形式呈现给用户编辑。binding 内部维护「最近一次解析成功的 ConfigNode」，
 * 用户编辑触发 {@link DocumentCodeEditorChangeHandler} 时立即尝试用
 * {@link ConfigSerializer#parse(String, ConfigFormat)} 解析当前文本：</p>
 * <ul>
 *   <li>解析成功：清空错误行集合与错误文案，更新缓存 ConfigNode，标记草稿变更；</li>
 *   <li>解析失败：捕获异常不抛出，把错误行兜底为 {0}（解析器无法可靠给出具体行号），
 *       通过 {@link DocumentCodeEditorControl#setErrorLines(Set)} 与
 *       {@link DocumentCodeEditorControl#setError(String)} 反馈给控件；缓存 ConfigNode
 *       <b>不</b>更新，从而保护草稿不被语法错误污染。</li>
 * </ul>
 *
 * <p>{@link #applyDraft()} 写回时只写缓存 ConfigNode，因此即便用户当前文本非法，
 * 保存的也是最后一次合法解析结果，不会写坏配置树。</p>
 */
final class RawEditorPropertyBinding extends ModernConfigPropertyBindings.ConfigPropertyBinding {

    private final ConfigFormat rawFormat;
    private final DocumentCodeEditorSyntaxSupport.Language language;

    private DocumentCodeEditorControl codeEditor;
    /** 初次渲染时从 config 序列化出的文本，用作 isDirty 基线。 */
    private String initialSerializedText;
    /** 最近一次解析成功的文本，用户编辑后更新；applyDraft 写回的就是它对应的 ConfigNode。 */
    private String lastValidText;
    /** 最近一次解析成功的 ConfigNode；解析失败时保留旧值，保护草稿。 */
    private ConfigNode lastValidNode;
    /** 最近一次解析错误文案；为空串表示当前文本合法。 */
    private String lastErrorMessage = "";

    RawEditorPropertyBinding(MutableConfig config, String path, ConfigNode node,
            ModernConfigTemplateScreen.FieldSpec fieldSpec, ModernConfigTypeInference.Result inference,
            ModernConfigPropertyBindings.ChangeListener changeListener) {
        super(config, path, node, fieldSpec, inference, changeListener);
        this.rawFormat = resolveRawFormat(inference);
        this.language = resolveLanguage(this.rawFormat);
    }

    @Override
    protected ElementNode createEditorElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        codeEditor = new DocumentCodeEditorControl(document)
                .setPlaceholder(resolvePlaceholder())
                .setMaxLength(ModernConfigPropertyBindings.resolveMaxLength(getFieldSpec(),
                        ModernConfigPropertyBindings.DEFAULT_LONG_TEXT_MAX_LENGTH))
                .setSurfaceColors(0xFF222233, 0xFF555577, theme.focusBorderColor, 0xFF333344, 0xFF444455)
                .setChangeHandler(new DocumentCodeEditorChangeHandler() {
                    @Override
                    public void onContentChanged(club.heiqi.uilib.ui.control.DocumentCodeEditorChangeEvent event) {
                        processUserText(event.getText());
                    }
                })
                .setErrorHandler(new DocumentCodeEditorErrorHandler() {
                    @Override
                    public void onErrorsUpdated(
                            club.heiqi.uilib.ui.control.DocumentCodeEditorErrorUpdateEvent event) {
                        // 控件自身已经渲染错误状态，binding 不需要额外动作
                    }
                });
        codeEditor.getElement().setAttribute("data-modern-config-control", "raw-editor");
        codeEditor.getElement().style().setWidth(UiStyleLength.percent(1.0F));
        codeEditor.setLanguage(language);
        // 程序化 setText 不触发 changeHandler：构造初始基线
        initialSerializedText = serializeCurrentNode();
        lastValidText = initialSerializedText;
        lastValidNode = getCurrentNode();
        codeEditor.setText(initialSerializedText);
        return codeEditor.getElement();
    }

    @Override
    boolean isDirty() {
        // 文本级别的相等比较：用户当前最后一次合法文本与初次基线不一致即视为脏。
        return !Objects.equals(initialSerializedText, lastValidText);
    }

    @Override
    void restoreCurrentValue() {
        initialSerializedText = serializeCurrentNode();
        lastValidText = initialSerializedText;
        lastValidNode = getCurrentNode();
        lastErrorMessage = "";
        if (codeEditor != null) {
            codeEditor.setErrorLines(Collections.<Integer>emptySet());
            codeEditor.setError("");
            codeEditor.setText(initialSerializedText);
        }
        notifyDraftChanged();
    }

    @Override
    void restoreDefaultValue() {
        Object defaultValue = getDefaultValue();
        ConfigNode defaultNode = defaultValue instanceof ConfigNode ? (ConfigNode) defaultValue : null;
        String serialized = serializeNode(defaultNode);
        initialSerializedText = serialized;
        lastValidText = serialized;
        lastValidNode = defaultNode;
        lastErrorMessage = "";
        if (codeEditor != null) {
            codeEditor.setErrorLines(Collections.<Integer>emptySet());
            codeEditor.setError("");
            codeEditor.setText(serialized);
        }
        notifyDraftChanged();
    }

    @Override
    String validateDraft() {
        return lastErrorMessage == null || lastErrorMessage.isEmpty() ? null : lastErrorMessage;
    }

    @Override
    void applyDraft() {
        if (lastValidNode == null) {
            // 还没有解析成功的值，不写回，避免覆盖既有配置
            return;
        }
        getConfig().set(getPath(), lastValidNode);
    }

    /**
     * 处理用户编辑后的新文本：尝试解析、更新缓存与错误反馈。
     *
     * <p>此方法同时作为 changeHandler 入口与测试入口（package-private）。</p>
     *
     * @param text 用户当前输入的完整文本
     */
    void processUserText(String text) {
        if (codeEditor != null && text == null) {
            text = codeEditor.getText();
        }
        String safeText = text == null ? "" : text;
        try {
            ConfigNode parsed = ConfigSerializer.parse(safeText, rawFormat);
            lastValidNode = parsed;
            lastValidText = safeText;
            lastErrorMessage = "";
            if (codeEditor != null) {
                codeEditor.setErrorLines(Collections.<Integer>emptySet());
                codeEditor.setError("");
            }
            notifyDraftChanged();
        } catch (ConfigException ex) {
            // 解析失败：不更新 lastValidNode/lastValidText，保护草稿
            lastErrorMessage = resolveErrorMessage(ex);
            if (codeEditor != null) {
                codeEditor.setErrorLines(Collections.<Integer>singleton(0));
                codeEditor.setError(lastErrorMessage);
            }
        } catch (RuntimeException ex) {
            // 防御：解析器实现可能抛出非 ConfigException 的运行时异常
            lastErrorMessage = resolveErrorMessage(ex);
            if (codeEditor != null) {
                codeEditor.setErrorLines(Collections.<Integer>singleton(0));
                codeEditor.setError(lastErrorMessage);
            }
        }
    }

    /**
     * 返回 binding 当前持有的源码编辑器控件，仅供同包测试与 5-D 屏幕层使用。
     *
     * @return 控件实例；尚未调用 createEditorElement 时为 null
     */
    DocumentCodeEditorControl getCodeEditor() {
        return codeEditor;
    }

    /**
     * 返回最近一次解析成功的 ConfigNode；解析失败时保留旧值。
     *
     * @return 最近一次合法 ConfigNode；尚无解析记录时为 null
     */
    ConfigNode getLastValidNode() {
        return lastValidNode;
    }

    /**
     * 返回最近一次解析错误文案；为空表示当前文本合法。
     *
     * @return 错误文案
     */
    String getLastErrorMessage() {
        return lastErrorMessage == null ? "" : lastErrorMessage;
    }

    /**
     * 返回当前源码格式（JSON/YAML）。
     *
     * @return 源码格式
     */
    ConfigFormat getRawFormat() {
        return rawFormat;
    }

    private ConfigFormat resolveRawFormat(ModernConfigTypeInference.Result inference) {
        ConfigFormat format = inference == null ? null : inference.getRawFormat();
        return format == null ? ConfigFormat.JSON : format;
    }

    private static DocumentCodeEditorSyntaxSupport.Language resolveLanguage(ConfigFormat format) {
        if (format == ConfigFormat.YAML) {
            return DocumentCodeEditorSyntaxSupport.Language.YAML;
        }
        if (format == ConfigFormat.JSON) {
            return DocumentCodeEditorSyntaxSupport.Language.JSON;
        }
        return DocumentCodeEditorSyntaxSupport.Language.PLAIN;
    }

    private String resolvePlaceholder() {
        ModernConfigTemplateScreen.FieldSpec fieldSpec = getFieldSpec();
        if (fieldSpec != null) {
            String placeholder = fieldSpec.getPlaceholder();
            if (placeholder != null && !placeholder.isEmpty()) {
                return placeholder;
            }
        }
        return "以 " + rawFormat + " 形式编辑当前配置";
    }

    private String serializeCurrentNode() {
        return serializeNode(getCurrentNode());
    }

    private String serializeNode(ConfigNode node) {
        try {
            return ConfigSerializer.toString(node, rawFormat);
        } catch (RuntimeException ex) {
            // 节点序列化失败时退回空文本，避免渲染崩溃；用户重写后会触发 parse 校验
            return "";
        }
    }

    private static String resolveErrorMessage(Throwable ex) {
        if (ex == null) {
            return "源码解析失败";
        }
        String message = ex.getMessage();
        if (message == null || message.isEmpty()) {
            return "源码解析失败：" + ex.getClass().getSimpleName();
        }
        return message;
    }
}
