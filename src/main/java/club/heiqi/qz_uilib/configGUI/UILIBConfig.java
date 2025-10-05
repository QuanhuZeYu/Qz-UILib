package club.heiqi.qz_uilib.configGUI;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.MyMod;
import club.heiqi.qz_uilib.client.BaseGUI;
import club.heiqi.qz_uilib.widget.*;
import club.heiqi.qz_uilib.widget.layout.*;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.lwjgl.opengl.Display;

import java.util.Map;

public class UILIBConfig extends BaseGUI {

    public UILIBConfig() {
        super();
        ListWidget listWidget = new ListWidget();
        listWidget.perfectHeight = -1;
        ConfigCategory category = Config.config.getCategory(Configuration.CATEGORY_GENERAL);

        for (Map.Entry<String, Property> entry : category.entrySet()) {
            String name = entry.getKey();
            Property value = entry.getValue();
            if (value.isBooleanValue()) {
                // 标签元素
                LabelWidget label = new LabelWidget().setText(name);
                label.perfectWidth = -1;
                LabelWidget buttonText = new LabelWidget().setText(value.getString());

                // 按钮元素创建
                ButtonWidget button = new ButtonWidget();
                button.setLayout(new CenterLayout());
                button.addChild(buttonText);
                button.setPerfectSize(-1, buttonText.perfectHeight+button.insideMargins*2);
                button.setCallBack(() -> {
                    if (value.getBoolean()) {
                        buttonText.setText("FALSE").setTextColor(0xfff02222);
                        button.setPerfectSize(-1,buttonText.perfectHeight+button.insideMargins*2);
                        value.set(false);

                    }
                    else {
                        buttonText.setText("TRUE").setTextColor(0xff22f022);
                        button.setPerfectSize(-1,buttonText.perfectHeight+button.insideMargins*2);
                        value.set(true);
                    }
                });

                // 列表元素
                Widget hW = new Widget().setLayout(new HorizontalLayout());
                hW.addChild(label);
                hW.addChild(button);
                hW.setPerfectSize(-1,Math.max(label.perfectHeight, button.perfectHeight) + hW.insideMargins*2);
                // hW.addChild(button).setPerfectSize(-1, Math.max(label.perfectHeight, button.perfectHeight));


                listWidget.addChild(hW);

            }
        }
        // for (int i = 0; i < 50; i ++) {
            LabelWidget label1  = new LabelWidget().setText("测试文本");
            listWidget.addChild(label1);
        // }
        listWidget.setPerfectSize(-1,-1);
        ListWidget list2 = new ListWidget();
        // for (int i = 0; i < 50; i ++) {
            LabelWidget label2  = new LabelWidget().setText("测试文本");
            list2.addChild(label2);
        // }
        list2.setPerfectSize(-1,-1);
        Widget hW = new Widget().setLayout(new HorizontalLayout());
        hW.setPerfectSize(-1,-1);
        hW.addChild(listWidget);
        hW.addChild(list2);

        // 根容器布局和大小初始化
        root = new Widget().setSize(Display.getWidth(),Display.getHeight());
        root.setLayout(new VerticalLayout());
        root.addChild(new LabelWidget().setText("可配置列表"));
        root.addChild(hW);
        LabelWidget saveButtonText = new LabelWidget().setText("保存");
        ButtonWidget saveButton = new ButtonWidget();
        saveButton.setLayout(new CenterLayout());
        saveButton.setCallBack(() -> {
            MyMod.proxy.config.load();
            Config.config.save();
        });
        saveButton.addChild(saveButtonText);
        // 编辑框测试
        TextEditWidget editWidget = new TextEditWidget();
        editWidget.setPerfectSize(-1, 32+editWidget.insideMargins*2);
        root.addChild(editWidget);
        // 整数编辑框测试
        IntegerEditWidget integerEditWidget = new IntegerEditWidget();
        integerEditWidget.setPerfectSize(-1,32+integerEditWidget.insideMargins*2);
        root.addChild(integerEditWidget);
        // 小数编辑测试
        DoubleEditWidget doubleEditWidget = new DoubleEditWidget();
        doubleEditWidget.setPerfectSize(-1,32+doubleEditWidget.insideMargins*2);
        root.addChild(doubleEditWidget);

        root.addChild(saveButton);

        // 设置缩放回调
        root.setResizeCallback((vec2) -> {
            float width = vec2.x;
            float height = vec2.y;
            root.setSize(width,height);
        });
    }

    GuiScreen parent;
    public UILIBConfig(GuiScreen parent) {
        this();
        this.parent = parent;
    }


}
