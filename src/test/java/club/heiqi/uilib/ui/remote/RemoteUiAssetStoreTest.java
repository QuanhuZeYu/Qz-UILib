package club.heiqi.uilib.ui.remote;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.net.api.NetResponse;

/**
 * 远程 UI HTML asset store 测试。
 */
public class RemoteUiAssetStoreTest {

    @Test
    public void shouldStoreHtmlBytesSha256AndStreamData() {
        RemoteUiAssetStore store = new RemoteUiAssetStore();
        RemoteUiAssetStore.Asset asset = store.putHtml("<p>hello</p>", 1_000L);

        Assert.assertNotNull(asset.getAssetId());
        Assert.assertEquals("<p>hello</p>", new String(asset.getBytes(), StandardCharsets.UTF_8));
        Assert.assertEquals(RemoteUiAssetStore.sha256Hex("<p>hello</p>".getBytes(StandardCharsets.UTF_8)),
                asset.getSha256());
        Assert.assertEquals(asset.getByteCount(), asset.toStreamData().getByteCount());

        NetResponse response = asset.toStreamData().toResponse();
        Assert.assertTrue(response.isOk());
        Assert.assertEquals(RemoteUiAssetStore.REMOTE_HTML_CONTENT_TYPE, response.getContentType());
        Assert.assertEquals("<p>hello</p>", response.getBody().asUtf8String());
    }

    @Test
    public void shouldCleanupExpiredAssets() {
        RemoteUiAssetStore store = new RemoteUiAssetStore();
        RemoteUiAssetStore.Asset asset = store.putHtml("old", 100L);

        store.cleanupExpired(100L);

        Assert.assertNull(store.get(asset.getAssetId()));
    }
}
