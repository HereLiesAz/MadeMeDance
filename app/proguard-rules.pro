# Keep Apache Commons Math3 FFT classes (uses reflection internally)
-keep class org.apache.commons.math3.** { *; }
-dontwarn org.apache.commons.math3.**

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
