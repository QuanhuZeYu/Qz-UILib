package club.heiqi.uilib.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * attrib 深度入口三档解析测试。
 *
 * <p>解析面是纯函数 {@link GlAttribDepth#resolveFrom(Class)}，因此可用替身类覆盖三种宿主形态；
 * 生产路径的进程级缓存不参与断言，各用例互不污染。三档的宿主实证见 {@link GlAttribDepth} 类注释。</p>
 */
public class GlAttribDepthTest {

    /** 2.1.x / 2.2.10：public static int getAttribDepth()。 */
    @Test
    public void resolvesPublicAccessorFirst() throws Exception {
        GlAttribDepth.DepthAccessor accessor = GlAttribDepth.resolveFrom(AccessorHost.class);

        Assert.assertNotNull(accessor);
        Assert.assertEquals(7, accessor.read());
    }

    /** 更早的 2.1.x：private static int attribDepth 字段。 */
    @Test
    public void fallsBackToDepthField() throws Exception {
        GlAttribDepth.DepthAccessor accessor = GlAttribDepth.resolveFrom(FieldHost.class);

        Assert.assertNotNull(accessor);
        Assert.assertEquals(3, accessor.read());
    }

    /** GTNH 2.8.x（Angelica 1.0.0-betaXX）：只有 private static final IntStack attribs 容器。 */
    @Test
    public void fallsBackToAttributeStackContainer() throws Exception {
        GlAttribDepth.DepthAccessor accessor = GlAttribDepth.resolveFrom(StackHost.class);

        Assert.assertNotNull(accessor);
        Assert.assertEquals(5, accessor.read());
        Assert.assertEquals("读出的是容器实时大小，不是快照", 6, readTwiceAfterPush(accessor));
    }

    /** 三档都缺失或类为 null：解析返回 null，调用方降级 no-op。 */
    @Test
    public void returnsNullWhenNothingIsAvailable() {
        Assert.assertNull(GlAttribDepth.resolveFrom(BareHost.class));
        Assert.assertNull(GlAttribDepth.resolveFrom(null));
    }

    /** 访问器优先于字段与容器：同时存在时只认访问器。 */
    @Test
    public void accessorWinsOverOtherShapes() throws Exception {
        Assert.assertEquals(11, GlAttribDepth.resolveFrom(MixedHost.class).read());
    }

    private static int readTwiceAfterPush(GlAttribDepth.DepthAccessor accessor) throws Exception {
        StackHost.push();
        return accessor.read();
    }

    /** 访问器形态。 */
    public static final class AccessorHost {

        @SuppressWarnings("unused")
        private static int depth = 7;

        public static int getAttribDepth() {
            return depth;
        }
    }

    /** 字段形态。 */
    public static final class FieldHost {

        @SuppressWarnings("unused")
        private static int attribDepth = 3;
    }

    /** 容器形态：与 Angelica 同为「私有 final 容器 + int size()」结构。 */
    public static final class StackHost {

        private static final java.util.List<Integer> attribs = new java.util.ArrayList<Integer>();

        static {
            push();
            push();
            push();
            push();
            push();
        }

        static void push() {
            attribs.add(Integer.valueOf(0));
        }
    }

    /** 有 size() 但字段名不符：不得被误认。 */
    @SuppressWarnings("unused")
    public static final class BareHost {

        private static final java.util.List<Integer> unrelated = new java.util.ArrayList<Integer>();
    }

    /** 三种形态同时存在。 */
    public static final class MixedHost {

        @SuppressWarnings("unused")
        private static int attribDepth = 3;

        @SuppressWarnings("unused")
        private static final java.util.List<Integer> attribs = new java.util.ArrayList<Integer>();

        public static int getAttribDepth() {
            return 11;
        }
    }
}
