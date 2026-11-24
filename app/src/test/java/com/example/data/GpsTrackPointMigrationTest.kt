package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.GpsTrackPoint
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GpsTrackPointMigrationTest {

    @Test
    fun testMigration16to17_preservesRunningSessionAndEnablesGpsTrackPoints() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbFile = context.getDatabasePath("test_migration_16_17.db")
            if (dbFile.exists()) {
                dbFile.delete()
            }

            // a) Cria DB na versão 16 baseado no schema oficial 16.json
            val schemaFile = File("app/schemas/com.example.data.database.AppDatabase/16.json").takeIf { it.exists() }
                ?: File("schemas/com.example.data.database.AppDatabase/16.json")
            assertTrue("Arquivo de schema v16 deve existir", schemaFile.exists())

            val helperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.name)
                .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val json = JSONObject(schemaFile.readText())
                        val databaseObj = json.getJSONObject("database")
                        val entities = databaseObj.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val entity = entities.getJSONObject(i)
                            val tableName = entity.getString("tableName")
                            val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
                            db.execSQL(createSql)
                            val indices = entity.optJSONArray("indices")
                            if (indices != null) {
                                for (j in 0 until indices.length()) {
                                    val idx = indices.getJSONObject(j)
                                    val idxSql = idx.optString("createSql")
                                    if (!idxSql.isNullOrEmpty()) {
                                        db.execSQL(idxSql.replace("\${TABLE_NAME}", tableName))
                                    }
                                }
                            }
                        }
                        val setupQueries = databaseObj.optJSONArray("setupQueries")
                        if (setupQueries != null) {
                            for (i in 0 until setupQueries.length()) {
                                db.execSQL(setupQueries.getString(i))
                            }
                        }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()

            val openHelper = FrameworkSQLiteOpenHelperFactory().create(helperConfig)
            val dbV16 = openHelper.writableDatabase

            // Popula running_sessions com 1 linha dummy
            dbV16.execSQL("""
                INSERT INTO `running_sessions` (
                    `id`, `date`, `distanceMeters`, `durationSec`, `avgPaceSecPerKm`,
                    `notes`, `rpe`, `weekNumber`, `dayNumber`, `sessionType`, `environment`,
                    `painResponse`, `isGentleMode`, `shoeName`, `avgHr`, `maxHr`
                ) VALUES (
                    'session_dummy_16', 1710000000000, 5000, 1800, 360,
                    'Treino de teste v16', 7, 2, 2, 'VOLUME', 'OUTDOOR',
                    'SEM_DOR', 0, 'Nike Pegasus 40', 155, 178
                )
            """.trimIndent())

            // b) Roda MIGRATION_16_17
            AppDatabase.MIGRATION_16_17.migrate(dbV16)

            // c) Verifica que a linha dummy continua lá (dados preservados)
            val cursor = dbV16.query("SELECT id, distanceMeters, shoeName, avgHr, maxHr FROM running_sessions WHERE id = 'session_dummy_16'")
            assertTrue("Linha dummy de running_sessions deve existir após a migração", cursor.moveToFirst())
            assertEquals("session_dummy_16", cursor.getString(0))
            assertEquals(5000, cursor.getInt(1))
            assertEquals("Nike Pegasus 40", cursor.getString(2))
            assertEquals(155, cursor.getInt(3))
            assertEquals(178, cursor.getInt(4))
            cursor.close()

            // Fecha a conexão SQLite direta antes de abrir pelo Room
            dbV16.close()
            openHelper.close()

            // d) Abre via Room v17, insere 3 pontos GPS via dao e confirma leitura ordenada
            val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.name)
                .addMigrations(
                    AppDatabase.MIGRATION_6_7,
                    AppDatabase.MIGRATION_7_8,
                    AppDatabase.MIGRATION_8_9,
                    AppDatabase.MIGRATION_9_10,
                    AppDatabase.MIGRATION_10_11,
                    AppDatabase.MIGRATION_11_12,
                    AppDatabase.MIGRATION_12_13,
                    AppDatabase.MIGRATION_13_14,
                    AppDatabase.MIGRATION_14_15,
                    AppDatabase.MIGRATION_15_16,
                    AppDatabase.MIGRATION_16_17,
                    AppDatabase.MIGRATION_17_18,
                    AppDatabase.MIGRATION_18_19,
                    AppDatabase.MIGRATION_19_20,
                    AppDatabase.MIGRATION_20_21,
                    AppDatabase.MIGRATION_21_22,
                    AppDatabase.MIGRATION_22_23
                )
                .allowMainThreadQueries()
                .build()

            val dao = roomDb.gpsTrackPointDao()
            assertNotNull("GpsTrackPointDao deve ser instanciado", dao)

            val point1 = GpsTrackPoint(
                sessionId = "session_dummy_16",
                timestampMs = 1000L,
                capturedAtMs = 1002L,
                latitude = -23.550520,
                longitude = -46.633308,
                accuracyM = 4.5f,
                speedMps = 3.2f,
                bearingDeg = 90f,
                altitudeM = 750.0
            )
            val point2 = GpsTrackPoint(
                sessionId = "session_dummy_16",
                timestampMs = 2000L,
                capturedAtMs = 2003L,
                latitude = -23.550600,
                longitude = -46.633400,
                accuracyM = 4.0f,
                speedMps = 3.4f,
                bearingDeg = 92f,
                altitudeM = 751.0
            )
            val point3 = GpsTrackPoint(
                sessionId = "session_dummy_16",
                timestampMs = 3000L,
                capturedAtMs = 3002L,
                latitude = -23.550700,
                longitude = -46.633500,
                accuracyM = 3.8f,
                speedMps = 3.6f,
                bearingDeg = 95f,
                altitudeM = 752.0
            )

            // Inserir propositalmente fora de ordem para validar ORDER BY timestampMs ASC
            dao.insert(point2)
            dao.insert(point3)
            dao.insert(point1)

            val points = dao.getForSessionOnce("session_dummy_16")
            assertEquals(3, points.size)
            assertEquals(1000L, points[0].timestampMs)
            assertEquals(2000L, points[1].timestampMs)
            assertEquals(3000L, points[2].timestampMs)
            assertEquals(-23.550520, points[0].latitude, 0.000001)

            val count = dao.countForSession("session_dummy_16")
            assertEquals(3, count)

            // Testar exclusão por sessão
            dao.deleteForSession("session_dummy_16")
            val countAfterDelete = dao.countForSession("session_dummy_16")
            assertEquals(0, countAfterDelete)

            roomDb.close()
            dbFile.delete()
        }
    }

    @Test
    fun testMigration22to23_addsProfileCalibrationColumnsAndBackfillsOnlyEsnaider() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbFile = context.getDatabasePath("test_migration_22_23.db")
            if (dbFile.exists()) {
                dbFile.delete()
            }

            val schemaFile = File("app/schemas/com.example.data.database.AppDatabase/22.json").takeIf { it.exists() }
                ?: File("schemas/com.example.data.database.AppDatabase/22.json")
            assertTrue("Arquivo de schema v22 deve existir", schemaFile.exists())

            val helperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.name)
                .callback(object : SupportSQLiteOpenHelper.Callback(22) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val json = JSONObject(schemaFile.readText())
                        val databaseObj = json.getJSONObject("database")
                        val entities = databaseObj.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val entity = entities.getJSONObject(i)
                            val tableName = entity.getString("tableName")
                            val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
                            db.execSQL(createSql)
                            val indices = entity.optJSONArray("indices")
                            if (indices != null) {
                                for (j in 0 until indices.length()) {
                                    val idx = indices.getJSONObject(j)
                                    val idxSql = idx.optString("createSql")
                                    if (!idxSql.isNullOrEmpty()) {
                                        db.execSQL(idxSql.replace("\${TABLE_NAME}", tableName))
                                    }
                                }
                            }
                        }
                        val setupQueries = databaseObj.optJSONArray("setupQueries")
                        if (setupQueries != null) {
                            for (i in 0 until setupQueries.length()) {
                                db.execSQL(setupQueries.getString(i))
                            }
                        }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()

            val openHelper = FrameworkSQLiteOpenHelperFactory().create(helperConfig)
            val dbV22 = openHelper.writableDatabase

            dbV22.execSQL(
                """
                INSERT INTO `user_profile` (
                    `id`, `nome`, `sexo`, `idade`, `altura`, `peso`, `nivelExperiencia`, `localTreino`,
                    `foto`, `massaMagra`, `percentualGordura`, `metaSemanal`, `iconStyle`, `imc`,
                    `taxaMuscular`, `gorduraSubcutanea`, `gorduraVisceral`, `aguaCorporal`,
                    `massaMuscularEsqueletica`, `massaMuscular`, `massaOssea`, `proteina`, `tmb`,
                    `idadeMetabolica`, `bioimpedanciaHistoricoJson`, `heightCm`, `weightKg`,
                    `runningGoal`, `baselinePaceMinPerKm`
                ) VALUES
                    ('1', 'Esnaider', 'Masculino', 50, 187, 84.7, 'Avançado', 'Academia',
                     '', 65.2, 23.0, 5, 'A', 24.5, 73.0, 16.5, 11.0, 52.8, 40.9, 61.8, 3.4, 20.2,
                     1778, 49, '[]', 187, 84.7, 'CONTINUOUS_5K', 7.0),
                    ('2', 'Alyne', 'Feminino', 42, 155, 72.0, 'Avançado', 'Academia',
                     '', 54.0, 25.0, 4, 'A', 29.9, 46.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                     1450, 35, '[]', 155, 72.0, 'CONTINUOUS_5K', NULL)
                """.trimIndent()
            )
            dbV22.close()
            openHelper.close()

            val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.name)
                .addMigrations(AppDatabase.MIGRATION_22_23)
                .allowMainThreadQueries()
                .build()

            val esnaider = roomDb.userDao().getUserProfileSync("1")
            assertNotNull(esnaider)
            assertEquals("Esnaider", esnaider!!.nome)
            assertEquals(190, esnaider.fcMaxCalibradaBpm)
            assertEquals(137, esnaider.limiarLt1Bpm)
            assertEquals(162, esnaider.limiarLt2Bpm)
            assertEquals(18.0f, esnaider.metaPercentualGordura!!, 0.01f)
            assertEquals(74.0f, esnaider.metaPercentualMusculo!!, 0.01f)
            assertEquals(4.2f, esnaider.metaAguaLitros!!, 0.01f)

            val alyne = roomDb.userDao().getUserProfileSync("2")
            assertNotNull(alyne)
            assertEquals("Alyne", alyne!!.nome)
            assertNull(alyne.fcMaxCalibradaBpm)
            assertNull(alyne.limiarLt1Bpm)
            assertNull(alyne.limiarLt2Bpm)
            assertNull(alyne.metaPercentualGordura)
            assertNull(alyne.metaPercentualMusculo)
            assertNull(alyne.metaAguaLitros)

            roomDb.close()
            dbFile.delete()
        }
    }
}
