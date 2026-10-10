package club.heiqi.uilib.ui.scene.text.font.nativefont;

import java.nio.ByteBuffer;

/** 线程所属字体资源作用域；关闭释放全部仍打开的face。
 * openFace拷贝source的remaining字节，调用方可立即释放或修改原缓冲；faceIndex是集合中的实际face。
 * emSize为有限正数，核心预算与绑定共同验证上限；不得将请求字号改成物理strike尺寸返回度量。
 */
public interface FontSession extends AutoCloseable {
    FontFace openFace(ByteBuffer source, long faceIndex, float emSize);

    default boolean supportsVariations() {
        return false;
    }

    default FontFace openFace(
            ByteBuffer source, long faceIndex, float emSize, java.util.Map<String, Float> axes) {
        if (!axes.isEmpty()) throw new UnsupportedOperationException("字体后端不支持可变实例");
        return openFace(source, faceIndex, emSize);
    }

    @Override
    void close();
}
