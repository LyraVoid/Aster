-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn java.beans.Introspector
-dontwarn java.beans.VetoableChangeListener
-dontwarn java.beans.VetoableChangeSupport
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.PropertyDescriptor

# Keep ini4j Service Provider Interface
-keep,allowobfuscation,allowoptimization class org.ini4j.spi.** { *; }

# JNI. libapjni.so registers its methods by the names and signatures written in apjni.cpp, so a
# class, a method or a type inside one of those signatures that R8 renames or moves makes
# RegisterNatives fail, JNI_OnLoad return JNI_ERR, and every process of this app die while loading
# Natives - which is what a minified build did before these rules were here.
-keep class me.bmax.apatch.Natives { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class me.bmax.apatch.Natives$Profile { *; }
-keep class me.bmax.apatch.Natives$KPMCtlRes { *; }
-keep class me.bmax.apatch.Natives$SuUidsResult { *; }
-keep class me.bmax.apatch.Natives$SuPathResult { *; }

# Named by the manifest's zygotePreloadName, which R8 cannot see, and registered by the same
# library.
-keep class me.bmax.apatch.magica.AppZygotePreload { *; }

# Kotlin
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}

-repackageclasses
-allowaccessmodification
-overloadaggressively
-renamesourcefileattribute SourceFile
