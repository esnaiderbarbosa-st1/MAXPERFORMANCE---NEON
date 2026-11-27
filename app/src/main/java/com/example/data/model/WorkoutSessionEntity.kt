package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class WorkoutSessionStatus(val rawValue: String) {
    ATIVO("ATIVO"),
    CONCLUIDO("CONCLUIDO"),
    DESCARTADO("DESCARTADO"),
    NAO_REALIZADO("NAO_REALIZADO");

    val isTerminal: Boolean
        get() = this != ATIVO

    fun canTransitionTo(target: WorkoutSessionStatus): Boolean {
        if (this.isTerminal) return false
        return when (target) {
            CONCLUIDO, NAO_REALIZADO, DESCARTADO -> true
            ATIVO -> false
        }
    }

    companion object {
        fun fromValue(value: String?): WorkoutSessionStatus {
            return when (value?.trim()?.uppercase()) {
                "ATIVO" -> ATIVO
                "CONCLUIDO", "CONCLUÍDO" -> CONCLUIDO
                "DESCARTADO" -> DESCARTADO
                "NAO_REALIZADO", "NÃO_REALIZADO" -> NAO_REALIZADO
                else -> ATIVO
            }
        }
    }
}

object WorkoutSessionStateMachine {
    fun isValidTransition(from: WorkoutSessionStatus, to: WorkoutSessionStatus): Boolean {
        if (from.isTerminal) {
            android.util.Log.e(
                "WorkoutSessionStateMachine",
                "Transição rejeitada: estado terminal '$from' não pode transicionar para '$to'"
            )
            return false
        }
        val allowed = from.canTransitionTo(to)
        if (!allowed) {
            android.util.Log.e(
                "WorkoutSessionStateMachine",
                "Transição de estado inválida: '$from' não pode transicionar para '$to'"
            )
        }
        return allowed
    }
}

object WorkoutSessionValidator {
    fun validateForPersist(session: WorkoutSessionEntity) {
        require(session.startTime > 0) { "startTime não pode ser nulo ou menor/igual a zero: ${session.startTime}" }
        require(session.version >= 1) { "version deve ser >= 1: ${session.version}" }
        val validStatus = WorkoutSessionStatus.values().any { it.rawValue == session.status }
        require(validStatus) { "Status inválido: '${session.status}'. Deve pertencer a: ATIVO, CONCLUIDO, DESCARTADO ou NAO_REALIZADO" }
    }

    fun validateExercises(exercises: List<WorkoutExercise>) {
        for (ex in exercises) {
            for (set in ex.sets) {
                require(set.weight >= 0f) { "Carga não pode ser negativa: ${set.weight}kg no exercício ${ex.name}" }
                require(set.reps >= 0) { "Repetições não podem ser negativas: ${set.reps} no exercício ${ex.name}" }
            }
        }
    }

    fun hasAtLeastOneRegisteredSet(exercises: List<WorkoutExercise>): Boolean {
        return exercises.any { ex ->
            ex.sets.any { set -> set.completed }
        }
    }
}

object WorkoutTimeUtils {
    private const val MAX_BOOT_EPOCH_DRIFT_MS = 120_000L

    fun readBootCount(context: android.content.Context): Int? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.provider.Settings.Global.getInt(
                    context.contentResolver,
                    android.provider.Settings.Global.BOOT_COUNT
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun hasDeviceRebooted(
        savedBootCount: Int?,
        currentBootCount: Int?,
        startTimeMonotonic: Long,
        fallbackStartMillis: Long,
        nowMonotonic: Long = android.os.SystemClock.elapsedRealtime(),
        nowWallMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (savedBootCount != null && currentBootCount != null && savedBootCount != currentBootCount) {
            return true
        }
        if (startTimeMonotonic > 0L && nowMonotonic < startTimeMonotonic) {
            return true
        }
        if (startTimeMonotonic > 0L && fallbackStartMillis > 0L && nowWallMillis >= fallbackStartMillis) {
            val estimatedBootEpochMillis = nowWallMillis - nowMonotonic
            if (estimatedBootEpochMillis > fallbackStartMillis + MAX_BOOT_EPOCH_DRIFT_MS) {
                return true
            }
        }
        return false
    }

    fun calculateSafeDurationSeconds(
        startTimeMonotonic: Long,
        fallbackStartMillis: Long,
        monotonicStartTimeNanos: Long = 0L,
        persistedDurationSeconds: Long = 0L,
        savedBootCount: Int? = null,
        currentBootCount: Int? = null,
        nowMonotonic: Long = android.os.SystemClock.elapsedRealtime(),
        nowNanos: Long = System.nanoTime(),
        nowWallMillis: Long = System.currentTimeMillis()
    ): Long {
        val safePersisted = maxOf(0L, persistedDurationSeconds)
        val wallDuration = if (fallbackStartMillis > 0L) {
            maxOf(0L, (nowWallMillis - fallbackStartMillis) / 1000L)
        } else {
            0L
        }

        val rebooted = hasDeviceRebooted(
            savedBootCount = savedBootCount,
            currentBootCount = currentBootCount,
            startTimeMonotonic = startTimeMonotonic,
            fallbackStartMillis = fallbackStartMillis,
            nowMonotonic = nowMonotonic,
            nowWallMillis = nowWallMillis
        )

        if (rebooted) {
            // Monotonic clocks reset on reboot; never subtract pre-reboot monotonic timestamps
            // nor fall through to System.nanoTime(). Preserve last persisted duration checkpoint
            // when available, otherwise use non-negative wall-clock elapsed time.
            return if (safePersisted > 0L) safePersisted else wallDuration
        }

        if (startTimeMonotonic > 0L && nowMonotonic >= startTimeMonotonic) {
            val diffSeconds = maxOf(0L, (nowMonotonic - startTimeMonotonic) / 1000L)
            return maxOf(diffSeconds, safePersisted)
        }

        if (monotonicStartTimeNanos > 0L && nowNanos >= monotonicStartTimeNanos) {
            val diffSeconds = maxOf(0L, (nowNanos - monotonicStartTimeNanos) / 1_000_000_000L)
            return maxOf(diffSeconds, safePersisted)
        }

        return maxOf(wallDuration, safePersisted)
    }

    fun calculateMonotonicDurationSeconds(
        startTimeMonotonic: Long,
        fallbackStartMillis: Long,
        monotonicStartTimeNanos: Long = 0L
    ): Long {
        return calculateSafeDurationSeconds(
            startTimeMonotonic = startTimeMonotonic,
            fallbackStartMillis = fallbackStartMillis,
            monotonicStartTimeNanos = monotonicStartTimeNanos
        )
    }
}

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val workoutId: String,
    val workoutName: String,
    val status: String, // "ATIVO", "CONCLUIDO", "DESCARTADO", "NAO_REALIZADO"
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val completedAt: Long? = null,
    val durationSeconds: Long = 0L,
    val exercisesJson: String,
    val version: Int = 1,
    val startTimeMonotonic: Long = android.os.SystemClock.elapsedRealtime(),
    val monotonicStartTimeNanos: Long = System.nanoTime()
)
