package club.heiqi.uilib.host.forge112.loading;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Forge 无客户端停止事件；仅在 Minecraft 销毁 Display 前释放所属 GPU 服务。 */
public final class ClientShutdownTransformer implements IClassTransformer {
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (!"net.minecraft.client.Minecraft".equals(transformedName) || bytes == null) return bytes;
        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, 0);
        int hooks = 0;
        for (MethodNode method : type.methods) {
            if (method.desc.equals("()V") && (method.name.equals("shutdownMinecraftApplet")
                    || method.name.equals("func_71405_e") || method.name.equals("h"))) {
                // 原方法的 finally 在正常/异常两条路径各含 Display.destroy；在共同入口只释放一次。
                method.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "club/heiqi/uilib/host/forge112/Forge112Client", "close", "()V", false));
                hooks++;
            }
        }
        if (hooks != 1) throw new IllegalStateException("Minecraft 客户端退出边界不唯一: " + hooks);
        ClassWriter output = new ClassWriter(0);
        type.accept(output);
        return output.toByteArray();
    }
}
