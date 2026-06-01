# HdrScreenActivator

**强制 Android 屏幕进入 HDR 激发态** —— 通过全透明悬浮窗循环播放内置 HDR10 视频，让屏幕持续保持高亮度、广色域模式。

## 原理

Android 14+ 支持 HDR 显示，但系统仅在检测到 HDR 内容（如播放 HDR 视频）时才会切换 `dynamic_range: HDR`，普通应用无法主动触发。

本项目通过屏幕叠加层，确保系统识别并保持 HDR 状态：

1. **MainActivity** —— 前台。 `SurfaceView` + `MediaPlayer` 循环播放内置 HDR10 视频
2. **HdrOverlayService** —— 全屏透明悬浮窗（alpha=0.001），同样播放 HDR10 视频

两个 HDR layer 叠加 → `numHdrLayers ≥ 1` → SurfaceFlinger 切换 `dynamic_range: HDR` → 屏幕进入高亮度/高色准状态。

## 屏幕效果

| 指标 | SDR 状态 | HDR 激发态 |
|------|---------|-----------|
| 亮度 | ~0.22 (归一化) | ~0.49+ |
| 色域 | sRGB | Display P3 / BT.2020 |
| `dynamic_range` | SDR | **HDR** |

> 实测设备：Redmi  Note 12 Turbo（澎湃os3.0.301），峰值亮度 420nit

## 使用

1. 安装 APK → 启动应用（自动开始方案A视频解码）
2. 点击「启动悬浮窗」→ 授予悬浮窗权限（系统设置 → 应用 → HDR屏幕激活器 → 允许在其他应用上层显示）
3. 按 Home 键回到桌面 → 悬浮窗继续运行，屏幕保持 HDR 状态

## 技术栈

- **最低 API**: 34 (Android 14)
- **语言**: Kotlin
- **依赖**: 零 AndroidX，纯原生 `android.app.*` API
- **视频**: HEVC HDR10 (BT.2020 + SMPTE 2084 PQ)，由 FFmpeg 生成，`libkvazaar` 编码
- **悬浮窗**: `TYPE_APPLICATION_OVERLAY`，`alpha=0.001` 用户无感知

## 架构

```
MainActivity                          HdrOverlayService
┌─────────────────────────┐          ┌──────────────────────────┐
│  SurfaceView (全屏)       │          │  WindowManager.addView    │
│  + MediaPlayer           │          │  SurfaceView (全屏, α≈0)  │
│  + HDR10 视频循环播放     │          │  + MediaPlayer            │
│  + COLOR_MODE_HDR        │          │  + HDR10 视频循环播放      │
└─────────────────────────┘          └──────────────────────────┘
         ↓                                      ↓
    numHdrLayers(1)            +       numHdrLayers(1)
         ↓                                      ↓
         └────────────── 叠加 ──────────────────┘
                          ↓
              SurfaceFlinger 检测到 HDR layer(s)
                          ↓
                 dynamic_range: HDR ✓
```

## 构建

```bash
# 1. 安装依赖
# - Android SDK (platforms/android-34, build-tools/34.0.0)
# - JDK 17+
# - Kotlin 1.9.22+
# - FFmpeg (需 libkvazaar 支持，用于生成内置视频)

# 2. 如需重新生成 HDR 测试视频
ffmpeg -y -f lavfi -i color=c=white:s=1920x1080:d=1 \
  -c:v libkvazaar -kvazaar-params preset=ultrafast \
  -pix_fmt yuv420p -colorspace bt2020nc \
  -color_primaries bt2020 -color_trc smpte2084 \
  -frames:v 30 app/src/main/res/raw/hdr_test.mp4

# 3. 编译
./gradlew assembleDebug

# 4. 安装
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 已尝试的路线

| 方案 | 描述 | 结果 |
|------|------|------|
| **A (✅ 采用)** | MediaPlayer + HDR10 视频解码 | 成功触发 `dynamic_range: HDR` |
| B (❌ 放弃) | OpenGL ES 3.0 扩展线性光渲染 | `numHdrLayers` 可达 1，但无法切换 `dynamic_range`，因为 API34 的 `SurfaceControl.setDataSpace` 在 MIUI 上空实现 |

## FAQ

**Q: 为什么不用 OpenGL 渲染而要播放视频？**

Android 14 的 `SurfaceControl.Transaction.setDataSpace(BT2020_PQ)` 在部分设备（如 MIUI）上是空实现。MediaPlayer 内部通过 native 层的 `ANativeWindow_setDataSpace` 设置 Surface 色彩空间，绕过了这个限制。

**Q: 耗电吗？**

软件功耗极低，但是硬件功耗极高。视频分辨率1920×1080 HEVC码率仅50kbps

**Q: 悬浮窗会影响触控吗？**

不会。设置了 `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE`，所有触摸事件穿透到下层应用。

## 许可

MIT License

Copyright (c) 2026 HZL

Permission is hereby granted, free of charge, to any person obtaining a copy...
