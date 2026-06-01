# Add project specific ProGuard rules here.
# 保留OpenGL相关类不被混淆
-keep class com.hdrscreen.HdrGlRenderer { *; }
-keep class com.hdrscreen.HdrTestPattern { *; }
-keep class com.hdrscreen.HdrHelper { *; }