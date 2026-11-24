package com.hereliesaz.mademedance

import android.app.Activity
import com.hereliesaz.aznavrail.updates.AzUpdateChecker
import com.hereliesaz.aznavrail.updates.play.AzPlayUpdates

/** Checks for updates using only this build's supported distribution channel. */
internal fun checkAppUpdates(activity: Activity) {
    AzUpdateChecker.check(activity, "HereLiesAz/MadeMeDance", playSource = AzPlayUpdates)
}
