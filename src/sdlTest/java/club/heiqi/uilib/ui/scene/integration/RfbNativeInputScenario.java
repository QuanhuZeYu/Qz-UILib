package club.heiqi.uilib.ui.scene.integration;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Manifest;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import com.gtnewhorizons.retrofuturabootstrap.SharedConfig;
import com.gtnewhorizons.retrofuturabootstrap.api.*;
import club.heiqi.uilib.host.input.rfb.NativeInputPlugin;

/** 原RFB类加载器保留宿主排除，变换真实EventLoop字节码后再运行无窗口消费事实场景。 */
public final class RfbNativeInputScenario {
    public static void main(String[] args) throws Exception {
        NativeInputPlugin plugin = new NativeInputPlugin();
        RfbClassTransformer production = plugin.makeEarlyTransformers()[0];
        int[] transformed = {0};
        RfbClassTransformer observing = new RfbClassTransformer() {
            public String id() { return production.id(); }
            public boolean shouldTransformClass(ExtensibleClassLoader loader, Context context,
                    Manifest manifest, String name, ClassNodeHandle node) {
                return production.shouldTransformClass(loader, context, manifest, name, node);
            }
            public boolean transformClassIfNeeded(ExtensibleClassLoader loader, Context context,
                    Manifest manifest, String name, ClassNodeHandle handle) {
                if (context != Context.LCL_NO_TRANSFORMS) throw new AssertionError("消费类未保持宿主原排除");
                boolean changed = production.transformClassIfNeeded(loader, context, manifest, name, handle);
                ClassNode node = new ClassNode(); new ClassReader(handle.computeBytes()).accept(node, 0);
                int hooks = 0, originalCalls = 0;
                for (MethodNode method : node.methods) {
                    if (!method.name.equals("internalPumpEvents") || !method.desc.equals("()V")) continue;
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        if (!(instruction instanceof MethodInsnNode)) continue;
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if (call.owner.equals("club/heiqi/uilib/ui/host/minecraft/SceneNativeTextInput")
                                && call.name.equals("captureCurrentEvent")) {
                            hooks++;
                            AbstractInsnNode next = instruction.getNext();
                            if (!(next instanceof MethodInsnNode) || !((MethodInsnNode) next).name.equals("lwjgl3ify$handleSdlEvent"))
                                throw new AssertionError("hook未紧邻原消费调用之前");
                        }
                        if (call.owner.equals("org/lwjglx/opengl/Display") && call.name.equals("lwjgl3ify$handleSdlEvent")
                                && call.desc.equals("()Z")) originalCalls++;
                    }
                }
                if (hooks != 1 || originalCalls != 1) throw new AssertionError("hook/原调用数量错误");
                transformed[0]++;
                System.out.println("RFB_NATIVE_CALLSITE context=" + context + " hooks=1 originalCall=1 immediatePredecessor=true exclusionsUnchanged=true");
                return changed;
            }
        };
        RfbPluginMetadata metadata = new RfbPluginMetadata.Builder(new File(".").toURI().toURL(), new File(".").toURI(),
                "qz-uilib-native-input", "Qz UILib native input", "1", NativeInputPlugin.class).build();
        Field registry = SharedConfig.class.getDeclaredField("rfbTransformers"); registry.setAccessible(true);
        @SuppressWarnings("unchecked") AtomicReference<RfbClassTransformerHandle[]> transformers =
                (AtomicReference<RfbClassTransformerHandle[]>) registry.get(null);
        transformers.set(new RfbClassTransformerHandle[] {new RfbClassTransformerHandle(metadata, plugin, observing)});
        List<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) urls.add(new File(entry).toURI().toURL());
        LaunchClassLoader loader = new LaunchClassLoader(urls.toArray(new URL[0]));
        loader.addTransformerExclusion("org.lwjgl."); loader.addTransformerExclusion("org.lwjglx.");
        Class<?> target = Class.forName("org.lwjglx.Lwjgl3ifyEventLoop", false, loader);
        if (target.getClassLoader() != loader || transformed[0] != 1) throw new AssertionError("原RFB加载链未命中消费目标: loader=" + target.getClassLoader() + " transformed=" + transformed[0]);
        Class<?> scenario = Class.forName("club.heiqi.uilib.ui.scene.integration.SdlTextInputScenario", true, loader);
        try { scenario.getMethod("main", String[].class).invoke(null, (Object) args); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception) throw (Exception) failure.getCause();
            if (failure.getCause() instanceof Error) throw (Error) failure.getCause();
            throw failure;
        }
    }
}
