package com.dayline.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.dayline.app.core.common.ReminderSync
import com.dayline.app.core.common.SystemTimeProvider
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.db.DaylineDatabase
import com.dayline.app.notifications.AlarmGateway
import com.dayline.app.notifications.AndroidAlarmGateway
import com.dayline.app.notifications.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DaylineDatabase =
        Room.databaseBuilder(context, DaylineDatabase::class.java, DaylineDatabase.NAME)
            .addMigrations(*DaylineDatabase.MIGRATIONS)
            .build()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("dayline_settings") })

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    @Binds
    abstract fun bindReminderSync(impl: ReminderScheduler): ReminderSync

    @Binds
    abstract fun bindAlarmGateway(impl: AndroidAlarmGateway): AlarmGateway
}
