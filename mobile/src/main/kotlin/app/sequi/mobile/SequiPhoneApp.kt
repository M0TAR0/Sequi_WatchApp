package app.sequi.mobile

import android.app.Application
import app.sequi.data.Library
import app.sequi.data.SequiSync

class SequiPhoneApp : Application() {
    lateinit var library: Library

    override fun onCreate() {
        super.onCreate()
        library = Library(filesDir)
        SequiSync.init(this, library)
        SequiSync.pull()
    }
}
