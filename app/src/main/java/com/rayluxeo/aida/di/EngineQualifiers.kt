package com.rayluxeo.aida.di

import javax.inject.Qualifier

/** Binds the on-device interpreter. Always present, always tried first. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalEngine

/** Binds the optional cloud interpreter. May be absent, unconfigured or offline. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiEngine
