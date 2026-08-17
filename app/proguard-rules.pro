-keep class ai.daylight.assistant.data.local.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-dontwarn org.conscrypt.**
