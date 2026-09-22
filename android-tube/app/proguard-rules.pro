# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Keep SLF4J
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

# Keep Metrolist extractor
-keep class com.metrolist.** { *; }
-dontwarn com.metrolist.**
# Keep our data models for JSON parsing
-keep class com.darkk.youtube.innertube.** { *; }
-keep class com.darkk.youtube.data.** { *; }

# Keep NewPipe Extractor
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Keep NanoJSON used by NewPipe
-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**

# Keep Rhino used by NewPipe for signature decryption
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# Keep Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
