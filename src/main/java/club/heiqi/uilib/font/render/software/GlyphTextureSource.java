package club.heiqi.uilib.font.render.software;

/**
 * 字符页纹理像素来源：headless 软件渲染验收场地提供 CPU 侧页像素。
 *
 * <p>真机页纹理由 GL 持有；headless 经软件 {@code GlApi} 把同一上传像素保留在 CPU 侧，
 * 按 textureId 提供给软件光栅化器按 UV 采样，实现与真机 shader 同源的字形 ink 呈现。</p>
 */
public interface GlyphTextureSource {

    /**
     * 解析指定纹理 ID 的页纹理像素。
     *
     * @param textureId 字符页纹理 ID（来自收集批次）
     * @return 页纹理（ARGB 像素 + 边长）；无法解析时返回 null（光栅化器回退几何框模式）
     */
    SoftwarePageTexture resolve(int textureId);
}
