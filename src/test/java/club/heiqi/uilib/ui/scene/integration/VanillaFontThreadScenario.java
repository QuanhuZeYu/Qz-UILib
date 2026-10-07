package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import club.heiqi.uilib.host.client.font.MinecraftVanillaFont;
import club.heiqi.uilib.mixin.client.VanillaFontRendererMixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FML启动绘制线程不接管；真实安装只建立逻辑适配器，不创建MC字体服务或GL对象。 */
public final class VanillaFontThreadScenario extends TestApplication {

    private static final class FontBoundary extends VanillaFontRendererMixin {
        @Override protected void enableAlpha(){throw new AssertionError("启动线程触碰GL");}
        @Override protected void setColor(float r,float g,float b,float a){throw new AssertionError("启动线程触碰GL");}
        @Override public int qz$fontHeight(){throw new AssertionError("启动线程进入UILib字体事实读取");}
        @Override public int[] qz$palette(){throw new AssertionError("启动线程进入UILib字体调色板");}
    }
    public static void main(String[] args) throws Exception { new VanillaFontThreadScenario().run(args); }

    @Override protected void setup() {
        require(!MinecraftVanillaFont.active(), "安装前不得接管FML字体");
        onClose(MinecraftVanillaFont::close);
    }

    @Override protected void runTests() throws Exception {
        scenario("真实安装线程接管", () -> {
        require(!MinecraftVanillaFont.active(),"安装前不得接管FML字体");
        MinecraftVanillaFont.install();

        require(MinecraftVanillaFont.active(),"安装线程须保持默认字体接管");
        });
        scenario("Splash全部标准callback不接管", () -> {
        AtomicReference<Throwable> failure=new AtomicReference<Throwable>();
        Thread splash=new Thread(()->{try{
            require(!MinecraftVanillaFont.active(),"安装后Splash线程不能使用主线程字体服务");
            FontBoundary font=new FontBoundary();int checked=0;
            for(Method method:VanillaFontRendererMixin.class.getDeclaredMethods()){
                if(!method.getName().startsWith("qz$")||!java.util.Arrays.asList("qz$draw","qz$width","qz$charWidth","qz$trim","qz$list","qz$wrap","qz$size","qz$splitHeight","qz$split","qz$bidiText").contains(method.getName()))continue;
                Class<?>[] types=method.getParameterTypes();Object[] values=new Object[types.length];
                CallbackInfo callback=null;
                for(int i=0;i<types.length;i++){
                    if(types[i]==String.class)values[i]="FML loading العربية 😀";
                    else if(types[i]==int.class)values[i]=32;
                    else if(types[i]==char.class)values[i]='A';
                    else if(types[i]==boolean.class)values[i]=false;
                    else if(types[i]==CallbackInfoReturnable.class){callback=new CallbackInfoReturnable<Object>(method.getName(),true);values[i]=callback;}
                    else if(types[i]==CallbackInfo.class){callback=new CallbackInfo(method.getName(),true);values[i]=callback;}
                    else throw new AssertionError("未知标准入口参数 "+types[i]);
                }
                method.setAccessible(true);try{method.invoke(font,values);}catch(InvocationTargetException e){throw new AssertionError("启动线程标准入口触及服务 "+method.getName(),e.getCause());}
                require(callback!=null&&!callback.isCancelled(),"启动线程不应取消宿主调用 "+method.getName());checked++;
            }
            require(checked==10,"必须实际调用全部十个标准callback: "+checked);
            System.out.println("PASS splash after install active=false all10CallbacksUncancelled noFontOrGLAccess");
        }catch(Throwable error){failure.set(error);}},"FML-Splash-font-regression");
        splash.start();splash.join();if(failure.get()!=null)throw new AssertionError("启动字体线程回归",failure.get());
        });
        scenario("Splash返回后owner仍接管", () -> {
        require(MinecraftVanillaFont.active(),"Splash返回后安装主线程仍须接管");
        System.out.println("PASS install owner remains active before/after worker; actual production install and gate");
        });
    }
}
