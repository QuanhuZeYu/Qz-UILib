package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.ElasticOptions;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.MotionValue;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleConsumer;

/** 通用水平范围控件；拖动预览、外部值和最终提交分开，沿唯一捕获与焦点路由。 */
public final class SceneSlider {
    private SceneSlider(){}
    public static final class Model {
        public final double min,max,step,keyStep,value,bufferStart,bufferEnd;
        public final boolean enabled;
        public final long token;
        public final ElasticOptions elastic;
        public Model(double min,double max,double step,double value,boolean enabled){this(min,max,step,step,value,enabled,0,Double.NaN,Double.NaN,null);}
        private Model(double min,double max,double step,double keyStep,double value,boolean enabled,long token,double start,double end,ElasticOptions elastic){
            if(!Double.isFinite(min)||!Double.isFinite(max)||max<min||!Double.isFinite(max-min)||!Double.isFinite(step)||step<=0||!Double.isFinite(value))throw new IllegalArgumentException("无效滑杆范围");
            this.min=min;this.max=max;this.step=step;this.keyStep=keyStep;this.value=clamp(value,min,max);this.enabled=enabled&&max>min;this.token=token;
            bufferStart=start;bufferEnd=end;this.elastic=elastic;
        }
        public Model buffered(double start,double end){if(!Double.isFinite(start)||!Double.isFinite(end)||end<start)throw new IllegalArgumentException("无效缓冲范围");return new Model(min,max,step,keyStep,value,enabled,token,start,end,elastic);}
        public Model keyboardStep(double amount){if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("无效键盘步长");return new Model(min,max,step,amount,value,enabled,token,bufferStart,bufferEnd,elastic);}
        public Model token(long token){return new Model(min,max,step,keyStep,value,enabled,token,bufferStart,bufferEnd,elastic);}
        /** 显式启用轨道软边缘；null 撤销形变，数值范围和提交方式保持不变。 */
        public Model elastic(ElasticOptions options){return new Model(min,max,step,keyStep,value,enabled,token,bufferStart,bufferEnd,options);}
        private double quantize(double value) {
            if (value <= min) return min;
            if (value >= max) return max;
            return clamp(min + Math.rint((value - min) / step) * step, min, max);
        }
    }
    public static final class Preview {
        public final boolean active;
        public final double value;
        Preview(boolean active,double value){this.active=active;this.value=value;}
    }
    public static SceneNode create(ScenePageContext page,ReadableSignal<Model> model,DoubleConsumer onChange,DoubleConsumer onCommit,ReadableSignal<SceneTheme> theme){
        Objects.requireNonNull(page).domain().checkWrite();
        Control control=new Control(page,Objects.requireNonNull(onChange),Objects.requireNonNull(onCommit),Objects.requireNonNull(theme));
        SceneNode node=new SceneNode().control(control).size(Size.fill(1),Size.natural()).focusable(true).clickFocus(true);
        node.bind(SceneControlBindings.declaration(model,(target,next)->control.configure(target,next)));
        node.bind(SceneControlBindings.declaration(()->{
            control.runtime.motionPolicy().get();control.runtime.interactionRevision().get();
            return control.actual.get();
        },(target,next)->control.refreshElastic(target)));
        node.bind((runtime,target,owner)->{
            control.runtime=runtime;
            return new BindingActivation(){public void refresh(){}public void close(){control.gesture.cancel();}};
        });
        return node;
    }
    public static ReadableSignal<Preview> preview(SceneNode node){if(!(node.control() instanceof Control))throw new IllegalArgumentException("不是滑杆");Control control=(Control)node.control();return control.gesture.preview::get;}
    /** 只读的有符号轨道形变，单位为 scene 像素；读取不会推进回弹。 */
    public static ReadableSignal<Double> elasticOffset(SceneNode node){if(!(node.control() instanceof Control))throw new IllegalArgumentException("不是滑杆");Control control=(Control)node.control();return control.elastic.offset::get;}
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
    /** 声明、运行时入口与业务回调；手势和弹性各自持有临时状态。 */
    private static final class Control implements SceneControl {
        SceneRuntime runtime;
        final Signal<Model> actual;
        final DoubleConsumer changed, committed;
        final ReadableSignal<SceneTheme> theme;
        final Gesture gesture;
        final Elastic elastic;
        Model model = new Model(0, 1, 1, 0, false);

        Control(ScenePageContext page, DoubleConsumer changed, DoubleConsumer committed, ReadableSignal<SceneTheme> theme) {
            runtime = page.runtime();
            actual = page.domain().signal(model);
            gesture = new Gesture(page.domain().signal(new Preview(false, 0)));
            elastic = new Elastic(page.domain().signal(0d));
            this.changed = changed;
            this.committed = committed;
            this.theme = theme;
        }

        void configure(SceneNode node, Model next) {
            Objects.requireNonNull(next, "滑杆声明");
            boolean invalid = !next.enabled || next.token != model.token;
            model = next;
            actual.set(next);
            node.enabled(next.enabled);
            if (invalid) gesture.cancel();
            else if (gesture.dragging) gesture.publish(true, clamp(gesture.value(), next.min, next.max));
            else gesture.publish(false, next.value);
            if (next.elastic == null) {
                elastic.clear();
                gesture.resetPickup();
            }
        }

        void refreshElastic(SceneNode node) {
            if (!runtime.interactive(node)) {
                gesture.cancel();
                return;
            }
            if (model.elastic == null) {
                elastic.clear();
                gesture.resetPickup();
                return;
            }
            elastic.refresh();
        }

        public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Arrays.asList(theme); }
        public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Arrays.asList(theme, gesture.preview, actual, elastic.offset); }
        public MeasuredSize measure(SceneNode node, Constraints c, SceneTextMeasurer text) {
            return new MeasuredSize(Math.round(theme.get().textStyle.logicalEm * 10),
                    Math.max(28, Math.round(theme.get().textStyle.logicalEm * 2)));
        }
        public club.heiqi.uilib.ui.scene.layout.ChildPlacements arrange(SceneNode node, LayoutBox box,
                club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer children) {
            return new club.heiqi.uilib.ui.scene.layout.ChildPlacements(java.util.Collections.emptyList());
        }

        public Object pointerTarget(SceneNode node, SceneGeometryView geometry, int x, int y) {
            SceneGeometryView.Entry entry = geometry.get(node);
            if (!model.enabled || entry == null) return null;
            return entry.box.contains(x, y)
                    || model.elastic != null && track(entry.box).containsThumb(model, gesture.value(), x, y) ? this : null;
        }
        public boolean pointerTargetValid(SceneNode node, Object target) { return model.enabled && target == this; }
        public boolean capturesPointer() { return model.enabled; }
        public boolean cancelCaptureOnEscape(SceneNode node) { return gesture.dragging; }
        public boolean passiveHover() { return true; }
        public void captureLost(SceneNode node) { gesture.cancel(); }
        public void focusChanged(SceneNode node, boolean focused, boolean eligible) { if (!focused) gesture.cancel(); }
        public void inputFrameCancelled(SceneNode node) { gesture.cancel(); }

        public long nextInputNanos(SceneNode node) {
            Model declaration = actual.get();
            Preview current = gesture.preview.get();
            double offset = elastic.offset.get();
            return declaration.elastic != null && (current.active || offset != 0) ? 0 : Long.MAX_VALUE;
        }
        public void inputFrame(SceneNode node, SceneGeometryView geometry, long now) { synchronizeElastic(node, geometry); }
        public void inputGeometryChanged(SceneNode node, SceneGeometryView geometry) { synchronizeElastic(node, geometry); }

        private void synchronizeElastic(SceneNode node, SceneGeometryView geometry) {
            if (model.elastic == null || !gesture.dragging && !elastic.active()) return;
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry == null || !runtime.interactive(node)) {
                gesture.cancel();
                return;
            }
            Track current = track(entry.box);
            if (gesture.dragging) gesture.synchronize(current);
            else elastic.constrain(current.baseWidth);
        }

        public void preparePointerRelease(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            if (gesture.dragging) gesture.move(geometry.get(node).box, event.x);
        }

        public void pointer(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            if (event.button != 0 && (event.kind == ScenePointerEvent.Kind.DOWN || event.kind == ScenePointerEvent.Kind.UP)) return;
            switch (event.kind) {
                case DOWN:
                    if (model.enabled) gesture.begin(geometry.get(node).box, event.x, event.y);
                    break;
                case MOVE:
                    if (gesture.dragging) gesture.move(geometry.get(node).box, event.x);
                    break;
                case UP:
                    if (gesture.dragging) gesture.release(node);
                    break;
                case CANCEL:
                    gesture.cancel();
                    break;
                default: break;
            }
        }

        public boolean edit(SceneNode node, SceneEditEvent event, SceneGeometryView geometry) {
            if (!model.enabled || event.command == null) return false;
            double next = gesture.value();
            switch (event.command) {
                case LEFT: case DOWN: next -= model.keyStep; break;
                case RIGHT: case UP: next += model.keyStep; break;
                case HOME: next = model.min; break;
                case END: next = model.max; break;
                case PAGE_DOWN: next -= model.keyStep * 10; break;
                case PAGE_UP: next += model.keyStep * 10; break;
                case ESCAPE:
                    if (!gesture.dragging) return false;
                    gesture.cancel();
                    return true;
                default: return false;
            }
            // 原指针 owner 直到 UP/ESC 结束；期间范围键不插入第二个提交，也不结束其预览。
            if (gesture.dragging) return true;
            elastic.clear();
            gesture.resetPickup();
            next = model.quantize(next);
            gesture.publish(false, next);
            changed.accept(next);
            committed.accept(next);
            return true;
        }

        private Track track(LayoutBox box) {
            return Track.create(box, theme.get().textStyle.logicalEm, model.elastic,
                    elastic.offset.get(), elastic.edge, elastic.engagement(gesture.dragging));
        }

        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {
            SceneGeometryView.Entry entry = geometry.get(node);
            boolean focusVisible = node.runtime() != null && node.runtime().focusRouter().focusVisible(node);
            track(entry.box).paint(paint, entry.clip, model, gesture.value(), new SceneAppearance.Palette(theme.get()), focusVisible);
        }

        /** 单次手势的预览与拾取基准；输入所有权沿唯一 scene 路由。 */
        private final class Gesture {
            final Signal<Preview> preview;
            boolean dragging;
            int pointerX;
            double pointerAdjustment, inputScale = 1;
            Track pickedTrack;

            Gesture(Signal<Preview> preview) { this.preview = preview; }
            double value() { return preview.get().value; }

            void publish(boolean active, double value) {
                Preview previous = preview.get();
                if (previous.active != active || Double.compare(previous.value, value) != 0)
                    preview.set(new Preview(active, value));
            }

            void resetPickup() {
                pointerAdjustment = 0;
                inputScale = 1;
                pickedTrack = null;
            }

            void cancel() {
                dragging = false;
                elastic.cancel();
                resetPickup();
                publish(false, model.value);
            }

            void begin(LayoutBox box, int x, int y) {
                Track current = track(box);
                if (model.elastic != null && elastic.active() && current.containsThumb(model, value(), x, y)) {
                    pickThumb(current, x);
                    return;
                }
                elastic.clear();
                resetPickup();
                dragging = true;
                move(box, x);
            }

            private void pickThumb(Track current, int x) {
                double value = value(), offset = elastic.offset.get();
                double raw = elastic.takeOver(current.baseWidth);
                pickedTrack = current;
                pointerX = x;
                dragging = true;
                double position = current.exactPosition(model, value);
                pointerAdjustment = position - x;
                double outside = current.outside(position);
                inputScale = outside * offset > 0 ? raw / outside : 1;
                publish(true, value);
                changed.accept(value);
            }

            void move(LayoutBox box, int x) {
                if (pickedTrack != null && x == pointerX) {
                    changed.accept(value());
                    return;
                }
                pickedTrack = null;
                pointerX = x;
                Track current = track(box);
                double position = position();
                double value = current.pointerValue(model, position);
                publish(true, value);
                elastic.follow(current.overshoot(position, inputScale), false);
                changed.accept(value);
            }

            void synchronize(Track current) {
                // 几何未换基时保持拾取形变；窗口/主题重排只更新预览，不回写业务值。
                if (pickedTrack != null && pickedTrack.sameBase(current)) return;
                pickedTrack = null;
                double position = position();
                publish(true, current.pointerValue(model, position));
                elastic.follow(current.overshoot(position, inputScale), false);
            }

            private double position() { return pointerX + pointerAdjustment; }

            void release(SceneNode node) {
                double value = value();
                dragging = false;
                pickedTrack = null;
                publish(false, value);
                elastic.release(node);
                committed.accept(value);
            }
        }

        /** 软边缘输入和释放轨迹；统一时间与活动调度仍由 SceneRuntime 提供。 */
        private final class Elastic {
            final Signal<Double> offset;
            final MotionValue rebound = new MotionValue();
            int edge;
            double overshoot, releaseOrigin;
            AutoCloseable scheduled;

            Elastic(Signal<Double> offset) { this.offset = offset; }
            boolean active() { return rebound.active(); }
            void cancel() { overshoot = 0; clear(); }

            void clear() {
                stopScheduled();
                rebound.snap(0, runtime.inputNanos());
                offset.set(0d);
                edge = 0;
                releaseOrigin = 0;
            }

            private void stopScheduled() {
                if (scheduled == null) return;
                try { scheduled.close(); }
                catch (Exception failure) { throw new IllegalStateException("滑杆回弹释放失败", failure); }
                finally { scheduled = null; }
            }

            double takeOver(int width) {
                double current = offset.get();
                stopScheduled();
                rebound.snap(current, runtime.inputNanos());
                overshoot = rawOffset(current, width);
                return overshoot;
            }

            private double rawOffset(double displacement, int width) {
                if (displacement == 0) return 0;
                double low = 0, high = width, target = Math.abs(displacement);
                ElasticOptions options = model.elastic;
                MotionPolicy policy = runtime.motionPolicy().get();
                // 拾取时反求共享阻力输入，避免对当前呈现再次压缩。
                for (int i = 0; i < 48; i++) {
                    double middle = (low + high) * .5;
                    if (options.displacement(middle, policy) < target) low = middle;
                    else high = middle;
                }
                return Math.copySign((low + high) * .5, displacement);
            }

            void follow(double raw, boolean keepEdge) {
                overshoot = raw;
                double current = model.elastic == null ? 0 : model.elastic.displacement(raw, runtime.motionPolicy().get());
                stopScheduled();
                rebound.snap(current, runtime.inputNanos());
                offset.set(current);
                if (!keepEdge) edge = Double.compare(raw, 0);
                releaseOrigin = 0;
            }

            void refresh() {
                if (gesture.dragging) {
                    follow(overshoot, gesture.pickedTrack != null);
                    return;
                }
                if (!rebound.active()) return;
                long now = runtime.inputNanos();
                double limit = Math.abs(model.elastic.displacement(Math.abs(overshoot), runtime.motionPolicy().get()));
                rebound.constrain(-limit, limit, now);
                rebound.retarget(0, runtime.motionPolicy().get().resolve(model.elastic.releaseMotion, true), now);
                offset.set(rebound.value());
                if (!rebound.active()) clear();
            }

            void constrain(int width) {
                overshoot = clamp(overshoot, -width, width);
                double limit = Math.abs(model.elastic.displacement(width, runtime.motionPolicy().get()));
                rebound.constrain(-limit, limit, runtime.inputNanos());
                offset.set(rebound.value());
                if (!rebound.active()) clear();
            }

            void release(SceneNode node) {
                if (model.elastic == null) {
                    clear();
                    return;
                }
                releaseOrigin = offset.get();
                MotionSpec strategy = runtime.motionPolicy().get().resolve(model.elastic.releaseMotion, true);
                rebound.release(releaseOrigin, 0, 0, strategy, runtime.inputNanos());
                offset.set(rebound.value());
                if (rebound.active()) scheduled = runtime.animate(now -> advance(node, now));
                else clear();
            }

            private boolean advance(SceneNode node, long now) {
                if (!runtime.interactive(node)) {
                    gesture.cancel();
                    return false;
                }
                boolean active = rebound.advance(now);
                offset.set(rebound.value());
                if (!active) {
                    scheduled = null;
                    edge = 0;
                    releaseOrigin = 0;
                }
                return active;
            }

            double engagement(boolean dragging) {
                if (dragging) return 1;
                return releaseOrigin == 0 ? 0 : Math.min(1, Math.abs(offset.get() / releaseOrigin));
            }
        }
    }

    /** 同一当前轨道供数值投影、圆形 thumb 命中和 PaintPlan 绘制使用。 */
    private static final class Track {
        final int baseLeft, baseRight, baseWidth, left, right, y, thickness, radius;

        Track(int baseLeft, int baseRight, int left, int right, int y, int thickness, int radius) {
            this.baseLeft = baseLeft;
            this.baseRight = baseRight;
            this.baseWidth = baseRight - baseLeft;
            this.left = left;
            this.right = right;
            this.y = y;
            this.thickness = thickness;
            this.radius = radius;
        }

        static Track create(LayoutBox box, float em, ElasticOptions options, double offset, int edge, double engagement) {
            int inset = Math.min(Math.max(5, Math.round(em * .45f)), box.width / 2);
            int thickness = Math.max(3, Math.round(em * .3f)), radius = Math.max(5, Math.round(em * .4f));
            if (options != null) {
                inset = Math.min(Math.max(radius + 6, box.width / 10), box.width / 2);
                double resting = clamp(em * .85, 12, 16), active = clamp(em * 1.2, 18, 22);
                double stretch = Math.min(1, Math.abs(offset) / options.maximumDisplacement);
                thickness = Math.max(1, (int) Math.round((resting + (active - resting) * engagement) * (1 - .22 * stretch)));
                radius = Math.max(radius, (thickness + 1) / 2 + 1);
            }
            int baseLeft = box.x + inset, baseRight = box.x + box.width - inset;
            // 回弹穿过零点时仍保留原边缘，不能把左端形变突然移到右端。
            int left = baseLeft + (edge < 0 ? (int) Math.round(offset) : 0);
            int right = baseRight + (edge > 0 ? (int) Math.round(offset) : 0);
            return new Track(baseLeft, baseRight, left, right, box.y + box.height / 2, thickness, radius);
        }

        boolean sameBase(Track other) { return baseLeft == other.baseLeft && baseRight == other.baseRight; }
        double outside(double x) { return x - clamp(x, baseLeft, baseRight); }

        double overshoot(double x, double inputScale) {
            // 极远捕获仍受当前轨道尺寸限制，阻力曲线留在 ElasticOptions。
            return clamp(outside(x) * inputScale, -baseWidth, baseWidth);
        }

        double pointerValue(Model model, double x) {
            double raw = model.min + (model.max - model.min) * clamp((x - (double) baseLeft) / Math.max(1, baseWidth), 0, 1);
            return model.quantize(raw);
        }

        double exactPosition(Model model, double value) {
            double ratio = model.max == model.min ? 0 : (clamp(value, model.min, model.max) - model.min) / (model.max - model.min);
            return left + (right - left) * ratio;
        }

        int position(Model model, double value) { return (int) Math.round(exactPosition(model, value)); }

        boolean containsThumb(Model model, double value, int x, int y) {
            double dx = x + .5 - position(model, value), dy = y + .5 - this.y;
            return dx * dx + dy * dy <= radius * (double) radius;
        }

        void paint(PaintPlanBuilder paint, LayoutBox clip, Model model, double value, SceneAppearance.Palette colors, boolean focusVisible) {
            ScenePaintShapes.rounded(paint, new LayoutBox(left, y - thickness / 2, Math.max(0, right - left), thickness), clip, colors.border, thickness / 2);
            if (Double.isFinite(model.bufferStart) && Double.isFinite(model.bufferEnd)) {
                int start = position(model, model.bufferStart), end = position(model, model.bufferEnd);
                ScenePaintShapes.rounded(paint, new LayoutBox(start, y - thickness / 2, Math.max(0, end - start), thickness), clip, colors.muted, thickness / 2);
            }
            int current = position(model, value);
            ScenePaintShapes.rounded(paint, new LayoutBox(left, y - thickness / 2, Math.max(0, current - left), thickness), clip, model.enabled ? colors.primary : colors.muted, thickness / 2);
            if (focusVisible)
                ScenePaintShapes.rounded(paint, new LayoutBox(current - radius - 3, y - radius - 3, radius * 2 + 6, radius * 2 + 6), clip, colors.muted, radius + 3);
            ScenePaintShapes.rounded(paint, new LayoutBox(current - radius, y - radius, radius * 2, radius * 2), clip, model.enabled ? colors.primary : colors.muted, radius);
            ScenePaintShapes.rounded(paint, new LayoutBox(current - radius + 2, y - radius + 2, Math.max(0, radius * 2 - 4), Math.max(0, radius * 2 - 4)), clip, colors.background, Math.max(1, radius - 2));
        }
    }
}
