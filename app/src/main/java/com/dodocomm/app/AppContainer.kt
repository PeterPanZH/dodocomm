package com.dodocomm.app

import android.content.Context
import com.dodocomm.app.bark.BarkClient
import com.dodocomm.app.core.db.HistoryDao
import com.dodocomm.app.core.db.HistoryDatabase
import com.dodocomm.app.core.db.HistoryRepository
import com.dodocomm.app.core.settings.SettingsRepository
import com.dodocomm.app.core.update.UpdateClient
import com.dodocomm.app.core.update.UpdatePrefs
import com.dodocomm.app.forward.ForwardEngine
import com.dodocomm.app.service.ServiceController
import com.dodocomm.app.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-written dependency container. Small app, no DI framework: cheap to
 * build, easy to reason about, and everything is lazily created on first use.
 */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val history: HistoryRepository by lazy {
        HistoryRepository(HistoryDao(HistoryDatabase(appContext)), appScope)
    }
    val barkClient: BarkClient by lazy { BarkClient() }
    val serviceController: ServiceController by lazy { ServiceController(appContext, this) }
    val forwardEngine: ForwardEngine by lazy { ForwardEngine(appContext, this) }
    val workScheduler: WorkScheduler by lazy { WorkScheduler(appContext) }
    val updateClient: UpdateClient by lazy { UpdateClient() }
    val updatePrefs: UpdatePrefs by lazy { UpdatePrefs(appContext) }

    /**
     * Called once from Application.onCreate.
     *
     * Deliberately does NOT start the foreground service here: on Android 16 a
     * process that was spawned in the background (e.g. by BOOT_COMPLETED) may
     * not call startForegroundService() from Application.onCreate. The service
     * is started from the foreground UI (MainActivity onResume) or from the
     * exempt BootReceiver context instead.
     */
    fun onAppStart() {
        workScheduler.ensureMaintenanceScheduled()
    }
}

/** Convenience accessor for receivers, workers and activities. */
val Context.appContainer: AppContainer
    get() = (applicationContext as Sms2BarkApp).container
