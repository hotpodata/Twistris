package com.hotpodata.twistris

import android.support.multidex.MultiDexApplication
import com.hotpodata.twistris.utils.TetrisFactory
import timber.log.Timber

/**
 * Created by jdrotos on 12/19/15.
 */
class TwistrisApplication : MultiDexApplication() {
    public override fun onCreate() {
        super.onCreate()
        if (BuildConfig.LOGGING_ENABLED) {
            Timber.plant(Timber.DebugTree())
        }
        TetrisFactory.setupResColors(this)
    }
}