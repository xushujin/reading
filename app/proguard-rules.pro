# 保留 Room 生成的实现类
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# 保留 XmlPullParser 反射入口（EPUB 解析使用 android.util.Xml）
-keep class org.xmlpull.** { *; }
-dontwarn org.xmlpull.**
