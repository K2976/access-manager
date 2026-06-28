package dev.kartik.accessmanager.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kartik.accessmanager.data.repository.AppRepositoryImpl
import dev.kartik.accessmanager.data.repository.PolicyRepositoryImpl
import dev.kartik.accessmanager.domain.repository.AppRepository
import dev.kartik.accessmanager.domain.repository.PolicyRepository
import dev.kartik.accessmanager.vpn.packet.PacketReader
import dev.kartik.accessmanager.vpn.packet.PacketReaderImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    @Binds
    @Singleton
    abstract fun bindPacketReader(impl: PacketReaderImpl): PacketReader

    @Binds
    @Singleton
    abstract fun bindRelayEngine(impl: dev.kartik.accessmanager.vpn.relay.native.NativeRelayEngine): dev.kartik.accessmanager.vpn.relay.RelayEngine

    @Binds
    @Singleton
    abstract fun bindNativeBridge(impl: dev.kartik.accessmanager.vpn.relay.native.JniNativeBridge): dev.kartik.accessmanager.vpn.relay.native.NativeBridge

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        @Singleton
        fun providePackageManager(@ApplicationContext context: Context): PackageManager =
            context.packageManager

        @Provides
        @Singleton
        fun provideConnectivityManager(@ApplicationContext context: Context): ConnectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
}


