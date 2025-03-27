package club.heiqi.qz_uilib.skija.gui;

import club.heiqi.qz_uilib.skija.gui.component.UIComponent;
import club.heiqi.qz_uilib.skija.gui.component.defaultStyle.BackGround;
import club.heiqi.qz_uilib.skija.gui.component.defaultStyle.Button;

public class TestGUI extends BaseGUI {

    @Override
    public void addComponent() {
        // 背景坐标
        float width = 0.8f; float height = 0.8f;
        float x = 0.1f; float y = 0.1f;
        UIComponent background = new BackGround(x,y,width,height);
        // 按钮1坐标
        width = 0.18f; height = 0.08f;
        x = 0.1895f; y = 0.7528f;
        UIComponent button1 = new Button(x,y,width,height).setText("取消")
            .setFillColor(0x00000000).setStrokeColor(0xFFFF7070);
        // 按钮2坐标
        x = 0.63f;
        UIComponent button2 = new Button(x,y,width,height).setText("确认");

        components.add(background);
        components.add(button1);
        components.add(button2);
    }
}
