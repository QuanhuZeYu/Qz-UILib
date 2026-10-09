package club.heiqi.uilib.host.client.image;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import club.heiqi.uilib.ui.scene.image.ImageSource;

/** 声明时深复制 ItemStack（含 NBT/数量/damage）。原对象变更须发布新的 source，不承诺自动观察外部对象。 */
public final class MinecraftItemSource extends ImageSource {
    private static final AtomicLong IDS = new AtomicLong();
    private final ItemStack snapshot;
    public MinecraftItemSource(ItemStack stack) {
        super(Kind.ITEM, "item:snapshot:" + IDS.incrementAndGet(), null, null);
        snapshot = Objects.requireNonNull(stack, "stack").copy();
        if (snapshot.getItem() == null) throw new IllegalArgumentException("物品为空");
    }
    ItemStack copy() { return snapshot.copy(); }
    /** 文档 item:minecraft:diamond 经正式宿主解析；非法注册标识由 ImageSource.parse 转为 resolve 失败。 */
    public static ImageSource parse(String uri) {
        if (!uri.startsWith("item:")) throw new IllegalArgumentException("不是物品 URI");
        Object item = Item.itemRegistry.getObject(uri.substring(5));
        if (!(item instanceof Item)) throw new IllegalArgumentException("未知物品：" + uri);
        return new MinecraftItemSource(new ItemStack((Item) item));
    }
}
