# chat3 真机行 junction 丢字符与 stuck hover 排查(K3 三轮)

## 错误现象

K3 三轮截图(f2f23a4b)真机实测三处换行 junction 丢字符(黄线丢 "o "、蓝线丢 "ur "、金色 URL 丢 "orizo"),
URL 第一行恒为 hover 色 (156,203,248)+下划线(截图内无光标、指针最后交互在输入条 → stuck hover),
系统消息行距 18px(应为 font-system 12/16)。

## 排查结论

1. HEAD ChatLineLayouter.splitLines(word-boundary 版)在真实字体度量
   (TextLayoutService/Dialog,GtnhWelcomeLineBreakRealMetricsTest)下,宽度 269/297/300/340 ×
   缩进 0/5 全部零丢失、散文词边界断行——任何路径都无法产生真机断点;旧算法
   (142519d7 前)断点位置与真机一致(如 URL "GT-New-Horizo|ns-Modpack"),但同样不丢字符。
   结论:真机运行的是 HEAD 之外的旧/中间态制品——build/libs 留存每次构建的历史 jar
   (含大量未提交中间态 dirty jar,如 0.187+b9e237cc24-dirty 距 142519d7 提交仅 15 分钟),
   部署拷贝错 jar 即复现。真机核查:mods 目录 qz_uilib jar 文件名内嵌 commit hash,
   与 build/libs 最新制品对照即可定罪。
2. 词边界回退在"前导缩进 + § 码 + 超宽词"场景会把缩进单独断成可见内容为空的 "§6" 行
   (占一行高度,真机 URL 缩进行)。修复:回退/硬断前检查行前缀可见性,无可见字符时不走
   词边界回退、不 emit 空行(commit 见下)。
3. 系统消息 link hover 的 hovered 清理绑定此前仅对非系统消息装配:指针离开系统消息链接
   行后 lineHovered 永久残留 → URL 行 stuck hover 色+下划线。修复:hovered 绑定覆盖全部
   消息,离开即 driver.onPointerLeave()。
4. 系统消息行渲染沿用 body 13/18(设计 font-system 12/16);系统行 pinned width 还被钳到
   maxBubble−2×paddingX(269@360 视口),行实宽 340 溢出节点且居中几何错位。修复:系统
   消息按 12/16 独立切分(专用 ChatLineLayouter)/渲染/高度估算,行宽不钳。
5. §2 DARK_GREEN 经 MinecraftColorTable 查表 = (0,170,0) 与 vanilla 完全一致,真机采样
   (6,194,8)/(0,242,0) 属 AA 边缘/阴影混色偏差,非色表问题。

## 预防措施

- 部署/真机验证前核对 mods 目录 jar 文件名中的 commit hash 与大小/时间戳,必须等于
  build/libs 最新制品;禁止直接拷贝历史 dirty jar。
- 换行器新增分支必须同时断言:零可见字符丢失 + 散文词边界断行 + 无可见内容为空的行。
- 交互清理路径(link hover)必须对"无气泡 hover 的消息类型(系统消息)"同样装配
  hovered 信号绑定,不能只挂在气泡分支下。
