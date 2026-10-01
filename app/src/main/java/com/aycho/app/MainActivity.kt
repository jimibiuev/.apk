package com.aycho.app

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import android.net.Uri
import android.provider.Settings
import com.aycho.app.agent.AgentRuntime
import com.aycho.app.controller.AppIndexer
import com.aycho.app.controller.DeviceBridge
import com.aycho.app.data.*
import com.aycho.app.ui.screens.*
import com.aycho.app.ui.theme.*
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.aycho.app.vlm.ModelGateway
import com.aycho.app.voice.VoiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import android.util.Log

private const val TAG = "MainActivity"

sealed class Screen(val route: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    object Home : Screen("home", "aycho", Icons.Outlined.Home, Icons.Filled.Home)
    object Capabilities : Screen("capabilities", "能力", Icons.Outlined.Star, Icons.Filled.Star)
    object Memory : Screen("memory", "记忆", Icons.Outlined.Info, Icons.Filled.Info)
    object History : Screen("history", "记录", Icons.Outlined.List, Icons.Filled.List)
    object Settings : Screen("settings", "设置", Icons.Outlined.Settings, Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {

    private lateinit var deviceController: DeviceBridge
    private lateinit var settingsManager: PreferencesStore
    private lateinit var executionRepository: ExecutionRepository

    private val agentRuntime = mutableStateOf<AgentRuntime?>(null)
    private var shizukuAvailable = mutableStateOf(false)

    // 当前执行的协程 Job（用于停止任务）
    private var currentExecutionJob: kotlinx.coroutines.Job? = null

    // 执行记录列表
    private val executionRecords = mutableStateOf<List<ExecutionRecord>>(emptyList())

    // 是否正在执行（点击发送后立即为 true）
    private val isExecuting = mutableStateOf(false)

    // 对话气泡（像真人聊天一样保留来回对话）
    private val chatMessages = mutableStateOf<List<ChatMessage>>(emptyList())

    // 长期记忆（可查看、可修改、可删除）
    private lateinit var memoryStore: PreferenceMemory
    private val memoryItems = mutableStateOf<List<MemoryItem>>(emptyList())

    // Agent 是否正在开口说话
    private val isSpeaking = mutableStateOf(false)

    // 语音播报
    private var voiceManager: VoiceManager? = null

    // 当前执行的记录 ID（用于停止后跳转）
    private val currentRecordId = mutableStateOf<String?>(null)

    // 是否需要跳转到记录详情（悬浮窗停止后触发）
    private val shouldNavigateToRecord = mutableStateOf(false)

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        shizukuAvailable.value = true
        if (checkShizukuPermission()) {
            Log.d(TAG, "Shizuku permission granted, binding service")
            deviceController.bindService()
        } else {
            Log.d(TAG, "Shizuku permission not granted")
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        shizukuAvailable.value = false
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        Log.d(TAG, "Shizuku permission result: $grantResult")
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            deviceController.bindService()
            Toast.makeText(this, "Shizuku 权限已获取", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // 设置边到边显示，深色状态栏和导航栏
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        deviceController = DeviceBridge(this)
        deviceController.setCacheDir(cacheDir)
        settingsManager = PreferencesStore(this)
        executionRepository = ExecutionRepository(this)
        memoryStore = PreferenceMemory(this)
        memoryItems.value = memoryStore.load()

        // 语音播报：设置里开着就边操作边说话
        voiceManager = VoiceManager(this).apply {
            setEnabled(settingsManager.settings.value.voiceOutput)
            onSpeakingChanged = { speaking ->
                runOnUiThread { isSpeaking.value = speaking }
            }
        }

        // 加载执行记录
        lifecycleScope.launch {
            executionRecords.value = executionRepository.getAllRecords()
        }

        // 添加 Shizuku 监听器
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        // 检查 Shizuku 状态
        checkAndUpdateShizukuStatus()

        // 预加载已安装应用
        lifecycleScope.launch(Dispatchers.IO) {
            AppIndexer(this@MainActivity).getApps()
        }

        setContent {
            val settings by settingsManager.settings.collectAsState()
            AychoTheme(themeMode = settings.themeMode) {
                val colors = AychoTheme.colors
                // 动态更新系统栏颜色
                SideEffect {
                    val window = this@MainActivity.window
                    window.statusBarColor = colors.background.toArgb()
                    window.navigationBarColor = colors.backgroundCard.toArgb()
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !colors.isDark
                        isAppearanceLightNavigationBars = !colors.isDark
                    }
                }

                // 开屏动画：黑底完整 wordmark 淡入（每次进程启动一次）
                var showBootSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1700)
                    showBootSplash = false
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 首次启动显示引导画面
                    if (!settings.hasSeenOnboarding) {
                        OnboardingScreen(
                            onComplete = {
                                settingsManager.setOnboardingSeen()
                            }
                        )
                    } else {
                        MainApp()
                    }

                    AnimatedVisibility(
                        visible = showBootSplash,
                        enter = EnterTransition.None,
                        exit = fadeOut(animationSpec = tween(durationMillis = 420))
                    ) {
                        AychoBootSplash()
                    }
                }
            }
        }
    }

    /** 开屏动画：黑底 + 完整 wordmark 淡入放大（不裁切，完整展示品牌字） */
    @Composable
    private fun AychoBootSplash() {
        var started by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { started = true }

        val wordmarkAlpha by animateFloatAsState(
            targetValue = if (started) 1f else 0.55f,
            animationSpec = tween(durationMillis = 900),
            label = "bootWordmarkAlpha"
        )
        val wordmarkScale by animateFloatAsState(
            targetValue = if (started) 1f else 0.62f,
            animationSpec = tween(durationMillis = 900),
            label = "bootWordmarkScale"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.splash_wordmark),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .graphicsLayer {
                        alpha = wordmarkAlpha
                        scaleX = wordmarkScale
                        scaleY = wordmarkScale
                    }
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainApp() {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
        var selectedRecord by remember { mutableStateOf<ExecutionRecord?>(null) }
        var showShizukuHelpDialog by remember { mutableStateOf(false) }
        var hasShownShizukuHelp by remember { mutableStateOf(false) }

        val settings by settingsManager.settings.collectAsState()
        val colors = AychoTheme.colors
        val agent = agentRuntime.value
        val agentState by agent?.state?.collectAsState() ?: remember { mutableStateOf(null) }
        val logs by agent?.logs?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
        val records by remember { executionRecords }
        val isShizukuAvailable = shizukuAvailable.value && checkShizukuPermission()
        val executing by remember { isExecuting }
        val messages by remember { chatMessages }
        val memoryList by remember { memoryItems }
        val speaking by remember { isSpeaking }
        val navigateToRecord by remember { shouldNavigateToRecord }
        val recordId by remember { currentRecordId }

        // 监听跳转事件
        LaunchedEffect(navigateToRecord, recordId) {
            if (navigateToRecord && recordId != null) {
                // 找到对应的记录并跳转
                val record = records.find { it.id == recordId }
                if (record != null) {
                    selectedRecord = record
                    currentScreen = Screen.History
                }
                shouldNavigateToRecord.value = false
            }
        }

        // 首次进入且 Shizuku 未连接时，显示帮助引导（只显示一次）
        LaunchedEffect(Unit) {
            if (!isShizukuAvailable && settings.hasSeenOnboarding && !hasShownShizukuHelp) {
                hasShownShizukuHelp = true
                showShizukuHelpDialog = true
            }
        }

        Scaffold(
            modifier = Modifier.background(colors.background),
            containerColor = colors.background,
            bottomBar = {
                if (selectedRecord == null) {
                    NavigationBar(
                        containerColor = colors.background,
                        contentColor = colors.textPrimary,
                        tonalElevation = 0.dp
                    ) {
                        listOf(Screen.Home, Screen.Capabilities, Screen.Memory, Screen.History, Screen.Settings).forEach { screen ->
                            val selected = currentScreen == screen
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.icon,
                                        contentDescription = screen.title
                                    )
                                },
                                label = { Text(screen.title) },
                                selected = selected,
                                onClick = { currentScreen = screen },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = if (colors.isDark) colors.textPrimary else Color.White,
                                    selectedTextColor = colors.primary,
                                    unselectedIconColor = colors.textSecondary,
                                    unselectedTextColor = colors.textSecondary,
                                    indicatorColor = colors.primary
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 处理系统返回手势
                BackHandler(enabled = selectedRecord != null) {
                    selectedRecord = null
                }

                // 详情页优先显示
                if (selectedRecord != null) {
                    HistoryDetailScreen(
                        record = selectedRecord!!,
                        onBack = { selectedRecord = null }
                    )
                } else {
                    // 主页面切换
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "screen"
                    ) { screen ->
                        when (screen) {
                            Screen.Home -> {
                                // 每次进入首页都检测 Shizuku 状态
                                LaunchedEffect(Unit) {
                                    checkAndUpdateShizukuStatus()
                                }
                                HomeScreen(
                                    agentState = agentState,
                                    logs = logs,
                                    onExecute = { instruction ->
                                        runAgent(instruction, settings.apiKey, settings.baseUrl, settings.model, settings.maxSteps)
                                    },
                                    onStop = {
                                        voiceManager?.stop()
                                        agentRuntime.value?.stop()
                                    },
                                    shizukuAvailable = isShizukuAvailable,
                                    currentModel = settings.model,
                                    onRefreshShizuku = { refreshShizukuStatus() },
                                    onShizukuRequired = { showShizukuHelpDialog = true },
                                    isExecuting = executing,
                                    chatMessages = messages,
                                    speaking = speaking
                                )
                            }
                            Screen.Capabilities -> CapabilitiesScreen()
                            Screen.Memory -> MemoryScreen(
                                memories = memoryList,
                                onAdd = { text -> memoryItems.value = memoryStore.add(text, "manual") },
                                onUpdate = { id, text -> memoryItems.value = memoryStore.update(id, text) },
                                onDelete = { id -> memoryItems.value = memoryStore.delete(id) },
                                onClearAll = { memoryItems.value = memoryStore.clear() }
                            )
                            Screen.History -> HistoryScreen(
                                records = records,
                                onRecordClick = { record -> selectedRecord = record },
                                onDeleteRecord = { id -> deleteRecord(id) }
                            )
                            Screen.Settings -> SettingsScreen(
                                settings = settings,
                                onUpdateApiKey = { settingsManager.updateApiKey(it) },
                                onUpdateBaseUrl = { settingsManager.updateBaseUrl(it) },
                                onUpdateModel = { settingsManager.updateModel(it) },
                                onUpdateCachedModels = { settingsManager.updateCachedModels(it) },
                                onUpdateThemeMode = { settingsManager.updateThemeMode(it) },
                                onUpdateMaxSteps = { settingsManager.updateMaxSteps(it) },
                                onUpdateCloudCrashReport = { enabled ->
                                    settingsManager.updateCloudCrashReportEnabled(enabled)
                                    App.getInstance().updateCloudCrashReportEnabled(enabled)
                                },
                                onUpdateThinkingMode = { settingsManager.updateThinkingMode(it) },
                                onUpdateRootModeEnabled = { settingsManager.updateRootModeEnabled(it) },
                                onUpdateSuCommandEnabled = { settingsManager.updateSuCommandEnabled(it) },
                                onSelectProvider = { settingsManager.selectProvider(it) },
                                shizukuAvailable = isShizukuAvailable,
                                shizukuPrivilegeLevel = if (isShizukuAvailable) {
                                    when (deviceController.getShizukuPrivilegeLevel()) {
                                        DeviceBridge.ShizukuPrivilegeLevel.ROOT -> "ROOT"
                                        DeviceBridge.ShizukuPrivilegeLevel.ADB -> "ADB"
                                        else -> "NONE"
                                    }
                                } else "NONE",
                                onFetchModels = { onSuccess, onError ->
                                    lifecycleScope.launch {
                                        val result = ModelGateway.fetchModels(settings.baseUrl, settings.apiKey)
                                        result.onSuccess { models ->
                                            onSuccess(models)
                                        }.onFailure { error ->
                                            onError(error.message ?: "未知错误")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Shizuku 帮助对话框
        if (showShizukuHelpDialog) {
            ShizukuHelpDialog(onDismiss = { showShizukuHelpDialog = false })
        }
    }

    private fun deleteRecord(id: String) {
        lifecycleScope.launch {
            executionRepository.deleteRecord(id)
            executionRecords.value = executionRepository.getAllRecords()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager?.shutdown()
        voiceManager = null
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        deviceController.unbindService()
    }

    private fun checkShizukuPermission(): Boolean {
        return try {
            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "checkShizukuPermission: $granted")
            granted
        } catch (e: Exception) {
            Log.e(TAG, "checkShizukuPermission error", e)
            false
        }
    }

    private fun checkAndUpdateShizukuStatus() {
        Log.d(TAG, "checkAndUpdateShizukuStatus called")
        try {
            val binderAlive = Shizuku.pingBinder()
            Log.d(TAG, "Shizuku pingBinder: $binderAlive")

            if (binderAlive) {
                shizukuAvailable.value = true
                val hasPermission = checkShizukuPermission()
                Log.d(TAG, "Shizuku hasPermission: $hasPermission")

                if (hasPermission) {
                    Log.d(TAG, "Binding Shizuku service")
                    deviceController.bindService()
                } else {
                    Log.d(TAG, "Requesting Shizuku permission")
                    requestShizukuPermission()
                }
            } else {
                Log.d(TAG, "Shizuku binder not alive")
                shizukuAvailable.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkAndUpdateShizukuStatus error", e)
            shizukuAvailable.value = false
        }
    }

    private fun refreshShizukuStatus() {
        Log.d(TAG, "refreshShizukuStatus called by user")
        Toast.makeText(this, "正在检查 Shizuku 状态...", Toast.LENGTH_SHORT).show()
        checkAndUpdateShizukuStatus()

        if (shizukuAvailable.value && checkShizukuPermission()) {
            Toast.makeText(this, "Shizuku 已连接", Toast.LENGTH_SHORT).show()
        } else if (shizukuAvailable.value) {
            Toast.makeText(this, "请在弹窗中授权 Shizuku", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "请先启动 Shizuku App", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "请先启动 Shizuku App", Toast.LENGTH_SHORT).show()
                return
            }

            if (Shizuku.isPreV11()) {
                Toast.makeText(this, "Shizuku 版本过低", Toast.LENGTH_SHORT).show()
                return
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku 权限已获取", Toast.LENGTH_SHORT).show()
                shizukuAvailable.value = true
                deviceController.bindService()
                return
            }

            Shizuku.requestPermission(0)
        } catch (e: Exception) {
            Toast.makeText(this, "请先启动 Shizuku App", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Agent 的话统一从这里上屏：
     * 与最后一条 Agent 气泡内容相同则跳过，避免同一次回答被重复显示两遍。
     */
    private fun appendAgentMessage(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        runOnUiThread {
            val current = chatMessages.value
            val last = current.lastOrNull()
            if (last != null && last.role == "agent" && last.text.trim() == content) return@runOnUiThread
            chatMessages.value = current + ChatMessage(role = "agent", text = content)
        }
    }

    private fun runAgent(instruction: String, apiKey: String, baseUrl: String, model: String, maxSteps: Int) {
        if (instruction.isBlank()) {
            Toast.makeText(this, "请输入指令", Toast.LENGTH_SHORT).show()
            return
        }
        if (apiKey.isBlank()) {
            Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
            val intent = android.content.Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        // 立即设置执行状态为 true，显示停止按钮
        isExecuting.value = true

        val activeProvider = settingsManager.settings.value.currentProvider
        val vlmClient = ModelGateway(
            apiKey = apiKey,
            baseUrl = baseUrl.ifBlank { activeProvider.baseUrl.ifBlank { "https://apihub.agnes-ai.com/v1" } },
            model = model.ifBlank { activeProvider.defaultModel.ifBlank { "agnes-2.5-flash" } },
            protocol = activeProvider.protocol
        )

        agentRuntime.value = AgentRuntime(vlmClient, deviceController, this)

        // 把长期记忆交给 Agent，让它带着对用户的了解干活
        agentRuntime.value?.agentMemories = memoryItems.value.map { it.text }

        // 语音播报开关跟随设置
        voiceManager?.setEnabled(settingsManager.settings.value.voiceOutput)

        // Agent 说的每一句话 -> 对话气泡 + 语音播报
        agentRuntime.value?.onNarrate = { message ->
            appendAgentMessage(message)
            voiceManager?.speak(message)
        }

        // 设置停止回调，用于取消协程
        agentRuntime.value?.onStopRequested = {
            currentExecutionJob?.cancel()
            currentExecutionJob = null
        }

        // 用户这句话进入对话流
        chatMessages.value = chatMessages.value + ChatMessage(role = "user", text = instruction)

        // 创建执行记录
        val record = ExecutionRecord(
            title = generateTitle(instruction),
            instruction = instruction,
            startTime = System.currentTimeMillis(),
            status = ExecutionStatus.RUNNING
        )

        // 保存当前记录 ID，用于停止后跳转
        currentRecordId.value = record.id

        // 取消之前的任务（如果有）
        currentExecutionJob?.cancel()

        currentExecutionJob = lifecycleScope.launch {
            // 保存初始记录
            executionRepository.saveRecord(record)
            executionRecords.value = executionRepository.getAllRecords()

            try {
                val result = agentRuntime.value!!.runInstruction(
                    instruction,
                    maxSteps,
                    thinkingMode = settingsManager.settings.value.thinkingMode
                )

                // 更新记录状态
                val agentState = agentRuntime.value?.state?.value
                val steps = agentState?.executionSteps ?: emptyList()
                val currentLogs = agentRuntime.value?.logs?.value ?: emptyList()

                val updatedRecord = record.copy(
                    endTime = System.currentTimeMillis(),
                    status = if (result.success) ExecutionStatus.COMPLETED else ExecutionStatus.FAILED,
                    steps = steps,
                    logs = currentLogs,
                    resultMessage = result.message
                )
                executionRepository.saveRecord(updatedRecord)
                executionRecords.value = executionRepository.getAllRecords()

                if (result.message.isNotBlank()) {
                    Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
                }

                // 结果进入对话流（旁白已念过同一句时会被自动去重）
                appendAgentMessage(result.message)

                // 任务结束后沉淀长期记忆：值得留的信息写进记忆库
                try {
                    val learned = agentRuntime.value?.extractMemory(instruction, result.message)
                    if (!learned.isNullOrBlank()) {
                        val before = memoryItems.value.size
                        val updated = memoryStore.add(learned, "auto")
                        if (updated.size > before) {
                            memoryItems.value = updated
                            appendAgentMessage("我记住了：$learned")
                            Log.d(TAG, "新增记忆: $learned")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "记忆沉淀失败", e)
                }

                // 重置执行状态
                isExecuting.value = false

                // 延迟3秒后清空日志，恢复默认状态
                kotlinx.coroutines.delay(3000)
                agentRuntime.value?.clearLogs()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户取消任务 - 使用 NonCancellable 确保清理操作完成
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    val agentState = agentRuntime.value?.state?.value
                    val steps = agentState?.executionSteps ?: emptyList()
                    val currentLogs = agentRuntime.value?.logs?.value ?: emptyList()

                    println("[MainActivity] 取消任务 - steps: ${steps.size}, logs: ${currentLogs.size}")

                    val updatedRecord = record.copy(
                        endTime = System.currentTimeMillis(),
                        status = ExecutionStatus.STOPPED,
                        steps = steps,
                        logs = currentLogs,
                        resultMessage = "已取消"
                    )
                    executionRepository.saveRecord(updatedRecord)
                    executionRecords.value = executionRepository.getAllRecords()

                    // 重置执行状态
                    isExecuting.value = false

                    Toast.makeText(this@MainActivity, "任务已停止", Toast.LENGTH_SHORT).show()
                    chatMessages.value = chatMessages.value +
                        ChatMessage(role = "agent", text = "好，我先停下，你继续说。")
                    agentRuntime.value?.clearLogs()

                    // 触发跳转到记录详情页
                    shouldNavigateToRecord.value = true
                }
            } catch (e: Exception) {
                // 更新失败记录
                val currentLogs = agentRuntime.value?.logs?.value ?: emptyList()
                val updatedRecord = record.copy(
                    endTime = System.currentTimeMillis(),
                    status = ExecutionStatus.FAILED,
                    logs = currentLogs,
                    resultMessage = "错误: ${e.message}"
                )
                executionRepository.saveRecord(updatedRecord)
                executionRecords.value = executionRepository.getAllRecords()

                // 重置执行状态
                isExecuting.value = false

                Toast.makeText(this@MainActivity, "错误: ${e.message}", Toast.LENGTH_LONG).show()
                chatMessages.value = chatMessages.value +
                    ChatMessage(role = "agent", text = "出错了：${e.message}")

                // 延迟3秒后清空日志，恢复默认状态
                kotlinx.coroutines.delay(3000)
                agentRuntime.value?.clearLogs()
            }
        }
    }

    private fun generateTitle(instruction: String): String {
        // 生成简短标题
        val keywords = listOf(
            "打开" to "打开应用",
            "点" to "点餐",
            "发" to "发送消息",
            "看" to "浏览内容",
            "搜" to "搜索",
            "设置" to "调整设置",
            "播放" to "播放媒体"
        )
        for ((key, title) in keywords) {
            if (instruction.contains(key)) {
                return title
            }
        }
        return if (instruction.length > 10) instruction.take(10) + "..." else instruction
    }
}
