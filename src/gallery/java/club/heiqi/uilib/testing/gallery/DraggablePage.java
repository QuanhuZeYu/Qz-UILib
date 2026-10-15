package club.heiqi.uilib.testing.gallery;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneDraggable;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontSource;

/** 用户指定的dnd-kit普通Draggable参考；只声明内容、拖中样式和状态，不维护坐标或输入。 */
public final class DraggablePage {
    private static final List<FontSource> UI=fonts();
    private DraggablePage() {}
    private static List<FontSource> fonts() {
        try{return Collections.singletonList(FontSource.resource("draggable-ui",1,DraggablePage.class.getClassLoader(),
                "assets/qz_uilib/fonts/ui/NotoSansSC-VF.ttf",false,Collections.<String>emptySet()));}
        catch(IOException failure){throw new ExceptionInInitializerError(failure);}
    }
    public static SceneNode create(ScenePageContext context,ReadableSignal<SceneTheme> inherited,Runnable leave) {
        ReadableSignal<SceneTheme> theme=() -> inherited.get().withTextStyle(new TextStyle("draggable-ui",15,inherited.get().textStyle.color,UI));
        SceneNode root=new SceneNode().size(Size.fill(1),Size.fill(1)).padding(28).gap(20).scrollable(true);
        SceneNode header=new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural()).gap(16);
        header.add(GalleryStyle.label(context,() -> "Draggable",theme,30,false).size(Size.fill(1),Size.natural()));
        header.add(GalleryStyle.button(context,() -> "返回场景概览",theme,leave));root.add(header);
        root.add(GalleryStyle.label(context,() -> "拾起、移动、释放。普通内容也可以拖动。",theme,16,false));
        root.add(GalleryStyle.label(context,() -> "参考：dnd-kit / React / Draggable",theme,13,true));
        SceneNode grid=GalleryStyle.grid(context,theme);
        grid.add(example(context,theme,"单个元素",new String[]{"拖动我"}));
        grid.add(example(context,theme,"多个独立元素",new String[]{"元素 A","元素 B","元素 C"}));root.add(grid);
        root.add(GalleryStyle.label(context,() -> "按住卡片拖动，松开后回到原位。Tab 聚焦后，空格拾起，方向键移动，空格释放，Esc 取消。",theme,14,true));
        Signal<Integer> clicks=context.domain().signal(0);
        SceneDraggable.State state=SceneDraggable.state(context.domain());
        SceneNode content=new SceneNode().size(Size.fixed(260).bounds(0,400),Size.natural()).padding(16).gap(12);
        content.add(GalleryStyle.label(context,() -> "拖动外框，按钮仍可点击",theme,14,false));
        content.add(GalleryStyle.button(context,() -> "普通按钮 · "+clicks.get(),theme,() -> clicks.set(clicks.get()+1)));
        root.add(GalleryStyle.label(context,() -> "交互内容",theme,18,false));
        root.add(draggable(context,content,theme,state));
        return root;
    }
    private static SceneNode draggable(ScenePageContext context,SceneNode body,ReadableSignal<SceneTheme> theme,SceneDraggable.State state) {
        SceneNode node=SceneDraggable.create(context,GalleryStyle.frame(body,theme).size(Size.natural(),Size.natural()),state);
        // 视觉跟随正式焦点信号；不建立页面级键盘监听或另一套目标身份。
        body.bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(() -> {
            boolean dark=theme.get().panel==GalleryStyle.DARK_BACKGROUND;
            return state.snapshot().get().active?(dark?0xff172554:0xffdbeafe)
                    :body.runtime()!=null&&body.runtime().focusRouter().focused().get()==node?(dark?0xff1e293b:0xffeef2ff):0x00000000;
        },SceneNode::background));
        return node;
    }
    private static SceneNode example(ScenePageContext context,ReadableSignal<SceneTheme> theme,String title,String[] labels) {
        SceneNode card=GalleryStyle.card(theme).size(Size.fill(1),Size.fixed(300));
        card.add(GalleryStyle.label(context,() -> title,theme,18,false));
        SceneNode items=new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.fill(1)).gap(18);
        for(String name:labels) {
            SceneDraggable.State state=SceneDraggable.state(context.domain());
            SceneNode body=new SceneNode().size(Size.fixed(labels.length==1?180:110),Size.natural()).padding(16).gap(6);
            body.add(GalleryStyle.label(context,() -> name,theme,15,false));
            body.add(GalleryStyle.label(context,() -> state.snapshot().get().active?"拖动中":"按住拖动",theme,12,true));
            items.add(draggable(context,body,theme,state));
        }
        card.add(items);return GalleryStyle.frame(card,theme);
    }
}
