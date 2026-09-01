# Add project specific ProGuard rules here.
# Room database rules
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Keep entities and models
-keep class com.offlinetracker.data.local.** { *; }
-keep class com.offlinetracker.protocol.** { *; }
