package com.aycho.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aycho.app.agent.AgentState
import com.aycho.app.ui.theme.AychoTheme
import com.aycho.app.ui.theme.Primary
import com.aycho.app.ui.theme.Secondary

/**
 * 一条对话消息（真人式对话气泡）
 */
data class ChatMessage(
    val role: String,   // "user" / "agent"
    val text: String,
    val time: Long = System.currentTimeMillis()
) {
    val isUser: Boolean get() = role == "user"
}

/**
 * 预设命令
 */
data class PresetCommand(
    val title: String,
    val command: String
)

val presetCommands = listOf(
    PresetCommand("点外卖", "帮我在 Uber Eats 点一份常吃的套餐"),
    PresetCommand("发动态", "帮我发一条帖文，内容是今天的好心情"),
    PresetCommand("看视频", "打开 YouTube 看热门视频，给第一个视频点个赞"),
    PresetCommand("查攻略", "帮我规划一次周末城市短途旅行"),
    PresetCommand("放首歌", "打开音乐 App 播放我的每日推荐"),
    PresetCommand("订杯咖啡", "帮我在 Starbucks 点一杯冰美式")
)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    agentState: AgentState?,
    logs: List<String>,
    onExecute: (String) -> Unit,
    onStop: () -> Unit,
    shizukuAvailable: Boolean,
    currentModel: String = "",
    onRefreshShizuku: () -> Unit = {},
    onShizukuRequired: () -> Unit = {},
    isExecuting: Boolean = false,
    chatMessages: List<ChatMessage> = emptyList(),
    speaking: Boolean = false
) {
    val colors = AychoTheme.colors
    var inputText by remember { mutableStateOf("") }
    // 使用 isExecuting 或 agentState?.isRunning 来判断是否运行中
    val isRunning = isExecuting || agentState?.isRunning == true
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 自动滚动到底部（气泡或日志更新时）
    val itemCount = if (chatMessages.isNotEmpty()) chatMessages.size else logs.size
    LaunchedEffect(itemCount) {
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        // 顶部标题
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "aycho",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                    Text(
                        text = when {
                            !shizukuAvailable -> "请先连接 Shizuku"
                            isRunning -> "正在处理，随时可以说新指令打断我"
                            speaking -> "正在说话…"
                            else -> "准备就绪，告诉我你想做什么"
                        },
                        fontSize = 14.sp,
                        color = if (shizukuAvailable) colors.textSecondary else colors.error
                    )
                }

                // 未连接时显示刷新按钮
                if (!shizukuAvailable) {
                    IconButton(
                        onClick = onRefreshShizuku,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.backgroundCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新 Shizuku 状态",
                            tint = colors.primary
                        )
                    }
                }
            }
        }

        // 内容区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (chatMessages.isNotEmpty() || isRunning || logs.isNotEmpty()) {
                // 对话气泡（执行中或有历史时）
                ChatConversationView(
                    messages = chatMessages,
                    logs = logs,
                    isRunning = isRunning,
                    currentStep = agentState?.currentStep ?: 0,
                    currentModel = currentModel,
                    speaking = speaking,
                    listState = listState,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 空闲时显示预设命令
                PresetCommandsView(
                    onCommandClick = { command ->
                        if (shizukuAvailable) {
                            inputText = command
                        } else {
                            onShizukuRequired()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 底部输入区域
        InputArea(
            inputText = inputText,
            onInputChange = { inputText = it },
            onExecute = {
                if (inputText.isNotBlank()) {
                    val text = inputText
                    // 发送即清空，保持对话流连续
                    inputText = ""
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onExecute(text)
                }
            },
            onStop = {
                inputText = ""
                onStop()
            },
            isRunning = isRunning,
            enabled = shizukuAvailable,
            onInputClick = {
                if (!shizukuAvailable) {
                    onShizukuRequired()
                }
            }
        )
    }
}

/**
 * 对话气泡视图：像聊天 App 一样逐句呈现"我说了什么 / agent 回了什么"
 */
@Composable
fun ChatConversationView(
    messages: List<ChatMessage>,
    logs: List<String>,
    isRunning: Boolean,
    currentStep: Int,
    currentModel: String,
    speaking: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (isRunning) {
            ExecutingIndicator(currentStep = currentStep, currentModel = currentModel)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (messages.isEmpty()) {
                // 没有对话气泡时退回执行日志
                items(logs) { log -> LogItem(log = log) }
            } else {
                items(messages) { message ->
                    ChatBubble(
                        message = message,
                        speaking = speaking && message === messages.lastOrNull() && !message.isUser
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, speaking: Boolean = false) {
    val colors = AychoTheme.colors
    val isUser = message.isUser

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            // agent 头像：渐变圆点
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(listOf(Primary, Secondary, Primary)))
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            color = if (isUser) colors.primary else colors.backgroundCard,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.text,
                    color = if (isUser) Color.White else colors.textPrimary,
                    fontSize = 14.sp
                )
                if (speaking) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.primary)
                    )
                }
            }
        }
    }
}

@Composable
fun PresetCommandsView(
    onCommandClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AychoTheme.colors
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "试试这些指令",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        presetCommands.chunked(2).forEach { rowCommands ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowCommands.forEach { preset ->
                    PresetCommandCard(
                        preset = preset,
                        onClick = { onCommandClick(preset.command) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 如果是奇数，补一个空白
                if (rowCommands.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun PresetCommandCard(
    preset: PresetCommand,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AychoTheme.colors
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundCard
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = preset.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
                Text(
                    text = preset.command,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ExecutionLogView(
    logs: List<String>,
    isRunning: Boolean,
    currentStep: Int,
    currentModel: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 执行状态指示器
        if (isRunning) {
            ExecutingIndicator(currentStep = currentStep, currentModel = currentModel)
        }

        // 日志列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(logs) { log ->
                LogItem(log = log)
            }
        }
    }
}

@Composable
fun ExecutingIndicator(currentStep: Int, currentModel: String = "") {
    val colors = AychoTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "executing")
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.backgroundCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 动画圆点
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(Primary, Secondary, Primary)
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "正在执行 Step $currentStep",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.primary
                        )
                    }
                    // 显示当前模型
                    if (currentModel.isNotEmpty()) {
                        Text(
                            text = currentModel,
                            fontSize = 11.sp,
                            color = colors.textHint,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 进度条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.backgroundInput)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Primary, Secondary)
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun LogItem(log: String) {
    val colors = AychoTheme.colors
    val logColor = when {
        log.contains("[失败]") -> colors.error
        log.contains("[成功]") -> colors.success
        log.contains("[提示]") -> colors.secondary
        log.contains("Step") || log.contains("=====") -> colors.primary
        log.contains("[安全]") -> colors.error
        else -> colors.textSecondary
    }

    Text(
        text = log,
        fontSize = 12.sp,
        color = logColor,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
fun InputArea(
    inputText: String,
    onInputChange: (String) -> Unit,
    onExecute: () -> Unit,
    onStop: () -> Unit,
    isRunning: Boolean,
    enabled: Boolean,
    onInputClick: () -> Unit = {}
) {
    val colors = AychoTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.backgroundCard,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 输入框：执行中也保持可输入，发送即打断
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.backgroundInput)
                    .then(
                        if (!enabled) {
                            Modifier.clickable { onInputClick() }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                if (enabled) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        textStyle = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(colors.primary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            Box {
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = if (isRunning) "说点什么，打断我…" else "告诉aycho你想做什么...",
                                        color = colors.textHint,
                                        fontSize = 15.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                } else {
                    // Shizuku 未连接，显示提示文字
                    Text(
                        text = "请先连接 Shizuku",
                        color = colors.textHint,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 执行中：停止按钮（打断当前任务，但不退出对话）
            if (isRunning) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.backgroundInput)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "停止执行",
                        tint = colors.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 发送按钮
            IconButton(
                onClick = onExecute,
                enabled = enabled && inputText.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank() && enabled) colors.primary
                        else colors.backgroundInput
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "发送",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
