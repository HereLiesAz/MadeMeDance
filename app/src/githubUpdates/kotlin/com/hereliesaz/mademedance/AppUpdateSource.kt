package com.hereliesaz.mademedance

import android.app.Activity
import com.hereliesaz.aznavrail.updates.AzUpdateChecker

/** Checks for updates using only this build's supported distribution channel. */
internal fun checkAppUpdates(activity: Activity) {
    AzUpdateChecker.check(activity, "HereLiesAz/MadeMeDance")
}
