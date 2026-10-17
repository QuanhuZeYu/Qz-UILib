package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTooltip;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextPolicy;

/** 使用正式提示声明的可运行案例；说明、方向、延迟与键盘行为可直接体验。 */
final class TooltipPage {
    private TooltipPage(){}
    static SceneNode create(ScenePageContext page,ReadableSignal<SceneTheme> theme,Runnable back){
        SceneNode root=new SceneNode().size(Size.fill(1),Size.fill(1)).padding(28).gap(24).scrollable(true);
        root.add(GalleryStyle.header(page,theme,"Tooltip","将指针停在操作上，或按 Tab 聚焦，查看补充说明。",back));
        SceneNode directions=GalleryStyle.card(theme).gap(16);
        directions.add(GalleryStyle.label(page,()->"方向与空间",theme,20,false));
        SceneNode row=GalleryStyle.centeredRow().size(Size.fill(1),Size.natural()).gap(12).padding(24);
        String[] names={"上侧提示","下侧提示","左侧提示","右侧提示"};
        SceneTooltip.Side[] sides=SceneTooltip.Side.values();
        for(int i=0;i<sides.length;i++){
            final String name=names[i];final SceneTooltip.Side side=sides[i];
            row.add(SceneTooltip.attach(page,GalleryStyle.button(page,()->name,theme,()->{}),
                    ()->SceneTooltip.Content.text(name+"：提示会沿当前窗口自动避让。","Tab · 键盘可见"),
                    ()->new SceneTooltip.Options().placement(side,SceneTooltip.Align.CENTER,8,0),theme));
        }
        directions.add(row);root.add(GalleryStyle.frame(directions,theme));
        SceneNode details=GalleryStyle.card(theme).gap(16);
        details.add(GalleryStyle.label(page,()->"完整说明",theme,20,false));
        SceneNode examples=GalleryStyle.centeredRow().size(Size.fill(1),Size.natural()).gap(12).scrollable(true);
        SceneTooltip.Content rich=new SceneTooltip.Content(TextContent.richTags("<b>多行与属性文字</b>\n这里保留 <color value=\"#f59e0b\">独立颜色</color>，提示内容随主题与字号更新。"),"Ctrl + S");
        examples.add(SceneTooltip.attach(page,GalleryStyle.button(page,()->"多行说明",theme,()->{}),()->rich,()->new SceneTooltip.Options().width(18),theme));
        examples.add(SceneTooltip.attach(page,GalleryStyle.button(page,()->"延迟说明",theme,()->{}),()->SceneTooltip.Content.text("停留后出现；离开立即撤销尚未显示的说明。"),()->new SceneTooltip.Options().timing(450_000_000L,100_000_000L,400_000_000L),theme));
        examples.add(SceneTooltip.attach(page,GalleryStyle.button(page,()->"RTL 对齐",theme,()->{}),()->SceneTooltip.Content.text("مرحبا — תיאור נוסף"),()->new SceneTooltip.Options().direction(TextPolicy.Direction.RTL).placement(SceneTooltip.Side.TOP,SceneTooltip.Align.START,8,0),theme));
        SceneNode disabled=new SceneNode().size(Size.natural(),Size.natural()).focusable(true)
                .add(GalleryStyle.button(page,()->"不可用功能",theme,()->{throw new IllegalStateException("禁用按钮不得激活");}).enabled(false));
        examples.add(SceneTooltip.attach(page,disabled,()->SceneTooltip.Content.text("此功能当前不可用，先选择一个有效来源。"),()->new SceneTooltip.Options(),theme));
        details.add(examples).add(GalleryStyle.label(page,()->"Esc 关闭当前提示；再次进入或重新聚焦可查看。提示不会阻挡按钮与滚动。",theme,14,true));
        root.add(GalleryStyle.frame(details,theme));return root;
    }
}
