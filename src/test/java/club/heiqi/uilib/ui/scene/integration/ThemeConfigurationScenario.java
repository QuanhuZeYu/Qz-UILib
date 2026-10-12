package club.heiqi.uilib.ui.scene.integration;

import java.io.StringReader;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.config.ConfigException;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.control.ThemeMode;

/** 真实YAML重启与跨页面域绑定；覆盖同值、迟到通知及关闭，不依赖MC或GPU。 */
public final class ThemeConfigurationScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new ThemeConfigurationScenario().run(args); }

    private static final class App implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        ConfigSession config;
        Owner owner;
        ThemeConfiguration themes;
        App() {
            domain.execute(() -> {
                owner = domain.owner();
                config = new ConfigSession(domain, ConfigSchema.of(ThemeConfiguration.KEY));
                themes = new ThemeConfiguration(domain, config, owner);
            });
        }
        void load(Path file) {
            domain.execute(() -> { try { config.load(file); } catch(IOException e) { throw new UncheckedIOException(e); } });
        }
        void save(Path file) {
            domain.execute(() -> { try { config.save(file); } catch(IOException e) { throw new UncheckedIOException(e); } });
        }
        public void close() { domain.execute(owner::close); }
    }

    @Override protected void runTests() throws Exception {
        scenario("真实文件缺字段默认、保存及新应用恢复主题与用户注释", () -> {
            Path file = Files.createTempFile("uilib-global-theme", ".yaml");
            own(() -> Files.deleteIfExists(file));
            Files.write(file, "# 用户主题说明\n{}\n".getBytes(StandardCharsets.UTF_8));
            App app = own(new App());
            app.load(file);
            app.domain.execute(() -> require(app.config.get(ThemeConfiguration.KEY)==ThemeMode.LIGHT,"旧文件缺主题应使用浅色默认"));
            app.domain.execute(() -> app.config.set(ThemeConfiguration.KEY, ThemeMode.DARK));
            require(new String(Files.readAllBytes(file),StandardCharsets.UTF_8).equals("# 用户主题说明\n{}\n"),"内存变更不得自动写文件");
            app.save(file);
            String saved = new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
            require(saved.contains("theme: dark")&&saved.contains("用户主题说明"),"持久化主题与用户注释未同时保留");
            App restarted = own(new App());
            restarted.load(file);
            restarted.domain.execute(() -> require(restarted.config.get(ThemeConfiguration.KEY)==ThemeMode.DARK,"新应用未加载持久化深色"));
            ReactiveDomain loadedPage = ReactiveDomain.direct();
            loadedPage.execute(() -> {
                Owner pageScope = loadedPage.owner();
                try { require(restarted.themes.bind(loadedPage,pageScope).get()==ThemeMode.DARK,
                        "已保存深色首次跨域挂载不能先回到浅色"); }
                finally { pageScope.close(); }
            });
            for(String value:new String[]{"system","null","true"}) app.domain.execute(() -> {
                try { app.config.load(new StringReader("ui:\n  theme: "+value+"\n")); throw new AssertionError("非法主题被接受："+value); }
                catch(ConfigException expected) { require(expected.path().contains("ui.theme"),"非法主题路径不准确"); }
                catch(IOException e) { throw new UncheckedIOException(e); }
                require(app.config.get(ThemeConfiguration.KEY)==ThemeMode.DARK,"拒绝值不应部分发布");
            });
        });
        scenario("跨页面状态域实时更新、同值去重和关闭只释放页面订阅", () -> {
            App app = own(new App());
            ReactiveDomain page = ReactiveDomain.direct();
            final Owner[] scope = new Owner[1];
            final ReadableSignal<?>[] binding = new ReadableSignal<?>[1];
            int[] updates={0};
            page.execute(() -> {
                scope[0]=page.owner();
                ReadableSignal<ThemeMode> mode=app.themes.bind(page,scope[0]);
                binding[0]=mode;
                page.effect(scope[0], () -> { mode.get(); updates[0]++; });
                require(mode.get()==ThemeMode.LIGHT,"首次跨域绑定无有效初值");
            });
            own(() -> page.execute(scope[0]::close));
            app.domain.execute(() -> app.config.set(ThemeConfiguration.KEY,ThemeMode.DARK));
            page.execute(() -> require(binding[0].get()==ThemeMode.DARK,"已打开其他域页面未同步深色"));
            int before=updates[0];
            app.domain.execute(() -> app.config.set(ThemeConfiguration.KEY,ThemeMode.DARK));
            require(updates[0]==before,"相同主题不应重复通知");
            page.execute(scope[0]::close);
            app.domain.execute(() -> app.config.set(ThemeConfiguration.KEY,ThemeMode.LIGHT));
            require(updates[0]==before,"关闭页面仍收到更新");
            page.execute(() -> {
                Owner reopened=page.owner();
                require(app.themes.bind(page,reopened).get()==ThemeMode.LIGHT,"关闭页面不应释放全局配置");
                reopened.close();
            });
        });
        scenario("排队更新合并到当前事实、关闭后的迟到通知不访问页面", () -> {
            App app=own(new App());
            Deque<Runnable> queue=new ArrayDeque<>();
            ReactiveDomain page=new ReactiveDomain(queue::addLast);
            final Owner[] scope=new Owner[1];
            final ReadableSignal<?>[] binding=new ReadableSignal<?>[1];
            page.execute(() -> { scope[0]=page.owner(); binding[0]=app.themes.bind(page,scope[0]); });
            while(!queue.isEmpty()) queue.removeFirst().run();
            app.domain.execute(() -> app.config.set(ThemeConfiguration.KEY,ThemeMode.DARK));
            app.domain.execute(() -> app.config.set(ThemeConfiguration.KEY,ThemeMode.LIGHT));
            app.domain.execute(() -> app.config.set(ThemeConfiguration.KEY,ThemeMode.DARK));
            page.execute(() -> require(binding[0].get()==ThemeMode.DARK,"排队主题未最终收敛到最新值"));
            while(!queue.isEmpty()) queue.removeFirst().run();
            page.execute(scope[0]::close);
            app.domain.execute(() -> app.config.set(ThemeConfiguration.KEY,ThemeMode.LIGHT));
            while(!queue.isEmpty()) queue.removeFirst().run();
        });
    }
}
