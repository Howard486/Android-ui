# Readable crash reports without leaking the original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The system instantiates the notification listener by name from the manifest.
-keep class com.foldspace.launcher.notifications.FoldSpaceNotificationListener { *; }
-keep class com.foldspace.launcher.context.signals.PowerConnectionReceiver { *; }
