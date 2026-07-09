package club.heiqi.uilib.ui.scene.control;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * {@link SceneAutocomplete} 成品壳 L3 轻量测试：create 可挂载、根节点非空、chrome 已设。
 */
public class SceneAutocompleteTest {

    /**
     * create 返回的 Supplier 可执行并产出带 padding/border 的根节点。
     */
    @Test
    public void createMountsRootWithChrome() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        Signal<String> value = Signal.create("Ari");
        AtomicReference<String> lastChange = new AtomicReference<String>("");
        List<String> candidates = Arrays.asList("Arial", "Calibri", "Segoe UI");
        SceneAutocomplete.Props props = new SceneAutocomplete.Props(
                value,
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                "字体",
                64,
                candidates,
                SceneAutocompletePrimitive.MatchMode.CONTAINS,
                8,
                lastChange::set);
        SceneNode root = SceneAutocomplete.create(rt, props).get();
        Assert.assertNotNull("根节点不应为 null", root);
        Assert.assertTrue("应设 padding（chrome）", root.getPaddingLeft() > 0 || root.getPaddingTop() > 0);
        Assert.assertTrue("应设边框宽度", root.getBorderWidth() > 0);
        rt.flush();
    }
}
