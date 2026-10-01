<p align="center">
  <strong>aycho</strong> · 基于视觉语言模型的 Android 自动化助手
</p>

# aycho

用自然语言描述任务，aycho 在手机上自动完成操作。基于视觉语言模型（VLM）理解屏幕、规划步骤并执行点击 / 输入 / 跳转。

## 特性

- **语音对话**：旁白 + TTS 播报 + 气泡交互，执行过程不打断当前页面
- **记忆模块**：跨任务保存并复用偏好（AgentMemory / MemoryScreen）
- **思考模式开关**：按需展开模型推理过程
- **技能体系**：21 项内置技能，支持应用深链直达与 GUI 自动化两条执行路径
- **主题**：蓝黑深色配色 + 浅色模式，全量去除 emoji，图标体系统一为 Material Icon
- **多后端**：Agnes AI（默认）/ 阿里云百炼 / OpenAI / OpenRouter / 自定义 OpenAI 兼容端点

## 环境要求

- Android 8.0 (API 26) 及以上
- Shizuku（或 Root）用于自动化执行权限

## 构建

```bash
./gradlew assembleDebug
```

依赖：JDK 17、Android SDK 34、Gradle 8.x

## 使用

1. 安装并启动 Shizuku，授予 aycho 权限
2. 在「设置 - API 服务商」填入 API Key（默认 Agnes AI）
3. 在首页输入或语音说出任务，例如「帮我在美团点一份常吃的套餐」

## 开源声明

aycho 是基于开源项目 [Turbo1123/roubao](https://github.com/Turbo1123/roubao)（MIT License，Copyright (c) 2025 Roubao Team）的二次开发版本。

- 自动化执行框架继承自 roubao / Mobile-Agent 系列的多 Agent 协作架构（Manager / Executor / Reflector / Notetaker）
- 技能体系沿用 Skills + Tools 双层结构与 JSON 描述方式
- 品牌、界面、配色、图标、语音对话与记忆模块为 aycho 自有实现

原项目许可证全文见 [LICENSE](LICENSE)，第三方声明见 [NOTICE](NOTICE)，改动记录见 [CHANGELOG.md](CHANGELOG.md)。
