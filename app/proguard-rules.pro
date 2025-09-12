-keep class com.nohate.app.NativeClassifier { *; }

# Keep annotations used by dependencies (e.g., Tink) so R8 doesn't strip them
-dontwarn javax.annotation.**
-keep class javax.annotation.** { *; }
-keep @interface javax.annotation.**
-dontwarn javax.annotation.concurrent.**
-keep class javax.annotation.concurrent.** { *; }

# Keep Google Tink (if used transitively by dependencies)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
