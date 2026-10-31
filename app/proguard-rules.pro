# SMS2Bark release rules.
# Keep OkHttp optional platform integrations quiet.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# JavaMail Android port (SMTP channel) uses reflection/service lookup.
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class myjava.awt.datatransfer.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn myjava.awt.datatransfer.**
