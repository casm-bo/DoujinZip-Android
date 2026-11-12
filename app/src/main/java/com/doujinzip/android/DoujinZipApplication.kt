package com.doujinzip.android

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.doujinzip.android.network.HitomiThumbnailInterceptor

class DoujinZipApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        com.doujinzip.android.data.CrashDiagnostics.install(this)
        com.doujinzip.android.data.LocalBackupStore(this).applyPending()
        androidx.work.WorkManager.initialize(this, androidx.work.Configuration.Builder().build())
    }
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(HitomiThumbnailInterceptor()) }
        .build()
}
