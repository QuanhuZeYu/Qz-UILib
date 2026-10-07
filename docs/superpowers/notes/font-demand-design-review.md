# 字体需求设计核查

实施暂停，用户要求先审专项。规格已按必需Unicode/彩色emoji/复杂脚本、原版性能比较、按需增量生成重写。

旧实现只读参考：font/page/GlyphPageManager.java已有时间/字节/请求预算及可见优先，GlyphUploadPlan保存RGBA；不能将“有预算”当新方案改进。旧manager33—90及相关上传匹配已读，不据局部读取评价全部旧实现。

候选改进待设计验证：实际face/glyph稀疏缓存替代全集direct-index；整形和metrics先行、可见字形请求合并；后台光栅，GL分帧批量区域更新；单色alpha和彩色RGBA分图集；有界需求与实际资源代际。比较吞吐、慢帧和内存后才能宣称更好。

Java2D单独不能据现有探针证明彩色emoji和全部复杂脚本；旧实施计划标失效。需评估完整Unicode双向/断行、HarfBuzz类整形、FreeType类彩色光栅及字体覆盖、与GTNH现有LWJGL3ify原生库的兼容性。工具不是规格，不能因依赖困难删必需能力。

最新用户审阅已归并入字体规格：Unicode文本控制、无效输入替代、默认emoji呈现、统一可配置优先级列表、em/实际行高、GPU未就绪空白、已裁定性能门槛、等待时限实测后提出、印度脚本集合。Q10已按最新反馈归组件策略，不设置字体全局断行默认。原版字体替代默认开启；MC适配入口与scene共享底层服务，不创建scene节点。宿主合批独立稿已按旧实现调查补充优化取舍，要求先做agent可操作的无平台真实集成测试机，减少单元测试；实现仍暂停。

本轮旧实现核对：FontConfig.fontSort、FontOrderPlanner.plan和FontMatcher.resolveFontIndex；旧版确有顺序目录，但还含未配置字体追加、字符规则及字重选择。新规格只继承用户明确要求的统一优先级语义，不自动带回这些分支。
