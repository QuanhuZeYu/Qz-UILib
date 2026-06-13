# 背景模糊系统配置化改造审查

**日期**: 2026-06-13  
**分支**: `add/configurable-backdrop-blur`  
**提交**: `ec0429a3`

## 改造目标

1. 新增可配置的UI背景模糊参数系统，提供常用参数和高级参数
2. 审查背景模糊设计的解耦性，评估迁移到不同MC版本的难度

## 设计审查结论

### 解耦性评估：★★★★☆ (4/5)

#### ✅ 良好解耦的部分

1. **渲染管线层次清晰**
   - `UiBackdropFilterRenderer` 作为独立渲染管线，与业务逻辑分离
   - 通过 `UiRenderContext` 接口隔离宿主环境
   
2. **CSS语义抽象**
   - 使用 `backdrop-blur-radius` 和 `backdrop-saturation` CSS-like 属性
   - 不向页面作者暴露底层 OpenGL 实现细节
   
3. **多层降级策略**
   - Shader → 固定管线 → Tint fallback
   - 兼容性强，各路径可独立开关
   
4. **MC版本无关的抽象**
   - 使用标准 OpenGL API (GL11/GL13/GL20)
   - 不直接依赖 Minecraft 版本特定 API

#### ⚠️ 轻微耦合点

1. **Tessellator 使用**
   - 使用 `Tessellator.instance` (MC 1.7.10 特定)
   - 迁移到新版本需改为 `BufferBuilder`

2. **LWJGL2 依赖**
   - FBO 操作使用 LWJGL2 API
   - 迁移到 LWJGL3 需调整 GL 调用方式

#### 🔧 迁移建议

迁移到其他MC版本时需调整：
- Tessellator → BufferBuilder (1.8+)
- LWJGL2 → LWJGL3 GL调用 (1.12.2+)
- 保持渲染管线和配置系统结构不变

### 原设计可优化点

1. ❌ **硬编码参数过多** → ✅ 已解决
   - `MAX_BACKDROP_BLUR_RADIUS = 48` 硬编码
   - Shader 中 `clamp(blurRadius, 1.0, 56.0)` 硬编码
   - 固定管线采样点数（8点）硬编码
   - 降采样策略阈值硬编码

2. ❌ **缺少配置入口** → ✅ 已解决
   - 宿主级背景模糊无任何参数暴露
   - 性能相关阈值无法调整
   - 降级策略无法由用户控制

3. ⚠️ **Shader参数限制** → 🔄 部分改善
   - 当前仍只支持 blur 和 saturation 两个参数
   - 采样模式固定为12点径向模糊
   - 未来可考虑支持自定义采样模式

## 实现方案

### 1. BackdropBlurConfig 配置类

新增 `club.heiqi.uilib.ui.render.BackdropBlurConfig` 单例配置类：

#### 常用参数 (3个)
- `maxBlurRadius`: 元素级模糊半径上限 (0-128, 默认48)
- `hostBackgroundBlurEnabled`: 宿主级背景模糊开关 (默认true)
- `hostBackgroundBlurStrength`: 宿主级模糊强度倍率 (0.0-3.0, 默认1.0)

#### 高级参数 - 渲染路径控制 (5个)
- `shaderEnabled`: Shader路径开关 (默认true)
- `shaderBlurRadiusLimit`: Shader内blur半径上限 (1.0-128.0, 默认56.0)
- `fixedPipelineEnabled`: 固定管线降级开关 (默认true)
- `fixedPipelineSampleCount`: 固定管线采样点数 (4-16, 默认8)
- `tintFallbackEnabled`: Tint降级开关 (默认true)

#### 高级参数 - 性能优化 (6个)
- `snapshotPoolSize`: 快照池最大容量 (8-128, 默认32)
- `downsampleThreshold`: 降采样触发阈值 (8-64, 默认16)
- `maxDownsampleFactor`: 最大降采样因子 (1-8, 默认4)
- `tileSize`: Tile网格尺寸 (64-256, 默认128, 必须2的幂)
- `contentVersionTrackingEnabled`: 快照内容版本追踪 (默认true)
- `separableBlurEnabled`: Separable blur filter (默认true)

#### 高级参数 - 调试与诊断 (2个)
- `diagnosticsEnabled`: 渲染路径诊断信息记录 (默认true)
- `logSnapshotFailures`: 快照失败警告日志 (默认false)

#### 便捷预设方法
- `resetToDefaults()`: 重置为默认值
- `applyPerformancePreset()`: 性能优先（降低质量提升性能）
- `applyQualityPreset()`: 质量优先（提升质量可能降低性能）
- `applyCompatibilityPreset()`: 兼容性优先（禁用高级特性）

### 2. 代码集成点

#### DocumentEffectChain.java
```java
// 模糊半径上限从配置读取
private static int resolveBackdropBlurRadius(DocumentLayoutBox box, ComputedStyle style) {
    int maxRadius = BackdropBlurConfig.getInstance().getMaxBlurRadius();
    return Math.max(0, Math.min(radius, maxRadius));
}
```

#### UiBackdropFilterRenderer.java
```java
// Shader路径可配置禁用
if (!config.getShaderEnabled()) {
    return false;
}

// 固定管线采样点数可配置
int sampleCount = Math.min(config.getFixedPipelineSampleCount(), UI_BACKDROP_BLUR_SAMPLES.length);

// Tint降级可配置禁用
if (!config.getTintFallbackEnabled()) {
    recordPath(BackdropFilterRenderPath.NONE, "tint-fallback-disabled");
    return;
}

// Shader半径上限从配置读取
float maxShaderRadius = config.getShaderBlurRadiusLimit();
```

#### UiHostBackgroundBlurRenderer.java
```java
// 宿主级模糊开关
if (!config.getHostBackgroundBlurEnabled()) {
    return;
}

// 宿主级模糊强度可调
float strength = config.getHostBackgroundBlurStrength();
float offsetX = sample[0] * strength / nativeWidth;
float offsetY = sample[1] * strength / nativeHeight;
```

#### uiBackdropF.frag
```glsl
// Shader内上限从56提升到128，配合配置系统
vec2 radiusStep = texelSize * clamp(blurRadius, 1.0, 128.0);
```

### 3. 向后兼容

- `DocumentEffectChain.MAX_BACKDROP_BLUR_RADIUS` 保留但标记为 `@Deprecated`
- 默认配置值与原有硬编码值一致，行为无变化
- 所有配置参数均带范围限制和默认值

## 性能影响

- 配置读取为单例访问，无明显性能开销
- 预设方法便于用户在性能与质量间切换
- 降采样、快照池等高级参数可精细调优性能

## 测试验证

- ✅ 编译测试通过 (`./gradlew compileJava`)
- ⏳ 需要游戏内测试三种预设的视觉效果
- ⏳ 需要测试各渲染路径开关的降级行为

## 后续建议

1. **配置持久化**
   - 当前为内存配置，重启丢失
   - 建议接入 `ForgeConfigTemplateScreen` 或独立配置文件

2. **配置页面**
   - 为常用参数和高级参数创建可视化配置页
   - 预设按钮便于快速切换

3. **性能分析工具**
   - 集成到 `/qzuilib test` 诊断页面
   - 显示当前渲染路径、快照复用率、平均模糊耗时

4. **自定义采样模式**
   - 未来可考虑支持用户自定义采样点和权重
   - 需要重新设计 shader uniform 传递方式

## 架构优势总结

1. **配置与渲染分离**：配置类独立，渲染逻辑保持稳定
2. **单一职责**：每个配置参数对应明确的渲染行为
3. **扩展性强**：新增参数无需修改现有渲染管线
4. **迁移友好**：核心抽象层与MC版本解耦，只需调整Tessellator和GL调用

## 相关文件

- `src/main/java/club/heiqi/uilib/ui/render/BackdropBlurConfig.java` (新增)
- `src/main/java/club/heiqi/uilib/ui/layout/DocumentEffectChain.java` (修改)
- `src/main/java/club/heiqi/uilib/ui/render/UiBackdropFilterRenderer.java` (修改)
- `src/main/java/club/heiqi/uilib/ui/screen/UiHostBackgroundBlurRenderer.java` (修改)
- `src/main/resources/shader/uiBackdropF.frag` (修改)
