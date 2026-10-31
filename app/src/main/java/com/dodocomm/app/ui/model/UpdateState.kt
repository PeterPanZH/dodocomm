package com.dodocomm.app.ui.model

import com.dodocomm.app.core.update.UpdateInfo

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Int) : UpdateState
    data class Ready(val info: UpdateInfo, val apkPath: String) : UpdateState
    data class Error(val message: String) : UpdateState
}
