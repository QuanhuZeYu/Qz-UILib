package club.heiqi.uilib.ui.reactive;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Owner 作用域上下文契约测试（信条三：作用域继承与生命周期）。
 *
 * <p>覆盖：同作用域读写、子继承父、子覆盖父、null 移除后父值重新可见、兄弟作用域隔离、
 * dispose 后不可见、类型化便捷查找。</p>
 */
public class OwnerScopeTest {

    private static final Object KEY = new Object();
    private static final Object OTHER_KEY = new Object();

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    @Test
    public void setScopeIsReadableOnSameOwner() {
        Owner owner = new Owner();
        owner.setScope(KEY, "value");
        Assert.assertEquals("value", owner.findScope(KEY));
    }

    @Test
    public void childInheritsParentScope() {
        Owner parent = new Owner();
        Owner child = parent.createChild();
        parent.setScope(KEY, Integer.valueOf(42));

        Assert.assertEquals(Integer.valueOf(42), child.findScope(KEY));
        Assert.assertNull("父不应看到子作用域的值", child.createChild().findScope(OTHER_KEY));
    }

    @Test
    public void childOverridesParentScope() {
        Owner parent = new Owner();
        Owner child = parent.createChild();
        parent.setScope(KEY, "parent");
        child.setScope(KEY, "child");

        Assert.assertEquals("child", child.findScope(KEY));
        Assert.assertEquals("父作用域值不被子覆盖", "parent", parent.findScope(KEY));
    }

    @Test
    public void nullValueRemovesKeyAndRevealsParentValue() {
        Owner parent = new Owner();
        Owner child = parent.createChild();
        parent.setScope(KEY, "parent");
        child.setScope(KEY, "child");

        child.setScope(KEY, null);
        Assert.assertEquals("移除本作用域键后父值重新可见", "parent", child.findScope(KEY));
    }

    @Test
    public void siblingScopesAreIsolated() {
        Owner parent = new Owner();
        Owner left = parent.createChild();
        Owner right = parent.createChild();
        left.setScope(KEY, "left");

        Assert.assertEquals("left", left.findScope(KEY));
        Assert.assertNull("兄弟作用域不应看到对方的键", right.findScope(KEY));
    }

    @Test
    public void disposedOwnerScopeIsInvisible() {
        Owner parent = new Owner();
        Owner child = parent.createChild();
        child.setScope(KEY, "child");

        child.dispose();
        Assert.assertNull("已销毁作用域的值不可见", child.findScope(KEY));
        Assert.assertNull("父作用域也看不到已销毁子作用域的值", parent.findScope(KEY));
    }

    @Test
    public void typedLookupReturnsNullOnTypeMismatch() {
        Owner owner = new Owner();
        owner.setScope(KEY, "text");

        Assert.assertEquals("text", owner.findScope(KEY, String.class));
        Assert.assertNull("类型不匹配返回 null 而非抛异常", owner.findScope(KEY, Integer.class));
        Assert.assertNull("null 类型返回 null", owner.findScope(KEY, null));
    }

    @Test
    public void unknownKeyAndNullKeyReturnNull() {
        Owner owner = new Owner();
        Assert.assertNull(owner.findScope(KEY));
        Assert.assertNull(owner.findScope(null));
    }

    @Test
    public void setScopeRejectsNullKey() {
        Owner owner = new Owner();
        try {
            owner.setScope(null, "value");
            Assert.fail("null 键应被拒绝");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }
}
