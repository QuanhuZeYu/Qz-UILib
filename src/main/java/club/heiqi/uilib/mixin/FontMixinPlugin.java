package club.heiqi.uilib.mixin;

import java.util.List;
import java.util.Set;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** 可选宿主只按目标class资源存在性选择；不得Class.forName提前初始化/变换目标。
 * 替代开关在标准入口运行时检查，关闭后保持原宿主完整实现，兼容桥不吞运行错误。 */
public final class FontMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName,String mixinClassName) {
        if(mixinClassName.endsWith(".Lwjgl3ifyTextInputMixin")) {
            ClassLoader loader=FontMixinPlugin.class.getClassLoader();
            // 只检查资源，避免在 Mixin 变换前加载 optional 目标类。
            return loader.getResource(targetClassName.replace('.','/')+".class")!=null
                &&loader.getResource("org/lwjgl/sdl/SDL_Event.class")!=null;
        }
        if(mixinClassName.contains(".Angelica")||mixinClassName.contains(".DisplayList")) {
            String path=targetClassName.replace('.','/')+".class";
            return FontMixinPlugin.class.getClassLoader().getResource(path)!=null;
        }
        return true;
    }
    @Override public void acceptTargets(Set<String> myTargets,Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName,ClassNode targetClass,String mixinClassName,IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName,ClassNode targetClass,String mixinClassName,IMixinInfo mixinInfo) {}
}
