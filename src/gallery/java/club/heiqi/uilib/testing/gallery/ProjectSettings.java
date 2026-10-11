package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Submission;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** 项目设置真实表单；草稿与已保存值分离，输入和历史由正式编辑控件管理。 */
final class ProjectSettings {
    private ProjectSettings() {}

    /** 导航保留本次返回的逻辑树即可保留草稿；重新创建则开始新的页面状态。 */
    static SceneNode create(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        Form form = new Form(context);
        SceneNode page = GalleryStyle.card(theme);
        page.add(GalleryStyle.label(context, () -> "项目设置", theme, 26, false));
        page.add(GalleryStyle.label(context,
                () -> "管理工坊项目的名称、制作批次与交接备注。保存后生效，未保存的修改会保留为草稿。",
                theme, 14, true));

        page.add(GalleryStyle.label(context, () -> "项目名称", theme, 15, false));
        page.add(SceneTextField.create(context, form.name, theme, () -> false,
                request -> form.save(form.name, request)));
        page.add(GalleryStyle.label(context, () -> "项目名称不能为空。", theme, 13, true));

        page.add(GalleryStyle.label(context, () -> "制作批次", theme, 15, false));
        page.add(SceneTextField.create(context, form.batch, theme, () -> false,
                request -> form.save(form.batch, request)));
        page.add(GalleryStyle.label(context, () -> "填写 1 至 99 的整数。", theme, 13, true));

        page.add(GalleryStyle.label(context, () -> "交接备注", theme, 15, false));
        SceneNode notes = SceneTextArea.create(context, form.notes, theme, () -> false,
                request -> form.save(form.notes, request));
        // 只声明正文视口尺寸，换行、滚动与光标显露仍由正式多行控件完成。
        notes.bind(NodeBinding.value(theme,
                (target, value) -> target.size(Size.fill(1),
                        Size.fixed(value.textStyle.size * 6 + value.padding * 2))));
        page.add(notes);
        page.add(GalleryStyle.label(context,
                () -> "Enter 换行；Ctrl+Enter 或下方按钮保存整份设置。", theme, 13, true));

        SceneNode actions = new SceneNode().axis(SceneNode.Axis.ROW)
                .size(Size.fill(1), Size.natural()).gap(12);
        actions.add(GalleryStyle.button(context, () -> "保存设置", theme,
                () -> form.save(null, null)));
        actions.add(GalleryStyle.button(context, () -> "恢复已保存", theme, form::restore));
        page.add(actions);
        SceneNode feedback = GalleryStyle.label(context, form.feedback, theme, 14, false);
        feedback.bind(NodeBinding.value(form.feedback,
                (target, value) -> target.visible(!value.isEmpty())));
        page.add(feedback);
        return page;
    }

    /** 全部状态只归本次页面；节点卸载不提交，也不取消独立草稿。 */
    private static final class Form {
        private final SceneEditState name;
        private final SceneEditState batch;
        private final SceneEditState notes;
        private final Signal<Settings> saved;
        private final Signal<String> feedback;

        private Form(ScenePageContext context) {
            Settings initial = new Settings("工坊计划", "12", "交接说明：材料到齐后开始制作。");
            name = new SceneEditState(context.domain(), initial.name);
            batch = new SceneEditState(context.domain(), initial.batch);
            notes = new SceneEditState(context.domain(), initial.notes, SceneEditState.Mode.MULTI_LINE);
            saved = context.domain().signal(initial);
            feedback = context.domain().signal("");
        }

        private void save(SceneEditState source, Submission existing) {
            // 不能用表单按钮强制接受其他字段尚未提交的输入法候选字。
            if (name.snapshot().get().composing || batch.snapshot().get().composing
                    || notes.snapshot().get().composing) {
                String reason = "请先完成输入法组字，再保存设置";
                if (existing != null) source.reject(existing, reason);
                feedback.set(reason);
                return;
            }
            String project = name.text().get().trim();
            String rawBatch = batch.text().get().trim();
            if (project.isEmpty()) {
                reject(name, source, existing, "项目名称不能为空");
                return;
            }
            int batches;
            try {
                batches = Integer.parseInt(rawBatch);
            } catch (NumberFormatException invalid) {
                reject(batch, source, existing, "批次必须是1至99的整数");
                return;
            }
            if (batches < 1 || batches > 99) {
                reject(batch, source, existing, "批次必须是1至99的整数");
                return;
            }
            Settings next = new Settings(project, String.valueOf(batches), notes.text().get());
            // request(true) 也适用于无编辑会话的初值；全部校验完成才接受三个字段。
            Submission nameRequest = request(name, source, existing);
            Submission batchRequest = request(batch, source, existing);
            Submission notesRequest = request(notes, source, existing);
            boolean acceptedName = name.accept(nameRequest, next.name);
            boolean acceptedBatch = batch.accept(batchRequest, next.batch);
            boolean acceptedNotes = notes.accept(notesRequest, next.notes);
            if (acceptedName && acceptedBatch && acceptedNotes) {
                saved.set(next);
                feedback.set("已保存：" + next.name + " · " + next.batch + " 批");
            }
        }

        private Submission request(SceneEditState state, SceneEditState source, Submission existing) {
            return state == source ? existing : state.request(true);
        }

        private void reject(SceneEditState invalid, SceneEditState source, Submission existing, String reason) {
            if (existing != null) source.reject(existing, reason);
            if (invalid != source) invalid.reject(invalid.request(true), reason);
            feedback.set(reason);
        }

        private void restore() {
            Settings value = saved.get();
            // 显式重置到上次整表保存值，清除旧错误、组字联系及撤销历史。
            name.reset(value.name);
            batch.reset(value.batch);
            notes.reset(value.notes);
            feedback.set("已恢复：" + value.name + " · " + value.batch + " 批");
        }
    }

    private static final class Settings {
        private final String name;
        private final String batch;
        private final String notes;

        private Settings(String name, String batch, String notes) {
            this.name = name;
            this.batch = batch;
            this.notes = notes;
        }
    }
}
