package club.heiqi.uilib.mixin.late.ae2;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import club.heiqi.uilib.font.config.FontConfig;

/**
 * AE 监控器的显示列表不能缓存 UILib 的 shader 字体提交（issue #69）。
 *
 * <p>AE 用 GL_COMPILE_AND_EXECUTE 编译屏幕，后续只 glCallList。字体矩阵 uniform、
 * VAO/VBO 状态和异步到达的字形并不具有固定管线列表的回放语义，数量因而可能消失或跟随镜头。
 * 替换开启时只绕过列表缓存，保留宿主的朝向/物品绘制，数量仍经 FontRenderer 注入走 UILib。
 * 同时保持 updateList 为脏，关闭替换后的首帧必须重建列表，不能复用开启前留下的原版字形。</p>
 */
@Pseudo
@Mixin(targets = "appeng.parts.reporting.AbstractPartMonitor", remap = false)
public abstract class MixinAbstractPartMonitor {

    @Shadow
    private boolean updateList;

    @Unique
    private boolean qzuilib$directMonitorRender;

    @Inject(method = "renderDynamic", at = @At("HEAD"), require = 1)
    private void qzuilib$beginMonitorRender(CallbackInfo ci) {
        // 单次调用固定决策，列表 begin/end 必须成对保留或跳过。
        qzuilib$directMonitorRender = FontConfig.replaceOrigin;
        if (qzuilib$directMonitorRender) {
            updateList = true;
        }
    }

    @WrapOperation(method = "renderDynamic",
            at = @At(value = "FIELD", target = "Lappeng/parts/reporting/AbstractPartMonitor;updateList:Z",
                    opcode = Opcodes.PUTFIELD),
            require = 1, expect = 1, allow = 1)
    private void qzuilib$keepDisplayListDirty(@Coerce Object monitor, boolean value, Operation<Void> original) {
        original.call(monitor, value || qzuilib$directMonitorRender);
    }

    @WrapOperation(method = "renderDynamic",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glNewList(II)V"),
            require = 1, expect = 1, allow = 1)
    private void qzuilib$beginDisplayList(int list, int mode, Operation<Void> original) {
        if (!qzuilib$directMonitorRender) {
            original.call(list, mode);
        }
    }

    @WrapOperation(method = "renderDynamic",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEndList()V"),
            require = 1, expect = 1, allow = 1)
    private void qzuilib$endDisplayList(Operation<Void> original) {
        if (!qzuilib$directMonitorRender) {
            original.call();
        }
    }
}
