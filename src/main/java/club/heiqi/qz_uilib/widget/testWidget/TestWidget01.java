package club.heiqi.qz_uilib.widget.testWidget;

import club.heiqi.qz_uilib.widget.ButtonWidget;
import club.heiqi.qz_uilib.widget.DraggableWidget;
import club.heiqi.qz_uilib.widget.Widget;
import club.heiqi.qz_uilib.widget.layout.CenterLayout;
import club.heiqi.qz_uilib.widget.layout.GridLayout;
import club.heiqi.qz_uilib.widget.layout.HorizontalLayout;
import club.heiqi.qz_uilib.widget.layout.VerticalLayout;
import org.lwjgl.opengl.Display;

public class TestWidget01 extends Widget {

    public TestWidget01() {
        super();
        this.setSize(Display.getWidth(),Display.getHeight())
                .setLayout(new CenterLayout());
        this.addChild(
                new Widget().setLayout(new VerticalLayout()).setSize(1080,1080)
                        .addChild(new Widget().setPerfectSize(-1, 512)
                                .addChild(new DraggableWidget().setSize(128,128).setLayout(new VerticalLayout())
                                        .addChild(new Widget())
                                        .addChild(new Widget())))
                        .addChild(new Widget().setLayout(new HorizontalLayout())
                                .addChild(new Widget())
                                .addChild(new Widget())
                                .addChild(new Widget()))
                        .addChild(new Widget().setPerfectSize(500, 300).setLayout(new GridLayout(3,9))
                                .addChild(new ButtonWidget())
                                .addChild(new Widget())
                                .addChild(new Widget())
                                .addChild(new ButtonWidget()))
                        .addChild(new ButtonWidget()));
    }
}
