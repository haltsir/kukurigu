# Add project specific ProGuard rules here.

# ---- kotlinx.serialization ----
# Keep `Companion` object fields of serializable classes.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep generated serializers.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
}
-keepclasseswithmembers class **.*$$serializer {
    *;
}

# Keep enum members used by serialization (DawnPhase).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
