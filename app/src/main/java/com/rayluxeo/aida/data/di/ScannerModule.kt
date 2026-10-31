package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.scanner.MlKitTextRecognizer
import com.rayluxeo.aida.domain.scanner.TextRecognizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScannerModule {

    @Binds
    @Singleton
    abstract fun bindTextRecognizer(impl: MlKitTextRecognizer): TextRecognizer
}
