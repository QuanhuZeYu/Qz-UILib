# 媒体适配的许可、来源与可替换动态库

本目录属于独立 media-adapter / media-adapter-java8 两个本地产物。UILib 自有 Java 实现按根许可证；下列第三方按其各自许可。核心不携带媒体绑定或native。版本/发布策略由既有构建管理，本材料不创建publication。

## 实际固定组成

| 组件 | 精确输入 | 适用许可和对应来源 |
| --- | --- | --- |
| JavaCPP | org.bytedeco:javacpp:1.5.10 及 windows-x86_64 | 选Apache-2.0；完整双许可原文随附。源与JNI工具链 https://github.com/bytedeco/javacpp/tree/1.5.10 |
| FFmpeg JavaCPP preset | org.bytedeco:ffmpeg:6.1.1-1.5.10 及 windows-x86_64，不选gpl classifier | preset选Apache-2.0；生成绑定/构建脚本 https://github.com/bytedeco/javacpp-presets/tree/1.5.10/ffmpeg 。原始native实际avutil/avcodec/avformat均返回LGPL version 3 or later，启用--enable-version3/--enable-shared，不含--enable-gpl。FFmpeg n6.1.1对应源码 https://ffmpeg.org/releases/ffmpeg-6.1.1.tar.bz2 |
| HLS列表解析 | io.lindstrom:m3u8-parser:0.29 | MIT原文及版权随附；https://github.com/carlanton/m3u8-parser/tree/release-0.29 |
| LWJGL core/OpenAL | org.lwjgl:lwjgl:3.4.2、lwjgl-openal:3.4.2，natives-windows | BSD原文；https://github.com/LWJGL/lwjgl3/tree/3.4.2 。根JNI类不改名；仅移除私有嵌套LWJGL的META-INF/versions覆盖，固定根JNI实现，宿主绑定不改 |
| OpenAL Soft | 所选LWJGL3.4.2 native实报OpenAL Soft1.25.2 | LGPL2-or-later，官方COPYING及版权随附；https://github.com/kcat/openal-soft/tree/1.25.2 ，对应源码 https://github.com/kcat/openal-soft/archive/refs/tags/1.25.2.tar.gz |

原Jar URL按 https://repo.maven.apache.org/maven2/ 的group路径、artifact/version/文件名组成。打包任务按固定GAV解析原Jar；assets/qz_uilib/runtime/media/index.txt记录实际嵌套字节SHA256，依赖包名/JNI名保持原样，JavaCPP/FFmpeg Jar未裁剪或重定位。FFmpeg Jar内附ffmpeg.exe/ffprobe.exe保留，但播放从未执行这些程序。

## FFmpeg实际启用组件的配套来源

准确配置及每个平台构建步骤见随附sources/ffmpeg-cppbuild.sh，来自presets1.5.10官方原文。以下是该Windows构建实际启用的库版本，许可正文按文件名随附；不是因本应用调用某些codec而删除其余随附组件的许可。

| 库 | 构建版本/对应源码 | 许可材料 |
| --- | --- | --- |
| zlib | 1.3.1 https://zlib.net/fossils/zlib-1.3.1.tar.gz | zlib-LICENSE |
| LAME | 3.100 https://downloads.sourceforge.net/project/lame/lame/3.100/lame-3.100.tar.gz | lame-COPYING、lame-LICENSE |
| Speex | 1.2.1 https://ftp.osuosl.org/pub/xiph/releases/speex/speex-1.2.1.tar.gz | speex-COPYING |
| Opus | 1.3.1 https://archive.mozilla.org/pub/opus/opus-1.3.1.tar.gz | opus-COPYING |
| opencore-amr | 0.1.6 https://downloads.sourceforge.net/project/opencore-amr/opencore-amr/opencore-amr-0.1.6.tar.gz | opencore-amr-LICENSE |
| vo-amrwbenc | 0.1.3 https://github.com/mstorsjo/vo-amrwbenc/tree/v0.1.3 | vo-amrwbenc-COPYING |
| OpenSSL | 3.2.0 https://github.com/openssl/openssl/tree/openssl-3.2.0 | OpenSSL-LICENSE |
| OpenH264 | 2.4.0 https://github.com/cisco/openh264/tree/v2.4.0 | OpenH264-LICENSE |
| libvpx | 1.13.1 https://github.com/webmproject/libvpx/tree/v1.13.1 | libvpx-LICENSE、PATENTS |
| FreeType | 2.13.2 https://github.com/freetype/freetype/tree/VER-2-13-2 | 选择FTL，FreeType-FTL.txt；本发行使用FreeType，其版权归FreeType Project |
| libxml2 | 2.9.12 https://github.com/GNOME/libxml2/tree/v2.9.12 | libxml2-Copyright |
| SRT | 1.5.3 https://github.com/Haivision/srt/tree/v1.5.3 | SRT-LICENSE（MPL2） |
| libwebp | 1.3.2 https://github.com/webmproject/libwebp/tree/v1.3.2 | libwebp-COPYING |
| libaom | 3.8.0 https://aomedia.googlesource.com/aom/+/refs/tags/v3.8.0 | AOM-LICENSE、PATENTS |
| SVT-AV1 | 1.8.0 https://gitlab.com/AOMediaCodec/SVT-AV1/-/tree/v1.8.0 | SVT-AV1-LICENSE |
| Intel mfx dispatcher | 1.35.1 https://github.com/lu-zero/mfx_dispatch/tree/1.35.1 | mfx-LICENSE |
| NVIDIA codec headers | n12.1.14.0 https://github.com/FFmpeg/nv-codec-headers/tree/n12.1.14.0 | nv-codec-headers-COPYRIGHT.txt；只绑定本机现有驱动，不分发CUDA驱动 |

构建参数还包含Vulkan/硬件codec和MinGW/GCC静态运行支持。GCC Runtime Exception和mingw-w64许可随附；原JavaCPP windows-x86_64 Jar还保留其上游分发的Microsoft UCRT/MSVC DLL。后者保持上游二进制原样，其适用条款/来源由JavaCPP1.5.10官方native发行及Microsoft Visual C++ runtime条款说明；它们不变成Apache许可。本仓不宣称重建这些专有系统运行库。

## 对应源码和重建

随附本仓sourcesJar含全部UILib媒体Java源、装配逻辑与本目录。JavaCPP1.5.10和presets1.5.10完整源码归档分别在 https://github.com/bytedeco/javacpp/archive/refs/tags/1.5.10.tar.gz 与 https://github.com/bytedeco/javacpp-presets/archive/refs/tags/1.5.10.tar.gz 。sources/ffmpeg-cppbuild.sh逐项固定所有第三方源码URL/版本与编译选项；对应源码按上表提供，不将所有独立库混称本仓源。上游native库没有本仓修改；本仓变更止于Java适配/私有装配、有限计数和LWJGL多版本覆盖剔除。

本地重建使用既有Gradle任务coreJar/fontAdapterJar/desktopJar/mediaAdapterJar与独立coreLegacyJar/fontAdapterLegacyJar/mediaAdapterLegacyJar；两套算法源同源，主线javac21 --release21，legacy --release8。FFmpeg上游native重建按presets1.5.10顶层cppbuild及此目录脚本选择windows-x86_64；使用相同配置、对应源码和工具链，JNI类名/ABI保持相容。OpenAL Soft按1.25.2源码的CMake说明构建动态库。

## 替换和调试动态库

允许为调试对LGPL库作修改和逆向工程；这些库仍按其自身许可。启动前设置只读系统选项-Duilib.media.runtime=<directory>，该目录放index.txt列出的全部同名Jar，可用同ABI重建/修改的native Jar替换；自定义内容不受默认随包SHA限制。native Jar保留原资源路径，JavaCPP/FFmpeg/LWJGL绑定与JNI名保持兼容。选项只在第一次媒体open读取，生效需重启JVM；本适配从不写进程全局org.lwjgl.librarypath/JavaCPP缓存属性。

默认私有加载器按所属application classloader复用一次。会话关闭释放自己的format/codec/packet/PCM/device/context/source/buffer，Windows已映射DLL到JVM退出前可能驻留，不以session close冒充卸载。第三方动态库替换不涉及更改游戏、字体或窗口的绑定。
