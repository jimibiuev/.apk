package com.aycho.app.tools

import com.aycho.app.controller.DeviceBridge

/**
 * DeepLink 工具
 *
 * 通过 Intent 打开应用的特定页面或功能
 * 这是实现 handoff 类型意图的核心工具
 */
class UriRouter(private val deviceController: DeviceBridge) : Tool {

    override val name = "deep_link"
    override val displayName = "深度链接"
    override val description = "通过 DeepLink/Intent 打开应用的特定页面或功能"

    override val params = listOf(
        ToolParam(
            name = "uri",
            type = "string",
            description = "DeepLink URI（如：https://、geo:、spotify:）",
            required = true
        ),
        ToolParam(
            name = "action",
            type = "string",
            description = "Intent Action（默认 VIEW）",
            required = false,
            defaultValue = "android.intent.action.VIEW"
        )
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val uri = params["uri"] as? String
            ?: return ToolResult.Error("缺少 uri 参数")

        return try {
            deviceController.openDeepLink(uri)
            ToolResult.Success(
                data = mapOf("uri" to uri),
                message = "已打开: $uri"
            )
        } catch (e: Exception) {
            ToolResult.Error("打开 DeepLink 失败: ${e.message}")
        }
    }

    companion object {
        /**
         * 常用 DeepLink 模板
         */
        val TEMPLATES = mapOf(
            // ========== 外卖 / 餐饮 ==========
            "ubereats_home" to "https://www.ubereats.com/",
            "ubereats_search" to "https://www.ubereats.com/search?q={query}",
            "doordash_home" to "https://www.doordash.com/",
            "deliveroo_home" to "https://deliveroo.com/",
            "starbucks_order" to "https://www.starbucks.com/menu",

            // ========== 出行 / 地图 ==========
            "gmaps_route" to "https://www.google.com/maps/dir/?api=1&destination={destination}",
            "gmaps_search" to "https://www.google.com/maps/search/?api=1&query={query}",
            "applemaps_route" to "https://maps.apple.com/?daddr={destination}&dirflg=d",
            "waze_navi" to "waze://?q={destination}&navigate=yes",
            "uber_ride" to "uber://?action=setPickup&pickup=my_location&dropoff[formatted_address]={destination}",
            "lyft_ride" to "lyft://ridetype?id=lyft",
            "bolt_ride" to "bolt://",

            // ========== 社交 / 消息 ==========
            "whatsapp_send" to "whatsapp://send?text={message}",
            "telegram_send" to "tg://msg?text={message}",
            "signal_send" to "https://signal.me/#p/{contact}",
            "instagram_profile" to "instagram://user?username={username}",
            "x_profile" to "twitter://user?screen_name={username}",
            "threads_profile" to "barcelona://user?username={username}",

            // ========== 支付 ==========
            "gpay_wallet" to "https://pay.google.com/gp/w/home",
            "paypal_me" to "https://www.paypal.com/paypalme/{handle}",

            // ========== 音乐 ==========
            "spotify_play" to "spotify:track:{id}",
            "spotify_search" to "spotify:search:{query}",
            "ytmusic_search" to "https://music.youtube.com/search?q={query}",
            "applemusic_search" to "https://music.apple.com/search?term={query}",

            // ========== 视频 ==========
            "youtube_search" to "https://www.youtube.com/results?search_query={query}",
            "youtube_watch" to "vnd.youtube:{videoId}",
            "netflix_home" to "nflx://",
            "tiktok_open" to "https://www.tiktok.com/",

            // ========== 购物 ==========
            "amazon_home" to "https://www.amazon.com/",
            "amazon_search" to "https://www.amazon.com/s?k={query}",
            "ebay_search" to "https://www.ebay.com/sch/i.html?_nkw={query}",
            "walmart_search" to "https://www.walmart.com/search?q={query}",

            // ========== 阅读 ==========
            "kindle_home" to "kindle://",
            "applebooks_home" to "ibooks://",

            // ========== AI 助手（delegation 目标）==========
            "chatgpt_chat" to "https://chatgpt.com/?q={query}",
            "gemini_chat" to "https://gemini.google.com/app",
            "claude_chat" to "https://claude.ai/new?q={query}",

            // ========== 通用 ==========
            "web" to "{url}",
            "geo" to "geo:{lat},{lon}?q={query}",
            "tel" to "tel:{phone}",
            "sms" to "sms:{phone}?body={message}",
            "email" to "mailto:{email}?subject={subject}&body={body}"
        )

        /**
         * 根据模板生成 DeepLink
         */
        fun fromTemplate(templateName: String, params: Map<String, String>): String? {
            val template = TEMPLATES[templateName] ?: return null
            var result = template
            for ((key, value) in params) {
                result = result.replace("{$key}", value)
            }
            return result
        }
    }
}
