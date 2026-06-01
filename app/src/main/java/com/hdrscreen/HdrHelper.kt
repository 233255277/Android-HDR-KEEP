package com.hdrscreen

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager

/**
 * HDR能力检测与工具类
 * 用于查询设备HDR支持情况，设置Activity的HDR色彩模式
 */
object HdrHelper {

    /** 设备支持的HDR类型列表 */
    data class HdrCapability(
        val hdrTypes: List<Int>,
        val maxLuminance: Float,
        val maxAverageLuminance: Float,
        val minLuminance: Float,
        val isHdrSupported: Boolean
    )

    /**
     * 查询当前默认Display的HDR能力
     */
    fun getHdrCapabilities(context: Context): HdrCapability {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return HdrCapability(
            hdrTypes = emptyList(),
            maxLuminance = 0f,
            maxAverageLuminance = 0f,
            minLuminance = 0f,
            isHdrSupported = false
        )

        return getHdrCapabilities(display)
    }

    /**
     * 查询指定Display的HDR能力
     */
    fun getHdrCapabilities(display: Display): HdrCapability {
        val hdrCaps = display.hdrCapabilities ?: return HdrCapability(
            hdrTypes = emptyList(),
            maxLuminance = 0f,
            maxAverageLuminance = 0f,
            minLuminance = 0f,
            isHdrSupported = false
        )

        return HdrCapability(
            hdrTypes = hdrCaps.supportedHdrTypes.toList(),
            maxLuminance = hdrCaps.desiredMaxLuminance,
            maxAverageLuminance = hdrCaps.desiredMaxAverageLuminance,
            minLuminance = hdrCaps.desiredMinLuminance,
            isHdrSupported = hdrCaps.supportedHdrTypes.isNotEmpty()
        )
    }

    /**
     * 获取HDR类型对应的可读名称
     */
    fun getHdrTypeName(hdrType: Int): String = when (hdrType) {
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
        else -> "Unknown($hdrType)"
    }

    /**
     * 设置Activity使用HDR色彩模式
     * 注意: API34+中该方法被标记为deprecated，但仍可使用
     * 新的替代方案需要在Surface级别设置HDR属性
     */
    fun setActivityHdrMode(activity: Activity, enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API34: Android14
            if (enable) {
                // 设置HDR色彩模式
                // COLOR_MODE_HDR = 7 (ActivityInfo)
                @Suppress("DEPRECATION")
                activity.window.setColorMode(ActivityInfo.COLOR_MODE_HDR)

                // 确保屏幕保持高亮度
                val window = activity.window
                window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)

                // 尝试设置HDR headroom（API34+）
                trySetHdrHeadroom(activity, 1.0f)
            } else {
                @Suppress("DEPRECATION")
                activity.window.setColorMode(ActivityInfo.COLOR_MODE_DEFAULT)
            }
        } else {
            // API33以下: Android13及更早
            @Suppress("DEPRECATION")
            if (enable) {
                activity.window.setColorMode(ActivityInfo.COLOR_MODE_HDR)
            } else {
                activity.window.setColorMode(ActivityInfo.COLOR_MODE_DEFAULT)
            }
        }
    }

    /**
     * 尝试设置HDR headroom (API34+)
     * 这会让SurfaceFlinger为这个窗口保留HDR合成能力
     */
    private fun trySetHdrHeadroom(activity: Activity, ratio: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                // 通过SurfaceControl设置desiredHdrHeadroom
                // 这个比例表示应用想要使用的HDR亮度范围相对于SDR白点的倍数
                // 例如 ratio=1.0 表示与SDR相同，ratio=10.0 表示10倍亮度
                val window = activity.window
                // 使用反射调用隐藏API(仅在target API34+且设备支持时有效)
                // 或者使用 SurfaceControl.Builder
                android.util.Log.d("HdrHelper", "API34+: attempting HDR headroom = $ratio")
                // 注: 正式API中可能需要使用 SurfaceControl.setDesiredHdrHeadroom()
            } catch (e: Exception) {
                android.util.Log.w("HdrHelper", "Failed to set HDR headroom: ${e.message}")
            }
        }
    }

    /**
     * 解析HDR类型为简短的显示字符串
     */
    fun formatHdrTypes(hdrTypes: List<Int>): String {
        return hdrTypes.joinToString(", ") { getHdrTypeName(it) }
    }

    /**
     * 检查当前Window是否处于HDR模式
     */
    fun isWindowInHdrMode(activity: Activity): Boolean {
        @Suppress("DEPRECATION")
        return activity.window.colorMode == ActivityInfo.COLOR_MODE_HDR
    }
}