package com.example.progetto

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class BackgroundActivity : Service(), LocationListener {

    private var rangeCorrente: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var velocita: Float = 0f
    private val intervalloCheckVelocita = 10000L
    private var confermaHandler: Handler? = null
    private var durataPausa: Long = 0L
    private lateinit var locationManager: LocationManager
    private var cancellaTracking = false
    private var attivitaTracciata: String? = null


    private val CHANNEL_ID = "ACTIVITY_CHANNEL"

    override fun onCreate() {
        super.onCreate()
        creaChannelNotifiche()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        aggiornamentiLocation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_CANCEL_TRACKING") {
            cancellaTracking = true
            startPausa()
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(mainIntent)

            return START_NOT_STICKY
        } else {
            val notifica = creaNotifica("Monitoraggio dell'attività...")
            startForeground(1, notifica)
            handler.postDelayed(::checkVelocita, intervalloCheckVelocita)
            return START_STICKY
        }
    }

    private fun aggiornamentiLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, this)
        }
    }

    override fun onLocationChanged(location: Location) {
        velocita = location.speed * 3.6f
    }

    private fun checkVelocita() {
        val adesso = System.currentTimeMillis()

        if (adesso < durataPausa) {
            handler.postDelayed(::checkVelocita, intervalloCheckVelocita)
            return
        }

        val nuovoRange = rangeVelocita(velocita)

        if (nuovoRange != rangeCorrente) {
            rangeCorrente = nuovoRange
            if (attivitaTracciata != nuovoRange) {
                mandaNotifica(nuovoRange)
                attivitaTracciata = nuovoRange
            }
        }

        handler.postDelayed(::checkVelocita, intervalloCheckVelocita)
    }

    private fun mandaNotifica(activityType: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val cancellaIntent = PendingIntent.getService(
            this, 0, Intent(this, BackgroundActivity::class.java).apply {
                action = "ACTION_CANCEL_TRACKING"
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        val notifica = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nuova attività rilevata")
            .setContentText("Sembra che tu stia facendo: $activityType")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .addAction(R.drawable.ic_launcher_foreground, "No, non sto facendo quello", cancellaIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this).notify(2, notifica)

        confermaHandler?.postDelayed({
            if (!cancellaTracking) {
                startTracking(activityType)
            }
        }, 10000L)
    }

    private fun creaNotifica(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking Attività")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun startPausa() {
        durataPausa = System.currentTimeMillis() + 10 * 60 * 1000
        handler.postDelayed({
            handler.post(::checkVelocita)
        }, 10 * 60 * 1000)
    }



    private fun startTracking(activityType: String) {
        val trackingIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("nomeAttivita", activityType)
            action = "START_TRACKING"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(trackingIntent)
    }


    private fun rangeVelocita(velocita: Float): String {
        return when {
            velocita in 0f..1f -> "Attività non sportive"
            velocita in 1f..7f -> "Camminata"
            velocita in 8f..20f -> "Corsa"
            else -> "Auto"
        }
    }

    override fun onDestroy() {
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun creaChannelNotifiche() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Activity Recognition Channel",
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }
}