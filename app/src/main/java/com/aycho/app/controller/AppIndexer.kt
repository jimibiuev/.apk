package com.aycho.app.controller

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * App 扫描器 - 获取所有已安装应用信息
 * 支持：预扫描缓存、拼音匹配、分类、语义搜索
 */
class AppIndexer(private val context: Context) {

    companion object {
        /** 由语言层调用：仅 zh-CN 时启用拼音/别名检索 */
        fun setAliasLookupEnabled(enabled: Boolean) {
            aliasLookupEnabled = enabled
        }

        private const val CACHE_FILE = "installed_apps.json"

        // 内存缓存 (应用生命周期内有效)
        @Volatile
        private var cachedApps: List<AppInfo>? = null

        // 别名/拼音检索开关：仅在 zh-CN 语言包下启用
        @Volatile
        private var aliasLookupEnabled: Boolean = true

        // 预编译的正则表达式（避免重复创建）
        private val PINYIN_CLEAN_REGEX = Regex("[^a-z0-9\\u4e00-\\u9fa5]")

        // 应用分类关键词映射
        private val CATEGORY_KEYWORDS = mapOf(
            "社交" to listOf("WhatsApp", "Telegram", "Signal", "Instagram", "Threads", "Discord", "Messenger", "Facebook"),
            "购物" to listOf("Amazon", "eBay", "Walmart", "Etsy", "Temu", "AliExpress", "Shop", "Target"),
            "外卖" to listOf("Uber Eats", "DoorDash", "Deliveroo", "Grubhub", "Just Eat", "McDonald", "Starbucks", "KFC"),
            "出行" to listOf("Uber", "Lyft", "Bolt", "Grab", "BlaBlaCar", "Transit"),
            "地图" to listOf("Google Maps", "Apple Maps", "Waze", "Maps", "导航"),
            "音乐" to listOf("Spotify", "YouTube Music", "Apple Music", "SoundCloud", "Deezer", "Tidal"),
            "视频" to listOf("YouTube", "Netflix", "TikTok", "Disney+", "Prime Video", "Hulu", "Twitch"),
            "支付" to listOf("Google Wallet", "PayPal", "Venmo", "Cash App", "Revolut", "Wise"),
            "笔记" to listOf("Notion", "Evernote", "OneNote", "Google Keep", "Obsidian", "备忘录", "Notes"),
            "相机" to listOf("Camera", "Photos", "Lens", "拍照"),
            "图片" to listOf("Gallery", "Photos", "Google Photos", "相册"),
            "浏览器" to listOf("Chrome", "Firefox", "Edge", "Brave", "Safari", "浏览器"),
            "办公" to listOf("Google Docs", "Sheets", "Slides", "Microsoft 365", "Slack", "Teams", "Zoom", "Notion"),
            "AI" to listOf("ChatGPT", "Claude", "Gemini", "Copilot", "Perplexity", "Grok", "Midjourney", "Leonardo"),
            "工具" to listOf("Calculator", "Compass", "Clock", "Alarm", "Calendar", "Weather", "Files", "计算器", "日历", "天气"),
            "阅读" to listOf("Kindle", "Apple Books", "Kobo", "Libby", "Audible", "Medium", "阅读"),
            "游戏" to listOf("Steam", "Roblox", "Minecraft", "Genshin", "PUBG", "Call of Duty", "游戏")
        )

        // 拼音映射表 (常用应用)
        private val PINYIN_MAP = mapOf(
            // 社交 / 消息
            "whatsapp" to "WhatsApp", "wa" to "WhatsApp",
            "telegram" to "Telegram", "tg" to "Telegram",
            "signal" to "Signal",
            "instagram" to "Instagram", "ins" to "Instagram", "ig" to "Instagram",
            "twitter" to "X", "threads" to "Threads",
            "discord" to "Discord", "messenger" to "Messenger", "facebook" to "Facebook",

            // 购物
            "amazon" to "Amazon", "ebay" to "eBay", "walmart" to "Walmart",
            "etsy" to "Etsy", "temu" to "Temu", "aliexpress" to "AliExpress",

            // 外卖 / 餐饮
            "ubereats" to "Uber Eats", "doordash" to "DoorDash",
            "deliveroo" to "Deliveroo", "grubhub" to "Grubhub",
            "starbucks" to "Starbucks", "mcdonald" to "McDonald's",

            // 出行 / 地图
            "uber" to "Uber", "lyft" to "Lyft", "bolt" to "Bolt", "grab" to "Grab",
            "maps" to "Google Maps", "gmaps" to "Google Maps", "googlemaps" to "Google Maps",
            "waze" to "Waze", "applemaps" to "Apple Maps",

            // 支付
            "paypal" to "PayPal", "venmo" to "Venmo", "cashapp" to "Cash App",
            "gpay" to "Google Wallet", "wallet" to "Google Wallet",

            // 音乐
            "spotify" to "Spotify", "ytmusic" to "YouTube Music",
            "applemusic" to "Apple Music", "soundcloud" to "SoundCloud",

            // 视频
            "youtube" to "YouTube", "yt" to "YouTube", "netflix" to "Netflix",
            "tiktok" to "TikTok", "twitch" to "Twitch", "disney" to "Disney+",

            // AI
            "chatgpt" to "ChatGPT", "gpt" to "ChatGPT", "claude" to "Claude",
            "gemini" to "Gemini", "copilot" to "Copilot", "perplexity" to "Perplexity", "grok" to "Grok",

            // 阅读 / 笔记 / 办公
            "kindle" to "Kindle", "audible" to "Audible", "kobo" to "Kobo",
            "notion" to "Notion", "evernote" to "Evernote", "onenote" to "OneNote",
            "keep" to "Google Keep", "obsidian" to "Obsidian",
            "slack" to "Slack", "teams" to "Teams", "zoom" to "Zoom",

            // 浏览器
            "chrome" to "Chrome", "firefox" to "Firefox", "edge" to "Edge", "brave" to "Brave",

            // 游戏
            "steam" to "Steam", "roblox" to "Roblox", "minecraft" to "Minecraft", "genshin" to "Genshin"
        )

        // 语义查询映射 (用户可能说的自然语言)
        private val SEMANTIC_MAP = mapOf(
            // 功能描述 -> 分类
            "拍照" to "相机", "照相" to "相机", "自拍" to "相机", "拍摄" to "相机",
            "看照片" to "图片", "看图" to "图片", "图片" to "图片",
            "聊天" to "社交", "发消息" to "社交", "通讯" to "社交",
            "买东西" to "购物", "购物" to "购物", "网购" to "购物", "下单" to "购物",
            "点餐" to "外卖", "叫外卖" to "外卖", "点外卖" to "外卖", "吃饭" to "外卖", "订餐" to "外卖",
            "打车" to "出行", "叫车" to "出行", "出行" to "出行", "坐车" to "出行",
            "导航" to "地图", "找路" to "地图", "去哪" to "地图", "怎么走" to "地图",
            "听歌" to "音乐", "听音乐" to "音乐", "放歌" to "音乐", "播放音乐" to "音乐",
            "看视频" to "视频", "刷视频" to "视频", "追剧" to "视频", "看电影" to "视频", "看剧" to "视频",
            "付款" to "支付", "支付" to "支付", "扫码" to "支付", "收款" to "支付",
            "记笔记" to "笔记", "记事" to "笔记", "记录" to "笔记", "写笔记" to "笔记",
            "上网" to "浏览器", "搜索" to "浏览器", "查资料" to "浏览器",
            "办公" to "办公", "工作" to "办公", "文档" to "办公",
            "画图" to "AI", "生成图片" to "AI", "AI画图" to "AI", "AI" to "AI",
            "看书" to "阅读", "阅读" to "阅读", "读书" to "阅读", "看小说" to "阅读",
            "玩游戏" to "游戏", "游戏" to "游戏"
        )
    }

    /**
     * 应用信息
     */
    data class AppInfo(
        val packageName: String,
        val appName: String,
        val pinyin: String,        // 拼音（自动生成）
        val category: String?,     // 分类
        val isSystem: Boolean,
        val keywords: List<String> // 关键词（用于搜索）
    )

    /**
     * 搜索结果
     */
    data class SearchResult(
        val app: AppInfo,
        val score: Float,          // 匹配分数 0-1
        val matchType: String      // 匹配类型：exact/contains/pinyin/category/semantic
    )

    /**
     * 获取应用列表 (优先内存 -> 文件 -> 扫描)
     */
    fun getApps(): List<AppInfo> {
        cachedApps?.let { return it }

        val cacheFile = File(context.filesDir, CACHE_FILE)
        if (cacheFile.exists()) {
            val loaded = loadFromFile(cacheFile)
            if (loaded.isNotEmpty()) {
                cachedApps = loaded
                println("[AppIndexer] 从文件加载 ${loaded.size} 个应用")
                return loaded
            }
        }

        return refreshApps()
    }

    /**
     * 强制刷新应用列表
     */
    fun refreshApps(): List<AppInfo> {
        println("[AppIndexer] 扫描已安装应用...")
        val apps = scanAllApps()
        cachedApps = apps

        val cacheFile = File(context.filesDir, CACHE_FILE)
        saveToFile(apps, cacheFile)
        println("[AppIndexer] 已缓存 ${apps.size} 个应用")

        return apps
    }

    /**
     * 扫描所有已安装应用
     */
    private fun scanAllApps(): List<AppInfo> {
        val pm = context.packageManager
        val apps = mutableListOf<AppInfo>()

        try {
            // 使用 0 作为 flag，获取所有应用（不过滤）
            val packages = pm.getInstalledApplications(0)
            for (appInfo in packages) {
                val appName = pm.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val pinyin = toPinyin(appName)
                val category = detectCategory(appName, appInfo.packageName)
                val keywords = generateKeywords(appName, appInfo.packageName, category)

                apps.add(AppInfo(
                    packageName = appInfo.packageName,
                    appName = appName,
                    pinyin = pinyin,
                    category = category,
                    isSystem = isSystem,
                    keywords = keywords
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return apps.sortedBy { it.appName }
    }

    /**
     * 智能搜索应用
     * @param query 搜索词（支持：应用名、拼音、分类、语义描述）
     * @param topK 返回前 K 个结果
     * @param includeSystem 是否包含系统应用
     */
    fun searchApps(query: String, topK: Int = 5, includeSystem: Boolean = true): List<SearchResult> {
        val apps = getApps()
        val lowerQuery = query.lowercase().trim()
        val results = mutableListOf<SearchResult>()

        // 先检查是否是语义查询，转换为分类
        val semanticCategory = SEMANTIC_MAP[lowerQuery]
        val pinyinMapped = if (aliasLookupEnabled) PINYIN_MAP[lowerQuery] else null

        for (app in apps) {
            if (!includeSystem && app.isSystem) continue

            var score = 0f
            var matchType = ""

            // 1. 精确匹配应用名 (最高优先级)
            if (app.appName.equals(query, ignoreCase = true)) {
                score = 1.0f
                matchType = "exact"
            }
            // 2. 拼音映射精确匹配
            else if (pinyinMapped != null && app.appName.contains(pinyinMapped)) {
                score = 0.95f
                matchType = "pinyin_exact"
            }
            // 3. 应用名包含查询词
            else if (app.appName.lowercase().contains(lowerQuery)) {
                score = 0.9f
                matchType = "contains"
            }
            // 4. 拼音包含
            else if (app.pinyin.contains(lowerQuery)) {
                score = 0.8f
                matchType = "pinyin"
            }
            // 5. 关键词匹配
            else if (app.keywords.any { it.contains(lowerQuery) || lowerQuery.contains(it) }) {
                score = 0.7f
                matchType = "keyword"
            }
            // 6. 分类匹配（语义查询）
            else if (semanticCategory != null && app.category == semanticCategory) {
                score = 0.6f
                matchType = "semantic"
            }
            // 7. 包名包含
            else if (app.packageName.lowercase().contains(lowerQuery)) {
                score = 0.5f
                matchType = "package"
            }

            if (score > 0) {
                // 非系统应用加分
                if (!app.isSystem) score += 0.05f
                results.add(SearchResult(app, score.coerceAtMost(1f), matchType))
            }
        }

        return results
            .sortedByDescending { it.score }
            .take(topK)
    }

    /**
     * 根据名称模糊搜索包名 (兼容旧接口)
     */
    fun findPackage(query: String): String? {
        val results = searchApps(query, topK = 1)
        return results.firstOrNull()?.app?.packageName
    }

    /**
     * 按分类获取应用
     */
    fun getAppsByCategory(category: String): List<AppInfo> {
        return getApps().filter { it.category == category }
    }

    /**
     * 获取所有分类
     */
    fun getAllCategories(): List<String> {
        return getApps().mapNotNull { it.category }.distinct().sorted()
    }

    /**
     * 格式化搜索结果给 LLM
     */
    fun formatSearchResultsForLLM(results: List<SearchResult>): String {
        if (results.isEmpty()) return "未找到匹配的应用"

        return buildString {
            append("找到以下应用，请选择最合适的：\n")
            results.forEachIndexed { index, result ->
                val app = result.app
                val categoryStr = app.category?.let { " [$it]" } ?: ""
                append("${index + 1}. ${app.appName}$categoryStr (${app.packageName})\n")
            }
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 简单拼音转换（仅处理常见中文字符）
     */
    private fun toPinyin(text: String): String {
        // 简单实现：保留英文数字，中文用首字母拼音
        // 完整拼音需要引入 pinyin4j 库，这里用简化版本
        return text.lowercase()
            .replace(PINYIN_CLEAN_REGEX, "")
    }

    /**
     * 检测应用分类
     */
    private fun detectCategory(appName: String, packageName: String): String? {
        val lowerName = appName.lowercase()
        val lowerPackage = packageName.lowercase()

        for ((category, keywords) in CATEGORY_KEYWORDS) {
            for (keyword in keywords) {
                if (lowerName.contains(keyword.lowercase()) ||
                    lowerPackage.contains(keyword.lowercase())) {
                    return category
                }
            }
        }
        return null
    }

    /**
     * 生成搜索关键词
     */
    private fun generateKeywords(appName: String, packageName: String, category: String?): List<String> {
        val keywords = mutableListOf<String>()

        // 从包名提取关键词
        val packageParts = packageName.split(".")
        keywords.addAll(packageParts.filter { it.length > 2 })

        // 添加分类
        category?.let { keywords.add(it) }

        // 从拼音映射反向添加
        for ((pinyin, name) in PINYIN_MAP) {
            if (appName.contains(name)) {
                keywords.add(pinyin)
            }
        }

        return keywords.distinct()
    }

    // ========== 缓存相关 ==========

    private fun saveToFile(apps: List<AppInfo>, file: File) {
        try {
            val jsonArray = JSONArray()
            for (app in apps) {
                val obj = JSONObject()
                obj.put("package", app.packageName)
                obj.put("name", app.appName)
                obj.put("pinyin", app.pinyin)
                obj.put("category", app.category ?: "")
                obj.put("system", app.isSystem)
                obj.put("keywords", JSONArray(app.keywords))
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadFromFile(file: File): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        try {
            val jsonArray = JSONArray(file.readText())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val keywordsArray = obj.optJSONArray("keywords")
                val keywords = mutableListOf<String>()
                if (keywordsArray != null) {
                    for (j in 0 until keywordsArray.length()) {
                        keywords.add(keywordsArray.getString(j))
                    }
                }

                apps.add(AppInfo(
                    packageName = obj.getString("package"),
                    appName = obj.getString("name"),
                    pinyin = obj.optString("pinyin", ""),
                    category = obj.optString("category", null)?.takeIf { it.isNotEmpty() },
                    isSystem = obj.optBoolean("system", false),
                    keywords = keywords
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return apps
    }
}
