package club.heiqi.qz_uilib.widget;

import club.heiqi.qz_uilib.widget.layout.FixedParentLayout;

import java.util.Arrays;

public class TextButtonWidget extends Widget {

    ButtonWidget button = new ButtonWidget();
    LabelWidget label = new LabelWidget();
    public TextButtonWidget() {
        setLayout(new FixedParentLayout());
        addChild(button);
        addChild(label);
    }

    public TextButtonWidget setText(String text) {
        label.setText(text);
        return this;
    }

    @Override
    public void drawSelf() {
        super.drawSelf();
        button.drawSelf();
        label.drawSelf();
    }

    @Override
    public void applyLayout() {
        super.applyLayout();
        button.x = label.x = x;
        button.y = label.y = y;
        button.width = label.width = width;
        button.height = label.height = height;
    }
}
