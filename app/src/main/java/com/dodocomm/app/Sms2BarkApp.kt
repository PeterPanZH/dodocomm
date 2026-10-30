package com.dodocomm.app

import android.app.Application
import com.dodocomm.app.ui.i18n.AppLocale
import com.dodocomm.app.ui.i18n.AppStrings

class Sms2BarkApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppStrings.init(this)
        AppLocale.apply(container.settings.current().appLanguage)
        container.onAppStart()
    }
}
