package com.rayluxeo.aida.data

import com.rayluxeo.aida.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

/** Routes every repository hop onto the test scheduler so `runTest` stays in control. */
class TestDispatcherProvider(dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}
