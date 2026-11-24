package com.example.data.model

import java.io.Serializable

@JvmInline
value class WorkoutId(val value: String) : Serializable {
    init {
        require(value.isNotBlank()) { "Workout ID cannot be blank" }
    }
}

@JvmInline
value class ExerciseId(val value: String) : Serializable {
    init {
        require(value.isNotBlank()) { "Exercise ID cannot be blank" }
    }
}

@JvmInline
value class SetId(val value: String) : Serializable {
    init {
        require(value.isNotBlank()) { "Set ID cannot be blank" }
    }
}

@JvmInline
value class ProtocolId(val value: String) : Serializable {
    init {
        require(value.isNotBlank()) { "Protocol ID cannot be blank" }
    }
}

enum class MovementPattern(val displayName: String) {
    EMPURRAR("Empurrar"),
    PUXAR("Puxar"),
    AGACHAMENTO("Agachamento"),
    DOBRADICA_QUADRIL("Dobradiça de Quadril"),
    AFUNDO("Afundo"),
    CARREGAMENTO("Carregamento"),
    ROTACAO("Rotação"),
    ISOMETRICO("Isométrico"),
    OUTROS("Outros"),

    // Sprint ENG-002: Technical movement patterns
    PUSH("Push"),
    PULL("Pull"),
    HINGE("Hinge"),
    SQUAT("Squat"),
    LUNGE("Lunge"),
    CARRY("Carry"),
    ROTATION("Rotation"),
    ISOLATION("Isolation");

    companion object {
        fun fromString(str: String): MovementPattern {
            return values().firstOrNull { it.displayName.equals(str, ignoreCase = true) || it.name.equals(str, ignoreCase = true) } ?: OUTROS
        }
    }
}

enum class Equipment(val displayName: String) {
    BARRA("Barra"),
    HALTERES("Halteres"),
    POLIA("Polia"),
    MAQUINA("Máquina"),
    PESO_CORPORAL("Peso Corporal"),
    KETTLEBELL("Kettlebell"),
    ELASTICO("Elástico"),
    OUTROS("Outros");

    companion object {
        fun fromString(str: String): Equipment {
            return values().firstOrNull { it.displayName.equals(str, ignoreCase = true) || it.name.equals(str, ignoreCase = true) } ?: OUTROS
        }
    }
}

enum class ExerciseType(val displayName: String) {
    COMPOSTO("Composto"),
    ISOLADO("Isolado"),
    CARDIO("Cardio"),
    MOBILIDADE("Mobilidade"),
    CORE("Core"),
    OUTROS("Outros");

    companion object {
        fun fromString(str: String): ExerciseType {
            return values().firstOrNull { it.displayName.equals(str, ignoreCase = true) || it.name.equals(str, ignoreCase = true) } ?: OUTROS
        }
    }
}

data class RepsRange(val min: Int, val max: Int) : Serializable {
    init {
        require(min >= 0) { "Min reps cannot be negative ($min)" }
        require(max >= min) { "Max reps ($max) cannot be less than min reps ($min)" }
    }

    override fun toString(): String = if (min == max) "$min" else "$min-$max"

    companion object {
        fun parse(str: String): RepsRange {
            val clean = str.trim()
            if (clean.isBlank()) return RepsRange(10, 10)
            val parts = clean.split("-")
            return if (parts.size == 2) {
                val minVal = parts[0].trim().toIntOrNull() ?: 10
                val maxVal = parts[1].trim().toIntOrNull() ?: minVal
                val finalMin = minVal.coerceAtLeast(0)
                val finalMax = maxVal.coerceAtLeast(finalMin)
                RepsRange(finalMin, finalMax)
            } else {
                val singleVal = clean.toIntOrNull() ?: 10
                val finalVal = singleVal.coerceAtLeast(0)
                RepsRange(finalVal, finalVal)
            }
        }
    }
}

data class RpeRange(val min: Float, val max: Float) : Serializable {
    init {
        require(min in 1f..10f) { "Min RPE must be in 1..10 range ($min)" }
        require(max in min..10f) { "Max RPE ($max) cannot be less than min RPE ($min)" }
    }

    override fun toString(): String = if (min == max) "$min" else "${min.toInt()}-${max.toInt()}"

    companion object {
        fun parse(str: String): RpeRange {
            val clean = str.trim()
            if (clean.isBlank()) return RpeRange(7f, 9f)
            val parts = clean.split("-")
            return if (parts.size == 2) {
                val minVal = parts[0].trim().toFloatOrNull() ?: 7f
                val maxVal = parts[1].trim().toFloatOrNull() ?: minVal
                val finalMin = minVal.coerceIn(1f, 10f)
                val finalMax = maxVal.coerceIn(finalMin, 10f)
                RpeRange(finalMin, finalMax)
            } else {
                val singleVal = clean.toFloatOrNull() ?: 8f
                val finalVal = singleVal.coerceIn(1f, 10f)
                RpeRange(finalVal, finalVal)
            }
        }
    }
}

data class RestInterval(val seconds: Int) : Serializable {
    init {
        require(seconds >= 0) { "Rest interval cannot be negative ($seconds)" }
    }

    val minutes: Int get() = seconds / 60
    val remainingSeconds: Int get() = seconds % 60

    override fun toString(): String {
        return if (minutes > 0) {
            if (remainingSeconds > 0) "${minutes}m ${remainingSeconds}s" else "${minutes}m"
        } else {
            "${seconds}s"
        }
    }
}

sealed interface ExerciseExecutionType : Serializable {
    val id: String
    val displayName: String

    object RepsExecution : ExerciseExecutionType {
        override val id: String = "REPS"
        override val displayName: String = "Repetições e Carga"
    }

    object TempoExecution : ExerciseExecutionType {
        override val id: String = "TEMPO"
        override val displayName: String = "Tempo Alvo (Segundos)"
    }

    companion object {
        fun fromId(id: String?): ExerciseExecutionType {
            return when (id?.uppercase()) {
                "TEMPO" -> TempoExecution
                else -> RepsExecution
            }
        }
    }
}

sealed interface TrainingMethod : Serializable {
    val id: String
    val displayName: String
    
    fun applyMethod(sets: List<WorkoutSet>): List<WorkoutSet>

    object LinearMethod : TrainingMethod {
        override val id: String = "LINEAR"
        override val displayName: String = "Progressão Linear"
        
        override fun applyMethod(sets: List<WorkoutSet>): List<WorkoutSet> {
            return sets
        }
    }

    object ConstantMethod : TrainingMethod {
        override val id: String = "CONSTANT"
        override val displayName: String = "Carga Constante"
        
        override fun applyMethod(sets: List<WorkoutSet>): List<WorkoutSet> {
            if (sets.isEmpty()) return sets
            val firstWeight = sets.first().weight
            val firstReps = sets.first().reps
            return sets.map { set ->
                set.copy(
                    reps = firstReps,
                    weight = firstWeight,
                    rpe = "8"
                )
            }
        }
    }

    object PyramidMethod : TrainingMethod {
        override val id: String = "PYRAMID"
        override val displayName: String = "Pirâmide Crescente"
        
        override fun applyMethod(sets: List<WorkoutSet>): List<WorkoutSet> {
            if (sets.isEmpty()) return sets
            val baseWeight = sets.first().weight
            val baseReps = sets.first().reps
            
            return sets.mapIndexed { idx, set ->
                val factor = idx
                val adjustedReps = (baseReps - (factor * 2)).coerceAtLeast(1)
                val adjustedWeight = if (baseWeight > 0f) {
                    val rawWeight = baseWeight + (factor * (baseWeight * 0.1f))
                    (Math.round(rawWeight * 2) / 2f).coerceAtLeast(1f)
                } else {
                    0f
                }
                set.copy(
                    reps = adjustedReps,
                    weight = adjustedWeight,
                    rpe = if (idx == 0) "7" else if (idx == 1) "8" else "9"
                )
            }
        }
    }

    object PyramidDecrescenteMethod : TrainingMethod {
        override val id: String = "PYRAMID_DESC"
        override val displayName: String = "Pirâmide Decrescente"
        
        override fun applyMethod(sets: List<WorkoutSet>): List<WorkoutSet> {
            if (sets.isEmpty()) return sets
            val baseWeight = sets.first().weight
            val baseReps = sets.first().reps
            
            return sets.mapIndexed { idx, set ->
                val adjustedReps = (baseReps + (idx * 2)).coerceAtLeast(1)
                val adjustedWeight = if (baseWeight > 0f) {
                    val rawWeight = baseWeight - (idx * (baseWeight * 0.1f))
                    (Math.round(rawWeight * 2) / 2f).coerceAtLeast(1f)
                } else {
                    0f
                }
                set.copy(
                    reps = adjustedReps,
                    weight = adjustedWeight,
                    rpe = if (idx == 0) "9" else if (idx == 1) "8" else "7"
                )
            }
        }
    }

    object TopSetMethod : TrainingMethod {
        override val id: String = "TOP_SET"
        override val displayName: String = "Top Set"
        
        override fun applyMethod(sets: List<WorkoutSet>): List<WorkoutSet> {
            if (sets.isEmpty()) return sets
            val baseWeight = sets.first().weight
            val baseReps = sets.first().reps
            
            return sets.mapIndexed { idx, set ->
                if (idx == 0) {
                    set.copy(
                        reps = (baseReps - 2).coerceAtLeast(4),
                        weight = if (baseWeight > 0f) (Math.round(baseWeight * 1.15f * 2) / 2f).coerceAtLeast(1f) else 0f,
                        rpe = "9.5"
                    )
                } else {
                    set.copy(
                        reps = baseReps,
                        weight = if (baseWeight > 0f) (Math.round(baseWeight * 0.9f * 2) / 2f).coerceAtLeast(1f) else 0f,
                        rpe = "8"
                    )
                }
            }
        }
    }

    object ClusterMethod : TrainingMethod {
        override val id: String = "CLUSTER"
        override val displayName: String = "Cluster"
        
        override fun applyMethod(sets: List<WorkoutSet>): List<WorkoutSet> {
            if (sets.isEmpty()) return sets
            val baseWeight = sets.first().weight
            
            return sets.map { set ->
                set.copy(
                    reps = 4,
                    weight = if (baseWeight > 0f) (Math.round(baseWeight * 1.1f * 2) / 2f).coerceAtLeast(1f) else 0f,
                    rpe = "9"
                )
            }
        }
    }

    object DropSetMethod : TrainingMethod {
        override val id: String = "DROP_SET"
        override val displayName: String = "Drop Set"
        
        override fun applyMethod(sets: List<WorkoutSet>): List<WorkoutSet> {
            if (sets.isEmpty()) return sets
            val baseWeight = sets.first().weight
            val baseReps = sets.first().reps
            
            return sets.mapIndexed { idx, set ->
                if (idx == sets.size - 1) {
                    set.copy(
                        reps = (baseReps + 4),
                        weight = if (baseWeight > 0f) (Math.round(baseWeight * 0.6f * 2) / 2f).coerceAtLeast(1f) else 0f,
                        rpe = "10"
                    )
                } else {
                    set.copy(
                        reps = baseReps,
                        weight = baseWeight,
                        rpe = "8"
                    )
                }
            }
        }
    }

    object MyoRepsMethod : TrainingMethod {
        override val id: String = "MYO_REPS"
        override val displayName: String = "Myo Reps"
        override fun applyMethod(sets: List<WorkoutSet>): List<WorkoutSet> {
            return sets // Future implementation architecture
        }
    }

    object RestPauseMethod : TrainingMethod {
        override val id: String = "REST_PAUSE"
        override val displayName: String = "Rest Pause"
        override fun applyMethod(sets: List<WorkoutSet>): List<WorkoutSet> {
            return sets // Future implementation architecture
        }
    }

    companion object {
        val ALL: List<TrainingMethod> = listOf(
            LinearMethod, 
            PyramidMethod, 
            PyramidDecrescenteMethod, 
            ConstantMethod,
            TopSetMethod, 
            ClusterMethod, 
            DropSetMethod,
            MyoRepsMethod,
            RestPauseMethod
        )
        
        fun fromId(id: String?): TrainingMethod {
            return when (id?.uppercase()) {
                "PYRAMID", "PIRAMIDE" -> PyramidMethod
                "PYRAMID_DESC" -> PyramidDecrescenteMethod
                "CONSTANT" -> ConstantMethod
                "TOP_SET" -> TopSetMethod
                "CLUSTER" -> ClusterMethod
                "DROP_SET" -> DropSetMethod
                "MYO_REPS" -> MyoRepsMethod
                "REST_PAUSE" -> RestPauseMethod
                else -> LinearMethod
            }
        }
    }
}

