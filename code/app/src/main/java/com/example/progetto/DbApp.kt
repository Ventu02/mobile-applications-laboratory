package com.example.progetto

import android.app.Application
import java.util.Date

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Database

import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(tableName = "attivita")
data class Attivita(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,
    val dataInizio: Date,
    val dataFine: Date?,
    val passi: Int? = null
)



@Database(entities = [Attivita::class], version = 2)
@TypeConverters(Convertitore::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun attivitaDao(): AttivitaDao
}



@Dao
interface AttivitaDao {
    @Insert
    suspend fun insert(attivita: Attivita)

    @Query("""
    SELECT * FROM Attivita 
    WHERE strftime('%Y-%m', dataInizio / 1000, 'unixepoch') = strftime('%Y-%m', 'now')
""")
    fun getAttivitaMeseCorrente(): List<Attivita>
}



class DbApp : Application() {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
}



class Convertitore {
    @TypeConverter
    fun daLongADate(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun daDateALong(date: Date?): Long? {
        return date?.time
    }
}