package com.commspurx.mobile.notifications

import android.content.Context
import com.commspurx.mobile.CommspurxApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single entry point for all notification polls — serialized and debounced so foreground,
 * service, WorkManager, and alarm paths cannot race on [MonitorSeenStore].
 */
object NotificationPollManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val runMutex = Mutex()
    private var debouncedJob: Job? = null

    private const val DEBOUNCE_MS = 800L

    fun schedulePoll(
        context: Context,
        resetBaseline: Boolean = false,
        debounce: Boolean = false,
    ) {
        val appContext = context.applicationContext
        if (debounce) {
            debouncedJob?.cancel()
            debouncedJob = scope.launch {
                delay(DEBOUNCE_MS)
                runPoll(appContext, resetBaseline)
            }
            return
        }
        scope.launch {
            runPoll(appContext, resetBaseline)
        }
    }

    suspend fun runPollNow(context: Context, resetBaseline: Boolean = false): PollResult {
        return runPoll(context.applicationContext, resetBaseline)
    }

    private suspend fun runPoll(context: Context, resetBaseline: Boolean): PollResult {
        return runMutex.withLock {
            val app = context as CommspurxApplication
            if (app.sessionStore.getRefreshToken() == null) {
                return PollResult.NoSession
            }
            app.syncAuthTokenFromStore()
            NotificationPollCoordinator(context).pollAndNotify(resetBaseline = resetBaseline)
        }
    }
}
