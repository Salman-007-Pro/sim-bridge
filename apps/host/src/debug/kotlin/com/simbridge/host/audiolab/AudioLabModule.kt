package com.simbridge.host.audiolab

import com.simbridge.host.navigation.HostTool
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Exists only in the debug source set, so release builds have no audio lab and no microphone permission. */
@Module
@InstallIn(SingletonComponent::class)
object AudioLabModule {
    @Provides
    @IntoSet
    fun audioLabTool(): HostTool = HostTool("Audio lab (debug)", AudioLabRoute) { onBack -> AudioLabScreen(onBack) }
}
