package com.executiveos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.executiveos.core.notifications.AndroidNotificationPublisher
import com.executiveos.domain.repository.ReminderRepository
import com.executiveos.domain.reminders.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class ExecutiveOSApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var publisher: AndroidNotificationPublisher
    @Inject lateinit var reminders: ReminderRepository
    @Inject lateinit var scheduler: ReminderScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        publisher.createChannels()
        // Sin reemplazar: abrir la app no debe reiniciar el calendario de avisos ya programado.
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            scheduler.scheduleRecurring(reminders.settings.first(), replaceExisting = false)
        }
    }
}
