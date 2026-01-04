package com.example.progetto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Intent

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var attivitaSelezionata: TextView
    private lateinit var testoTimer: TextView
    private lateinit var stopButton: Button
    private lateinit var testoPassi: TextView

    private var tracking = false
    private var timer = 0
    private var attivitaCorrente = ""
    private var dataInizio: Date? = null

    private lateinit var sensorManager: SensorManager
    private var sensoreContaPassi: Sensor? = null
    private var passi = 0
    private var passiIniziali = 0
    private var passiSessione = 0

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var attivitaDao: AttivitaDao

    private val CODICE_RICHIESTA_ACTIVITY_RECOGNITION = 1001
    private val CODICE_RICHIESTA_LOCATION = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val database = (application as DbApp).database
        attivitaDao = database.attivitaDao()


        attivitaSelezionata = findViewById(R.id.attivita_selezionata)
        testoTimer = findViewById(R.id.timer)
        stopButton = findViewById(R.id.button_stop)
        stopButton.isEnabled = false
        stopButton.visibility = View.GONE
        testoTimer.text = ""


        testoPassi = findViewById(R.id.step_count)
        testoPassi.visibility = View.GONE


        val buttonAltro: Button = findViewById(R.id.button_altro)
        val buttonAuto: Button = findViewById(R.id.button_auto)
        val buttonCamminata: Button = findViewById(R.id.button_camminata)
        val buttonCorsa: Button = findViewById(R.id.button_corsa)
        val buttonBici: Button = findViewById(R.id.button_bici)
        val buttonSport: Button = findViewById(R.id.button_sport)


        buttonAltro.setOnClickListener { startTracking("Attività non sportive") }
        buttonAuto.setOnClickListener { startTracking("Auto") }
        buttonCamminata.setOnClickListener { startTracking("Camminata") }
        buttonCorsa.setOnClickListener { startTracking("Corsa") }
        buttonBici.setOnClickListener { startTracking("Bici") }
        buttonSport.setOnClickListener { startTracking("Altri Sport") }

        stopButton.setOnClickListener {
            stopTracking()
        }

        val buttonStats: Button =findViewById(R.id.button_statistiche)
        buttonStats.setOnClickListener {
            val intent= Intent(this,StatsActivity::class.java)
            startActivity(intent)
        }


        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensoreContaPassi = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        checkPermessiLocation()


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                CODICE_RICHIESTA_ACTIVITY_RECOGNITION
            )
        } else {
            startContaPassi()
        }

        intent?.let { receivedIntent ->
            if (receivedIntent.action == "START_TRACKING") {
                val nomeAttivita = receivedIntent.getStringExtra("nomeAttivita")
                nomeAttivita?.let {
                    startTracking(it)
                }
                intent.action = null
            }
        }
    }

    private fun checkPermessiLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                CODICE_RICHIESTA_LOCATION
            )
        } else {
            startBackgroundActivity()
        }
    }

    private fun startBackgroundActivity() {
        val backgroundIntent = Intent(this, BackgroundActivity::class.java)
        ContextCompat.startForegroundService(this, backgroundIntent)
    }


    private fun startContaPassi() {
        if (sensoreContaPassi != null) {
            sensorManager.registerListener(this, sensoreContaPassi, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }


    override fun onRequestPermissionsResult(
        codiceRichiesta: Int,
        permessi: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(codiceRichiesta, permessi, grantResults)
        if (codiceRichiesta == CODICE_RICHIESTA_ACTIVITY_RECOGNITION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startContaPassi()
        }
        super.onRequestPermissionsResult(codiceRichiesta, permessi, grantResults)
        if (codiceRichiesta == CODICE_RICHIESTA_LOCATION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startBackgroundActivity()
        }
    }



    private fun startTracking(nomeAttivita: String) {
        if (!tracking) {
            tracking = true
            attivitaCorrente = nomeAttivita
            attivitaSelezionata.text = nomeAttivita
            timer = 0
            dataInizio = Date()
            aggiornaTimer()
            handler.post(runnable)
            stopButton.isEnabled = true
            stopButton.visibility = View.VISIBLE

            passi = 0

            if (nomeAttivita == "Camminata" || nomeAttivita == "Corsa") {
                testoPassi.visibility = View.VISIBLE
                passiIniziali = 0
            } else {
                testoPassi.visibility = View.GONE
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
                sensorManager.registerListener(this, sensoreContaPassi, SensorManager.SENSOR_DELAY_FASTEST)
            }
        }
    }



    private fun stopTracking() {
        if (tracking) {
            tracking = false
            handler.removeCallbacks(runnable)
            sensorManager.unregisterListener(this)

            val dataFine = Date()

            val passiFinali = if (attivitaCorrente == "Camminata" || attivitaCorrente == "Corsa") passiSessione else 0

            val attivita = Attivita(
                nome = attivitaCorrente,
                dataInizio = dataInizio!!,
                dataFine = dataFine,
                passi = passiFinali
            )

            CoroutineScope(Dispatchers.IO).launch {
                attivitaDao.insert(attivita)
            }

            attivitaSelezionata.text = "Nessuna attività in corso"
            testoTimer.text = ""
            stopButton.isEnabled = false
            stopButton.visibility = View.GONE
            testoPassi.visibility = View.GONE
        }
    }



    private fun aggiornaTimer() {
        val ore = timer / 3600
        val minuti = (timer % 3600) / 60
        val secondi = timer % 60
        testoTimer.text = String.format("Tempo: %02d:%02d:%02d", ore, minuti, secondi)
    }

    private val runnable = object : Runnable {
        override fun run() {
            if (tracking) {
                timer++
                aggiornaTimer()
                handler.postDelayed(this, 1000)
            }
        }
    }



    override fun onSensorChanged(event: SensorEvent?) {
        if (tracking && event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val contaPassiAttuale = event.values[0].toInt()

            if (passiIniziali == 0) {
                passiIniziali = contaPassiAttuale
            }

            passiSessione = contaPassiAttuale - passiIniziali

            testoPassi.text = "Passi: $passiSessione"
        }
    }



    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}