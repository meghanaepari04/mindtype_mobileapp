# Add project specific ProGuard rules here.
# Keep TensorFlow Lite classes
-keep class org.tensorflow.** { *; }
-keepclassmembers class org.tensorflow.** { *; }

# Keep Room entities
-keep class com.mindtype.mobile.data.entity.** { *; }

# Keep WorkManager workers
-keep class com.mindtype.mobile.workers.** { *; }
