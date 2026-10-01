package com.aycho.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aycho.app.data.MemoryItem
import com.aycho.app.ui.theme.AychoTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记忆页：查看 / 新增 / 编辑 / 删除 Agent 记住的事
 */
@Composable
fun MemoryScreen(
    memories: List<MemoryItem>,
    onAdd: (String) -> Unit,
    onUpdate: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val colors = AychoTheme.colors

    // 编辑/新增弹窗状态
    var editingItem by remember { mutableStateOf<MemoryItem?>(null) }
    var editingText by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "记忆",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                    Text(
                        text = if (memories.isEmpty()) "还没有记住任何事" else "已记住 ${memories.size} 条，我会一直带着它们",
                        fontSize = 14.sp,
                        color = colors.textSecondary
                    )
                }
                if (memories.isNotEmpty()) {
                    Text(
                        text = "清空",
                        fontSize = 14.sp,
                        color = colors.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showClearConfirm = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            if (memories.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "我还没记住什么",
                        fontSize = 16.sp,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "任务里出现的偏好我会自动记住，你也可以点右下角手动添加",
                        fontSize = 13.sp,
                        color = colors.textHint
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(memories, key = { it.id }) { item ->
                        MemoryCard(
                            item = item,
                            onEdit = {
                                editingItem = item
                                editingText = item.text
                                showEditor = true
                            },
                            onDelete = { onDelete(item.id) }
                        )
                    }
                }
            }
        }

        // 新增按钮
        FloatingActionButton(
            onClick = {
                editingItem = null
                editingText = ""
                showEditor = true
            },
            containerColor = colors.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "新增记忆")
        }
    }

    // 编辑弹窗
    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            containerColor = colors.backgroundCard,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textPrimary,
            title = {
                Text(text = if (editingItem == null) "新增记忆" else "修改记忆")
            },
            text = {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.backgroundInput)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = editingText,
                            onValueChange = { editingText = it },
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                            cursorBrush = SolidColor(colors.primary),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Box {
                                    if (editingText.isEmpty()) {
                                        Text(
                                            text = "例如：常喝冰美式，不加糖",
                                            color = colors.textHint,
                                            fontSize = 15.sp
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "这条内容会带到之后的每次任务里",
                        fontSize = 12.sp,
                        color = colors.textHint
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val item = editingItem
                    if (item == null) {
                        onAdd(editingText)
                    } else {
                        onUpdate(item.id, editingText)
                    }
                    showEditor = false
                }) {
                    Text(text = "保存", color = colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) {
                    Text(text = "取消", color = colors.textSecondary)
                }
            }
        )
    }

    // 清空确认
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = colors.backgroundCard,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text(text = "清空全部记忆？") },
            text = { Text(text = "清空后我对你的偏好会全部忘掉，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearConfirm = false
                }) {
                    Text(text = "清空", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(text = "取消", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun MemoryCard(
    item: MemoryItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = AychoTheme.colors
    val timeText = remember(item.createdAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.createdAt))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.backgroundCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = item.text,
                fontSize = 15.sp,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (item.source == "manual") colors.primary else colors.secondary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = (if (item.source == "manual") "手动添加 · $timeText" else "任务中记住 · $timeText"),
                        fontSize = 11.sp,
                        color = colors.textHint
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "修改",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = colors.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
