package club.heiqi.uilib.ui.host.minecraft;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** 只读取可选宿主的 class 资源，插件选择与页面绑定使用同一消费前提，不提前加载目标类。 */
final class SceneNativeInputSupport {
    private SceneNativeInputSupport() {}

    static boolean resourcesPresent(ClassLoader loader) {
        return loader.getResource("org/lwjglx/opengl/Display.class") != null
                && loader.getResource("me/eigenraven/lwjgl3ify/api/InputEvents.class") != null
                && loader.getResource("org/lwjgl/sdl/SDL_Event.class") != null
                && loader.getResource("com/gtnewhorizons/retrofuturabootstrap/api/RfbPlugin.class") != null;
    }

    static String consumerFailure(ClassLoader loader) {
        try {
            if (!member(loader, "org/lwjglx/opengl/Display.class", true,
                    "lwjgl3ify$handleSdlEvent", "()Z", 0x0009))
                return "org.lwjglx.opengl.Display 缺少 public static lwjgl3ify$handleSdlEvent()Z";
            if (!member(loader, "org/lwjglx/Lwjgl3ifyEventLoop.class", false,
                    "event", "Lorg/lwjgl/sdl/SDL_Event;", 0x0009))
                return "org.lwjglx.Lwjgl3ifyEventLoop 缺少 public static SDL_Event event 消费槽";
            if (!member(loader, "org/lwjglx/Lwjgl3ifyEventLoop.class", true, "internalPumpEvents", "()V", 0x000a))
                return "org.lwjglx.Lwjgl3ifyEventLoop 缺少 private static internalPumpEvents()V 消费入口";
            return null;
        } catch (IOException failure) {
            return "无法读取 SDL 消费绑定的 class 资源: " + failure.getMessage();
        }
    }

    /** 读取 class 文件的 UTF 常量与成员表；不依赖旧宿主 ASM 对 Java25 字节码的支持。 */
    private static boolean member(ClassLoader loader, String path, boolean method,
            String name, String descriptor, int requiredAccess) throws IOException {
        InputStream resource = loader.getResourceAsStream(path);
        if (resource == null) return false;
        try (DataInputStream input = new DataInputStream(resource)) {
            if (input.readInt() != 0xcafebabe) throw new IOException("不是 class 文件: " + path);
            input.readUnsignedShort(); input.readUnsignedShort();
            String[] strings = new String[input.readUnsignedShort()];
            for (int index = 1; index < strings.length; index++) {
                int tag = input.readUnsignedByte();
                switch (tag) {
                    case 1: strings[index] = input.readUTF(); break;
                    case 3: case 4: skip(input, 4); break;
                    case 5: case 6: skip(input, 8); index++; break;
                    case 7: case 8: case 16: case 19: case 20: skip(input, 2); break;
                    case 9: case 10: case 11: case 12: case 17: case 18: skip(input, 4); break;
                    case 15: skip(input, 3); break;
                    default: throw new IOException("未知常量 tag=" + tag + ": " + path);
                }
            }
            skip(input, 6);
            skip(input, input.readUnsignedShort() * 2);
            boolean fields = members(input, strings, method ? null : name, descriptor, requiredAccess);
            return method ? members(input, strings, name, descriptor, requiredAccess) : fields;
        }
    }

    private static boolean members(DataInputStream input, String[] strings, String name,
            String descriptor, int requiredAccess) throws IOException {
        boolean found = false;
        int count = input.readUnsignedShort();
        for (int index = 0; index < count; index++) {
            int access = input.readUnsignedShort();
            String memberName = strings[input.readUnsignedShort()];
            String memberDescriptor = strings[input.readUnsignedShort()];
            if (name != null && name.equals(memberName) && descriptor.equals(memberDescriptor)
                    && (access & requiredAccess) == requiredAccess) found = true;
            int attributes = input.readUnsignedShort();
            for (int attribute = 0; attribute < attributes; attribute++) {
                input.readUnsignedShort(); skip(input, input.readInt());
            }
        }
        return found;
    }

    private static void skip(DataInputStream input, int bytes) throws IOException {
        while (bytes > 0) {
            int skipped = input.skipBytes(bytes);
            if (skipped == 0) { input.readByte(); skipped = 1; }
            bytes -= skipped;
        }
    }
}
