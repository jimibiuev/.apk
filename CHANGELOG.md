# Changelog

## 2.0.0 - 2026-10-01

基于 roubao V1.4.2（MIT）的 aycho 首个版本。

### 品牌改造
- 应用名 `aycho`，包名 `com.aycho.app`
- 主题类 `BaoziColors` / `BaoziTheme` 重命名为 `AychoColors` / `AychoTheme`（含实例名与 CompositionLocal）
- 启动主题 `Theme.Baozi` / `Theme.Baozi.Splash` → `Theme.Aycho` / `Theme.Aycho.Splash`
- 蓝黑配色方案，全量去除 emoji，替换启动图与应用图标

### 功能
- 语音对话：旁白 + TTS + 气泡
- 记忆模块：`AgentMemory` + `MemoryScreen`
- 思考模式开关（`AppSettings` / `SettingsManager` / `SettingsScreen` / `MainActivity` 四处联动）
- 默认 API 服务商改为 Agnes AI（`agnes-2.5-flash`），保留阿里云百炼 / OpenAI / OpenRouter / 自定义端点
- 应用内「设置 - 关于 - 开源声明」入口

### 去指纹
- 截图缓存路径 `/data/local/tmp/autopilot_screen.png` → `/data/local/tmp/aycho_screen.png`
- 内部别名去掉 Autopilot；SharedPreferences、通知渠道、剪贴板标签、崩溃日志目录全部去 `baozi` / `roubao` 化
- `assets/skills.json` 技能名、描述、关键词、参数示例、深链自有参数、优先级全量重写
- `res/xml/network_security_config.xml`、`res/xml/file_paths.xml` 按 aycho 需求重写
- 首页预设指令卡片与中英文案改写
- 移除原项目 logo、截图与 demo 素材

### 版本
- `versionCode` 7 → 20，`versionName` 1.4.2 → 2.0.0
