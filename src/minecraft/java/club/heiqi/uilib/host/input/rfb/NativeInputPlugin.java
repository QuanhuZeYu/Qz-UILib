package club.heiqi.uilib.host.input.rfb;

import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbPlugin;

/** 仅由已有RFB读取描述资源后加载；普通LWJGL2不加载此可选绑定。 */
public final class NativeInputPlugin implements RfbPlugin {
    @Override public RfbClassTransformer[] makeEarlyTransformers() {
        return new RfbClassTransformer[] {new NativeInputTransformer()};
    }
}
