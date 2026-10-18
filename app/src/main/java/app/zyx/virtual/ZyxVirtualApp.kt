package app.zyx.virtual

import android.app.Application
import app.zyx.virtual.core.Keeper

class ZyxVirtualApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Keeper.ensureChannel(this)
    }
}
