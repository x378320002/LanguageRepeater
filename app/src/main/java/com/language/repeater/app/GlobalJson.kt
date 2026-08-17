package com.language.repeater.app

import kotlinx.serialization.json.Json

/**
 * 全局 Json 配置
 * 统一配置序列化/反序列化行为
 */
val GlobalJson: Json = Json {
  ignoreUnknownKeys = true // 如果以后增加了字段，旧版本数据读取时不崩溃
  encodeDefaults = true    // 确保默认值被写入
}
