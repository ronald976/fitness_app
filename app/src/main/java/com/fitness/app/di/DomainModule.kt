package com.fitness.app.di

import com.fitness.app.domain.suggestion.DoubleProgressionStrategy
import com.fitness.app.domain.suggestion.ProgressionStrategy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    @Singleton
    abstract fun bindProgressionStrategy(
        impl: DoubleProgressionStrategy
    ): ProgressionStrategy
}
