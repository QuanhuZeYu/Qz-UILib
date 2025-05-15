package club.heiqi.qz_uilib.skija.gui;

import club.heiqi.qz_uilib.config.Config;
import club.heiqi.qz_uilib.skija.alignment.StringAlignUtils;
import club.heiqi.qz_uilib.skija.font.FontLoader;
import club.heiqi.qz_uilib.skija.gui.component.UIComponent;
import club.heiqi.qz_uilib.skija.gui.component.defaultStyle.BackGround;
import club.heiqi.qz_uilib.skija.gui.component.defaultStyle.buttons.Button;
import club.heiqi.qz_uilib.skija.gui.component.defaultStyle.Label;
import club.heiqi.qz_uilib.skija.gui.component.defaultStyle.checkBoxs.CheckBoxWithLabel;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import org.joml.Vector2f;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

public class ExampleGUI extends BaseGUI {

    @Override
    public void addComponent() {
        // 背景坐标
        float width = 0.8f; float height = 0.8f;
        float x = 0.1f; float y = 0.1f;
        UIComponent background = new BackGround(x,y,width,height).setBlur(false).setDefaultTween();
        // 按钮1坐标
        width = 0.18f; height = 0.08f;
        x = 0.1895f; y = 0.7528f;
        UIComponent button1 = new Button(x,y,width,height).setText("取消")
            .setDefaultFillColor(0x00000000).setDefaultStrokeColor(0xFFFF7070)
            .setDefaultHoverFillColor(0x00000000).setDefaultHoverStrokeColor(0xFFffffff)
            .setDefaultPressFillColor(0x00000000).setDefaultPressStrokeColor(0xFFd11f1a)
            .setDefaultTween()
            .setParent(background)
            .setClickedTask(() -> {
                this.mc.displayGuiScreen(null);
                this.mc.setIngameFocus();
            });
        // 按钮2坐标
        x = 0.63f;
        UIComponent button2 = new Button(x,y,width,height).setText("确认")
            .setDefaultTween()
            .setParent(background)
            .setClickedTask(() -> {
                this.mc.displayGuiScreen(null);
                this.mc.setIngameFocus();
            });
        // 文本标签元素
        x = 0.5f; y = 0.19f; width = 0.075f; height = 0.04907407f;
        UIComponent label1 = new Label(x,y,width,height).setText("测试窗口")
            .setAlign(StringAlignUtils.Align.CENTER_TO_TARGET)
            .setParent(background);
        // 勾选框
        x = 0.1895f; y = 0.2888f; width = 0.62083f; height = 0.04444f;
        CheckBoxWithLabel checkBoxWithLabel = new CheckBoxWithLabel(x,y,width,height).setText("DEBUG输出开关")
            .setCheckBoxCallBack((state) -> {
                Config.setConfig(Config.ConfigField.DEBUG_LOG.field,state);
            })
            .setCheckBoxStateHook(() -> Config.debugLOG)
            .setParent(background);
        components.add(background);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int x = Mouse.getX(); int y = Mouse.getY();
        canvas.addRender(canvas1 -> {
            Font font = new Font(FontLoader.getDefaultFont().getTypeface()).setSize(FontLoader.DYNAMIC_FONT_SIZE*Display.getHeight());
            Paint strPaint = new Paint().setColor(0xFFFFFFFF).setAntiAlias(true);
            String debug1 = "鼠标位置:"+x+","+y+" | "+mouseX+","+mouseY;
            String debug2 = "鼠标状态:"+mouseState.name();
            float leading = font.getMetrics().getLeading();
            float fontHeight = font.getMetrics().getHeight();
            float spacing = leading+fontHeight; // 行间距
            Vector2f debug1Pos = StringAlignUtils.textBLToTarget(debug1,font,new Vector2f(0, Display.getHeight()));
            canvas1.drawString(debug1,debug1Pos.x,debug1Pos.y,font,strPaint);
            canvas1.drawString(debug2,debug1Pos.x,debug1Pos.y-spacing,font,strPaint);
            strPaint.close(); font.close();
        });
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
