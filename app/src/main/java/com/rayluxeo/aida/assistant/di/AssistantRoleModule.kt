package com.rayluxeo.aida.assistant.di

import com.rayluxeo.aida.assistant.AssistantRoleController
import com.rayluxeo.aida.assistant.AssistantRoleControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the one reader of the assistant role.
 *
 * Deliberately unscoped. The implementation keeps nothing between calls — Abu can hand the role to
 * another app from a system screen while Mira is running, so every answer is asked of the platform
 * at the moment it is shown, and there is no state worth sharing between the screens that ask.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantRoleModule {

    @Binds
    abstract fun bindAssistantRoleController(
        impl: AssistantRoleControllerImpl,
    ): AssistantRoleController
}
