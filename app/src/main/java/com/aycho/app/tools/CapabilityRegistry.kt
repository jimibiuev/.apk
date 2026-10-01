package com.aycho.app.tools

import android.content.Context
import com.aycho.app.controller.AppIndexer
import com.aycho.app.controller.DeviceBridge

/**
 * 工具管理器
 *
 * 负责初始化、注册和管理所有 Tools
 * 作为 Tool 层的统一入口
 */
class CapabilityRegistry private constructor(
    private val context: Context,
    private val deviceController: DeviceBridge,
    private val appScanner: AppIndexer
) {

    // 持有各个工具的引用（方便直接调用）
    lateinit var searchAppsTool: AppFinder
        private set
    lateinit var openAppTool: AppLauncher
        private set
    lateinit var clipboardTool: ClipboardTool
        private set
    lateinit var deepLinkTool: UriRouter
        private set
    lateinit var shellTool: ShellBridge
        private set
    lateinit var httpTool: HttpRequest
        private set

    /**
     * 初始化所有工具
     */
    private fun initialize() {
        // 创建工具实例
        searchAppsTool = AppFinder(appScanner)
        openAppTool = AppLauncher(deviceController, appScanner)
        clipboardTool = ClipboardTool(context)
        deepLinkTool = UriRouter(deviceController)
        shellTool = ShellBridge(deviceController)
        httpTool = HttpRequest()

        // 注册到全局 Registry
        CapabilityCatalog.register(searchAppsTool)
        CapabilityCatalog.register(openAppTool)
        CapabilityCatalog.register(clipboardTool)
        CapabilityCatalog.register(deepLinkTool)
        CapabilityCatalog.register(shellTool)
        CapabilityCatalog.register(httpTool)

        println("[CapabilityRegistry] 已初始化 ${CapabilityCatalog.getAll().size} 个工具")
    }

    /**
     * 执行工具
     */
    suspend fun execute(toolName: String, params: Map<String, Any?>): ToolResult {
        return CapabilityCatalog.execute(toolName, params)
    }

    /**
     * 获取所有工具描述（给 LLM）
     */
    fun getToolDescriptions(): String {
        return CapabilityCatalog.getAllDescriptions()
    }

    /**
     * 获取可用工具列表
     */
    fun getAvailableTools(): List<Tool> {
        return CapabilityCatalog.getAll()
    }

    companion object {
        @Volatile
        private var instance: CapabilityRegistry? = null

        /**
         * 初始化单例
         */
        fun init(
            context: Context,
            deviceController: DeviceBridge,
            appScanner: AppIndexer
        ): CapabilityRegistry {
            return instance ?: synchronized(this) {
                instance ?: CapabilityRegistry(context, deviceController, appScanner).also {
                    it.initialize()
                    instance = it
                }
            }
        }

        /**
         * 获取单例
         */
        fun getInstance(): CapabilityRegistry {
            return instance ?: throw IllegalStateException("CapabilityRegistry 未初始化，请先调用 init()")
        }

        /**
         * 检查是否已初始化
         */
        fun isInitialized(): Boolean = instance != null
    }
}
