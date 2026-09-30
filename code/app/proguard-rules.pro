# Add project specific ProGuard rules here.

# Strip debug/verbose/info logging in release (Log.w/e stay)
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# Readable release stack traces (retrace with build/outputs/mapping/release/mapping.txt)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
