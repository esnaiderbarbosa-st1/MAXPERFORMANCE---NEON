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
    fun calculateMonotonicDurationSeconds(
        startTimeMonotonic: Long,
        fallbackStartMillis: Long,
        monotonicStartTimeNanos: Long = 0L
    ): Long {
        val nowMonotonic = android.os.SystemClock.elapsedRealtime()
        if (startTimeMonotonic > 0L && nowMonotonic >= startTimeMonotonic) {
            val diffSeconds = (nowMonotonic - startTimeMonotonic) / 1000L
            return maxOf(0L, diffSeconds)
        }
        val nowNanos = System.nanoTime()
        if (monotonicStartTimeNanos > 0L && nowNanos >= monotonicStartTimeNanos) {
            val diffSeconds = (nowNanos - monotonicStartTimeNanos) / 1_000_000_000L
            return maxOf(0L, diffSeconds)
        }
        val nowMillis = System.currentTimeMillis()
        return maxOf(0L, (nowMillis - fallbackStartMillis) / 1000L)
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
