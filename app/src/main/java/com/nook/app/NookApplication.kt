package com.nook.app

import android.app.Application
import android.os.Build
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.crossfade
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.nook.app.di.appModule
import com.nook.app.notifications.Notifier
import com.nook.app.session.SessionManager
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class NookApplication : Application(), SingletonImageLoader.Factory {

    var firebaseReady: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        firebaseReady = AppConfig.firebaseReady(this)
        if (firebaseReady) {
            // Offline persistence keeps reads (and the Spark quota) low and makes sends optimistic.
            FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().setSizeBytes(100L * 1024 * 1024).build())
                .build()
        }
        startKoin {
            androidContext(this@NookApplication)
            modules(appModule)
        }
        Notifier.createChannels(this)
        if (firebaseReady) {
            val session: SessionManager = get()
            ProcessLifecycleOwner.get().lifecycle.addObserver(session)
            session.start()
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
