package com.example.utils

import com.example.data.model.WorkoutExercise
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object JsonUtils {

    val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val workoutExerciseListType = Types.newParameterizedType(List::class.java, WorkoutExercise::class.java)
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)

    private val exercisesListAdapter = moshi.adapter<List<WorkoutExercise>>(workoutExerciseListType)
    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)

    fun parseWorkoutExercises(json: String): List<WorkoutExercise> {
        return try {
            exercisesListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeWorkoutExercises(exercises: List<WorkoutExercise>): String {
        return try {
            exercisesListAdapter.toJson(exercises)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun parseStringList(json: String): List<String> {
        return try {
            stringListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeStringList(list: List<String>): String {
        return try {
            stringListAdapter.toJson(list)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun parseActiveWorkoutSession(json: String): com.example.data.model.ActiveWorkoutSession? {
        return try {
            moshi.adapter(com.example.data.model.ActiveWorkoutSession::class.java).fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    fun serializeActiveWorkoutSession(session: com.example.data.model.ActiveWorkoutSession): String {
        return try {
            moshi.adapter(com.example.data.model.ActiveWorkoutSession::class.java).toJson(session)
        } catch (e: Exception) {
            ""
        }
    }
}
