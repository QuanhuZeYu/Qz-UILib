# ERROR-20260820 GlyphPageManager 新增 GlApi 构造器导致 this(null) 重载歧义

## 现象

为 headless 软件渲染验收给 `GlyphPageManager` 新增 `public GlyphPageManager(GlApi glApi)`
注入点后，`new GlyphPageManager()`（无参构造 `this(null)`）抛
`IllegalArgumentException: mailbox capacity/reserve/clock 配置无效`。
受影响测试：`GlyphRuntimeVersionIsolationTest` 14 例。

## 根因

Java 重载解析选**更具体**的形参类型：`null` 同时匹配 `Object` 与 `GlApi` 时，
接口 `GlApi` 比 `Object` 更具体，`this(null)` 被解析到 `GlyphPageManager(GlApi)`，
`glApi=null` 触发构造器校验。

## 修复

```java
public GlyphPageManager() {
    this((Object) null);   // 显式消歧
}
```

## 经验

- 给已有类加「比现有重载更具体」的引用类型构造器时，先查所有 `this(null)`/
  `new X(null)` 单参调用点，显式强转消歧。
- 本类后续新增构造器优先考虑静态工厂而非重载，避免再次踩同名坑。
