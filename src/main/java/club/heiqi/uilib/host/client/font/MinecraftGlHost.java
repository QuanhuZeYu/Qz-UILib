package club.heiqi.uilib.host.client.font;

import club.heiqi.uilib.host.client.gl.GlApi;

/** MC最外层按实际窗口宿主选择binding；核心与GPU算法不检测游戏或加载另一套bindings。 */
final class MinecraftGlHost {
    private MinecraftGlHost(){}
    static GlApi create(){
        ClassLoader loader=MinecraftGlHost.class.getClassLoader();
        boolean lwjgl3=loader.getResource("org/lwjgl/opengl/GL.class")!=null;
        String implementation="club.heiqi.uilib.host.client.gl."+(lwjgl3?"Lwjgl3GlApi":"Lwjgl2GlApi");
        try{return (GlApi)Class.forName(implementation,true,loader).getConstructor().newInstance();}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("无法加载MC窗口宿主的GPU适配: "+implementation,failure);}
    }
}
