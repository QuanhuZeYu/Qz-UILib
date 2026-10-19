package club.heiqi.uilib.host.input.rfb;

import java.util.jar.Manifest;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;

/** RFB仍处理LCL_NO_TRANSFORMS，保留LWJGL3ify对org.lwjgl/org.lwjglx的原排除。 */
public final class NativeInputTransformer implements RfbClassTransformer {
    @Override public String id() { return "native-input-order"; }

    @Override public boolean shouldTransformClass(ExtensibleClassLoader loader, Context context,
            Manifest manifest, String name, ClassNodeHandle node) {
        return "org.lwjglx.Lwjgl3ifyEventLoop".equals(name) && node.isPresent();
    }

    @Override public boolean transformClassIfNeeded(ExtensibleClassLoader loader, Context context,
            Manifest manifest, String name, ClassNodeHandle handle) {
        ClassNode node = handle.getNode();
        int captured = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("internalPumpEvents") || !method.desc.equals("()V")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals("org/lwjglx/opengl/Display")
                        && call.name.equals("lwjgl3ify$handleSdlEvent") && call.desc.equals("()Z")) {
                    method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "club/heiqi/uilib/ui/host/minecraft/SceneNativeTextInput", "captureCurrentEvent", "()V", false));
                    captured++;
                }
            }
        }
        if (captured != 1) throw new IllegalStateException("UILib SDL消费调用点不唯一: " + captured);
        MethodNode ready = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "uilib$nativeInputCaptureReady", "()V", null, null);
        ready.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
        node.methods.add(ready);
        handle.markDirty();
        return true;
    }
}
