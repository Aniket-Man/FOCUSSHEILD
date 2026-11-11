package com.example.feature.applimits.ui

import android.content.Context
import com.example.R
import com.example.data.repository.AppLimitWriteResult

internal fun AppLimitWriteResult.feedbackMessage(context: Context): String = context.getString(when (this) {
    is AppLimitWriteResult.Applied -> if (followUpFailure == null) R.string.limits_change_saved else R.string.limits_follow_up_error
    is AppLimitWriteResult.Failed -> R.string.limits_save_error
    is AppLimitWriteResult.Rejected -> when (reason) {
        AppLimitWriteResult.Reason.STRICT_LOCKED -> R.string.limits_strict_locked
        AppLimitWriteResult.Reason.MISSING -> R.string.limits_missing_error
        AppLimitWriteResult.Reason.INVALID -> R.string.limits_minutes_error
        AppLimitWriteResult.Reason.BUSY -> R.string.limits_busy_error
    }
})
