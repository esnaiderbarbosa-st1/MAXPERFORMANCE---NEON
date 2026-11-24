package com.example.data.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AppUser
import com.example.data.model.AccessRequest
import com.example.data.model.AuditLog
import com.example.data.model.CardioSession
import com.example.data.model.ChatMessage
import com.example.data.model.Classroom
import com.example.data.model.CustomExerciseEntity
import com.example.data.model.FeedItem
import com.example.data.model.PendingSyncAction
import com.example.data.model.UserProfileData
import com.example.data.model.Workout
import com.example.data.model.GymProfile
import com.example.data.model.WorkoutHistoryEntry
import com.example.data.model.TelemetryEventEntity
import com.example.data.model.CrashReportEntity
import com.example.data.model.BetaEvaluationEntity
import com.example.data.model.WorkoutSessionEntity
import com.example.data.model.RunningSession
import com.example.data.model.PreRunCheckIn
import com.example.data.model.SessionExecutionAudit
import com.example.data.model.MetricProvenance
import com.example.data.model.ExecutedInterval
import com.example.data.model.AdvancedRunningMetrics
import com.example.data.model.toJsonString
import com.example.data.model.toPreRunCheckIn
import com.example.data.model.toSessionExecutionAudit
import com.example.data.model.toMetricProvenance
import com.example.data.model.toExecutedIntervalsJsonString
import com.example.data.model.toExecutedIntervals
import com.example.data.model.toAdvancedRunningMetrics
import com.example.data.model.GpsTrackPoint
import com.example.data.dao.GpsTrackPointDao
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

// --- TYPE CONVERTERS ---
class Converters {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        if (value == null) return null
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        return moshi.adapter<List<String>>(type).toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        return moshi.adapter<List<String>>(type).fromJson(value)
    }

    @TypeConverter
    fun fromPreRunCheckIn(value: PreRunCheckIn?): String? = value?.toJsonString()

    @TypeConverter
    fun toPreRunCheckInConverter(value: String?): PreRunCheckIn? = value.toPreRunCheckIn()

    @TypeConverter
    fun fromSessionExecutionAudit(value: SessionExecutionAudit?): String? = value?.toJsonString()

    @TypeConverter
    fun toSessionExecutionAuditConverter(value: String?): SessionExecutionAudit? = value.toSessionExecutionAudit()

    @TypeConverter
    fun fromMetricProvenance(value: MetricProvenance?): String? = value?.toJsonString()

    @TypeConverter
    fun toMetricProvenanceConverter(value: String?): MetricProvenance? = value.toMetricProvenance()

    @TypeConverter
    fun fromExecutedIntervals(value: List<ExecutedInterval>?): String? = value?.toExecutedIntervalsJsonString()

    @TypeConverter
    fun toExecutedIntervalsConverter(value: String?): List<ExecutedInterval> = value.toExecutedIntervals()

    @TypeConverter
    fun fromAdvancedRunningMetrics(value: AdvancedRunningMetrics?): String? = value?.toJsonString()

    @TypeConverter
    fun toAdvancedRunningMetricsConverter(value: String?): AdvancedRunningMetrics? = value.toAdvancedRunningMetrics()
}

// --- DAOs ---

@Dao
interface UserDao {
    @Query("SELECT * FROM user_profile WHERE id = :id LIMIT 1")
    fun getUserProfile(id: String = "1"): Flow<UserProfileData?>

    @Query("SELECT * FROM user_profile WHERE id = :id LIMIT 1")
    suspend fun getUserProfileSync(id: String = "1"): UserProfileData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileData)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY createdAt DESC")
    fun getAllWorkouts(): Flow<List<Workout>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: Workout)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkoutById(id: String)

    @Query("DELETE FROM workouts")
    suspend fun clearAllWorkouts()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM workout_history ORDER BY date DESC")
    fun getAllHistory(): Flow<List<WorkoutHistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntry(entry: WorkoutHistoryEntry)

    @Query("DELETE FROM workout_history WHERE id = :id")
    suspend fun deleteHistoryById(id: String)

    @Query("DELETE FROM workout_history")
    suspend fun clearAllHistory()
}

@Dao
interface CustomExerciseDao {
    @Query("SELECT * FROM custom_exercises ORDER BY name ASC")
    fun getAllCustomExercises(): Flow<List<CustomExerciseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomExercise(exercise: CustomExerciseEntity)
}

@Dao
interface CardioDao {
    @Query("SELECT * FROM cardio_sessions ORDER BY date DESC")
    fun getAllCardioSessions(): Flow<List<CardioSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCardio(cardio: CardioSession)

    @Query("DELETE FROM cardio_sessions WHERE id = :id")
    suspend fun deleteCardioById(id: String)

    @Query("DELETE FROM cardio_sessions")
    suspend fun clearAllCardio()
}

@Dao
interface ClassroomDao {
    @Query("SELECT * FROM classrooms")
    fun getAllClassrooms(): Flow<List<Classroom>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassroom(classroom: Classroom)

    @Query("DELETE FROM classrooms WHERE id = :id")
    suspend fun deleteClassroomById(id: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE classroomId = :classroomId ORDER BY createdAt ASC")
    fun getMessagesForClassroom(classroomId: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)
}

@Dao
interface FeedDao {
    @Query("SELECT * FROM feed_items ORDER BY createdAt DESC")
    fun getFeedItems(): Flow<List<FeedItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedItem(item: FeedItem)
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM pending_sync_actions ORDER BY timestamp ASC")
    fun getPendingActionsFlow(): Flow<List<PendingSyncAction>>

    @Query("SELECT * FROM pending_sync_actions ORDER BY timestamp ASC")
    suspend fun getPendingActionsList(): List<PendingSyncAction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: PendingSyncAction)

    @Query("DELETE FROM pending_sync_actions WHERE id = :id")
    suspend fun deleteActionById(id: String)

    @Query("DELETE FROM pending_sync_actions")
    suspend fun clearActions()
}

@Dao
interface AccessControlDao {
    @Query("SELECT * FROM app_users ORDER BY createdAt DESC")
    fun getAllUsersFlow(): Flow<List<AppUser>>

    @Query("SELECT * FROM app_users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): AppUser?

    @Query("SELECT * FROM app_users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): AppUser?

    @Query("SELECT * FROM app_users WHERE cpf = :cpf LIMIT 1")
    suspend fun getUserByCpf(cpf: String): AppUser?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: AppUser)

    @Delete
    suspend fun deleteUser(user: AppUser)

    @Query("SELECT * FROM access_requests ORDER BY createdAt DESC")
    fun getAllRequestsFlow(): Flow<List<AccessRequest>>

    @Query("SELECT * FROM access_requests WHERE id = :id LIMIT 1")
    suspend fun getRequestById(id: String): AccessRequest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: AccessRequest)

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogsFlow(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLog)
}

@Dao
interface TelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: TelemetryEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrash(crash: CrashReportEntity)

    @Query("DELETE FROM telemetry_events WHERE id = :id")
    suspend fun deleteEventById(id: String)

    @Query("DELETE FROM telemetry_events WHERE timestamp < :timestamp")
    suspend fun deleteEventsOlderThan(timestamp: Long)

    @Query("DELETE FROM crash_reports WHERE timestamp < :timestamp")
    suspend fun deleteCrashesOlderThan(timestamp: Long)

    @Query("SELECT * FROM telemetry_events ORDER BY timestamp DESC")
    fun getAllEventsFlow(): Flow<List<TelemetryEventEntity>>

    @Query("SELECT * FROM telemetry_events ORDER BY timestamp DESC")
    suspend fun getAllEvents(): List<TelemetryEventEntity>

    @Query("SELECT * FROM telemetry_events WHERE userId = :userId ORDER BY timestamp DESC")
    fun getEventsByUser(userId: String): Flow<List<TelemetryEventEntity>>

    @Query("SELECT * FROM telemetry_events WHERE eventType = :eventType ORDER BY timestamp DESC")
    fun getEventsByType(eventType: String): Flow<List<TelemetryEventEntity>>

    @Query("SELECT * FROM telemetry_events WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedEvents(): List<TelemetryEventEntity>

    @Query("UPDATE telemetry_events SET synced = 1 WHERE id IN (:ids)")
    suspend fun markEventsAsSynced(ids: List<String>)

    @Query("SELECT COUNT(*) FROM telemetry_events")
    fun getEventCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM telemetry_events")
    suspend fun getEventCount(): Int

    @Query("SELECT COUNT(*) FROM crash_reports")
    fun getCrashCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM crash_reports")
    suspend fun getCrashCount(): Int

    @Query("SELECT * FROM crash_reports ORDER BY timestamp DESC")
    fun getAllCrashesFlow(): Flow<List<CrashReportEntity>>

    @Query("SELECT * FROM crash_reports ORDER BY timestamp DESC")
    suspend fun getAllCrashes(): List<CrashReportEntity>
}

@Dao
interface GymProfileDao {
    @Query("SELECT * FROM gym_profiles ORDER BY name ASC")
    fun getAllGymProfiles(): Flow<List<GymProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGymProfile(profile: GymProfile)

    @Query("DELETE FROM gym_profiles WHERE id = :id")
    suspend fun deleteGymProfileById(id: String)
}

@Dao
interface BetaLabDao {
    @Query("SELECT * FROM beta_evaluations ORDER BY timestamp DESC")
    fun getAllEvaluations(): Flow<List<BetaEvaluationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvaluation(evaluation: BetaEvaluationEntity)

    @Query("DELETE FROM beta_evaluations WHERE id = :id")
    suspend fun deleteEvaluationById(id: String)

    @Query("DELETE FROM beta_evaluations")
    suspend fun clearAllEvaluations()
}

// --- DATABASE CLASS ---

@Database(
    entities = [
        UserProfileData::class,
        CustomExerciseEntity::class,
        Workout::class,
        WorkoutHistoryEntry::class,
        CardioSession::class,
        Classroom::class,
        ChatMessage::class,
        FeedItem::class,
        PendingSyncAction::class,
        AppUser::class,
        AccessRequest::class,
        AuditLog::class,
        TelemetryEventEntity::class,
        CrashReportEntity::class,
        GymProfile::class,
        BetaEvaluationEntity::class,
        WorkoutSessionEntity::class,
        RunningSession::class,
        GpsTrackPoint::class
    ],
    version = 23,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun historyDao(): HistoryDao
    abstract fun customExerciseDao(): CustomExerciseDao
    abstract fun cardioDao(): CardioDao
    abstract fun classroomDao(): ClassroomDao
    abstract fun chatDao(): ChatDao
    abstract fun feedDao(): FeedDao
    abstract fun syncDao(): SyncDao
    abstract fun accessControlDao(): AccessControlDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun gymProfileDao(): GymProfileDao
    abstract fun betaLabDao(): BetaLabDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun runningSessionDao(): RunningSessionDao
    abstract fun gpsTrackPointDao(): GpsTrackPointDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_6 = object : Migration(1, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("MAX_PERFORMANCE_DEBUG", "Migrating legacy schema 1 to 6 safely")
            }
        }
        val MIGRATION_2_6 = object : Migration(2, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("MAX_PERFORMANCE_DEBUG", "Migrating legacy schema 2 to 6 safely")
            }
        }
        val MIGRATION_3_6 = object : Migration(3, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("MAX_PERFORMANCE_DEBUG", "Migrating legacy schema 3 to 6 safely")
            }
        }
        val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("MAX_PERFORMANCE_DEBUG", "Migrating legacy schema 4 to 6 safely")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("MAX_PERFORMANCE_DEBUG", "Migrating legacy schema 5 to 6 safely")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `beta_evaluations` (
                        `id` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `appVersion` TEXT NOT NULL,
                        `buildNumber` TEXT NOT NULL,
                        `buildDate` TEXT NOT NULL,
                        `workoutType` TEXT NOT NULL,
                        `sessionTimeMinutes` TEXT NOT NULL,
                        `ratingExCount` INTEGER NOT NULL,
                        `ratingOrderQuality` INTEGER NOT NULL,
                        `ratingVariety` INTEGER NOT NULL,
                        `ratingLoadProgression` INTEGER NOT NULL,
                        `ratingEstimatedTime` INTEGER NOT NULL,
                        `ratingRecommendation` INTEGER NOT NULL,
                        `ratingEaseOfUse` INTEGER NOT NULL,
                        `ratingOverallQuality` INTEGER NOT NULL,
                        `checkLoadProgression` INTEGER NOT NULL,
                        `checkImages` INTEGER NOT NULL,
                        `checkGifs` INTEGER NOT NULL,
                        `checkTimer` INTEGER NOT NULL,
                        `checkSound` INTEGER NOT NULL,
                        `checkFullNames` INTEGER NOT NULL,
                        `checkCorrectExercises` INTEGER NOT NULL,
                        `checkWarmup` INTEGER NOT NULL,
                        `checkDragDrop` INTEGER NOT NULL,
                        `checkReplacement` INTEGER NOT NULL,
                        `checkTodaySessionScreen` INTEGER NOT NULL,
                        `checkExecutionScreen` INTEGER NOT NULL,
                        `checkFinalScreen` INTEGER NOT NULL,
                        `bugsText` TEXT NOT NULL,
                        `suggestionsText` TEXT NOT NULL,
                        `priority` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `workout_sessions` (
                        `id` TEXT NOT NULL,
                        `workoutId` TEXT NOT NULL,
                        `workoutName` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER,
                        `durationSeconds` INTEGER NOT NULL,
                        `exercisesJson` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `monotonicStartTimeNanos` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `startTimeMonotonic` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `completedAt` INTEGER")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `heightCm` INTEGER")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `weightKg` REAL")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `runningGoal` TEXT NOT NULL DEFAULT 'CONTINUOUS_5K'")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `running_sessions` (
                        `id` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `weekNumber` INTEGER NOT NULL,
                        `sessionType` TEXT NOT NULL,
                        `durationSec` INTEGER NOT NULL,
                        `distanceMeters` INTEGER NOT NULL,
                        `avgPaceSecPerKm` INTEGER NOT NULL,
                        `rpe` INTEGER NOT NULL,
                        `notes` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `environment` TEXT NOT NULL DEFAULT 'OUTDOOR'")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `baselinePaceMinPerKm` REAL")
                db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `painResponse` TEXT")
                db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `isGentleMode` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `shoeName` TEXT")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `dayNumber` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(running_sessions)")
                var hasAvgHr = false
                var hasMaxHr = false
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        val colName = cursor.getString(nameIndex)
                        if (colName.equals("avgHr", ignoreCase = true)) hasAvgHr = true
                        if (colName.equals("maxHr", ignoreCase = true)) hasMaxHr = true
                    }
                }
                cursor.close()

                if (!hasAvgHr) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `avgHr` INTEGER")
                }
                if (!hasMaxHr) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `maxHr` INTEGER")
                }
            }
        }

        // MIGRATION_16_17: Cria tabela gps_track_points para histórico de telemetria GPS
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("MAX_PERFORMANCE_DEBUG", "Applying MIGRATION_16_17: creating gps_track_points table")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `gps_track_points` (
                        `sessionId` TEXT NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `capturedAtMs` INTEGER NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `accuracyM` REAL,
                        `speedMps` REAL,
                        `bearingDeg` REAL,
                        `altitudeM` REAL,
                        PRIMARY KEY(`sessionId`, `timestampMs`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_track_points_sessionId` ON `gps_track_points` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_track_points_sessionId_timestampMs` ON `gps_track_points` (`sessionId`, `timestampMs`)")
            }
        }

        // MIGRATION_17_18: Adiciona métricas isoladas de corrida principal, splits km a km e timeline BPM na tabela running_sessions
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("MAX_PERFORMANCE_DEBUG", "Applying MIGRATION_17_18: adding mainRun fields, kmSplitsJson and hrTimeline")
                val cursor = db.query("PRAGMA table_info(running_sessions)")
                val existingCols = mutableSetOf<String>()
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        existingCols.add(cursor.getString(nameIndex).lowercase())
                    }
                }
                cursor.close()

                if (!existingCols.contains("mainrundurationsec")) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `mainRunDurationSec` INTEGER")
                }
                if (!existingCols.contains("mainrundistancemeters")) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `mainRunDistanceMeters` INTEGER")
                }
                if (!existingCols.contains("mainrunavgpacesecperkm")) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `mainRunAvgPaceSecPerKm` INTEGER")
                }
                if (!existingCols.contains("kmsplitsjson")) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `kmSplitsJson` TEXT")
                }
                if (!existingCols.contains("hrtimeline")) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `hrTimeline` TEXT")
                }
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("MAX_PERFORMANCE_DEBUG", "Applying MIGRATION_18_19: adding routePointsJson")
                val cursor = db.query("PRAGMA table_info(running_sessions)")
                val existingCols = mutableSetOf<String>()
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        existingCols.add(cursor.getString(nameIndex).lowercase())
                    }
                }
                cursor.close()

                if (!existingCols.contains("routepointsjson")) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `routePointsJson` TEXT")
                }
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("MAX_PERFORMANCE_DEBUG", "Applying MIGRATION_19_20: adding aiCoachReport")
                val cursor = db.query("PRAGMA table_info(running_sessions)")
                val existingCols = mutableSetOf<String>()
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        existingCols.add(cursor.getString(nameIndex).lowercase())
                    }
                }
                cursor.close()

                if (!existingCols.contains("aicoachreport")) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `aiCoachReport` TEXT")
                }
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("MAX_PERFORMANCE_DEBUG", "Applying MIGRATION_20_21: adding Pacote 1 scientific columns to running_sessions")
                val cursor = db.query("PRAGMA table_info(running_sessions)")
                val existingCols = mutableSetOf<String>()
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        existingCols.add(cursor.getString(nameIndex).lowercase())
                    }
                }
                cursor.close()

                if (!existingCols.contains("preruncheckin")) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `preRunCheckIn` TEXT")
                }
                if (!existingCols.contains("rpecr10")) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `rpeCr10` INTEGER")
                }
                if (!existingCols.contains("srpeloadua")) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `sRpeLoadUa` INTEGER")
                }
                if (!existingCols.contains("executionaudit")) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `executionAudit` TEXT")
                }
                if (!existingCols.contains("hrprovenance")) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `hrProvenance` TEXT")
                }
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("MAX_PERFORMANCE_DEBUG", "Applying MIGRATION_21_22: adding Pacote 2 executedIntervals and advancedMetrics to running_sessions")
                val cursor = db.query("PRAGMA table_info(running_sessions)")
                val existingCols = mutableSetOf<String>()
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        existingCols.add(cursor.getString(nameIndex).lowercase())
                    }
                }
                cursor.close()

                if (!existingCols.contains("executedintervals")) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `executedIntervals` TEXT NOT NULL DEFAULT '[]'")
                }
                if (!existingCols.contains("advancedmetrics")) {
                    db.execSQL("ALTER TABLE `running_sessions` ADD COLUMN `advancedMetrics` TEXT")
                }
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("MAX_PERFORMANCE_DEBUG", "Applying MIGRATION_22_23: adding calibration and bioimpedance goal columns to user_profile")
                val cursor = db.query("PRAGMA table_info(user_profile)")
                val existingCols = mutableSetOf<String>()
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        existingCols.add(cursor.getString(nameIndex).lowercase())
                    }
                }
                cursor.close()

                if (!existingCols.contains("fcmaxcalibradabpm")) {
                    db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `fcMaxCalibradaBpm` INTEGER")
                }
                if (!existingCols.contains("limiarlt1bpm")) {
                    db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `limiarLt1Bpm` INTEGER")
                }
                if (!existingCols.contains("limiarlt2bpm")) {
                    db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `limiarLt2Bpm` INTEGER")
                }
                if (!existingCols.contains("metapercentualgordura")) {
                    db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `metaPercentualGordura` REAL")
                }
                if (!existingCols.contains("metapercentualmusculo")) {
                    db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `metaPercentualMusculo` REAL")
                }
                if (!existingCols.contains("metaagualitros")) {
                    db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `metaAguaLitros` REAL")
                }

                // Migração única de dados: preserva calibração fisiológica e metas do perfil Esnaider existente
                db.execSQL("UPDATE `user_profile` SET `fcMaxCalibradaBpm` = 190 WHERE `nome` LIKE '%Esnaider%' AND `fcMaxCalibradaBpm` IS NULL")
                db.execSQL("UPDATE `user_profile` SET `limiarLt1Bpm` = 137 WHERE `nome` LIKE '%Esnaider%' AND `limiarLt1Bpm` IS NULL")
                db.execSQL("UPDATE `user_profile` SET `limiarLt2Bpm` = 162 WHERE `nome` LIKE '%Esnaider%' AND `limiarLt2Bpm` IS NULL")
                db.execSQL("UPDATE `user_profile` SET `metaPercentualGordura` = 18.0 WHERE `nome` LIKE '%Esnaider%' AND `metaPercentualGordura` IS NULL")
                db.execSQL("UPDATE `user_profile` SET `metaPercentualMusculo` = 74.0 WHERE `nome` LIKE '%Esnaider%' AND `metaPercentualMusculo` IS NULL")
                db.execSQL("UPDATE `user_profile` SET `metaAguaLitros` = 4.2 WHERE `nome` LIKE '%Esnaider%' AND `metaAguaLitros` IS NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                android.util.Log.i("MAX_PERFORMANCE_DEBUG", "Database initialized with migrations.")
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "max_performance_db"
                )
                .addMigrations(
                    MIGRATION_1_6,
                    MIGRATION_2_6,
                    MIGRATION_3_6,
                    MIGRATION_4_6,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22,
                    MIGRATION_22_23
                )
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
