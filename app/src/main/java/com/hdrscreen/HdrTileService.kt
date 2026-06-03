package com.hdrscreen

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * 快捷设置图块：一键开关 HDR 悬浮窗
 * 下拉通知栏 → 编辑图块 → 添加「HDR悬浮窗」→ 点击即可开关
 */
class HdrTileService : TileService() {

    override fun onTileAdded() {
        updateTile()
    }

    override fun onStartListening() {
        updateTile()
    }

    override fun onClick() {
        if (HdrOverlayService.isServiceRunning) {
            stopService(Intent(this, HdrOverlayService::class.java))
            HdrOverlayService.setRunning(false)
        } else {
            val intent = Intent(this, HdrOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            HdrOverlayService.setRunning(true)
        }
        updateTile()
    }

    private fun updateTile() {
        qsTile?.apply {
            state = if (HdrOverlayService.isServiceRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "HDR悬浮窗"
            subtitle = if (HdrOverlayService.isServiceRunning) "已开启" else "已关闭"
            updateTile()
        }
    }
}