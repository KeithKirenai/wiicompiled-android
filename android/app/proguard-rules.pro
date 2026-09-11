# Keep JNI native entry points and models
-keepclassmembers class * {
    native <methods>;
}

# Keep SDL Activity callbacks and native interfaces
-keep class org.libsdl.app.** { *; }

# Keep App components and activities
-keep class com.wiicompiled.mkw.** { *; }
