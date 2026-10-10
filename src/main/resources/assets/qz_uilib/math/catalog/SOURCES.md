# 数学目录静态事实来源

本目录供 UILib 自有数学能力读取事实，不包含 KaTeX renderer、解析器、宏执行器或布局算法。

- KaTeX：`0.16.21`，commit `923f2aa15692d06ef77051bfb018c17339ef1ab4`；源 bundle SHA-256 `65e825a9ca4ff2a8e85a52d765436f347c212a2241aed1f2ad1b13532e136c39`。
- VS Code：`1.140.0`，commit `07f806f999227108933c2e30515b26eecc1fda74`；内置插件 `@vscode/markdown-it-katex 1.1.2`。
- 每行的 sourcePath/sourceUrl 与 distLine 原样来自固定注册表；distLine 是固定 bundle 的登记行号，不是源文件行号。普通字母和补充平面 Unicode 注册项同样保留其来源。
- [原始 MIT 许可](<LICENSE-KaTeX.txt>) 随资源发布；Copyright (c) 2013-2020 Khan Academy and other contributors。

正式数据为 [symbols.tsv](<symbols.tsv>)、[functions.tsv](<functions.tsv>)、[environments.tsv](<environments.tsv>)、[macros.tsv](<macros.tsv>)、[implicit.tsv](<implicit.tsv>) 和 [reference.tsv](<reference.tsv>)。它们统一由测试资料中的 [固定 facts](<../../../../../../test/resources/math/reference/katex-0.16.21-facts.json>) 与 [纯生成脚本](<../../../../../../test/resources/math/reference/generate-catalog.mjs>) 产生。脚本的 `generate(facts)` 返回六份资源文本以及参照 corpus 和支持矩阵，不读文件、不执行第三方代码；调用方用正式读写工具管理 IO。

UTF-8 TSV 保留空白与原始 command token，不做 trim。字段编码：`\\` 为字面反斜杠，`\t`/`\n`/`\r` 为 Tab/LF/CR；独立字段 `\N` 为 null，因此字面 `\N` 必须编码为 `\\N`。空字符串与 null 不等价。函数/环境尾部以 argTypesSpecified 和 argTypeCount 加各参数字段表示原始 argTypes。

符号 font=main/ams 和 group 是参照事实，不直接绑定宿主字体。所有 Unicode 与 command 别名保持独立行；replace=null 的 spacing token 仍有语义。宏 kind=string 的 expansion 全部保留（包括参数 #1/#2/#3、转义 ##、分组、空白）；kind=function 没有 expansion，源实现仅由来源定位。目录集合和 descriptor 不可变；登记本身不等于生产语义、布局或 GPU 支持。
