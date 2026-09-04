package club.heiqi.uilib.util;

import org.junit.Assert;
import org.junit.Test;

import cpw.mods.fml.relauncher.Side;

/**
 * 启动侧权威判定（#71 同族修复的公共底座）。
 *
 * <p>本类唯一需要锁住的行为是 fail-open 的方向：<b>只有明确读到 SERVER 才算专用服务端</b>。
 * 方向搞反的代价不对称——把未知当服务端，会让所有"仅客户端"的逻辑在测试与非 FML 宿主里
 * 静默停摆；把未知当客户端只是少一层保护。</p>
 */
public class LaunchSideTest {

    @Test
    public void onlyAnExplicitServerCountsAsDedicatedServer() {
        Assert.assertTrue("SERVER 必须判定为专用服务端",
                LaunchSide.forSide(Side.SERVER).isDedicatedServer());
        Assert.assertFalse("CLIENT 不是专用服务端",
                LaunchSide.forSide(Side.CLIENT).isDedicatedServer());
        Assert.assertFalse("侧别读不到（非 FML 宿主）不得按专用服务端处理",
                LaunchSide.forSide(null).isDedicatedServer());
    }

    @Test
    public void unknownSideStaysUnknownInsteadOfBeingGuessed() {
        Assert.assertNull("forSide(null) 必须原样保留未知，不伪造侧别",
                LaunchSide.forSide(null).side());
        Assert.assertEquals("未知侧的描述要说明原因，日志里不能只剩一个 null",
                "未知（非 FML 启动环境）", LaunchSide.forSide(null).describe());
        Assert.assertEquals("已知侧的描述直接用侧别名，便于和 FML 自己的日志对齐",
                "SERVER", LaunchSide.forSide(Side.SERVER).describe());
    }

    @Test
    public void productionInstanceReadsFmlLaunchSideWithoutFailingHosts() {
        // 读不到侧别时不得抛异常（这条路径一旦抛，字体与网络两侧都会在启动里挂掉）。
        Assert.assertNotNull("LAUNCH 必须总是可用", LaunchSide.LAUNCH);
        Assert.assertNotNull("LAUNCH 的描述必须可读", LaunchSide.LAUNCH.describe());
    }
}
