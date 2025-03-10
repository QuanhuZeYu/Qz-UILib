# Qz-UI - Minecraft 沉浸式界面增强库
✨ 为 Minecraft 带来现代化、一致且高性能的 UI 体验 ✨

## 🌟 特性亮点

### 🎨 已实现功能

- **高清字体渲染系统**
    - ![Font Rendering Demo](docs%2F48e83cb9-21ac-489f-a5ef-7bb2124d7381.png)
    - 基于 Skija 图形库的次像素抗锯齿渲染
    - 支持多语言混合排版（中文/英文/表情符号）
    - 动态纹理分页技术，支持 10,000+ 字符实时渲染
    - 自动字体回退机制（优先使用系统字体）
    - 异步的纹理生成，保证在大量字符缺省时依然流畅（例如更换别的语言时需要重新生成纹理页时）

      - **配置字体教程**: 将需要自定义的字体`.ttf格式`放入根目录下fonts文件夹即可，读取顺序将按照文件名排序，未来实现设置界面重构后将会改为游戏内自定义排序配置

### 🚧 开发路线图
| 模块     | 进度 |主要特性|
|--------|----|-|
| 设置界面重构 | 0% | 现代化布局/暗色主题/搜索功能                                           |
| 全息菜单系统 | 0% | 3D 透视效果/快捷手势操作/自定义皮肤                                    |
| 组件库    | 5% | 可扩展的 UI 组件/主题系统/动画引擎                                    |
| 资源包兼容层 | 0% | 无缝兼容传统资源包/自动样式转换                                        |
