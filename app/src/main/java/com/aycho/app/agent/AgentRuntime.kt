package com.aycho.app.agent

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import com.aycho.app.App
import com.aycho.app.controller.AppIndexer
import com.aycho.app.controller.DeviceBridge
import com.aycho.app.data.ExecutionStep
import com.aycho.app.skills.IntentRouter
import com.aycho.app.ui.HeadsUpService
import com.aycho.app.vlm.ModelGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/**
 * Mobile Agent 主循环 - 移植自 AgentRuntime-v3
 *
 * 新增 Skill 层支持：
 * - 快速路径：高置信度 delegation Skill 直接执行
 * - 增强模式：GUI 自动化 Skill 提供上下文指导
 */
class AgentRuntime(
    private val vlmClient: ModelGateway,
    private val controller: DeviceBridge,
    private val context: Context
) {
    // App 扫描器 (使用 App 单例中的实例)
    private val appScanner: AppIndexer = App.getInstance().appScanner
    private val manager = Planner()
    private val executor = Actuator()
    private val reflector = Verifier()
    private val scribe = Scribe()

    companion object {
        /** 单次任务内允许按 Home 键的最大次数，超出后直接拦截 */
        private const val MAX_HOME_PRESSES = 1
    }

    /** 本任务内已按 Home 的次数 */
    private var homeButtonCount = 0

    /** 叙述回调：把 Agent 正在做的事用自然语言说出来（对话气泡 + 语音播报） */
    var onNarrate: ((String) -> Unit)? = null

    /** 长期记忆：上层注入的“关于用户的已知信息”，会拼进提示词 */
    var agentMemories: List<String> = emptyList()

    // Skill 管理器
    private val skillManager: IntentRouter? = try {
        IntentRouter.getInstance().also {
            println("[aycho] IntentRouter 已加载，共 ${it.getAllSkills().size} 个 Skills")
            // 设置 VLM 客户端用于意图匹配
            it.setVLMClient(vlmClient)
        }
    } catch (e: Exception) {
        println("[aycho] IntentRouter 加载失败: ${e.message}")
        null
    }

    // 状态流
    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    /**
     * 执行指令
     */
    suspend fun runInstruction(
        instruction: String,
        maxSteps: Int = 25,
        useScribe: Boolean = false,
        thinkingMode: Boolean = true
    ): AgentResult {
        log("开始执行: $instruction")
        log("思考模式: ${if (thinkingMode) "开启" else "关闭（快速模式）"}")

        // 使用 LLM 匹配 Skill，生成上下文信息给 Agent（不执行任何操作）
        log("正在分析意图...")
        val skillContext = skillManager?.generateAgentContextWithLLM(instruction)

        val blackboard = Blackboard(instruction = instruction)
        homeButtonCount = 0
        // 硬性行为约束：禁止为了"去桌面找应用"而退出当前应用
        blackboard.additionalKnowledge = "Do NOT press the Home button to go back to the home screen, and do NOT look for app icons on the launcher - use the `open_app` action to switch apps. " +
            "`Back` is NOT the same as `Home`: pressing `Back` only returns ONE step within the current app, and it is allowed and encouraged whenever the current page is not the one you need " +
            "(wrong page opened, popup / sub-page in the way, or the same click keeps failing). " +
            "All click coordinates must be NORMALIZED integers in [0, 999] relative to the screenshot (x: 0 = left, 999 = right; y: 0 = top, 999 = bottom)."

        // 注入长期记忆（用户偏好 / 已知信息）
        val memoryBlock = memoryBlockText()
        if (memoryBlock.isNotEmpty()) {
            blackboard.additionalKnowledge += memoryBlock
            log("已注入 ${agentMemories.size} 条长期记忆")
        }

        // 初始化 Actuator 的对话记忆
        val executorSystemPrompt = buildString {
            append("You are an agent who can operate an Android phone. ")
            append("Decide the next action based on the current state.\n\n")
            append("User Request: $instruction\n")
            if (agentMemories.isNotEmpty()) {
                append("\nKnown info about this user (respect it):\n")
                agentMemories.take(20).forEach { append("- $it\n") }
            }
        }
        blackboard.executorMemory = SessionMemory.withSystemPrompt(executorSystemPrompt)
        log("已初始化对话记忆")

        // 如果有 Skill 上下文，添加到 Blackboard，让 Planner 知道可用的工具
        if (!skillContext.isNullOrEmpty() && skillContext != "未找到相关技能或可用应用，请使用通用 GUI 自动化完成任务。") {
            blackboard.skillContext = skillContext
            log("已匹配到可用技能:\n$skillContext")
        } else {
            log("未匹配到特定技能，使用通用 GUI 自动化")
        }

        // 获取屏幕尺寸
        val (width, height) = controller.getScreenSize()
        blackboard.screenWidth = width
        blackboard.screenHeight = height
        log("屏幕尺寸: ${width}x${height}")

        // 获取已安装应用列表（只取非系统应用，限制数量避免 prompt 过长）
        val apps = appScanner.getApps()
            .filter { !it.isSystem }
            .take(50)
            .map { it.appName }
        blackboard.installedApps = apps.joinToString(", ")
        log("已加载 ${apps.size} 个应用")

        // 显示悬浮窗 (带停止按钮)
        HeadsUpService.show(context, "开始执行...") {
            // 停止回调 - 设置状态为停止
            // 注意：协程取消需要在 MainActivity 中处理
            updateState { copy(isRunning = false) }
            // 调用 stop() 方法确保清理
            stop()
        }

        updateState { copy(isRunning = true, currentStep = 0, instruction = instruction) }

        // 开场白由模型按当前指令实时生成，不再使用固定台词
        val opening = generateLine(
            instruction = instruction,
            task = "请像真人一样用一句自然口语回应他（12~30字），可以简短说说你打算怎么做",
            outcome = null
        )
        if (opening.isNotEmpty()) narrate(opening)

        try {
            for (step in 0 until maxSteps) {
                // 检查协程是否被取消
                coroutineContext.ensureActive()

                // 检查是否被用户停止
                if (!_state.value.isRunning) {
                    log("用户停止执行")
                    HeadsUpService.hide(context)
                    bringAppToFront()
                    return AgentResult(success = false, message = "用户停止")
                }

                updateState { copy(currentStep = step + 1) }
                log("\n========== Step ${step + 1} ==========")
                HeadsUpService.update("Step ${step + 1}/$maxSteps")

                // 1. 截图 (先隐藏悬浮窗避免被识别)
                log("截图中...")
                HeadsUpService.setVisible(false)
                delay(100) // 等待悬浮窗隐藏
                val screenshotResult = controller.screenshotWithFallback()
                HeadsUpService.setVisible(true)
                val screenshot = screenshotResult.bitmap

                // 处理敏感页面（截图被系统阻止）
                if (screenshotResult.isSensitive) {
                    log("[警告] 检测到敏感页面（截图被阻止），请求人工接管")
                    val confirmed = withContext(Dispatchers.Main) {
                        waitForUserConfirm("检测到敏感页面，是否继续执行？")
                    }
                    if (!confirmed) {
                        log("用户取消，任务终止")
                        HeadsUpService.hide(context)
                        bringAppToFront()
                        return AgentResult(success = false, message = "敏感页面，用户取消")
                    }
                    log("用户确认继续（使用黑屏占位图）")
                } else if (screenshotResult.isFallback) {
                    log("[警告] 截图失败，使用黑屏占位图继续")
                }

                // 再次检查停止状态（截图后）
                if (!_state.value.isRunning) {
                    log("用户停止执行")
                    HeadsUpService.hide(context)
                    bringAppToFront()
                    return AgentResult(success = false, message = "用户停止")
                }

                // 2. 检查错误升级
                checkErrorEscalation(blackboard)

                // 3. 跳过 Planner 的情况
                val skipManager = !blackboard.errorFlagPlan &&
                        blackboard.actionHistory.isNotEmpty() &&
                        blackboard.actionHistory.last().type == "invalid"

                // 4. Planner 规划
                if (!skipManager) {
                    log("Planner 规划中...")

                    // 检查停止状态
                    if (!_state.value.isRunning) {
                        log("用户停止执行")
                        HeadsUpService.hide(context)
                        bringAppToFront()
                        return AgentResult(success = false, message = "用户停止")
                    }

                    val planPrompt = manager.getPrompt(blackboard)
                    val planResponse = vlmClient.predict(planPrompt, listOf(screenshot))

                    // VLM 调用后检查停止状态
                    if (!_state.value.isRunning) {
                        log("用户停止执行")
                        HeadsUpService.hide(context)
                        bringAppToFront()
                        return AgentResult(success = false, message = "用户停止")
                    }

                    if (planResponse.isFailure) {
                        log("Planner 调用失败: ${planResponse.exceptionOrNull()?.message}")
                        continue
                    }

                    val planResult = manager.parseResponse(planResponse.getOrThrow())
                    blackboard.completedPlan = planResult.completedSubgoal
                    blackboard.plan = planResult.plan

                    log("计划: ${planResult.plan.take(100)}...")

                    // 检查是否遇到敏感页面
                    if (planResult.plan.contains("STOP_SENSITIVE")) {
                        log("检测到敏感页面（支付/密码等），已停止执行")
                        HeadsUpService.update("敏感页面，已停止")
                        delay(2000)
                        HeadsUpService.hide(context)
                        updateState { copy(isRunning = false, isCompleted = false) }
                        bringAppToFront()
                        return AgentResult(success = false, message = "检测到敏感页面（支付/密码），已安全停止")
                    }

                    // 检查是否完成
                    if (planResult.plan.contains("Finished") && planResult.plan.length < 20) {
                        log("任务完成!")
                        val closing = generateLine(
                            instruction = instruction,
                            task = "任务已经做完了，请用一句自然口语向用户汇报结果（12~30字）",
                            outcome = blackboard.completedPlan
                        )
                        if (closing.isNotEmpty()) narrate(closing)
                        HeadsUpService.update("完成!")
                        delay(1500)
                        HeadsUpService.hide(context)
                        updateState { copy(isRunning = false, isCompleted = true) }
                        bringAppToFront()
                        return AgentResult(success = true, message = closing)
                    }
                }

                // 5. Actuator 决定动作 (使用上下文记忆)
                log("Actuator 决策中...")

                // 检查停止状态
                if (!_state.value.isRunning) {
                    log("用户停止执行")
                    HeadsUpService.hide(context)
                    bringAppToFront()
                    return AgentResult(success = false, message = "用户停止")
                }

                val actionPrompt = executor.getPrompt(blackboard)

                // 使用上下文记忆调用 VLM
                val memory = blackboard.executorMemory
                val actionResponse = if (memory != null) {
                    // 添加用户消息（带截图）
                    memory.addUserMessage(actionPrompt, screenshot)
                    log("记忆消息数: ${memory.size()}, 估算 token: ${memory.estimateTokens()}")

                    // 调用 VLM
                    val response = vlmClient.predictWithContext(memory.toMessagesJson())

                    // 删除图片节省 token
                    memory.stripLastUserImage()

                    response
                } else {
                    // 降级：使用普通方式
                    vlmClient.predict(actionPrompt, listOf(screenshot))
                }

                // VLM 调用后检查停止状态
                if (!_state.value.isRunning) {
                    log("用户停止执行")
                    HeadsUpService.hide(context)
                    bringAppToFront()
                    return AgentResult(success = false, message = "用户停止")
                }

                if (actionResponse.isFailure) {
                    log("Actuator 调用失败: ${actionResponse.exceptionOrNull()?.message}")
                    continue
                }

                val responseText = actionResponse.getOrThrow()
                val executorResult = executor.parseResponse(responseText)

                // 将助手响应添加到记忆
                memory?.addAssistantMessage(responseText)
                val action = executorResult.action

                log("思考: ${executorResult.thought.take(80)}...")
                log("动作: ${executorResult.actionStr}")
                log("描述: ${executorResult.description}")

                blackboard.lastActionThought = executorResult.thought
                blackboard.lastSummary = executorResult.description

                if (action == null) {
                    log("动作解析失败")
                    blackboard.actionHistory.add(Action(type = "invalid"))
                    blackboard.summaryHistory.add(executorResult.description)
                    blackboard.actionOutcomes.add("C")
                    blackboard.errorDescriptions.add("Invalid action format")
                    continue
                }

                narrate(actionNarration(action, executorResult.description))

                // 特殊处理: answer 动作
                if (action.type == "answer") {
                    log("回答: ${action.text}")
                    action.text?.takeIf { it.isNotBlank() }?.let { narrate(it) }
                    HeadsUpService.update("${action.text?.take(20)}...")
                    delay(1500)
                    HeadsUpService.hide(context)
                    updateState { copy(isRunning = false, isCompleted = true, answer = action.text) }
                    bringAppToFront()
                    return AgentResult(success = true, message = action.text?.trim().orEmpty())
                }

                // 6. 敏感操作确认
                if (action.needConfirm || action.message != null && action.type in listOf("click", "double_tap", "long_press")) {
                    val confirmMessage = action.message ?: "确认执行此操作？"
                    log("[警告] 敏感操作: $confirmMessage")

                    val confirmed = withContext(Dispatchers.Main) {
                        waitForUserConfirm(confirmMessage)
                    }

                    if (!confirmed) {
                        log("[失败] 用户取消操作")
                        blackboard.actionHistory.add(action)
                        blackboard.summaryHistory.add("用户取消: ${executorResult.description}")
                        blackboard.actionOutcomes.add("C")
                        blackboard.errorDescriptions.add("User cancelled")
                        continue
                    }
                    log("[成功] 用户确认，继续执行")
                }

                // 6.5 行为拦截：禁止无必要地退出当前应用返回主页
                if (action.type == "system_button" &&
                    action.button?.equals("Home", ignoreCase = true) == true
                ) {
                    if (homeButtonCount >= MAX_HOME_PRESSES) {
                        log("[拦截] 拒绝按 Home（本任务已按 $homeButtonCount 次），继续留在当前应用")
                        HeadsUpService.update("已阻止返回主页")
                        blackboard.actionHistory.add(action)
                        blackboard.summaryHistory.add("Home 被拦截：应留在当前应用")
                        blackboard.actionOutcomes.add("C")
                        blackboard.errorDescriptions.add("Home button blocked. Use open_app to switch apps; use Back if you need to leave the current page.")
                        blackboard.importantNotes = "Do NOT press Home again. Stay in this app and use open_app to switch apps. Pressing Back (one step) is allowed when the current page is wrong."
                        delay(500)
                        continue
                    }
                    homeButtonCount++
                    log("[提示] 按 Home 第 $homeButtonCount 次（上限 $MAX_HOME_PRESSES）")
                }

                // 7. 执行动作
                log("执行动作: ${action.type}")
                HeadsUpService.update("${action.type}: ${executorResult.description.take(15)}...")
                executeAction(action, blackboard)
                blackboard.lastAction = action

                // 立即记录执行步骤（outcome 暂时为 "?" 表示进行中）
                val currentStepIndex = _state.value.executionSteps.size
                val executionStep = ExecutionStep(
                    stepNumber = step + 1,
                    timestamp = System.currentTimeMillis(),
                    action = action.type,
                    description = executorResult.description,
                    thought = executorResult.thought,
                    outcome = "?" // 进行中
                )
                updateState { copy(executionSteps = executionSteps + executionStep) }

                // 等待动作生效
                delay(if (step == 0) 5000 else 2000)

                // 检查停止状态
                if (!_state.value.isRunning) {
                    log("用户停止执行")
                    HeadsUpService.hide(context)
                    bringAppToFront()
                    return AgentResult(success = false, message = "用户停止")
                }

                // 8. 截图 (动作后，隐藏悬浮窗)
                HeadsUpService.setVisible(false)
                delay(100)
                val afterScreenshotResult = controller.screenshotWithFallback()
                HeadsUpService.setVisible(true)
                val afterScreenshot = afterScreenshotResult.bitmap
                if (afterScreenshotResult.isFallback) {
                    log("动作后截图失败，使用黑屏占位图")
                }

                // 9. Reflector 反思（思考模式开启时执行；快速模式跳过，直接视为成功）
                val reflectResult: VerifierResult
                if (thinkingMode) {
                    log("Reflector 反思中...")

                    // 检查停止状态
                    if (!_state.value.isRunning) {
                        log("用户停止执行")
                        HeadsUpService.hide(context)
                        bringAppToFront()
                        return AgentResult(success = false, message = "用户停止")
                    }

                    val reflectPrompt = reflector.getPrompt(blackboard)
                    val reflectResponse = vlmClient.predict(reflectPrompt, listOf(screenshot, afterScreenshot))

                    reflectResult = if (reflectResponse.isSuccess) {
                        reflector.parseResponse(reflectResponse.getOrThrow())
                    } else {
                        VerifierResult("C", "Failed to call reflector")
                    }
                } else {
                    log("快速模式：跳过反思，直接进入下一步")
                    reflectResult = VerifierResult("A", "")
                }

                log("结果: ${reflectResult.outcome} - ${reflectResult.errorDescription.take(50)}")

                // 更新历史
                blackboard.actionHistory.add(action)
                blackboard.summaryHistory.add(executorResult.description)
                blackboard.actionOutcomes.add(reflectResult.outcome)
                blackboard.errorDescriptions.add(reflectResult.errorDescription)
                blackboard.progressStatus = blackboard.completedPlan

                // 更新执行步骤的 outcome（之前添加的步骤 outcome 是 "?"）
                updateState {
                    val updatedSteps = executionSteps.toMutableList()
                    if (currentStepIndex < updatedSteps.size) {
                        updatedSteps[currentStepIndex] = updatedSteps[currentStepIndex].copy(
                            outcome = reflectResult.outcome
                        )
                    }
                    copy(executionSteps = updatedSteps)
                }

                // 10. Scribe (可选)
                if (useScribe && reflectResult.outcome == "A" && action.type != "answer") {
                    log("Scribe 记录中...")

                    // 检查停止状态
                    if (!_state.value.isRunning) {
                        log("用户停止执行")
                        HeadsUpService.hide(context)
                        bringAppToFront()
                        return AgentResult(success = false, message = "用户停止")
                    }

                    val notePrompt = scribe.getPrompt(blackboard)
                    val noteResponse = vlmClient.predict(notePrompt, listOf(afterScreenshot))
                    if (noteResponse.isSuccess) {
                        blackboard.importantNotes = scribe.parseResponse(noteResponse.getOrThrow())
                    }
                }
            }
        } catch (e: CancellationException) {
            log("任务被取消")
            HeadsUpService.hide(context)
            updateState { copy(isRunning = false) }
            bringAppToFront()
            throw e
        }

        log("达到最大步数限制")
        val closing = generateLine(
            instruction = instruction,
            task = "任务没做成，请用一句自然口语告诉用户你停了下来，并请他说得更具体些（15~35字）",
            outcome = null
        )
        if (closing.isNotEmpty()) narrate(closing)
        HeadsUpService.update("达到最大步数")
        delay(1500)
        HeadsUpService.hide(context)
        updateState { copy(isRunning = false, isCompleted = false) }
        bringAppToFront()
        return AgentResult(success = false, message = "达到最大步数限制")
    }

    /**
     * 执行具体动作 (在 IO 线程执行，避免 ANR)
     */
    private suspend fun executeAction(action: Action, blackboard: Blackboard) = withContext(Dispatchers.IO) {
        // 动态获取屏幕尺寸（处理横竖屏切换）
        val (screenWidth, screenHeight) = controller.getScreenSize()

        when (action.type) {
            "click" -> {
                val x = mapCoordinate(action.x ?: 0, screenWidth)
                val y = mapCoordinate(action.y ?: 0, screenHeight)
                controller.tap(x, y)
            }
            "double_tap" -> {
                val x = mapCoordinate(action.x ?: 0, screenWidth)
                val y = mapCoordinate(action.y ?: 0, screenHeight)
                controller.doubleTap(x, y)
            }
            "long_press" -> {
                val x = mapCoordinate(action.x ?: 0, screenWidth)
                val y = mapCoordinate(action.y ?: 0, screenHeight)
                controller.longPress(x, y)
            }
            "swipe" -> {
                val x1 = mapCoordinate(action.x ?: 0, screenWidth)
                val y1 = mapCoordinate(action.y ?: 0, screenHeight)
                val x2 = mapCoordinate(action.x2 ?: 0, screenWidth)
                val y2 = mapCoordinate(action.y2 ?: 0, screenHeight)
                controller.swipe(x1, y1, x2, y2)
            }
            "type" -> {
                action.text?.let { controller.type(it) }
            }
            "system_button" -> {
                when (action.button) {
                    "Back", "back" -> controller.back()
                    "Home", "home" -> controller.home()
                    "Enter", "enter" -> controller.enter()
                    else -> log("未知系统按钮: ${action.button}")
                }
            }
            "open_app" -> {
                action.text?.let { appName ->
                    // 智能匹配包名 (客户端模糊搜索，省 token)
                    val packageName = appScanner.findPackage(appName)
                    if (packageName != null) {
                        log("找到应用: $appName -> $packageName")
                        controller.openApp(packageName)
                    } else {
                        log("未找到应用: $appName，尝试直接打开")
                        controller.openApp(appName)
                    }
                }
            }
            "wait" -> {
                // 智能等待：模型决定等待时长
                val duration = (action.duration ?: 3).coerceIn(1, 10)
                log("等待 ${duration} 秒...")
                delay(duration * 1000L)
            }
            "take_over" -> {
                // 人机协作：暂停等待用户手动完成操作
                val message = action.message ?: "请完成操作后点击继续"
                log("[协作] 人机协作: $message")
                withContext(Dispatchers.Main) {
                    waitForUserTakeOver(message)
                }
                log("[成功] 用户已完成，继续执行")
            }
            else -> {
                log("未知动作类型: ${action.type}")
            }
        }
    }

    /**
     * 等待用户完成手动操作（人机协作）
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun waitForUserTakeOver(message: String) = suspendCancellableCoroutine<Unit> { continuation ->
        com.aycho.app.ui.HeadsUpService.showTakeOver(message) {
            if (continuation.isActive) {
                continuation.resume(Unit) {}
            }
        }
    }

    /**
     * 等待用户确认敏感操作
     * @return true = 用户确认，false = 用户取消
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun waitForUserConfirm(message: String) = suspendCancellableCoroutine<Boolean> { continuation ->
        com.aycho.app.ui.HeadsUpService.showConfirm(message) { confirmed ->
            if (continuation.isActive) {
                continuation.resume(confirmed) {}
            }
        }
    }

    /**
     * 坐标映射 - 统一归一化体系
     *
     * 规则（与提示词严格一致）:
     * - 模型输出的 x/y 一律视为 [0, 999] 归一化坐标，按屏幕宽/高等比映射到像素；
     * - 仅当数值 > 999 时才兼容性地视为已给的绝对像素；
     * - 结果强制裁剪到 [0, screenMax - 1]，避免上/下/右边界（如 999）映射后越界
     *   导致点击落在屏幕外而"点不中"。
     *
     * @param value 模型输出的坐标值
     * @param screenMax 屏幕实际尺寸（像素）
     */
    private fun mapCoordinate(value: Int, screenMax: Int): Int {
        if (screenMax <= 0) return 0
        val px = if (value > 999) {
            // 兼容：模型直接给了绝对像素
            value
        } else {
            // 归一化 [0, 999] -> 像素
            (value.coerceAtLeast(0) * screenMax / 1000)
        }
        return px.coerceIn(0, screenMax - 1)
    }

    /**
     * 检查错误升级
     */
    private fun checkErrorEscalation(blackboard: Blackboard) {
        blackboard.errorFlagPlan = false
        val thresh = blackboard.errToManagerThresh

        if (blackboard.actionOutcomes.size >= thresh) {
            val recentOutcomes = blackboard.actionOutcomes.takeLast(thresh)
            val failCount = recentOutcomes.count { it in listOf("B", "C") }
            if (failCount == thresh) {
                blackboard.errorFlagPlan = true
            }
        }
    }

    // 停止回调（由 MainActivity 设置，用于取消协程）
    var onStopRequested: (() -> Unit)? = null

    /**
     * 停止执行
     */
    fun stop() {
        HeadsUpService.hide(context)
        updateState { copy(isRunning = false) }
        // 通知 MainActivity 取消协程
        onStopRequested?.invoke()
    }

    /**
     * 清空日志
     */
    fun clearLogs() {
        _logs.value = emptyList()
        updateState { copy(executionSteps = emptyList()) }
    }

    /**
     * 返回aychoApp
     */
    private fun bringAppToFront() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            context.startActivity(intent)
        } catch (e: Exception) {
            log("返回App失败: ${e.message}")
        }
    }

    /** 拼装长期记忆提示块 */
    private fun memoryBlockText(): String {
        if (agentMemories.isEmpty()) return ""
        return "\n\n[long term memory] Known info about this user (respect it, do not ask again):\n" +
            agentMemories.take(20).joinToString("\n") { "- $it" }
    }

    /** 清洗模型返回的一句话，去掉格式噪声 */
    private fun cleanLine(raw: String): String {
        var t = raw.trim().replace("\r", " ").replace("\n", " ").replace("**", "")
        t = t.replace(Regex("^(回答|回复|输出|台词)[:：]\\s*"), "")
        t = t.trim('"', '\'', '“', '”', '`', ' ')
        if (t.length > 60) t = t.take(60)
        return t
    }

    /**
     * 让模型按当前语境实时生成一句自然的话。
     * 失败返回空串（宁可不说话，也不回退到固定台词）。
     */
    private suspend fun generateLine(instruction: String, task: String, outcome: String?): String {
        val memoryHint = if (agentMemories.isEmpty()) "" else
            "已知用户信息：" + agentMemories.take(5).joinToString("；") + "\n"
        val prompt = buildString {
            append("你是装在安卓手机里的智能助手 aycho，正在跟用户面对面说话，并且可以直接操作他的手机。\n")
            append("用户说的话：「").append(instruction).append("」\n")
            if (!outcome.isNullOrBlank()) append("当前进展：").append(outcome.take(120)).append("\n")
            if (memoryHint.isNotEmpty()) append(memoryHint)
            append(task).append("\n")
            append("要求：像真人聊天一样自然，别用「好的、收到、没问题」这类客套开头，")
            append("不要提模型、接口、提示词之类的字眼，不要输出引号、括号说明、emoji 或 markdown，")
            append("只输出这一句话本身。")
        }
        return try {
            val res = vlmClient.predict(prompt, emptyList())
            val text = res.getOrNull() ?: return ""
            cleanLine(text)
        } catch (e: Exception) {
            log("生成台词失败: ${e.message}")
            ""
        }
    }

    /**
     * 任务结束后提炼一条值得长期记住的用户信息；没有则返回 null。
     */
    suspend fun extractMemory(instruction: String, outcome: String): String? {
        val prompt = buildString {
            append("下面是一次手机助手任务的记录。\n")
            append("用户指令：").append(instruction.take(300)).append("\n")
            append("执行结果：").append(outcome.take(300)).append("\n")
            append("如果这次任务暴露了值得长期记住的用户信息（常用应用、口味或饮食偏好、收货地址、称呼、习惯、日程等），")
            append("请提炼成一句不超过 40 字的中文陈述句。\n")
            append("如果没有任何值得长期保存的信息，只输出 NONE。\n")
            append("只输出那一句话或 NONE，不要解释，不要加引号。")
        }
        return try {
            val res = vlmClient.predict(prompt, emptyList())
            val text = cleanLine(res.getOrNull() ?: return null)
            if (text.isEmpty() || text.uppercase().startsWith("NONE")) null else text
        } catch (e: Exception) {
            null
        }
    }

    /** 说给用户听的一句话（对话气泡 + 语音播报） */
    private fun narrate(message: String) {
        val text = message.trim()
        if (text.isEmpty()) return
        log("[说] $text")
        onNarrate?.invoke(text)
    }

    /** 根据动作生成自然旁白，优先使用模型给出的简短中文描述 */
    private fun actionNarration(action: Action, description: String): String {
        val desc = description.trim()
        if (desc.isNotEmpty() && desc.length <= 30 &&
            Regex("[\u4e00-\u9fa5]").containsMatchIn(desc)
        ) {
            return desc
        }
        return when (action.type) {
            "open_app" -> "正在打开${action.text ?: "应用"}"
            "type" -> "正在输入内容"
            "swipe" -> "正在滑动页面"
            "wait" -> "稍等，页面正在加载"
            "double_tap" -> "正在操作界面"
            "long_press" -> "正在长按屏幕"
            "system_button" -> when (action.button?.lowercase()) {
                "back" -> "正在返回上一页"
                "enter" -> "正在确认"
                else -> "正在操作系统按键"
            }
            else -> "正在操作屏幕"
        }
    }

    private fun log(message: String) {
        println("[aycho] $message")
        _logs.value = _logs.value + message
    }

    private fun updateState(update: AgentState.() -> AgentState) {
        _state.value = _state.value.update()
    }

}

data class AgentState(
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val currentStep: Int = 0,
    val instruction: String = "",
    val answer: String? = null,
    val executionSteps: List<ExecutionStep> = emptyList()
)

data class AgentResult(
    val success: Boolean,
    val message: String
)
