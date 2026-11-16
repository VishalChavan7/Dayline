package com.dayline.app.core.common

import com.dayline.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Runs work that must finish even if the screen that started it goes away (e.g. a sheet closing). */
@Singleton
class ApplicationScopeHolder @Inject constructor(@ApplicationScope private val scope: CoroutineScope) {
    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)
}
