package club.heiqi.uilib.host.client.localization;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import cpw.mods.fml.common.registry.LanguageRegistry;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.Language;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.localization.LocalizationBinding;
import club.heiqi.uilib.ui.scene.localization.LocalizationSnapshot;

/** 不启动游戏：真正执行Forge patched LanguageManager和Locale加载/合并，再走生产捕获与递交。
 * 普通测试JVM没有Mixin变换，仅字段读取由测试侧反射桥接；不替换原版加载或Formatter。
 * 注入应用与发行包两宿主运行仍由父审查及用户验收。 */
public final class MinecraftLocalizationScenario extends TestApplication {
    private static final class Manager extends LanguageManager implements LanguageManagerAccess {
        Manager(String code) { super(null, code); }
        private static Object field(Class<?> type, Object receiver, String name) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(receiver); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("测试JVM读取真实MC字段失败", failure); }
        }
        @Override public String qz$languageCode() { return (String) field(LanguageManager.class, this, "currentLanguage"); }
        @SuppressWarnings("unchecked")
        @Override public Map<String,String> qz$translations() {
            Object locale = field(LanguageManager.class, null, "currentLocale");
            return (Map<String,String>) field(net.minecraft.client.resources.Locale.class, locale, "field_135032_a");
        }
        void load(IResourceManager resources, MinecraftLocalizationSource source) {
            onResourceManagerReload(resources); source.loaded(this);
        }
    }
    /** 仅资源包I/O边界；原版决定语言顺序、包覆盖及数值占位归一。 */
    private static final class Resources implements IResourceManager {
        private final Map<String,List<byte[]>> files = new HashMap<>();
        Resources add(String language, String content) { return add(language, content.getBytes(StandardCharsets.UTF_8)); }
        Resources add(String language, byte[] content) { files.computeIfAbsent("lang/" + language + ".lang", ignored -> new ArrayList<>()).add(content); return this; }
        @Override public Set<String> getResourceDomains() { return Collections.singleton("localization_scenario"); }
        @Override public IResource getResource(ResourceLocation location) throws IOException { return getAllResources(location).get(0); }
        @Override public List<IResource> getAllResources(ResourceLocation location) throws IOException {
            List<byte[]> contents = files.get(location.getResourcePath()); if (contents == null) throw new IOException("不存在该语言资源 " + location);
            List<IResource> result = new ArrayList<>();
            for (byte[] content : contents) result.add(new IResource() {
                @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
                @Override public boolean hasMetadata() { return false; }
                @Override public IMetadataSection getMetadata(String section) { return null; }
            });
            return result;
        }
    }
    @Override protected void runTests() throws Exception {
        scenario("真实原版归一包覆盖和Forge合并，metadata为空仍捕获实际语言", this::realLoad);
        scenario("加载hook不执行消费者，catch外保留原异常及其他绑定更新，不重复递交", this::dispatchFailure);
        scenario("最终资料合并及关闭丢弃pending，迟到加载不复活来源", this::coalesceAndClose);
    }
    private void realLoad() throws Exception {
        String resourceKey = "qz.scenario.shared", forgeKey = "qz.scenario.forge", englishKey = "qz.scenario.english";
        LanguageRegistry.instance().addStringLocalization(resourceKey, "zh_CN", "Forge不得覆盖资源");
        LanguageRegistry.instance().addStringLocalization(forgeKey, "zh_CN", "Forge中文");
        LanguageRegistry.instance().addStringLocalization(forgeKey, "en_US", "Forge英文不得覆盖当前语言");
        LanguageRegistry.instance().addStringLocalization(englishKey, "en_US", "Forge英文补缺");
        Resources resources = new Resources().add("en_US", resourceKey + "=base\nqz.scenario.numeric=%2$d / %1$.2f / 100%%\n")
            .add("en_US", resourceKey + "=resource override\n").add("zh_CN", resourceKey + "=资源中文\n");
        // 把验收页实际.lang输入交给原版加载，避免手工页仅在代码中看似接线。
        for (String language : new String[]{"en_US", "zh_CN"}) try (InputStream input = getClass().getResourceAsStream("/assets/qz_uilib/lang/" + language + ".lang")) {
            require(input != null, "缺少验收页实际语言资源 " + language);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int length;
            while ((length = input.read(buffer)) != -1) bytes.write(buffer, 0, length); resources.add(language, bytes.toByteArray());
        }
        Manager manager = new Manager("zh_CN"); require(manager.getCurrentLanguage() == null, "此反例需要metadata尚为空");
        manager.onResourceManagerReload(resources); LocalizationSnapshot initial = MinecraftLocalizationServices.capture(manager);
        require("zh_CN".equals(initial.language()) && "资源中文".equals(initial.format(resourceKey)), "捕获了metadata默认语言或Forge覆盖资源");
        require("Forge中文".equals(initial.format(forgeKey)) && "Forge英文补缺".equals(initial.format(englishKey)), "未捕获Forge完成合并表");
        require("2 / 1.5 / 100%".equals(initial.format("qz.scenario.numeric", 1.5, 2)), "真实原版数值占位归一结果错误");
        require(initial.format("qz.scenario.numeric", 1.5, 2).equals(I18n.format("qz.scenario.numeric", 1.5, 2)), "捕获格式与真实I18n结果不同");
        require("语言动态接入：第3次操作".equals(initial.format("qz_uilib.lang.title", 3)), "真实验收页中文资源未加载");
        manager.setCurrentLanguage(new Language("en_US", "US", "English", false)); manager.onResourceManagerReload(resources);
        LocalizationSnapshot english = MinecraftLocalizationServices.capture(manager);
        require("resource override".equals(english.format(resourceKey)) && "资源中文".equals(initial.format(resourceKey)), "原版包覆盖错误或旧快照跟随宿主可变表");
        require("Localization: operation 3".equals(english.format("qz_uilib.lang.title", 3)), "真实验收页英文资源未加载");
        System.out.println("ACTUAL_MC_LOCALE privateLanguage=zh_CN metadata=null numeric=" + initial.format("qz.scenario.numeric", 1.5, 2)
            + " resource=" + initial.format(resourceKey) + " forge=" + initial.format(forgeKey));
    }
    private void dispatchFailure() {
        Manager manager = new Manager("en_US"); manager.onResourceManagerReload(new Resources().add("en_US", "qz.scenario.value=before\n"));
        MinecraftLocalizationSource source = own(new MinecraftLocalizationSource(MinecraftLocalizationServices.capture(manager)));
        ReactiveDomain first = ReactiveDomain.direct(), second = ReactiveDomain.direct(); Owner[] owners = new Owner[2];
        Computed<String>[] text = new Computed[1]; int[] observed = {0}; RuntimeException original = new IllegalStateException("真实消费者失败");
        first.execute(() -> {
            owners[0] = first.owner(); LocalizationBinding binding = new LocalizationBinding(first, owners[0], source);
            first.effect(owners[0], () -> { if ("after".equals(binding.snapshot().get().format("qz.scenario.value"))) { observed[0]++; throw original; } });
        }); onClose(() -> first.execute(owners[0]::close));
        second.execute(() -> { owners[1] = second.owner(); text[0] = new LocalizationBinding(second, owners[1], source).text("qz.scenario.value"); });
        onClose(() -> second.execute(owners[1]::close));
        manager.load(new Resources().add("en_US", "qz.scenario.value=after\n"), source);
        require(observed[0] == 0 && "before".equals(source.current().format("qz.scenario.value")), "加载hook同步执行消费者，异常会进入资源恢复catch");
        Throwable caught = null; try { source.dispatchPending(); } catch (Throwable failure) { caught = failure; }
        require(caught == original && observed[0] == 1, "catch外发布吞错或改变原异常身份");
        second.execute(() -> require("after".equals(text[0].get()), "首个消费者失败截断另一个独立绑定资料"));
        source.dispatchPending(); require(observed[0] == 1, "消费者失败后的tick重放已消费通知");
    }
    private void coalesceAndClose() {
        Manager manager = new Manager("en_US"); manager.onResourceManagerReload(new Resources().add("en_US", "qz.scenario.value=initial\n"));
        MinecraftLocalizationSource source = new MinecraftLocalizationSource(MinecraftLocalizationServices.capture(manager)); onClose(source::close);
        int[] notices = {0}; source.subscribe(next -> notices[0]++);
        manager.load(new Resources().add("en_US", "qz.scenario.value=intermediate\n"), source);
        manager.setCurrentLanguage(new Language("zh_CN", "CN", "中文", false));
        manager.load(new Resources().add("en_US", "qz.scenario.value=english\n").add("zh_CN", "qz.scenario.value=final\n"), source);
        source.dispatchPending(); require(notices[0] == 2 && "zh_CN".equals(source.current().language()) && "final".equals(source.current().format("qz.scenario.value")), "pending未合并到最终语言资料");
        manager.load(new Resources().add("zh_CN", "qz.scenario.value=discard\n"), source); source.close(); source.loaded(null); source.dispatchPending();
        require(notices[0] == 2, "关闭后pending或迟到加载通知复活来源");
        try { source.subscribe(next -> { }); throw new AssertionError("关闭来源允许重新订阅"); } catch (IllegalStateException expected) { }
        // 未安装/退出后的静态hook必须无效果，不能因为回调创建全局owner。
        MinecraftLocalizationServices.loaded(null); MinecraftLocalizationServices.dispatchPending(); MinecraftLocalizationServices.closeShared();
    }
    public static void main(String[] args) throws Exception { new MinecraftLocalizationScenario().run(args); }
}
