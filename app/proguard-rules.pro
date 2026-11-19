-optimizationpasses 9
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses ''

# Keep components referenced by AndroidManifest.xml from being renamed or removed.
-keep class com.shreenivas.tvhome.MainActivity { *; }
-keep class com.shreenivas.tvhome.services.NightNudgeReceiver { *; }
-keep class com.shreenivas.tvhome.services.TvNotificationService { *; }

# Do not remove Kotlin runtime null checks or lateinit guards: they are correctness checks.
