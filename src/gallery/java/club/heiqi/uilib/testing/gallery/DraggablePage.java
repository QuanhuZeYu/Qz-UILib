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
        root.add(GalleryStyle.label(context,() -> "更多参考交互",theme,22,false));
        Signal<String> result=context.domain().signal("正常放下会更新此处；取消不会提交。");
        root.add(GalleryStyle.label(context,result,theme,14,true));
        SceneNode variants=GalleryStyle.grid(context,theme);
        variants.add(variant(context,theme,"独立把手","只拖动四点把手，正文保持原交互。","handle",result));
        variants.add(variant(context,theme,"双启动把手","左右两个把手都能拾取同一元素。","dual",result));
        variants.add(variant(context,theme,"拖动预览","原槽保持，独立预览沿当前输入范围显示。","overlay",result));
        variants.add(variant(context,theme,"只沿横轴","指针与键盘都只改变横向位置。","horizontal",result));
        variants.add(variant(context,theme,"只沿纵轴","指针与键盘都只改变纵向位置。","vertical",result));
        variants.add(variant(context,theme,"网格吸附","按 24 像素网格移动。","grid",result));
        variants.add(variant(context,theme,"容器边界","卡片保持在当前示例容器内。","parent",result));
        variants.add(variant(context,theme,"窗口边界","拖动不能越过当前窗口视口。","viewport",result));
        variants.add(variant(context,theme,"距离激活","移动超过 8 像素才开始，轻点不算放下。","distance",result));
        variants.add(variant(context,theme,"延时激活","按住 250 毫秒且移动不超过 5 像素开始。","delay",result));
        root.add(variants);
        return root;
    }
    private static SceneNode variant(ScenePageContext context,ReadableSignal<SceneTheme> theme,String title,String help,String kind,Signal<String> result) {
        SceneNode card=GalleryStyle.card(theme).size(Size.fill(1),Size.fixed(230));
        card.add(GalleryStyle.label(context,() -> title,theme,18,false));
        card.add(GalleryStyle.label(context,() -> help,theme,13,true));
        SceneNode stage=new SceneNode().size(Size.fill(1),Size.fill(1)).padding(12);
        SceneNode body=GalleryStyle.centeredRow().size(Size.natural(),Size.natural()).padding(14).gap(10);
        SceneNode handle=GalleryStyle.dragGrip(theme);
        body.add(handle).add(GalleryStyle.label(context,() -> "拾起我",theme,15,false));
        SceneNode second=GalleryStyle.dragGrip(theme);if(kind.equals("dual"))body.add(second);
        SceneDraggable.State state=SceneDraggable.state(context.domain());
        SceneDraggable.Options options=new SceneDraggable.Options().onDrop(drop -> result.set(title+"：已放下（"+drop.deltaX+", "+drop.deltaY+"）"));
        switch(kind) {
            case "handle":options=options.handles(handle);break;
            case "dual":options=options.handles(handle,second);break;
            case "horizontal":options=options.axis(SceneDraggable.Axis.X);break;
            case "vertical":options=options.axis(SceneDraggable.Axis.Y);break;
            case "grid":options=options.grid(24,24);break;
            case "parent":options=options.bounds(SceneDraggable.Bounds.PARENT);break;
            case "viewport":options=options.bounds(SceneDraggable.Bounds.VIEWPORT);break;
            case "distance":options=options.activation(8,0,0);break;
            case "delay":options=options.activation(0,250,5);break;
            case "overlay":options=options.overlay(GalleryStyle.frame(GalleryStyle.label(context,() -> "拾起我 · 预览",theme,15,false).padding(14),theme).size(Size.natural(),Size.natural()));break;
            default:throw new AssertionError(kind);
        }
        SceneNode surface=GalleryStyle.frame(body,theme).size(Size.natural(),Size.natural());
        SceneNode node=SceneDraggable.create(context,surface,state,options);
        surface.bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(() -> state.snapshot().get().active?theme.get().hover:
                context.runtime().focusRouter().focused().get()==node?theme.get().pressed:theme.get().panel,SceneNode::background));
        stage.add(node);card.add(stage);return GalleryStyle.frame(card,theme);
    }
    private static SceneNode draggable(ScenePageContext context,SceneNode body,ReadableSignal<SceneTheme> theme,SceneDraggable.State state) {
        SceneNode surface=GalleryStyle.frame(body,theme).size(Size.natural(),Size.natural());
        SceneNode node=SceneDraggable.create(context,surface,state);
        // 视觉跟随正式焦点信号；不建立页面级键盘监听或另一套目标身份。
        surface.bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(() -> {
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
