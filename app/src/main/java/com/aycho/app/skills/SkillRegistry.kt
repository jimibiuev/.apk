package com.aycho.app.skills

import android.content.Context
import com.aycho.app.controller.AppIndexer
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Skill 注册表
 *
 * 管理所有 Skills 的注册、查找和匹配
 * 核心功能：
 * - 从 intents.json 加载意图定义
 * - 查询本地已安装 App，筛选可用应用
 * - 根据优先级选择最佳执行方案
 */
class SkillRegistry private constructor(
    private val context: Context,
    private val appScanner: AppIndexer
) {

    private val skills = mutableMapOf<String, Skill>()
    private val categoryIndex = mutableMapOf<String, MutableList<Skill>>()

    // 缓存已安装 App 的包名集合（启动时刷新）
    private var installedPackages: Set<String> = emptySet()

    /**
     * 初始化：刷新已安装应用列表
     */
    fun refreshInstalledApps() {
        val apps = appScanner.getApps()
        installedPackages = apps.map { it.packageName }.toSet()
        println("[SkillRegistry] 已缓存 ${installedPackages.size} 个已安装应用")
    }

    /**
     * 检查包名是否已安装
     */
    fun isAppInstalled(packageName: String): Boolean {
        return installedPackages.contains(packageName)
    }

    /**
     * 从 assets/intents.json 加载意图定义
     */
    fun loadFromAssets(filename: String = "intents.json"): Int {
        try {
            val jsonString = context.assets.open(filename).bufferedReader().use { it.readText() }
            return loadFromJson(jsonString)
        } catch (e: IOException) {
            println("[SkillRegistry] 无法加载 $filename: ${e.message}")
            return 0
        }
    }

    /**
     * 从 JSON 字符串加载 Skills
     */
    fun loadFromJson(jsonString: String): Int {
        var loadedCount = 0
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val config = parseSkillConfig(obj)
                register(Skill(config))
                loadedCount++
            }
            println("[SkillRegistry] 已加载 $loadedCount 个 Skills")
        } catch (e: Exception) {
            println("[SkillRegistry] JSON 解析错误: ${e.message}")
            e.printStackTrace()
        }
        return loadedCount
    }

    /**
     * 解析单个 Skill 配置（新结构）
     */
    private fun parseSkillConfig(obj: JSONObject): SkillConfig {
        // 解析调用参数
        val params = mutableListOf<SkillParam>()
        val argsArray = obj.optJSONArray("args")
        if (argsArray != null) {
            for (i in 0 until argsArray.length()) {
                val argObj = argsArray.getJSONObject(i)
                val examples = mutableListOf<String>()
                val examplesArray = argObj.optJSONArray("examples")
                if (examplesArray != null) {
                    for (j in 0 until examplesArray.length()) {
                        examples.add(examplesArray.getString(j))
                    }
                }
                params.add(SkillParam(
                    name = argObj.getString("name"),
                    type = argObj.optString("type", "string"),
                    description = argObj.optString("description", ""),
                    required = argObj.optBoolean("required", false),
                    defaultValue = argObj.opt("default"),
                    examples = examples
                ))
            }
        }

        // 解析触发词
        val triggers = mutableListOf<String>()
        val triggersArray = obj.optJSONArray("triggers")
        if (triggersArray != null) {
            for (i in 0 until triggersArray.length()) {
                triggers.add(triggersArray.getString(i))
            }
        }

        // 解析目标应用列表
        val targets = mutableListOf<RelatedApp>()
        val targetsArray = obj.optJSONArray("targets")
        if (targetsArray != null) {
            for (i in 0 until targetsArray.length()) {
                val appObj = targetsArray.getJSONObject(i)

                // 解析目标应用执行模式
                val modeStr = appObj.optString("mode", "automation")
                val mode = when (modeStr.lowercase()) {
                    "handoff" -> ExecutionType.DELEGATION
                    else -> ExecutionType.GUI_AUTOMATION
                }

                // 解析执行剧本
                val steps = mutableListOf<String>()
                val stepsArray = appObj.optJSONArray("playbook")
                if (stepsArray != null) {
                    for (j in 0 until stepsArray.length()) {
                        steps.add(stepsArray.getString(j))
                    }
                }

                targets.add(RelatedApp(
                    packageName = appObj.getString("package"),
                    name = appObj.getString("name"),
                    type = mode,
                    deepLink = appObj.optString("uri", null)?.takeIf { it.isNotEmpty() },
                    steps = if (steps.isEmpty()) null else steps,
                    priority = appObj.optInt("rank", 0),
                    description = appObj.optString("description", null)?.takeIf { it.isNotEmpty() }
                ))
            }
        }

        return SkillConfig(
            id = obj.getString("intent_id"),
            name = obj.getString("label"),
            description = obj.optString("summary", ""),
            category = obj.optString("group", "通用"),
            keywords = triggers,
            params = params,
            relatedApps = targets,
            promptHint = obj.optString("prompt_hint", null)?.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * 注册 Skill
     */
    fun register(skill: Skill) {
        skills[skill.config.id] = skill

        // 更新分类索引
        val category = skill.config.category
        categoryIndex.getOrPut(category) { mutableListOf() }.add(skill)

        println("[SkillRegistry] 注册 Skill: ${skill.config.id} (${skill.config.relatedApps.size} 关联应用)")
    }

    /**
     * 获取 Skill
     */
    fun get(id: String): Skill? = skills[id]

    /**
     * 获取所有 Skills
     */
    fun getAll(): List<Skill> = skills.values.toList()

    /**
     * 按分类获取 Skills
     */
    fun getByCategory(category: String): List<Skill> {
        return categoryIndex[category] ?: emptyList()
    }

    /**
     * 获取所有分类
     */
    fun getAllCategories(): List<String> = categoryIndex.keys.toList()

    /**
     * 匹配用户意图（基于关键词）
     */
    fun match(query: String, topK: Int = 3, minScore: Float = 0.3f): List<SkillMatch> {
        val matches = mutableListOf<SkillMatch>()

        for (skill in skills.values) {
            val score = skill.matchScore(query)
            if (score >= minScore) {
                val params = skill.extractParams(query)
                matches.add(SkillMatch(skill, score, params))
            }
        }

        return matches
            .sortedByDescending { it.score }
            .take(topK)
    }

    /**
     * 获取最佳匹配
     */
    fun matchBest(query: String, minScore: Float = 0.3f): SkillMatch? {
        return match(query, topK = 1, minScore = minScore).firstOrNull()
    }

    /**
     * 匹配意图并返回可用应用（核心方法）
     *
     * 1. 匹配用户意图到 Skill
     * 2. 筛选出已安装的关联应用
     * 3. 按优先级排序
     */
    fun matchAvailableApps(
        query: String,
        minScore: Float = 0.3f
    ): List<AvailableAppMatch> {
        val skillMatches = match(query, topK = 5, minScore = minScore)
        val results = mutableListOf<AvailableAppMatch>()

        for (skillMatch in skillMatches) {
            val skill = skillMatch.skill
            val params = skillMatch.params

            // 筛选已安装的应用，按优先级排序
            val availableApps = skill.config.relatedApps
                .filter { isAppInstalled(it.packageName) }
                .sortedByDescending { it.priority }

            for (app in availableApps) {
                results.add(AvailableAppMatch(
                    skill = skill,
                    app = app,
                    params = params,
                    score = skillMatch.score
                ))
            }
        }

        // 按 (匹配分数 * 0.5 + 应用优先级 * 0.01) 综合排序
        return results.sortedByDescending { it.score * 0.5f + it.app.priority * 0.01f }
    }

    /**
     * 获取意图的最佳可用应用
     */
    fun getBestAvailableApp(query: String, minScore: Float = 0.3f): AvailableAppMatch? {
        return matchAvailableApps(query, minScore).firstOrNull()
    }

    /**
     * 生成 Skills 描述（给 LLM）
     */
    fun getSkillsDescription(): String {
        return buildString {
            append("可用技能列表：\n\n")
            for ((category, categorySkills) in categoryIndex) {
                append("【$category】\n")
                for (skill in categorySkills) {
                    val config = skill.config
                    append("- ${config.name}: ${config.description}\n")
                    if (config.keywords.isNotEmpty()) {
                        append("  关键词: ${config.keywords.joinToString(", ")}\n")
                    }
                    // 显示已安装的应用
                    val installedApps = config.relatedApps.filter { isAppInstalled(it.packageName) }
                    if (installedApps.isNotEmpty()) {
                        val appNames = installedApps.map {
                            val typeIcon = if (it.type == ExecutionType.DELEGATION) "[直达]" else "[自动化]"
                            "$typeIcon${it.name}"
                        }
                        append("  可用应用: ${appNames.joinToString(", ")}\n")
                    }
                }
                append("\n")
            }
        }
    }

    companion object {
        @Volatile
        private var instance: SkillRegistry? = null

        fun init(context: Context, appScanner: AppIndexer): SkillRegistry {
            return instance ?: synchronized(this) {
                instance ?: SkillRegistry(context.applicationContext, appScanner).also {
                    it.refreshInstalledApps()
                    instance = it
                }
            }
        }

        fun getInstance(): SkillRegistry {
            return instance ?: throw IllegalStateException("SkillRegistry 未初始化，请先调用 init()")
        }

        fun isInitialized(): Boolean = instance != null
    }
}
