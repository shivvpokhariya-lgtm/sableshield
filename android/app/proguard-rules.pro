# Keep Vosk + JNA (reflection/JNI targets that R8 cannot see).
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn org.vosk.**
-dontwarn com.sun.jna.**
