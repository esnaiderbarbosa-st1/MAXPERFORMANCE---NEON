package com.example.services.recommendation

import com.example.data.model.Exercise
import com.example.data.model.Workout
import com.example.data.model.WorkoutExercise
import com.example.data.model.WorkoutSet
import com.example.utils.JsonUtils
import java.util.UUID

class WorkoutBuilder {
    private var workoutId: String = "wk_" + UUID.randomUUID().toString().substring(0, 8)
    private var templateId: String? = null
    private var name: String = "Novo Treino Recomendado"
    private var description: String = ""
    private val exercises = mutableListOf<WorkoutExercise>()

    fun setWorkoutId(id: String) = apply { this.workoutId = id }
    fun setTemplateId(tid: String?) = apply { this.templateId = tid }
    fun setName(name: String) = apply { this.name = name }
    fun setDescription(desc: String) = apply { this.description = desc }

    fun addExercise(
        exercise: Exercise,
        setsCount: Int,
        repsRange: String,
        weight: Float,
        restTimeSeconds: Int,
        progressionType: String = "Linear",
        blockType: String = "Principal",
        executionType: String = "REPS"
    ) = apply {
        val wExerciseId = "w_ex_" + UUID.randomUUID().toString().substring(0, 8)
        
        // Parse reps from range, e.g., "8-12" -> average 10 reps, or fallback
        val defaultReps = try {
            if (repsRange.contains("-")) {
                val parts = repsRange.split("-")
                (parts[0].trim().toInt() + parts[1].trim().toInt()) / 2
            } else {
                repsRange.trim().toInt()
            }
        } catch (e: Exception) {
            10 // fallback
        }

        val setsList = (1..setsCount).map { idx ->
            WorkoutSet(
                id = "set_${wExerciseId}_$idx",
                index = idx,
                reps = defaultReps,
                weight = weight,
                completed = false,
                rpe = ""
            )
        }

        val workoutEx = WorkoutExercise(
            id = wExerciseId,
            exerciseId = exercise.id,
            name = exercise.name,
            category = exercise.category,
            sets = setsList,
            restTimeSeconds = restTimeSeconds,
            progressionType = progressionType,
            movementPattern = exercise.movementPattern,
            equipment = exercise.equipment,
            exerciseType = exercise.exerciseType,
            executionType = executionType,
            blockType = blockType
        )
        exercises.add(workoutEx)
    }

    fun build(): Workout {
        val json = JsonUtils.serializeWorkoutExercises(exercises)
        return Workout(
            id = workoutId,
            templateId = templateId,
            name = name,
            description = description,
            exercisesJson = json,
            createdAt = System.currentTimeMillis()
        )
    }
}
