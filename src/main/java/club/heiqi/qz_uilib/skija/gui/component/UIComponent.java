package club.heiqi.qz_uilib.skija.gui.component;

import aurelienribon.tweenengine.TweenManager;
import io.github.humbleui.skija.Canvas;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.util.ArrayList;
import java.util.List;

/**
 * 基础组件类, 内部含有自动更新的动画管理器{@code TweenManager}, 你可以使用它来创建自己的动画效果, 动画使用方式请参考 Universal Tween Engine
 * <br>
 * 坐标使用屏幕百分比坐标
 */
public abstract class UIComponent {
    public static Logger LOG = LogManager.getLogger();
    // 动画管理器
    public TweenManager manager = new TweenManager();
    long lastTime ;
    // 属性
    public float x, y;
    public float width, height; // 组件坐标和宽高 单位百分比
    // 父组件
    @Nullable
    public UIComponent parent;
    // 子组件记录
    public List<UIComponent> childs = new ArrayList<>();
    /**穿透模式 - 组件如点击之类的操作可以穿透到下层父组件上<br>
     * 功能特性: 只有最外部的组件可以阻断，其嵌套的子组件仍会传递
     * */
    public boolean penetrate = true;
    public boolean mouseContain = false;

    /**
     * 使用百分比单位创建
     * @param x 在左边 % 多少
     * @param y 在顶部 % 多少
     * @param width 占屏幕宽度 %
     * @param height 占屏幕高度 %
     * <br>--------------------<br>
     * 其继承类中大多含有{@code set_xxx}的链式调用函数可用, 请多留意<p/>
     * 如有每帧逻辑可以重写{@code onTick}逻辑<br>
     * 初始化后坐标变为绝对值坐标而不是百分比坐标了<br>
     * 当窗口缩放后，MC的GUI会自动重新创建
     */
    public UIComponent(float x, float y, float width, float height) {
        int sw = Display.getWidth(); int sh = Display.getHeight();
        x = sw*x; y = sh*y; width = sw*width; height = sh*height; // 将百分比转为绝对值坐标
        this.x = x; this.y = y; this.width = width; this.height = height;
        lastTime = System.currentTimeMillis();
    }

    public void draw(Canvas canvas) {
        if (!childs.isEmpty()) {
            for (UIComponent child : childs) child.draw(canvas);
        }
    }

    /**
     * 当鼠标在组件内时每帧调用<br>
     * 并将 {@code mouseContain} 状态调整为 true
     */
    public void onHoverTick(boolean transmit) {
        int mx = Mouse.getX(); int my = Display.getHeight() - Mouse.getY();
        mouseContain = true;

        if (transmit && !childs.isEmpty()) {
            for (UIComponent child : childs) {
                if (child.contains(mx,my)) child.onHoverTick(transmit);
            }
        }
    }

    /**
     * 当鼠标不在组件内时每帧调用<br>
     * 并将 {@code mouseContain} 状态调整为 false
     */
    public void onMouseOutTick() {
        int mx = Mouse.getX(); int my = Display.getHeight() - Mouse.getY();
        mouseContain = false;
        pressed = false;
        if (!childs.isEmpty()) {
            for (UIComponent child : childs) {
                if (!child.contains(mx,my)) child.onMouseOutTick();
            }
        }
    }

    public abstract void onDragTick();

    /**
     * 用于父组件被拖拽时跟随移动
     * @param vec
     */
    public void followMove(Vector2i vec) {
        if (!childs.isEmpty()) {
            for (UIComponent child : childs) {
                child.followMove(vec);
            }
        }
        x = x + vec.x; y = y + vec.y;
    }

    public boolean onPreRelease(boolean transmit) {
        int mx = Mouse.getX(); int my = Display.getHeight() - Mouse.getY();
        boolean penetrate = true;
        if (transmit && !childs.isEmpty()) {
            for (UIComponent component : childs) {
                if (component.contains(mx,my)) {
                    component.onPreRelease(transmit);
                    penetrate = component.canPenetrate() && penetrate; // 出现一个阻止的就会阻止穿透
                }
            }
        }
        return penetrate;
    }

    public Runnable clickedTask;
    /**
     * 当在子组件内时，会由父组件进行判断是否触发该函数<br>
     * 任意时刻在该组件上释放都会触发该函数，如果需要制作clicked效果请使用 {@code pressed} 字段进行判断处理
     * @param transmit 是否进行正向穿透 - 默认为true 为false会阻止某些更新逻辑
     * @return 返回是否可以穿透执行逻辑
     */
    public boolean onRelease(boolean transmit) {
        int mx = Mouse.getX(); int my = Display.getHeight() - Mouse.getY();
        boolean penetrate = true;
        if (transmit && !childs.isEmpty()) {
            for (UIComponent component : childs) {
                if (component.contains(mx,my)) {
                    component.onRelease(transmit);
                    penetrate = component.canPenetrate() && penetrate; // 出现一个阻止的就会阻止穿透
                }
            }
        }
        return penetrate;
    }

    public boolean onPostRelease(boolean transmit) {
        int mx = Mouse.getX(); int my = Display.getHeight() - Mouse.getY();
        boolean penetrate = true;
        if (transmit && !childs.isEmpty()) {
            for (UIComponent component : childs) {
                if (component.contains(mx,my)) {
                    component.onPreRelease(transmit);
                    penetrate = component.canPenetrate() && penetrate; // 出现一个阻止的就会阻止穿透
                }
            }
        }
        pressed = false;
        return penetrate;
    }

    public boolean onPrePressTick(boolean transmit) {
        int mx = Mouse.getX(); int my = Display.getHeight() - Mouse.getY();
        // 更新子组件
        boolean penetrate = true;
        if (transmit && !childs.isEmpty()) {
            for (UIComponent component : childs) {
                if (component.contains(mx,my)) {
                    component.onPrePressTick(true);
                    penetrate = component.canPenetrate() && penetrate; // 出现一个阻止的就会阻止穿透
                }
            }
        }
        if (penetrate) pressed = true; // 只有穿透时才设置为true
        return penetrate;
    }

    public boolean pressed = false;

    /**
     *
     * @return 是否进行穿透更新，返回false阻止父级的事件应用
     */
    public boolean onPressTick(boolean transmit) {
        int mx = Mouse.getX(); int my = Display.getHeight() - Mouse.getY();
        // 更新子组件
        boolean penetrate = true;
        if (!childs.isEmpty()) {
            for (UIComponent component : childs) {
                if (component.contains(mx,my)) {
                    component.onPressTick(true);
                    penetrate = component.canPenetrate() && penetrate; // 出现一个阻止的就会阻止穿透
                }
            }
        }
        return penetrate;
    }

    /**
     * 每帧更新</p>
     * 务必使用super调用父类方法，否则动画可能会失效
     */
    public void onTick() {
        long delta = System.currentTimeMillis() - lastTime;
        manager.update((float) delta / 1000);
        lastTime = System.currentTimeMillis();
        // 更新子组件
        if (!childs.isEmpty()) {
            int mx = Mouse.getX(); int my = Display.getHeight() - Mouse.getY();
            for (UIComponent component : childs) {
                component.onTick();
                // MouseOutTick也需要更新
                if (!component.contains(mx,my)) {
                    component.onMouseOutTick();
                }
            }
        }
    }

    /**
     * 检查鼠标是否在组件内部
     * @param px 鼠标x
     * @param py 鼠标y
     * @return true 在组件内部 false 不在组件内部
     */
    public boolean contains(float px, float py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    /**
     * 检查组件是否可以穿透传递事件
     * @return
     */
    public boolean canPenetrate() {
        return penetrate;
    }

    public <T extends UIComponent> T setClickedTask(Runnable runnable) {
        clickedTask = runnable; return (T) this;
    }

    /**
     * 设置父组件 - 并自动将自身设置为父组件的子组件
     * @param parent
     */
    public <T extends UIComponent> T setParent(UIComponent parent) {
        this.parent = parent;
        this.parent.addChildren(this);
        return (T)this;
    }

    /**
     * 设置组件是否可以穿透传递事件
     * @param b
     * @return
     * @param <T>
     */
    public <T extends UIComponent> T setPenetrate(boolean b) {
        this.penetrate = b; return (T) this;
    }

    public <T extends UIComponent> T setWidth(float width) {
        this.width = width; return (T) this;
    }
    public <T extends UIComponent> T setHeight(float height) {
        this.height = height; return (T) this;
    }

    /**
     * 添加指定子组件
     * @param child
     * @return
     * @param <T>
     */
    public <T extends UIComponent> T addChildren(UIComponent child) {
        this.childs.add(child);
        return (T)this;
    }

    /**
     * 移除指定子组件
     * @param child
     * @return
     * @param <T>
     */
    public <T extends UIComponent> T removeChildren(UIComponent child) {
        this.childs.remove(child);
        return (T)this;
    }

    /**
     * 设置默认动画效果 - 例如鼠标悬停 - 点击等
     */
    public <T extends UIComponent> T setDefaultTween() {
        return (T)this;
    }
}
