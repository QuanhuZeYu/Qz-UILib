package club.heiqi.uilib.ui.container.experimental.presentation;

import club.heiqi.uilib.ui.container.experimental.model.ItemDescriptor;

/** Experimental descriptor 到展示值的纯解析边界；不读取 storage、scene 或平台对象。 */
public interface ItemPresentationResolver<I> {
    /** 根据 descriptor 解析不可变展示值。 */
    ItemPresentation<I> resolve(ItemDescriptor item);
}
