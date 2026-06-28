package dev.kartik.accessmanager.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kartik.accessmanager.data.repository.AppRepositoryImpl
import dev.kartik.accessmanager.data.repository.PolicyRepositoryImpl
import dev.kartik.accessmanager.domain.repository.AppRepository
import dev.kartik.accessmanager.domain.repository.PolicyRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindAppRepository(impl: AppRepositoryImpl): AppRepository

    @Binds
    @Singleton
    abstract fun bindPolicyRepository(impl: PolicyRepositoryImpl): PolicyRepository
}

