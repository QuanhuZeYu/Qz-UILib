package club.heiqi.qz_uilib.widget.testWidget;

import club.heiqi.qz_uilib.widget.LabelWidget;
import club.heiqi.qz_uilib.widget.ListWidget;
import club.heiqi.qz_uilib.widget.Widget;
import club.heiqi.qz_uilib.widget.layout.HorizontalLayout;
import club.heiqi.qz_uilib.widget.layout.VerticalLayout;
import org.lwjgl.opengl.Display;

public class TestWidgetList01 extends Widget {

    public TestWidgetList01() {
        super();
        Widget horizonComponent = new Widget().setLayout(new HorizontalLayout())
                .addChild(new LabelWidget().setText("横向宽度测试"))
                .addChild(new LabelWidget().setText("🦊🦝😡🤒🤕💀"));
        Widget listComponent = new ListWidget()
                .addChild(new LabelWidget().setText("测试文本001"))
                .addChild(new LabelWidget().setText("这是测试文本😻🙀😽😾😺🐶"))
                .addChild(new Widget())
                .addChild(horizonComponent)
                .addChild(new Widget())
                ;
        this.setSize(Display.getWidth(), Display.getHeight())
                .setLayout(new VerticalLayout())
                .addChild(listComponent);
    }
}
