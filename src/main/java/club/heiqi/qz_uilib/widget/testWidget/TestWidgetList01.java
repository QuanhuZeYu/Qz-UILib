package club.heiqi.qz_uilib.widget.testWidget;

import club.heiqi.qz_uilib.widget.LabelWidget;
import club.heiqi.qz_uilib.widget.ListWidget;
import club.heiqi.qz_uilib.widget.Widget;
import club.heiqi.qz_uilib.widget.layout.FixedParentLayout;
import club.heiqi.qz_uilib.widget.layout.HorizontalLayout;
import club.heiqi.qz_uilib.widget.layout.SimpleVerticalLayout;
import club.heiqi.qz_uilib.widget.layout.VerticalLayout;
import org.lwjgl.opengl.Display;

import java.util.Arrays;

public class TestWidgetList01 extends Widget {

    public TestWidgetList01() {
        super();
        Widget horizonComponent = new Widget().setLayout(new HorizontalLayout());
        horizonComponent.insideMargins = 0;
        LabelWidget label1 = new LabelWidget().setText("横向宽度测试");
        LabelWidget label2 = new LabelWidget().setText("🦊🦝😡🤒🤕💀");
        label2.perfectWidth = label1.perfectWidth = -1;
        horizonComponent.addChild(label1);
        horizonComponent.addChild(label2);
        horizonComponent.setPerfectSize(-1, Math.max(label1.perfectHeight, label2.perfectHeight) + horizonComponent.insideMargins*2);

        Widget listComponent = new ListWidget().setSize(Display.getWidth(), Display.getHeight());
        listComponent.addChild(new LabelWidget().setText("测试文本001"));
        listComponent.addChild(new LabelWidget().setText("这是测试文本😻🙀😽😾😺🐶"));
                listComponent.addChild(new Widget());
                listComponent.addChild(horizonComponent);
                listComponent.addChild(new Widget());

        this.setSize(Display.getWidth(), Display.getHeight());
        this.setLayout(new VerticalLayout());
        this.addChild(listComponent);
    }
}
