# LanguageRepeater 项目规则

## 编译验证

本项目**覆盖**全局规则中的「生成代码后不要自行编译或测试」一条：**本项目允许并鼓励编译验证**。

改完代码后应主动跑一次编译，确认无误再交付；复杂任务的执行步骤中也应包含编译验证步骤。

```bash
# 首选：只编译 Kotlin，最快，足以抓出类型与引用错误
./gradlew :app:compileDebugKotlin

# 需要验证资源、manifest 合并或产出 APK 时
./gradlew :app:assembleDebug
```

单模块项目（只有 `:app`），JDK 17。编译较慢时放到后台执行，不要反复轮询。

## 其他

全局规则中关于注释、`DuplicatedClickFilter` 防抖在本项目没有, 不用不用考虑, 不压缩语法糖/不省略 `if` 大括号的约定，在本项目继续生效。
