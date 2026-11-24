package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore

// 1. User profile data
@Entity(tableName = "user_profile")
data class UserProfileData(
    @PrimaryKey val id: String = "1",
    val nome: String = "",
    val sexo: String = "",
    val idade: Int = 0,
    val altura: Int = 0, // cm
    val peso: Float = 0f, // kg
    val nivelExperiencia: String = "",
    val localTreino: String = "",
    val foto: String = "",
    val massaMagra: Float = 0f, // kg
    val percentualGordura: Float = 0f, // %
    val metaSemanal: Int = 0, // dias de treino por semana
    val iconStyle: String = "A", // A = Vetuais, B = 3D Renders
    val imc: Float = 0f,
    val taxaMuscular: Float = 0f,
    val gorduraSubcutanea: Float = 0f,
    val gorduraVisceral: Float = 0f,
    val aguaCorporal: Float = 0f,
    val massaMuscularEsqueletica: Float = 0f,
    val massaMuscular: Float = 0f,
    val massaOssea: Float = 0f,
    val proteina: Float = 0f,
    val tmb: Int = 0,
    val idadeMetabolica: Int = 0,
    val bioimpedanciaHistoricoJson: String = "[]",
    val heightCm: Int? = null,
    val weightKg: Float? = null,
    val runningGoal: String = "",
    val baselinePaceMinPerKm: Float? = null,
    val fcMaxCalibradaBpm: Int? = null,
    val limiarLt1Bpm: Int? = null,
    val limiarLt2Bpm: Int? = null,
    val metaPercentualGordura: Float? = null,
    val metaPercentualMusculo: Float? = null,
    val metaAguaLitros: Float? = null
) {
    companion object {
        val PRESET_ESNAIDER = UserProfileData(
            id = "1",
            nome = "Esnaider",
            sexo = "Masculino",
            idade = 50,
            altura = 187,
            peso = 84.7f,
            nivelExperiencia = "Avançado",
            localTreino = "Academia",
            foto = "",
            massaMagra = 65.2f,
            percentualGordura = 23.0f,
            metaSemanal = 5,
            iconStyle = "A",
            imc = 24.5f,
            taxaMuscular = 73.0f,
            gorduraSubcutanea = 16.5f,
            gorduraVisceral = 11.0f,
            aguaCorporal = 52.8f,
            massaMuscularEsqueletica = 40.9f,
            massaMuscular = 61.8f,
            massaOssea = 3.4f,
            proteina = 20.2f,
            tmb = 1778,
            idadeMetabolica = 49,
            bioimpedanciaHistoricoJson = "[]",
            heightCm = 187,
            weightKg = 84.7f,
            runningGoal = "CONTINUOUS_5K",
            baselinePaceMinPerKm = 7.0f,
            fcMaxCalibradaBpm = 190,
            limiarLt1Bpm = 137,
            limiarLt2Bpm = 162,
            metaPercentualGordura = 18.0f,
            metaPercentualMusculo = 74.0f,
            metaAguaLitros = 4.2f
        )

        val PRESET_ALYNE = UserProfileData(
            id = "1",
            nome = "Alyne",
            sexo = "Feminino",
            idade = 42,
            altura = 155,
            peso = 72.0f,
            nivelExperiencia = "Avançado",
            localTreino = "Academia",
            foto = "",
            massaMagra = 54.0f,
            percentualGordura = 25.0f,
            metaSemanal = 4,
            iconStyle = "A",
            imc = 29.9f,
            taxaMuscular = 46.0f,
            gorduraSubcutanea = 0f,
            gorduraVisceral = 0f,
            aguaCorporal = 0f,
            massaMuscularEsqueletica = 0f,
            massaMuscular = 0f,
            massaOssea = 0f,
            proteina = 0f,
            tmb = 1450,
            idadeMetabolica = 35,
            bioimpedanciaHistoricoJson = "[]",
            heightCm = 155,
            weightKg = 72.0f,
            runningGoal = "CONTINUOUS_5K",
            baselinePaceMinPerKm = null,
            fcMaxCalibradaBpm = null,
            limiarLt1Bpm = null,
            limiarLt2Bpm = null,
            metaPercentualGordura = null,
            metaPercentualMusculo = null,
            metaAguaLitros = null
        )
    }
}

/**
 * Retorna o peso corporal válido (> 0f) do perfil ou null quando não informado (sem fallback silencioso de 70 kg).
 */
fun resolveValidWeightKgOrNull(profile: UserProfileData?): Float? {
    if (profile == null) return null
    val wk = profile.weightKg
    if (wk != null && wk > 0f) return wk
    val p = profile.peso
    if (p > 0f) return p
    return null
}

/**
 * Retorna weightKg se válido (> 0), senão peso convertido se válido (> 0), senão 70f por padrão.
 */
fun resolveWeightKg(profile: UserProfileData?): Float {
    return resolveValidWeightKgOrNull(profile) ?: 70f
}

/**
 * Retorna a altura válida (entre 100 e 230 cm) do perfil (`heightCm`, com `altura` como alternativa),
 * ou null quando não houver altura válida.
 */
fun resolveValidHeightCmOrNull(profile: UserProfileData?): Int? {
    if (profile == null) return null
    val h = profile.heightCm?.takeIf { it in 100..230 }
    if (h != null) return h
    return profile.altura.takeIf { it in 100..230 }
}

/**
 * Calcula o IMC automático a partir do peso (> 30 kg) e altura válida (entre 100 e 230 cm).
 * Retorna null quando não houver peso ou altura válidos.
 */
fun calculateAutoBmiOrNull(weightKg: Float?, heightCm: Int?): Float? {
    if (weightKg == null || weightKg <= 30f) return null
    val validHeightCm = heightCm?.takeIf { it in 100..230 } ?: return null
    val hm = validHeightCm / 100f
    return weightKg / (hm * hm)
}

// BioimpedanceRecord is now defined in BodyRecompModels.kt (same package com.example.data.model)

enum class MuscleGroup(val displayName: String) {
    PEITO("Peito"),
    COSTAS("Costas"),
    PERNAS("Pernas"),
    OMBROS("Ombros"),
    BICEPS("Bíceps"),
    TRICEPS("Tríceps"),
    TRAPEZIO("Trapézio"),
    ABDOMINAIS("Abdominais"),
    CORE("Core"),
    LOMBAR("Lombar"),
    GLUTEOS("Glúteos");

    companion object {
        fun fromString(str: String): MuscleGroup {
            return values().firstOrNull { it.displayName.equals(str, ignoreCase = true) || it.name.equals(str, ignoreCase = true) } ?: PEITO
        }
    }
}

enum class ExerciseDifficulty(val displayName: String) {
    INICIANTE("Iniciante"),
    INTERMEDIARIO("Intermediário"),
    AVANCADO("Avançado");

    companion object {
        fun fromString(str: String): ExerciseDifficulty {
            val normalized = when (str.lowercase()) {
                "iniciante" -> "Iniciante"
                "intermediário", "intermediario", "médio", "medio" -> "Intermediário"
                "avançado", "avancado" -> "Avançado"
                else -> str
            }
            return values().firstOrNull { it.displayName.equals(normalized, ignoreCase = true) || it.name.equals(normalized, ignoreCase = true) } ?: INTERMEDIARIO
        }
    }
}

enum class ProgressionType(val displayName: String) {
    LINEAR("Linear"),
    DUPLA_PROGRESSAO("Dupla Progressão"),
    TRIPLA_PROGRESSAO("Tripla Progressão"),
    ONDULATORIO("Ondulatório");

    companion object {
        fun fromString(str: String): ProgressionType {
            return values().firstOrNull { it.displayName.equals(str, ignoreCase = true) || it.name.equals(str, ignoreCase = true) } ?: LINEAR
        }
    }
}

enum class CardioType(val displayName: String) {
    ESTEIRA("Esteira"),
    BICICLETA("Bicicleta"),
    ELIPTICO("Elíptico"),
    CORDA("Corda"),
    OUTROS("Outros");

    companion object {
        fun fromString(str: String): CardioType {
            return values().firstOrNull { it.displayName.equals(str, ignoreCase = true) || it.name.equals(str, ignoreCase = true) } ?: OUTROS
        }
    }
}

// 2. Exercise representation
enum class EquipmentType(val displayName: String) {
    BARBELL("Barbell"),
    DUMBBELL("Dumbbell"),
    CABLE("Cable"),
    SMITH("Smith"),
    MACHINE("Machine"),
    BODYWEIGHT("Bodyweight"),
    BENCH("Bench"),
    PLATE_LOADED("Plate Loaded"),
    KETTLEBELL("Kettlebell"),
    BAND("Band");

    companion object {
        fun fromString(str: String): EquipmentType? {
            return values().firstOrNull { it.name.equals(str, ignoreCase = true) || it.displayName.equals(str, ignoreCase = true) }
        }
    }
}

enum class WeightProfile(val displayName: String) {
    FREE_WEIGHT("Free Weight"),
    MACHINE("Machine"),
    BODYWEIGHT("Bodyweight"),
    VARIABLE("Variable"),
    FIXED("Fixed");

    companion object {
        fun fromString(str: String): WeightProfile {
            return values().firstOrNull { it.name.equals(str, ignoreCase = true) || it.displayName.equals(str, ignoreCase = true) } ?: FREE_WEIGHT
        }
    }
}

data class Exercise(
    val id: String = "",
    val name: String = "",
    val category: String = "Geral", // Peito, Costas, Pernas, Ombros, Bíceps, Tríceps, Trapézio, Abdominais
    val description: String = "",
    val muscles: List<String> = emptyList(),
    val difficulty: String = "Médio", // Iniciante, Intermediário, Avançado
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    val primaryMuscle: String = category,
    val secondaryMuscles: List<String> = muscles.filter { it != category },
    val movementPattern: String = "Outros", // Empurrar, Puxar, Agachamento, etc.
    val equipment: String = "Outros", // Barra, Halteres, Polia, etc.
    val exerciseType: String = "Outros", // Composto, Isolado, etc.
    val isUnilateral: Boolean = false,
    val weightIncrement: Float = 2.5f,
    
    // Sprint ENG-002: Exercise Intelligence Layer
    val compound: Boolean = false,
    val bilateral: Boolean = !isUnilateral,
    val unilateral: Boolean = isUnilateral,
    val warmupNeeded: Boolean = false,
    val skillRequirement: String = "Basic",
    val fatigueCost: Double = 1.0,
    val recoveryIndex: Double = 1.0,
    val averageExecutionTime: Int = 45, // seconds
    val averageSetupTime: Int = 60, // seconds
    val preferredMethods: List<String> = emptyList(),
    val contraindicatedMethods: List<String> = emptyList(),
    val preferredProgressions: List<String> = emptyList(),
    val weightProfile: String = "FREE_WEIGHT",
    val recommendedRepRange: String = "8-12",
    val recommendedRestRange: String = "60-90",
    val injuryRisk: String = "Low",
    val technicalComplexity: String = "Low",
    val equipmentTypes: List<EquipmentType> = emptyList(),
    val substitutionGroupId: String? = null,

    // Architectural layer: Exercise Illustration ID
    val illustrationId: String? = null,

    // SPRINT — TRAINING ENGINE 001 Metadata
    val fatigueScore: Double = fatigueCost,
    val gymTrafficScore: Int = 2, // 1 (low traffic/freeweight) to 5 (high traffic/smith/hack)
    val setupTime: Int = averageSetupTime,
    val transitionCost: Int = 15, // seconds
    val priority: Int = 1,
    val evidenceLevel: String = "Alta"
) {
    val exerciseIdVal: ExerciseId get() = ExerciseId(id)
    val difficultyType: ExerciseDifficulty get() = ExerciseDifficulty.fromString(difficulty)
    val primaryMuscleGroup: MuscleGroup get() = MuscleGroup.fromString(primaryMuscle)
    val secondaryMuscleGroups: List<MuscleGroup> get() = secondaryMuscles.map { MuscleGroup.fromString(it) }
    val movementPatternType: MovementPattern get() = com.example.data.model.MovementPattern.fromString(movementPattern)
    val equipmentType: Equipment get() = com.example.data.model.Equipment.fromString(equipment)
    val exerciseTypeEnum: ExerciseType get() = com.example.data.model.ExerciseType.fromString(exerciseType)
}

// 3. Custom Exercise Entity (for database persistence)
@Entity(tableName = "custom_exercises")
data class CustomExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val description: String,
    val difficulty: String
)

enum class SetType {
    STANDARD,       // Mesma carga todas as séries
    PYRAMID_UP,     // Carga aumenta (ex: 60, 70, 80)
    PYRAMID_DOWN,   // Carga diminui (ex: 80, 70, 60)
    DROP_SET        // Cai durante a série (ex: 80→60→40)
}

// 4. WorkoutSet structure (reps, weight, completion)
data class WorkoutSet(
    val id: String = "",
    val index: Int = 1,
    val reps: Int = 10,
    val weight: Float = 0f,
    val completed: Boolean = false,
    val rpe: String = "",
    val baseWeight: Float? = null,
    val setType: SetType = SetType.STANDARD
) {
    val setId: SetId get() = SetId(id)
    val rpeRangeVal: RpeRange get() = RpeRange.parse(rpe)
}

// 5. Exercise inside a workout
data class WorkoutExercise(
    val id: String = "",
    val exerciseId: String = "",
    val name: String = "",
    val category: String = "Geral",
    val sets: List<WorkoutSet> = emptyList(),
    val restTimeSeconds: Int = 60,
    var progressionType: String = "Linear", // Carga, Repetições, etc.
    val movementPattern: String = "Outros",
    val equipment: String = "Outros",
    val exerciseType: String = "Outros",
    val executionType: String = "REPS", // REPS, TEMPO, etc.
    var blockType: String = "Principal", // Aquecimento, Principal, Acessórios, Cardio, Alongamento
    val illustrationId: String? = null,
    val notes: String = ""
) {
    val progression: ProgressionType get() = ProgressionType.fromString(progressionType)
    val categoryMuscleGroup: MuscleGroup get() = MuscleGroup.fromString(category)
    val exerciseIdentifier: ExerciseId get() = ExerciseId(exerciseId)
    val restInterval: RestInterval get() = RestInterval(restTimeSeconds)
    val movementPatternType: MovementPattern get() = MovementPattern.fromString(movementPattern)
    val equipmentType: Equipment get() = Equipment.fromString(equipment)
    val exerciseTypeEnum: ExerciseType get() = ExerciseType.fromString(exerciseType)
    val executionTypeEnum: ExerciseExecutionType get() = ExerciseExecutionType.fromId(executionType)
}

// 6. Workout representation (templates and custom workouts)
@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey val id: String = "",
    val templateId: String? = null,
    val name: String = "",
    val description: String = "",
    val exercisesJson: String = "[]", // Stored as JSON string
    val createdAt: Long = System.currentTimeMillis()
) {
    val workoutIdentifier: WorkoutId get() = WorkoutId(id)
}

data class PeriodizedWorkoutCardData(
    val workout: Workout,
    val title: String,
    val muscleGroups: String,
    val durationText: String,
    val sequenceTag: String,
    val exerciseCount: Int = 0
)

/**
 * Model representing a single day in the 7-day Workout Calendar schedule.
 */
data class CalendarDaySchedule(
    val dateMillis: Long,
    val dayOfWeek: String,         // e.g. "SEG", "TER", "HOJE", "SEX"
    val dayOfMonth: Int,           // e.g. 9, 10, 11
    val isToday: Boolean,
    val isRestDay: Boolean,
    val workoutCard: PeriodizedWorkoutCardData?,
    val isCompleted: Boolean = false
)

// 7. Workout History Entry
@Entity(tableName = "workout_history")
data class WorkoutHistoryEntry(
    @PrimaryKey val id: String,
    val workoutId: String,
    val workoutName: String,
    val date: Long = System.currentTimeMillis(),
    val durationSeconds: Long,
    val totalWeight: Float,
    val exercisesCompletedCount: Int,
    val exercisesJson: String // List<WorkoutExercise> converted to JSON
)

// 8. Cardio Session
@Entity(tableName = "cardio_sessions")
data class CardioSession(
    @PrimaryKey val id: String,
    val type: String, // Esteira, Bicicleta, Elíptico, Corda, Outros
    val durationMinutes: Int,
    val calories: Int,
    val date: Long = System.currentTimeMillis(),
    val distance: Float = 0f,
    val durationSeconds: Int = 0,
    val avgHr: Int = 0,
    val steps: Int = 0,
    val pace: String = "",
    val fatBurnZone: Float = 0f,
    val anaerobicZone: Float = 0f,
    val limitZone: Float = 0f,
    val archived: Boolean = false
) {
    val cardioType: CardioType get() = CardioType.fromString(type)
}

// 9. Classroom for professor-student interactions
@Entity(tableName = "classrooms")
data class Classroom(
    @PrimaryKey val id: String,
    val name: String,
    val professorId: String,
    val professorName: String,
    val studentIdsJson: String, // List<String> JSON
    val accessCode: String // 8 digits
)

// 10. Chat Message for classrooms or private chats
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val id: String,
    val classroomId: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String?, // null if public group chat
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

// 11. Feed Items (Social Network)
@Entity(tableName = "feed_items")
data class FeedItem(
    @PrimaryKey val id: String,
    val authorName: String,
    val text: String,
    val imageUri: String? = null,
    val likesCount: Int = 0,
    val hasUserLiked: Boolean = false,
    val commentsJson: String = "[]", // List<String> JSON
    val createdAt: Long = System.currentTimeMillis()
)

// 12. Pending Synced Action
@Entity(tableName = "pending_sync_actions")
data class PendingSyncAction(
    @PrimaryKey val id: String,
    val actionType: String, // "CREATE_WORKOUT", "ADD_HISTORY", "LOG_CARDIO", "ADD_CUSTOM_EXERCISE", "SEND_MESSAGE"
    val payloadJson: String,
    val timestamp: Long = System.currentTimeMillis()
)

// 13. Gym Equipment Configuration (Gym Profile MVP)
data class GymEquipmentConfig(
    val gymName: String,
    val equipmentName: String,
    val defaultLoad: Float,
    val initialLoad: Float,
    val increment: Float = 2.5f,
    val barWeight: Float = 0f,
    val notes: String = ""
)

@Entity(tableName = "gym_profiles")
data class GymProfile(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
    val availableExercisesIds: List<String>,
    val unavailableExercisesIds: List<String>,
    val usesLibras: Boolean = false
) {
    @Ignore var hasMachines: Boolean = true
    @Ignore var hasOlympicBar: Boolean = true
    @Ignore var hasDumbbells: Boolean = true
    @Ignore var hasSmith: Boolean = true
    @Ignore var hasCables: Boolean = true
    @Ignore var hasHack: Boolean = true
    @Ignore var hasLegPress: Boolean = true
}

data class ActiveWorkoutSession(
    val workoutId: String = "",
    val sessionId: String = "",
    val startTime: Long = 0L,
    val currentExerciseIndex: Int = 0,
    val completedExercises: List<String> = emptyList(),
    val completedSets: List<String> = emptyList(),
    val weightsUsed: List<String> = emptyList(),
    val repsPerformed: List<String> = emptyList(),
    val timers: String = "",
    val restStatus: String = "",
    val progressionChosen: String = "Linear",
    val exerciseOrder: List<String> = emptyList(),
    val elapsedTime: Long = 0L,
    val sessionState: String = "ACTIVE", // "ACTIVE" or "INACTIVE"
    val exercisesJson: String = "[]",
    val workoutName: String = "",
    val workoutJson: String = "{}"
)


