package com.dayline.app.core.common

import com.dayline.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Emits the current date and rolls over at midnight, so "Today" never goes stale while the app is open. */
@Singleton
class AppClock @Inject constructor(time: TimeProvider, @ApplicationScope scope: CoroutineScope) {
    val today: StateFlow<LocalDate> = flow {
        while (true) {
            emit(time.today())
            delay(30_000)
        }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, time.today())
}
