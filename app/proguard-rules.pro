# kotlinx.serialization generates serializer classes at compile time and looks
# them up by name. Without these rules R8 strips or renames them and every
# encode/decode fails in a minified release build.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class **$$serializer {
    <fields>;
}

-keepclasseswithmembers class com.space.app.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.space.app.domain.model.**$$serializer {
    *;
}

-keep,includedescriptorclasses class com.space.app.data.**$$serializer {
    *;
}

# Serializers are referenced reflectively from the companion of each class.
-keepclassmembers class com.space.app.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.space.app.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Tink references these Error Prone annotations for compile-time checks only.
# They are absent at runtime, so R8 only needs to be told not to warn.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
