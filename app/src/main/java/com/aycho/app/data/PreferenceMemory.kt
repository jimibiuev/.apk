package com.aycho.app.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 一条长期记忆
 */
data class MemoryItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = "auto"   // auto=任务中自动沉淀，manual=用户手动添加
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("createdAt", createdAt)
        put("source", source)
    }

    companion object {
        fun fromJson(json: JSONObject): MemoryItem = MemoryItem(
            id = json.optString("id", UUID.randomUUID().toString()),
            text = json.optString("text", ""),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            source = json.optString("source", "auto")
        )
    }
}

/**
 * 长期记忆存储：落到 filesDir/aycho_memories.json
 *
 * 设计目标：轻量、可查看、可编辑、可删除 —— 不做复杂向量检索，
 * 只把"关于用户的要点"拼进提示词，让 Agent 越用越懂你。
 */
class PreferenceMemory(context: Context) {

    companion object {
        private const val TAG = "PreferenceMemory"
        private const val FILE_NAME = "aycho_memories.json"
        private const val MAX_ITEMS = 200
    }

    private val file = File(context.filesDir, FILE_NAME)

    fun load(): List<MemoryItem> {
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            val array = JSONArray(text)
            val list = mutableListOf<MemoryItem>()
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { list.add(MemoryItem.fromJson(it)) }
            }
            list.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            Log.e(TAG, "load failed", e)
            emptyList()
        }
    }

    private fun save(items: List<MemoryItem>): List<MemoryItem> {
        val trimmed = items.take(MAX_ITEMS)
        return try {
            val array = JSONArray()
            trimmed.forEach { array.put(it.toJson()) }
            file.writeText(array.toString())
            trimmed
        } catch (e: Exception) {
            Log.e(TAG, "save failed", e)
            trimmed
        }
    }

    /** 新增一条；text 为空或重复则不写入 */
    fun add(text: String, source: String = "auto"): List<MemoryItem> {
        val content = text.trim()
        if (content.isEmpty()) return load()
        val current = load()
        if (current.any { it.text.trim() == content }) return current
        val item = MemoryItem(text = content, source = source)
        return save(listOf(item) + current)
    }

    fun update(id: String, text: String): List<MemoryItem> {
        val content = text.trim()
        val current = load()
        if (content.isEmpty()) return delete(id)
        return save(current.map { if (it.id == id) it.copy(text = content) else it })
    }

    fun delete(id: String): List<MemoryItem> = save(load().filterNot { it.id == id })

    fun clear(): List<MemoryItem> = save(emptyList())
}
