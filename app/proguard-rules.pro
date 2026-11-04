# Add project specific ProGuard rules here.
-keep class com.tastydrive.app.util.NativeEngine { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

