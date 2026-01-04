package com.example.progetto

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import kotlin.time.DurationUnit
import kotlin.time.toDuration


data class ActivityItem(val nome: String, val data: String, val durata: String, val passi: Int)

class StatsActivity : AppCompatActivity() {

    private lateinit var pieChart: PieChart
    private lateinit var lineChart: LineChart
    private lateinit var recyclerView: RecyclerView
    private lateinit var attivitaDao: AttivitaDao
    private lateinit var spinnerAttivita: Spinner

    private var listaAttivita = listOf<ActivityItem>()
    private val tipoAttivita = listOf("Tutte", "Attività non sportive", "Auto", "Camminata", "Corsa", "Bici", "Altri Sport")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        val database = (application as DbApp).database
        attivitaDao = database.attivitaDao()

        pieChart = findViewById(R.id.pieChart)
        lineChart = findViewById(R.id.lineChart)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        spinnerAttivita = findViewById(R.id.filterSpinner)
        setupSpinner()

        caricaDati()
    }

    private fun caricaDati() {
        CoroutineScope(Dispatchers.IO).launch {
            val activities = attivitaDao.getAttivitaMeseCorrente()

            listaAttivita = activities.mapNotNull { attivita ->
                val nome = attivita.nome ?: return@mapNotNull null
                val dataInizio = attivita.dataInizio
                val dataFine = attivita.dataFine

                val durata = calcolaDurata(dataInizio, dataFine)
                val passi = attivita.passi ?: 0

                ActivityItem(
                    nome = nome,
                    data = dataInizio?.toString() ?: return@mapNotNull null,
                    durata = durata,
                    passi = passi
                )
            }.reversed()

            withContext(Dispatchers.Main) {
                if (listaAttivita.isNotEmpty()) {
                    setupPieChart()
                    setupLineChart()
                    recyclerView.adapter = adapterAttivita(listaAttivita)
                }
            }
        }
    }



    private fun setupPieChart() {
        CoroutineScope(Dispatchers.IO).launch {
            val attivita = attivitaDao.getAttivitaMeseCorrente()

            val activityMap = attivita.groupBy { it.nome ?: "" }.mapValues { it.value.size }

            val entries = activityMap.map { PieEntry(it.value.toFloat(), it.key) }

            val dataSet = PieDataSet(entries, "Attività Mensili")
            dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
            dataSet.valueTextColor = android.graphics.Color.BLACK
            pieChart.setEntryLabelColor(android.graphics.Color.BLACK)
            dataSet.sliceSpace = 3f

            dataSet.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }

            val pieData = PieData(dataSet)

            withContext(Dispatchers.Main) {
                pieChart.data = pieData
                pieChart.description.isEnabled = false
                pieChart.centerText = "Attività Mensili"
                pieChart.animateY(1000)
                pieChart.invalidate()
            }
        }
    }




    private fun setupLineChart() {
        CoroutineScope(Dispatchers.IO).launch {
            val attivita = attivitaDao.getAttivitaMeseCorrente().filter {
                it.nome == "Camminata" || it.nome == "Corsa"
            }

            val entries = mutableListOf<Entry>()

            val calendario = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val giorniMese = calendario.getActualMaximum(Calendar.DAY_OF_MONTH)

            val passiGiornalieri = mutableMapOf<Int, Int>()

            attivita.forEach { activity ->
                val giorno = calendario.apply { timeInMillis = activity.dataInizio?.time ?: 0 }.get(Calendar.DAY_OF_MONTH)
                passiGiornalieri[giorno] = passiGiornalieri.getOrDefault(giorno, 0) + (activity.passi ?: 0)
            }

            for (day in 1..giorniMese) {
                val passiTotali = passiGiornalieri[day] ?: 0
                entries.add(Entry(day.toFloat(), passiTotali.toFloat()))
            }

            withContext(Dispatchers.Main) {
                val lineDataSet = LineDataSet(entries, "Passi Giornalieri")
                lineDataSet.color = android.graphics.Color.BLACK
                lineDataSet.valueTextColor = android.graphics.Color.BLACK
                lineDataSet.circleRadius = 6f

                val lineData = LineData(lineDataSet)

                lineChart.data = lineData
                lineChart.description.isEnabled = false
                lineChart.xAxis.apply {
                    labelCount = giorniMese
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    valueFormatter = IndexAxisValueFormatter((1..giorniMese).map { it.toString() })
                }
                lineChart.axisRight.isEnabled = false
                lineChart.animateX(1000)
                lineChart.invalidate()
            }
        }
    }



    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tipoAttivita)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAttivita.adapter = adapter

        spinnerAttivita.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                filtraAttivita(tipoAttivita[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun filtraAttivita(type: String) {
        val attivitaFiltrata = when (type) {
            "Tutte" -> listaAttivita
            else -> listaAttivita.filter { it.nome == type }
        }

        recyclerView.adapter = adapterAttivita(attivitaFiltrata)
    }

    fun calcolaDurata(dataInizio: Date?, dataFine: Date?): String {
        if (dataInizio == null || dataFine == null || dataInizio >= dataFine) {
            return "Durata non disponibile"
        }

        val durata = (dataFine.time - dataInizio.time).toDuration(DurationUnit.MILLISECONDS)

        val hours = durata.inWholeHours
        val minutes = (durata - hours.toDuration(DurationUnit.HOURS)).inWholeMinutes

        return String.format("%02d:%02d", hours, minutes)
    }

    inner class adapterAttivita(private val activities: List<ActivityItem>) :
        RecyclerView.Adapter<adapterAttivita.ActivityViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_activity, parent, false)
            return ActivityViewHolder(view)
        }

        override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
            val activity = activities[position]
            holder.bind(activity)
        }

        override fun getItemCount(): Int = activities.size

        inner class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nome: TextView = itemView.findViewById(R.id.ActivityName)
            private val data: TextView = itemView.findViewById(R.id.ActivityDate)
            private val durata: TextView = itemView.findViewById(R.id.ActivityDuration)
            private val passi: TextView = itemView.findViewById(R.id.ActivitySteps)

            fun bind(activity: ActivityItem) {
                nome.text = activity.nome
                data.text = activity.data
                durata.text = activity.durata
                passi.text = if (activity.passi > 0) "${activity.passi} passi" else "-"
            }
        }
    }
}