package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import java.util.Objects;

/** 补充说明绑定在原触发器上；不替换控件、焦点或业务动作。 */
public final class SceneTooltip {
    public enum Side { TOP, BOTTOM, LEFT, RIGHT }
    public enum Align { START, CENTER, END }
    private SceneTooltip(){}
    public static final class Content {
        public final TextContent text;
        public final String shortcut;
        public final String plainText;
        private final Object key;
        public Content(TextContent text,String shortcut){this(text,shortcut,text);}
        private Content(TextContent text,String shortcut,Object key){
            this.text=Objects.requireNonNull(text,"提示内容");this.shortcut=Objects.requireNonNull(shortcut,"快捷键说明");this.key=key;plainText=key instanceof String?(String)key:null;
        }
        public static Content text(String value){return text(value,"");}
        public static Content text(String value,String shortcut){return new Content(TextContent.plain(value),shortcut,value);}
        /** 普通无链接文字可复用相同值；富文本/typed声明保持原有身份代际。 */
        public boolean same(Content other){return other!=null&&(key==other.key||key instanceof String&&key.equals(other.key))&&shortcut.equals(other.shortcut);}
    }
    public static final class Options {
        public final Side side;
        public final Align align;
        public final int sideOffset,alignOffset;
        public final float maxWidthEm;
        public final long openDelayNanos,closeDelayNanos,skipDelayNanos;
        public final boolean arrow,hoverable,enabled;
        public final TextPolicy.Direction direction;
        public Options(){this(Side.TOP,Align.CENTER,4,0,24,0,0,400_000_000L,true,true,true,TextPolicy.Direction.AUTO);}
        private Options(Side side,Align align,int sideOffset,int alignOffset,float width,long open,long close,long skip,boolean arrow,boolean hoverable,boolean enabled,TextPolicy.Direction direction){
            this.side=Objects.requireNonNull(side);this.align=Objects.requireNonNull(align);this.direction=Objects.requireNonNull(direction);
            if(!Float.isFinite(width)||width<=0||open<0||close<0||skip<0)throw new IllegalArgumentException("无效提示宽度或延迟");
            this.sideOffset=sideOffset;this.alignOffset=alignOffset;maxWidthEm=width;openDelayNanos=open;closeDelayNanos=close;skipDelayNanos=skip;
            this.arrow=arrow;this.hoverable=hoverable;this.enabled=enabled;
        }
        public Options placement(Side side,Align align,int sideOffset,int alignOffset){return new Options(side,align,sideOffset,alignOffset,maxWidthEm,openDelayNanos,closeDelayNanos,skipDelayNanos,arrow,hoverable,enabled,direction);}
        public Options timing(long open,long close,long skip){return new Options(side,align,sideOffset,alignOffset,maxWidthEm,open,close,skip,arrow,hoverable,enabled,direction);}
        public Options width(float em){return new Options(side,align,sideOffset,alignOffset,em,openDelayNanos,closeDelayNanos,skipDelayNanos,arrow,hoverable,enabled,direction);}
        public Options arrow(boolean value){return new Options(side,align,sideOffset,alignOffset,maxWidthEm,openDelayNanos,closeDelayNanos,skipDelayNanos,value,hoverable,enabled,direction);}
        public Options hoverable(boolean value){return new Options(side,align,sideOffset,alignOffset,maxWidthEm,openDelayNanos,closeDelayNanos,skipDelayNanos,arrow,value,enabled,direction);}
        public Options enabled(boolean value){return new Options(side,align,sideOffset,alignOffset,maxWidthEm,openDelayNanos,closeDelayNanos,skipDelayNanos,arrow,hoverable,value,direction);}
        public Options direction(TextPolicy.Direction value){return new Options(side,align,sideOffset,alignOffset,maxWidthEm,openDelayNanos,closeDelayNanos,skipDelayNanos,arrow,hoverable,enabled,value);}
    }
    /** 正式控件可提供当前纯值事实，自动省略/图标/物品提示不另建一套呈现。 */
    public static final class Hint {
        public final Content content;
        public final Options options;
        public final SceneTheme theme;
        public Hint(Content content,Options options,SceneTheme theme){this.content=Objects.requireNonNull(content);this.options=Objects.requireNonNull(options);this.theme=Objects.requireNonNull(theme);}
    }
    public static SceneNode attach(ScenePageContext page,SceneNode trigger,ReadableSignal<Content> content,ReadableSignal<Options> options,ReadableSignal<SceneTheme> theme){
        Objects.requireNonNull(page).domain().checkWrite();
        return Objects.requireNonNull(trigger).bind(NodeBinding.tooltip(content,options,theme));
    }
}
