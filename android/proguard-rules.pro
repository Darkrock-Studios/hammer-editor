# Phone-only rules. Rules shared with the wear app live in /proguard-common.pro.

# Glance instantiates ActionCallback implementations reflectively via their no-arg
# constructor. Glance's own consumer rule keeps the class but not its members, so
# R8 strips the constructor and every widget tap throws NoSuchMethodException.
-keepclassmembers class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
