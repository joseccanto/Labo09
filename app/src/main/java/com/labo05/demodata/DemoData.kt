package com.labo05.demodata

import android.app.Application

import com.labo05.demodata.data.local.DemoDataDatabase
import com.labo05.demodata.data.repository.GpsRepository
import com.labo05.demodata.data.session.SessionManager
import com.labo05.demodata.data.local.FileStorageManager
import com.labo05.demodata.data.repository.AudioRepository
import com.labo05.demodata.data.repository.MediaRepository
class DemoData : Application() {

    val database     by lazy { DemoDataDatabase.getInstance(this) }
    val fileStorage  by lazy { FileStorageManager(this) }
    val sessionManager by lazy { SessionManager(this) }

    val gpsRepository by lazy {
        GpsRepository(database.gpsGoogleDao(), database.gpsSensorsDao())
    }
    val mediaRepository by lazy {
        MediaRepository(database.mediaDao(), fileStorage)
    }
    val audioRepository by lazy {
        AudioRepository(database.audioDao(), fileStorage)
    }
}