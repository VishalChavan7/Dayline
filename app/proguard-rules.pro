# Room, Hilt and WorkManager ship their own consumer rules.
# Keep enum names used in persisted JSON backups.
-keepclassmembers enum com.dayline.app.core.model.** { *; }

# Glance instantiates ActionCallback classes by name from the widget click intent.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
-keep class com.dayline.app.widget.** { *; }

# Annotation-only references that are not on the runtime classpath.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**

# Notes editor re-numbers ordered lists through this private method (reflection).
-keepclassmembers class com.mohamedrejeb.richeditor.model.RichTextState {
    private void checkParagraphsType();
}
