<p align="center">
  <img src="docs/images/banner.png" alt="aycho" width="100%">
</p>

<h1 align="center">aycho</h1>

<p align="center">把一句话交给它，手机自己把事办完。</p>

<p align="center">
  <b>简体中文</b> ｜ <a href="README_EN.md">English</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android%208.0%2B-2674F8?style=flat-square" alt="platform">
  <img src="https://img.shields.io/badge/version-2.1.0-2674F8?style=flat-square" alt="version">
  <img src="https://img.shields.io/badge/license-MIT-2674F8?style=flat-square" alt="license">
  <img src="https://img.shields.io/badge/theme-%E8%93%9D%E9%BB%91%E6%B7%B1%E8%89%B2-0B1220?style=flat-square" alt="theme">
</p>

---

## 目录

- [项目简介](#项目简介)
- [应用概览](#应用概览)
- [核心特性](#核心特性)
- [工作原理](#工作原理)
- [环境要求](#环境要求)
- [下载安装](#下载安装)
- [从源码构建](#从源码构建)
- [快速上手](#快速上手)
- [模型服务商](#模型服务商)
- [内置技能](#内置技能)
- [项目结构](#项目结构)
- [常见问题](#常见问题)
- [开源说明](#开源说明)
- [许可证](#许可证)

---

## 项目简介

**aycho** 是一款运行在 Android 手机上的自动化助手。你用自然语言说出想做的事——「帮我点一份常吃的套餐」「导航到公司」「给朋友发条消息」——aycho 会看懂当前屏幕、自己规划步骤，并把点击、输入、跳转这些操作一步步做完。

它不依赖任何第三方 App 的私有接口，也不要求对方开放 API。aycho 走的是**视觉理解 + 界面操作**这条路线：截图理解屏幕内容，像人一样去点、去滑、去输入，因此对绝大多数应用都能用。

整个过程以浮层气泡的形式在屏幕上方呈现，不打断你正在看的页面；完成后自动收起，把结果留给你。

## 应用概览

<p align="center">
  <img src="docs/images/app_icon.png" width="112" alt="应用图标">
  &nbsp;&nbsp;&nbsp;
  <img src="docs/images/splash_preview.png" width="260" alt="启动画面">
</p>

<p align="center"><sub>应用图标与启动画面 · 蓝黑深色配色体系</sub></p>

界面由六个页面组成，各自职责清晰：

| 页面 | 作用 |
| --- | --- |
| 首页 | 输入或语音说出任务，展示执行过程气泡 |
| 能力 | 查看全部内置技能及其可执行范围 |
| 记忆 | 管理跨任务复用的偏好信息 |
| 历史 | 回看已完成任务的执行记录 |
| 设置 | 模型服务商、思考模式、语音播报等开关 |
| 引导 | 首次启动时的权限与用法说明 |

## 核心特性

**自然语言驱动**
不需要记任何命令或菜单路径。说清楚目标即可，中间的步骤规划、界面识别、异常兜底全部由 aycho 自己处理。

**看得懂屏幕**
每一轮操作前先截图理解界面：识别按钮、输入框、列表项的位置与语义，判断当前处于哪一层页面，再决定下一步动作。

**语音对话与播报**
执行过程中用旁白气泡同步进度，支持 TTS 语音播报。播报只占用浮层，不会把你的页面顶掉；随时可以打断或中止。

**记忆模块**
跨任务保存并复用你的偏好——常点的套餐、家与公司地址、常用联系人。下次提同类需求时不必重复交代。

**思考模式可开关**
需要推理链时展开模型思考过程，追求速度时一键关闭，走快速响应通道。

**技能体系**
内置 21 项技能，覆盖外卖、出行、购物、社交、影音、支付、工具等高频场景。每条技能同时具备两条执行路径：能深链直达的走深链，不能的退化为界面自动化，保证可用性。

**多模型后端**
模型网关同时支持 OpenAI、Gemini、Claude 三种协议族，内置多家服务商，也支持填入任意 OpenAI 兼容端点。换模型不用改代码，在设置页切一下即可。

**统一的视觉语言**
蓝黑深色主题贯穿全局，图标体系统一使用 Material Icon，界面内不使用任何 emoji，长时间使用不刺眼。

## 工作原理

aycho 内部是一个协作式的 Agent 运行时，任务被拆成四步循环推进：

```text
观察观察（截图 + 界面解析）
   ↓
规划（理解目标 → 拆解下一步动作）
   ↓
执行（点击 / 输入 / 滑动 / 深链跳转）
   ↓
验证（确认结果是否符合预期，不符则回退重试）
   ↑______________ 循环直到任务完成 ______________|
```

几个关键设计：

- **任务级共享黑板**：规划、执行、验证三方通过 Blackboard 交换状态，避免各模块各自为战。
- **执行结果校验**：每一步动作后都做一次结果确认，点错了能及时发现并纠正，而不是一路错到底。
- **会话记忆与 token 控制**：截图用完即删，只保留必要的文本上下文，长时间任务也不会把上下文撑爆。
- **能力注册表**：所有原子工具（应用查找、应用启动、深链路由、Shell 桥接、网络请求、剪贴板）统一注册，规划器按需调用。

## 环境要求

| 项目 | 要求 |
| --- | --- |
| 系统 | Android 8.0（API 26）及以上 |
| 权限 | Shizuku（推荐）或 Root |
| 网络 | 需要联网访问所选模型服务商 |
| 模型 | 一个可用的 API Key（默认使用 Agnes AI） |

> aycho 通过 **Shizuku** 获取自动化执行权限，无需 Root。Shizuku 的授权由系统层面的 ADB 权限提供，比无障碍服务更稳定，也不会常驻读取你的屏幕。

## 下载安装

1. 打开 [Releases](https://github.com/jimibiuev/aycho/releases) 页面，下载最新版本的 `aycho-v2.1.0-release-signed.apk`
2. 在系统设置中允许「安装未知来源应用」，然后完成安装
3. 安装并启动 **Shizuku**，按提示完成授权（Android 11 及以上可通过无线调试启动）
4. 回到 aycho，在「设置 → API 服务商」填入你的 API Key
5. 回到首页，说出你的第一个任务

**校验安装包完整性**（可选）：

```bash
sha256sum -c SHA256SUMS.txt
```

## 从源码构建

需要 JDK 17、Android SDK 34、Gradle 8.x：

```bash
git clone https://github.com/jimibiuev/aycho.git
cd aycho
./gradlew assembleDebug
```

构建产物位于 `app/build/outputs/apk/debug/`。

如需自行签名正式包，在 `app/build.gradle.kts` 中配置签名信息后执行：

```bash
./gradlew assembleRelease
```

> 请在本地妥善保管签名密钥（`.jks`），它一经丢失将无法为已发布应用推送升级。仓库的 `.gitignore` 已默认排除所有密钥文件。

## 快速上手

装好之后，在首页直接说出你要做的事就行：

| 你可以说 | aycho 会做 |
| --- | --- |
| 帮我在附近找家评分高的川菜馆 | 打开本地生活应用，搜索并按评分筛选 |
| 导航到公司 | 规划路线并唤起导航 |
| 帮我叫辆车去机场 | 打开出行应用，填写终点并呼叫 |
| 给妈妈发消息说我晚点到 | 打开聊天应用，找到联系人并发送 |
| 设一个明早七点的闹钟 | 打开时钟应用完成设置 |
| 买两张明天下午的电影票 | 进入购票流程并选座 |
| 帮我把刚才拍的照片发个动态 | 编辑并发布社交动态 |

执行时屏幕上方会出现旁白气泡，实时说明当前在做什么。任务完成后气泡自动收起。

## 模型服务商

在「设置 → API 服务商」中选择或自定义：

| 服务商 | 协议 | 说明 |
| --- | --- | --- |
| Agnes AI | OpenAI 兼容 | 默认选项，开箱即用 |
| Google Gemini | Gemini | 原生协议接入 |
| OpenAI | OpenAI | 官方接口 |
| Anthropic Claude | Claude | 原生协议接入 |
| 自定义 | OpenAI 兼容 | 填入 Base URL + 模型 ID + API Key 即可对接任意兼容端点 |

**API Key 只保存在本机**，使用系统加密存储，不会上传到任何第三方服务器。

## 内置技能

共 21 项，按场景分组：

**生活与消费**

| 技能 | 说明 |
| --- | --- |
| 外卖下单 | 按口味挑选餐厅并下单外卖、订餐 |
| 附近美食 | 搜索附近的餐厅与好吃的去处 |
| 附近玩乐 | 搜索附近好玩的地方与娱乐推荐 |
| 酒店预订 | 预订酒店与民宿 |
| 电影票购买 | 在线购买电影票并选座 |
| 在线购物 | 在网上购物下单 |

**出行**

| 技能 | 说明 |
| --- | --- |
| 路线导航 | 规划路线并导航到目的地 |
| 叫车出行 | 呼叫网约车出行 |

**社交与内容**

| 技能 | 说明 |
| --- | --- |
| 消息发送 | 给好友发消息 |
| 动态发布 | 发布社交动态与图文 |
| 图文笔记 | 发布图文笔记与长文分享 |

**影音阅读**

| 技能 | 说明 |
| --- | --- |
| 音乐播放 | 播放想听的音乐 |
| 视频播放 | 看视频、刷短视频 |
| 电子书阅读 | 阅读电子书 |

**支付与工具**

| 技能 | 说明 |
| --- | --- |
| 扫码付款 | 扫码付款、完成支付 |
| 二维码扫描 | 扫描二维码与条码 |
| 相机拍照 | 打开相机拍照 |
| 闹钟提醒 | 设置闹钟与定时提醒 |

**AI 能力**

| 技能 | 说明 |
| --- | --- |
| 智能问答 | 与 AI 助手对话问答 |
| 通用助手 | 调用 AI 助手代办查询、规划与生活服务 |
| 图片生成 | 用 AI 生成图片 |

## 项目结构

```text
app/src/main/java/com/aycho/app/
├── App.kt / MainActivity.kt        应用入口与主界面容器
├── agent/                          Agent 运行时
│   ├── AgentRuntime.kt             任务总控：观察 → 规划 → 执行 → 验证
│   ├── Planner.kt                  屏幕理解与下一步动作规划
│   ├── Actuator.kt                 动作执行（点击 / 输入 / 滑动 / 深链）
│   ├── Verifier.kt                 执行结果校验与纠错
│   ├── Scribe.kt                   过程记录与播报文本生成
│   ├── Blackboard.kt               任务级共享状态
│   └── SessionMemory.kt            会话记忆（图片用后即删）
├── skills/                         技能体系（SkillRegistry / IntentRouter）
├── tools/                          原子工具（AppFinder / AppLauncher / UriRouter /
│                                   ShellBridge / HttpRequest / ClipboardTool）
├── controller/                     AppIndexer（应用索引）/ DeviceBridge（设备状态）
├── vlm/ModelGateway.kt             模型网关：OpenAI / Gemini / Claude 三协议族
├── ui/                             界面层
│   ├── HeadsUpService.kt           浮层气泡（前台服务）
│   ├── screens/                    首页 / 能力 / 记忆 / 历史 / 设置 / 引导
│   └── theme/                      蓝黑深色主题
├── voice/VoiceManager.kt           旁白与 TTS 播报
├── service/ShellService.kt         Shell 执行桥接
├── data/                           偏好与记忆存储
└── utils/CrashHandler.kt           崩溃兜底

app/src/main/assets/
├── intents.json                    技能与意图定义
└── licenses/                       第三方许可声明
```

## 常见问题

**需要 Root 吗？**
不需要。使用 Shizuku 即可完成授权，Android 11 及以上可直接通过无线调试激活，不必连接电脑。

**会不会读取我的隐私？**
aycho 只在你主动发起任务时截图分析当前屏幕，用于判断下一步该点哪里。截图在当轮用完即删，不上传、不留存。

**API Key 存在哪里？**
仅保存在本机加密存储中，不会同步、不会上传。

**为什么有些操作要确认？**
涉及支付、密码、隐私类动作时，aycho 会先暂停并向你确认，避免误操作造成损失。

**支持哪些 Android 版本？**
Android 8.0（API 26）及以上。

**能自动完成任何 App 的操作吗？**
能走深链的优先走深链，其余通过界面自动化完成。绝大多数主流应用都可以操作；少数有强反自动化检测的应用可能受限。

## 开源说明

aycho 是基于开源项目 [Turbo1123/roubao](https://github.com/Turbo1123/roubao)（MIT License）的二次开发版本，在原有自动化执行框架的基础上完成了品牌、界面、语音对话、记忆模块与技能体系的重新实现。

- 自动化执行框架的多 Agent 协作架构继承自 roubao / Mobile-Agent 系列
- 技能体系沿用 Skills + Tools 双层结构与 JSON 描述方式
- 品牌、界面、配色、图标、语音与记忆模块为 aycho 自有实现

上游项目的 MIT 许可证全文见 [LICENSE](LICENSE)，第三方组件声明见 [NOTICE](NOTICE) 与 [THIRD_PARTY_LICENSES](app/src/main/assets/licenses/THIRD_PARTY_LICENSES.txt)，改动记录见 [CHANGELOG.md](CHANGELOG.md)。

## 许可证

[MIT License](LICENSE)
