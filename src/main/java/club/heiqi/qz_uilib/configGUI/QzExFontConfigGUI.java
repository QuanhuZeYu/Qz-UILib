package club.heiqi.qz_uilib.configGUI;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.fontsystem.FontManager;
import club.heiqi.qz_uilib.fontsystem.impl.ReplaceFontRender;
import club.heiqi.qz_uilib.gui.BaseGUI;
import club.heiqi.qz_uilib.widget.*;
import club.heiqi.qz_uilib.widget.layout.HorizontalLayout;
import club.heiqi.qz_uilib.widget.layout.VerticalLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.opengl.Display;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

public class QzExFontConfigGUI extends BaseGUI {

    public GuiScreen parent;
    public QzExFontConfigGUI(GuiScreen parent) {
        super();
        this.parent = parent;
    }

    public ListWidget createFontList() {
        FontManager fontManager = FontManager.getInstance();

        // 捕获原始的 LinkedHashSet 引用
        final java.util.LinkedHashSet<Font> originalFontSet = fontManager.fonts;
        final QzExFontConfigGUI self = this;

        // 1. 将 LinkedHashSet 转换为 List 以进行操作
        // 注意：这里使用了一个 ArrayList 来构建 GUI，但操作的是原始集合
        final java.util.List<Font> currentFontList = new ArrayList<>(originalFontSet);

        ListWidget fontList = new ListWidget();

        int i = 0;
        for (Font font : fontManager.fonts) {
            final Font currentFont = font;
            final int originalIndex = i;

            String fontName = font.getName();
            LabelWidget fontNameLabel = new LabelWidget().setText(fontName);
            fontNameLabel.perfectWidth = -1;

            Widget hW = new Widget().setLayout(new HorizontalLayout());

            Widget hW2 = new Widget().setLayout(new HorizontalLayout());
            LabelWidget tipLabel = new LabelWidget().setText("输入排序序号");

            IntegerEditWidget integerEditWidget = new IntegerEditWidget().setPerfectHeight(Arrays.asList(fontNameLabel));
            integerEditWidget.setContent(String.valueOf(i));
            integerEditWidget.setPerfectHeight(Arrays.asList(tipLabel));

            ButtonWithTextWidget submitButton = new ButtonWithTextWidget().setText("提交修改")
                    .setPerfectHeight(Arrays.asList(fontNameLabel));
            submitButton.setPerfectHeight(Arrays.asList(tipLabel));

            submitButton.setCallBack(() -> {
                int newIndex = integerEditWidget.getIntValue();

                // 检查索引是否有效
                if (newIndex < 0) {
                    newIndex = 0;
                } else if (newIndex >= currentFontList.size()) {
                    newIndex = currentFontList.size() - 1;
                }

                // **在临时 List 上执行移动操作**
                currentFontList.remove(currentFont); // 从临时列表移除
                currentFontList.add(newIndex, currentFont); // 插入到新位置

                // **清空并重建原始 LinkedHashSet**
                // 3.1 清空原始集合
                originalFontSet.clear();

                // 3.2 按 List 的新顺序重新添加所有元素
                originalFontSet.addAll(currentFontList);

                // 4. 重新加载 GUI
                self.initGui();
            });

            hW2.addChild(tipLabel);
            hW2.addChild(integerEditWidget);
            hW2.addChild(submitButton);
            integerEditWidget.perfectWidth = submitButton.perfectWidth = -1;
            hW2.setPerfectHeight(Arrays.asList(tipLabel, integerEditWidget, submitButton));

            hW.addChild(fontNameLabel);
            hW.addChild(hW2);
            fontNameLabel.perfectWidth = hW2.perfectWidth = -1;
            hW.setPerfectHeight(Arrays.asList(fontNameLabel, hW2));

            fontList.addChild(hW);
            i++;
        }

        return fontList;
    }

    @Override
    public void initGui() {
        super.initGui();
        // 重新设置根容器的内容，就像构造函数中做的那样
        this.root = new Widget().setSize(Display.getWidth(), Display.getHeight());
        root.setLayout(new VerticalLayout());

        LabelWidget title = new LabelWidget().setTextSize(48).setText("字体配置");
        title.perfectWidth = -1;
        root.addChild(title);

        // 搜索栏
        Widget searchGroup = new Widget().setPerfectSize(-1,-1);
        searchGroup.setLayout(new HorizontalLayout());
        LabelWidget searchTitle = new LabelWidget().setText("搜索字体:");
        TextEditWidget searchWidget = new TextEditWidget();
        searchWidget.perfectWidth = -1;
        searchWidget.setPerfectHeight(Arrays.asList(searchTitle));

        searchGroup.addChild(searchTitle);
        searchGroup.addChild(searchWidget);
        searchGroup.setPerfectHeight(Arrays.asList(searchTitle,searchWidget));
        root.addChild(searchGroup);

        // 字体列表
        ListWidget fontList = createFontList();
        // 用于搜索备份
        final ListWidget cacheList = createFontList();
        root.addChild(fontList);

        // 应用按钮
        ButtonWithTextWidget applyButton = new ButtonWithTextWidget().setText("应用配置");
        applyButton.perfectWidth = -1;
        applyButton.setCallBack(() -> {
            ReplaceFontRender.getInstance().reload();
            for (Font font : FontManager.getInstance().fonts) {
                LOG.info(font.getFontName());
            }
            initGui();
        });

        // 保存排序列表
        ButtonWithTextWidget saveSort = new ButtonWithTextWidget().setText("保存排序");
        saveSort.perfectWidth = -1;
        saveSort.setCallBack(() -> {
            ArrayList<String> sortResult = new ArrayList<>();
            for (Widget child : fontList.children) {
                if (child.children.isEmpty()) continue;
                for (Widget widget : child.children) {
                    if (widget instanceof LabelWidget labelWidget) {
                        sortResult.add(labelWidget.text);
                    }
                }
            }
            String[] fontSort = new String[sortResult.size()];
            for (int i = 0; i < sortResult.size(); i++) {
                fontSort[i] = sortResult.get(i);
            }
            Config.fontSort = fontSort;
            Config.config.get(Config.FONT_SYSTEM, "fontSort", fontSort).set(fontSort);
            Config.config.save();
        });

        // 应用 - 保存 组件组
        Widget applySaveGroup = new Widget().setLayout(new HorizontalLayout());
        applySaveGroup.addChild(applyButton)
                .addChild(saveSort)
                .setPerfectHeight(Arrays.asList(applyButton, saveSort));
        root.addChild(applySaveGroup);

        // 搜索栏回调
        searchWidget.setTextChangeCallBack((string) -> {
            // list下的每个元素 1->水平组件->2->标题+水平组件
            ArrayList<Widget> results = new ArrayList<>();
            for (Widget child : cacheList.children) {
                if (child.children.isEmpty()) continue;
                // 查看水平组件的子组件
                for (Widget widget : child.children) {
                    if (widget instanceof LabelWidget labelWidget) {
                        if (labelWidget.text.contains(string)) {
                            results.add(child);
                        }
                    }
                }
            }
            fontList.children.clear();
            fontList.children.addAll(results);
        });
    }

    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            if (parent != null)
                Minecraft.getMinecraft().displayGuiScreen(this.parent);
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }
}
