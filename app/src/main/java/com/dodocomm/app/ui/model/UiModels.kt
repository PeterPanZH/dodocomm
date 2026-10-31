package com.dodocomm.app.ui.model

import android.content.Context
import com.dodocomm.app.core.model.ForwardStatus
import com.dodocomm.app.core.system.AppPermissions

/** History filters shown as chips on the history screen. */
enum class HistoryFilter(val label: String) {
    ALL("全部"),
    SENT("已送达"),
    RETRY("待重试"),
    FILTERED("已过滤"),
    ;

    fun matches(status: ForwardStatus): Boolean = when (this) {
        ALL -> true
        SENT -> status == ForwardStatus.SENT
        RETRY -> status == ForwardStatus.FAILED || status == ForwardStatus.PENDING
        FILTERED -> status == ForwardStatus.FILTERED
    }
}

/** Snapshot of the system permission / power state used by the checklist. */
data class PermissionState(
    val receiveSms: Boolean = false,
    val postNotifications: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val batteryOptimizationsIgnored: Boolean = false,
    val readPhoneState: Boolean = false,
) {
    val notificationsReady: Boolean get() = postNotifications && notificationsEnabled

    companion object {
        fun read(context: Context): PermissionState = PermissionState(
            receiveSms = AppPermissions.hasReceiveSms(context),
            postNotifications = AppPermissions.hasPostNotifications(context),
            notificationsEnabled = AppPermissions.areNotificationsEnabled(context),
            batteryOptimizationsIgnored = AppPermissions.isIgnoringBatteryOptimizations(context),
            readPhoneState = AppPermissions.hasReadPhoneState(context),
        )
    }
}

/** One row in the home setup checklist. */
data class SetupItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val done: Boolean,
    val actionLabel: String,
)

/** Result state of the "test connection / test push" action. */
sealed interface TestPushState {
    data object Idle : TestPushState
    data object Loading : TestPushState
    data class Success(val message: String) : TestPushState
    data class Failure(val message: String) : TestPushState
}

/** One-shot feedback shown through the app-level Snackbar host. */
data class SnackbarEvent(val message: String, val isError: Boolean = false)
