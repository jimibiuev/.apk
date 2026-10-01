package com.aycho.app

import android.app.Application
import android.content.pm.PackageManager
import com.aycho.app.controller.AppIndexer
import com.aycho.app.controller.DeviceBridge
import com.aycho.app.data.PreferencesStore
import com.aycho.app.skills.IntentRouter
import com.aycho.app.tools.CapabilityRegistry
import com.aycho.app.utils.CrashHandler
import rikka.shizuku.Shizuku

class App : Application() {

    lateinit var deviceController: DeviceBridge
        private set
    lateinit var appScanner: AppIndexer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化崩溃捕获（本地日志）
        CrashHandler.getInstance().init(this)

        // 云端崩溃上报通道已移除（不再依赖 Google Firebase）

        // 初始化 Shizuku
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)

        // 初始化核心组件
        initializeComponents()
    }

    private fun initializeComponents() {
        // 初始化设备控制器
        deviceController = DeviceBridge(this)
        deviceController.setCacheDir(cacheDir)

        // 初始化应用扫描器
        appScanner = AppIndexer(this)

        // 初始化 Tools 层
        val toolManager = CapabilityRegistry.init(this, deviceController, appScanner)

        // 异步预扫描应用列表（避免 ANR）
        println("[App] 开始异步扫描已安装应用...")
        Thread {
            appScanner.refreshApps()
            println("[App] 已扫描 ${appScanner.getApps().size} 个应用")
        }.start()

        // 初始化 Skills 层（传入 appScanner 用于检测已安装应用）
        val skillManager = IntentRouter.init(this, toolManager, appScanner)
        println("[App] IntentRouter 已加载 ${skillManager.getAllSkills().size} 个 Skills")

        println("[App] 组件初始化完成")
    }

    override fun onTerminate() {
        super.onTerminate()
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
    }

    /**
     * 动态更新云端崩溃上报开关
     */
    fun updateCloudCrashReportEnabled(enabled: Boolean) {
        // 云端上报通道已移除，此开关仅保留兼容（不再对外发送任何数据）
        println("[App] 云端崩溃上报已${if (enabled) "开启" else "关闭"}（未接入）")
    }

    companion object {
        @Volatile
        private var instance: App? = null

        fun getInstance(): App {
            return instance ?: throw IllegalStateException("App 未初始化")
        }

        private val REQUEST_PERMISSION_RESULT_LISTENER =
            Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                println("[Shizuku] Permission result: $granted")
            }
    }
}
