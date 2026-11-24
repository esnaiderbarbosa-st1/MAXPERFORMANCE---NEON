package com.example.engine

import android.content.Context
import com.example.data.model.CurrentGymContext
import com.example.data.model.GymProfile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object GymContextManager {
    private const val PREFS_NAME = "max_performance_gym_context_prefs"
    private const val KEY_ACTIVE_GYM_ID = "active_gym_profile_id"
    private const val KEY_CURRENT_GYM_JSON = "current_gym_context_json"
    private const val KEY_NONE_SELECTED = "gym_profile_none_selected"

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(CurrentGymContext::class.java)

    fun getSavedActiveGymId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_GYM_ID, null)
    }

    fun isNoneSelectedExplicitly(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NONE_SELECTED, false)
    }

    fun saveActiveGym(context: Context, profile: GymProfile?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (profile == null) {
            prefs.edit()
                .putBoolean(KEY_NONE_SELECTED, true)
                .remove(KEY_ACTIVE_GYM_ID)
                .remove(KEY_CURRENT_GYM_JSON)
                .apply()
            return
        }

        val gymContext = CurrentGymContext(
            gymId = profile.id,
            name = profile.name,
            lastUsedTimestamp = System.currentTimeMillis(),
            gymProfile = profile,
            availableEquipment = profile.availableExercisesIds,
            enabledExercisesIds = profile.availableExercisesIds
        )

        try {
            val json = adapter.toJson(gymContext)
            prefs.edit()
                .putBoolean(KEY_NONE_SELECTED, false)
                .putString(KEY_ACTIVE_GYM_ID, profile.id)
                .putString(KEY_CURRENT_GYM_JSON, json)
                .apply()
        } catch (e: Exception) {
            prefs.edit()
                .putBoolean(KEY_NONE_SELECTED, false)
                .putString(KEY_ACTIVE_GYM_ID, profile.id)
                .apply()
        }
    }

    fun loadSavedGymContext(context: Context): CurrentGymContext? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CURRENT_GYM_JSON, null) ?: return null
        return try {
            adapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }
}
