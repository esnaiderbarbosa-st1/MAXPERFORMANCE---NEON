package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Representa uma sessão concluída de treino de musculação (GYM) no MAX PERFORMANCE.
 * Modelo persistido e estruturado para histórico, relatórios .TXT e compartilhamento.
 */
data class GymWorkoutSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val workoutName: String = "Treino de Musculação",
    val durationSeconds: Int = 0,
    val totalVolumeKg: Float = 0f,
    val totalSets: Int = 0,
    val completedSets: Int = 0,
    val exercises: List<GymExerciseRecord> = emptyList(),
    val notes: String = "",
    val aiCoachReport: String = "",
    val aiLoadAdjustmentsJson: String = ""
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("id", id)
        root.put("date", date)
        root.put("workoutName", workoutName)
        root.put("durationSeconds", durationSeconds)
        root.put("totalVolumeKg", totalVolumeKg.toDouble())
        root.put("totalSets", totalSets)
        root.put("completedSets", completedSets)
        root.put("notes", notes)
        if (aiCoachReport.isNotBlank()) root.put("aiCoachReport", aiCoachReport)
        if (aiLoadAdjustmentsJson.isNotBlank()) root.put("aiLoadAdjustmentsJson", aiLoadAdjustmentsJson)

        val exsArray = JSONArray()
        exercises.forEach { ex ->
            val exObj = JSONObject()
            exObj.put("name", ex.name)
            exObj.put("equipment", ex.equipment)
            exObj.put("category", ex.category)
            exObj.put("notes", ex.notes)

            val setsArray = JSONArray()
            ex.sets.forEach { set ->
                val setObj = JSONObject()
                setObj.put("setIndex", set.setIndex)
                setObj.put("weight", set.weight.toDouble())
                setObj.put("reps", set.reps)
                setObj.put("completed", set.completed)
                setsArray.put(setObj)
            }
            exObj.put("sets", setsArray)
            exsArray.put(exObj)
        }
        root.put("exercises", exsArray)
        return root.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): GymWorkoutSession? {
            return try {
                val root = JSONObject(jsonStr)
                val id = root.optString("id", java.util.UUID.randomUUID().toString())
                val date = root.optLong("date", System.currentTimeMillis())
                val workoutName = root.optString("workoutName", "Treino de Musculação")
                val durationSeconds = root.optInt("durationSeconds", 0)
                val totalVolumeKg = root.optDouble("totalVolumeKg", 0.0).toFloat()
                val totalSets = root.optInt("totalSets", 0)
                val completedSets = root.optInt("completedSets", 0)
                val notes = root.optString("notes", "")
                val aiCoachReport = root.optString("aiCoachReport", "")
                val aiLoadAdjustmentsJson = root.optString("aiLoadAdjustmentsJson", "")

                val exercises = mutableListOf<GymExerciseRecord>()
                val exsArray = root.optJSONArray("exercises")
                if (exsArray != null) {
                    for (i in 0 until exsArray.length()) {
                        val exObj = exsArray.getJSONObject(i)
                        val name = exObj.optString("name", "")
                        val equipment = exObj.optString("equipment", "Geral")
                        val category = exObj.optString("category", "Geral")
                        val exNotes = exObj.optString("notes", "")

                        val sets = mutableListOf<GymSetRecord>()
                        val setsArray = exObj.optJSONArray("sets")
                        if (setsArray != null) {
                            for (j in 0 until setsArray.length()) {
                                val sObj = setsArray.getJSONObject(j)
                                sets.add(
                                    GymSetRecord(
                                        setIndex = sObj.optInt("setIndex", j + 1),
                                        weight = sObj.optDouble("weight", 0.0).toFloat(),
                                        reps = sObj.optInt("reps", 0),
                                        completed = sObj.optBoolean("completed", true)
                                    )
                                )
                            }
                        }

                        exercises.add(
                            GymExerciseRecord(
                                name = name,
                                equipment = equipment,
                                category = category,
                                sets = sets,
                                notes = exNotes
                            )
                        )
                    }
                }

                GymWorkoutSession(
                    id = id,
                    date = date,
                    workoutName = workoutName,
                    durationSeconds = durationSeconds,
                    totalVolumeKg = totalVolumeKg,
                    totalSets = totalSets,
                    completedSets = completedSets,
                    exercises = exercises,
                    notes = notes,
                    aiCoachReport = aiCoachReport,
                    aiLoadAdjustmentsJson = aiLoadAdjustmentsJson
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class GymExerciseRecord(
    val name: String = "",
    val equipment: String = "Geral",
    val category: String = "Geral",
    val sets: List<GymSetRecord> = emptyList(),
    val notes: String = ""
)

data class GymSetRecord(
    val setIndex: Int = 1,
    val weight: Float = 0f,
    val reps: Int = 0,
    val completed: Boolean = true
)
