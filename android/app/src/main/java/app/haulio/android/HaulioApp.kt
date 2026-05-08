package app.haulio.android

import app.haulio.android.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class HaulioApp : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@HaulioApp)
            modules(appModule)
        }
    }
}
