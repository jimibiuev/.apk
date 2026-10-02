# Changelog

## 2.3.1 - 2026-10-02

预发布。**修复「Shizuku 已授权却永远未连接、指令全部无效」的根因**。

- **根因**：`proguard-rules.pro` 中 `-keep class * extends android.app.Service { *; }` 覆盖不到 `ShellService`（它继承 AIDL 的 `IShellService.Stub`，并非 `android.app.Service`）。R8 在 release 构建里将该类重命名/裁剪，Shizuku 服务端按类名字符串反射加载失败，UserService 绑定永久失效
- **dex 层证据**：v2.3.0-release 的 dex 中 `Lcom/aycho/app/service/ShellService;` 完全不存在；未混淆的 v1.4.2-debug 存在；本版已恢复
- **修复**：显式 keep `com.aycho.app.service.ShellService` 与 `IShellService`（含 `$Stub` / `$*`）并加 `-keepnames`；绑定目标由 `ShellService::class.java.name` 改为字面量 `"com.aycho.app.service.ShellService"`
- **语义修正**：权限级别以「是否已授权」为准，不再把「已授权但服务未绑定」误报为未连接；首页文案改为「已授权 · 可以执行指令」
- **绑定兜底**：`awaitReady()` 超时后补一次强制重绑（再等 1.5s），仍失败才如实返回未就绪；已授权但未连接时输出显式诊断日志
- **通道说明**：Shizuku 13.1.5 的 `newProcess` 为 private、服务端接口不在公开构件中，无法做旁路兜底；UserService 是官方唯一支持的执行通道
- 沿用 2.3.0 全部修复

## 2.3.0 - 2026-10-02

预发布。Shizuku 授权状态可观察 + 操作执行链路修复。

- **状态可观察**：新增 `ShizukuState`（未运行 / 未授权 / 已授权待连接 / 已连接），连接、断开、权限变化主动回调 UI，界面实时刷新
- **执行前置守卫**：进入 ReAct 循环前 `awaitReady()` 等待 UserService 就绪，未就绪直接返回明确原因，不再空转耗尽步数
- **打开应用一步直达**：纯「打开 / 启动 / 进入某应用」指令走本地快速路径，零模型往返
- **打开应用多级回退 + 结果校验**：`monkey` → 显式 `am start` Intent → launcher 组件 `am start -n`，每次轮询前台包名确认真实结果
- **执行结果可判定**：新增 `ShellResult`（ok / output / channel / code），快速模式不再无条件判成功
- **Shell 工具接入 Shizuku**：`ShellBridge` 统一走 `DeviceBridge` 执行通道
- **移除隐式 su 兜底**：su 仅在显式允许时尝试并统一加超时，无 root 设备不再长时间卡住

## 2.2.2 - 2026-10-01

预发布（Pre-release）。服务商显示名英文化。

- 设置页「阿里云 (Qwen-VL)」→ **Qwen-VL**，默认模型卡片副标题 → 「Qwen-VL 视觉模型」（仅界面文案）
- 内部标识 `aliyun`、`baseUrl`（DashScope 兼容模式）、默认模型 `qwen3-vl-plus` 保持不变——该标识为旧版本写入用户配置的键，改动会导致老用户升级后配置丢失
- 沿用 2.2.1 全部修复

## 2.2.1 - 2026-10-01

预发布。极速手感回归 + Shizuku 执行链路修复。

- **极速手感回归**：命中本地意图的委托型指令走快速路径，不请求模型、不截图、不等待
- **修复「指令不执行」**：补回 Qwen-VL 与 OpenRouter 服务商，默认模型恢复为 `qwen3-vl-plus`，旧版残留标识自动归一化迁移
- **Shizuku / Root 修复**：移除 `ShellService` 的无效 `<service>` 声明（该服务由 Shizuku fork 进程反射实例化，声明既无效又阻断构建）；可用性判定改为先 `pingBinder`；执行通道明确为 Shizuku → Root → 本地兜底
- **悬浮窗再缩小**：直径 116–232dp 收至 78–150dp

## 2.2.0 - 2026-10-01

预发布。悬浮窗重做 + 执行提速。

- **悬浮窗（AI 气泡）重做**：长条七彩渐变 → 纯蓝全圆形，圆内显示 AI 文本与「第 N/M 步」，直径随文字自适应，停止/确认改为黑底白字胶囊
- **Shizuku 状态刷新修复**：授权返回与绑定变化后主动刷新可用状态
- **执行提速**：首步等待 5s → 1.2s、后续 2s → 0.6s；截图改轮询（通常 <100ms）；快速模式跳过二次截图；上传体积长边缩至 1024 + JPEG60；会话历史限最近 10 条；网络超时与重试下调；任务记忆提炼改后台异步

## 2.1.0 - 2026-10-01

首个对外正式版（`releases/latest` 当前指向本版）。VLM 驱动的屏幕理解与自动操作，支持 Shizuku / Root，含语音旁白播报、记忆模块、思考模式开关与 21 项内置技能。

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
