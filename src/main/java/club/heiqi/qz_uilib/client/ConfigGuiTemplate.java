package club.heiqi.qz_uilib.client;

import club.heiqi.qz_uilib.widget.*;
import club.heiqi.qz_uilib.widget.layout.HorizontalLayout;
import club.heiqi.qz_uilib.widget.layout.VerticalLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Property;
import org.lwjgl.input.Keyboard;

import java.util.*;
import java.util.function.Consumer;

public class ConfigGuiTemplate extends BaseGUI {

    public HashMap<String, Runnable> saveOperators = new HashMap<>();
    public Map<Boolean, Integer> boolColorMap = new HashMap<>();
    public GuiScreen parent;

    public ConfigGuiTemplate(GuiScreen parent) {
        this.parent = parent;
        boolColorMap.put(false, 0xfff02020);
        boolColorMap.put(true, 0xff20f020);

        ListWidget configList = createConfigList();

        LabelWidget title = new LabelWidget().setText("配置列表");
        title.perfectWidth = -1;

        ButtonWithTextWidget saveButton = new ButtonWithTextWidget().setText("保存");
        ButtonWithTextWidget cancelButton = new ButtonWithTextWidget().setText("取消");

        saveButton.setCallBack(() -> {
            for (Map.Entry<String, Runnable> entry : saveOperators.entrySet()) {
                Runnable runnable = entry.getValue();
                runnable.run();
            }
            saveOperators.clear();
            saveConfigCallback();
        });
        cancelButton.setCallBack(() -> {
            saveOperators.clear();
        });

        Widget buttonGroup = new Widget().setLayout(new HorizontalLayout());
        saveButton.perfectWidth = cancelButton.perfectWidth = -1;
        buttonGroup.addChild(saveButton).addChild(cancelButton);
        buttonGroup.setPerfectSize(-1, Math.max(saveButton.perfectHeight, cancelButton.perfectHeight) + buttonGroup.insideMargins*2);

        root.setLayout(new VerticalLayout());
        root.addChild(title);
        root.addChild(configList);
        root.addChild(buttonGroup);
    }

    public ListWidget createConfigList() {
        List<ConfigCategory> categories = getCategory();
        ListWidget configList = new ListWidget();

        for (ConfigCategory category : categories) {
            String categoryName = category.getName();
            LabelWidget categoryLabel = new LabelWidget().setText(categoryName);
            categoryLabel.setPerfectSize(-1,64);

            Set<Map.Entry<String, Property>> entries = category.entrySet();
            for (Map.Entry<String, Property> entry : entries) {
                String title = entry.getKey();
                Property property = entry.getValue();
                LabelWidget titleLabel = new LabelWidget().setText(title);
                titleLabel.setTooltip(property.comment);

                // 根据属性类型选择填充
                Property.Type type = property.getType();
                Widget valueWidget = new Widget();
                switch (type) {
                    case INTEGER -> {
                        int initValue = property.getInt();

                        IntegerEditWidget edit = new IntegerEditWidget();
                        edit.content = String.valueOf(initValue);
                        edit.setPerfectSize(-1,32+edit.insideMargins*2);
                        valueWidget = edit;
                        // 设置回调
                        Consumer<String> onTextChange = (value) -> {
                            int intValue = edit.getIntValue();
                            // 确认值在合法范围内
                            if (intValue >= Integer.parseInt(property.getMinValue()) && intValue <= Integer.parseInt(property.getMaxValue())) {
                                saveOperators.put(title, () -> {
                                    property.set(intValue);
                                });
                            }
                        };
                        edit.setTextChangeCallBack(onTextChange);
                    }
                    case BOOLEAN -> {
                        boolean initValue = property.getBoolean();

                        ButtonWithTextWidget edit = new ButtonWithTextWidget();
                        edit.setText(String.valueOf(initValue)).setTextColor(boolColorMap.get(initValue));
                        edit.setPerfectSize(-1,32+edit.insideMargins*2);
                        valueWidget = edit;
                        edit.setCallBack(() -> {
                            boolean setValue = false;
                            if (edit.text.equalsIgnoreCase("false")) {
                                setValue = true;
                            }
                            else {
                                setValue = false;
                            }
                            edit.setText(String.valueOf(setValue)).setTextColor(boolColorMap.get(setValue));
                            edit.perfectWidth = -1;
                            final boolean setValue2 = setValue;
                            saveOperators.put(title, () -> {
                                property.set(setValue2);
                            });
                        });
                    }
                    case STRING -> {
                        String initValue = property.getString();

                        TextEditWidget edit = new TextEditWidget();
                        edit.setContent(initValue);
                        edit.setPerfectSize(-1,32+edit.insideMargins*2);
                        valueWidget = edit;
                        Consumer<String> onTextChange = (value) -> {
                            saveOperators.put(title, () -> {
                                property.set(value);
                            });
                        };
                        edit.setTextChangeCallBack(onTextChange);
                    }
                    case DOUBLE -> {
                        double initValue = property.getDouble();

                        DoubleEditWidget edit = new DoubleEditWidget();
                        edit.setContent(String.valueOf(initValue));
                        edit.setPerfectSize(-1,32+edit.insideMargins*2);
                        valueWidget = edit;
                        // 设置回调
                        Consumer<String> onTextChange = (value) -> {
                            double doubleValue = edit.getDoubleValue();
                            // 确认值在合法范围内
                            if (doubleValue >= Double.parseDouble(property.getMinValue()) && doubleValue <= Double.parseDouble(property.getMaxValue())) {
                                saveOperators.put(title, () -> {
                                    property.set(doubleValue);
                                });
                            }
                        };
                        edit.setTextChangeCallBack(onTextChange);
                    }
                }
                if (property.isList()) {
                    // TODO
                }
                titleLabel.perfectWidth = valueWidget.perfectWidth = -1;
                // 元素主体
                Widget hW = new Widget().setLayout(new HorizontalLayout());
                hW.addChild(titleLabel);
                hW.addChild(valueWidget);
                // 最后记得设置元素最佳大小
                hW.setPerfectSize(-1,Math.max(titleLabel.perfectHeight, valueWidget.perfectHeight)+hW.insideMargins*2);
                // 添加到列表组件
                configList.addChild(hW);
            }
        }

        return configList;
    }

    /**需要重写该方法以此实现内容填充*/
    public List<ConfigCategory> getCategory() {
        return new ArrayList<>();
    }

    /**重写此方法以此实现配置保存 - 即你在Config类中执行加载配置的代码 config.getXxx()*/
    public void saveConfigCallback() {

    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}
