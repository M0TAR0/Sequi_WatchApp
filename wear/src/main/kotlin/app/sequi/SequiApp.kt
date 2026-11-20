package app.sequi

import android.app.Application
import app.sequi.data.Repo

class SequiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
    }
}
