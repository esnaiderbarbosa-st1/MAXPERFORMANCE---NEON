package com.example.services.recommendation

import com.example.data.model.Workout
import com.example.engine.WorkoutContext

/**
 * Interface representing a Recommendation Advisor.
 * In the future, this can be implemented by an AI agent (Athena) to provide advanced,
 * highly-personalized athletic guidance and adjust workout context parameters dynamically.
 */
interface RecommendationAdvisor {
    /**
     * Recommends a collection of adjustments or visual advice based on the user's WorkoutContext.
     */
    fun advise(context: WorkoutContext): List<String>
}

/**
 * Interface representing a Workout Critic.
 * In the future, this will be implemented by an AI critic to analyze generated workouts
 * against physiological science principles and recommend fixes or refinements.
 */
interface WorkoutCritic {
    /**
     * Critiques a generated workout under the given context.
     * @return a list of constructive feedback items, where empty means perfect alignment.
     */
    fun critique(workout: Workout, context: WorkoutContext): List<String>
}

/**
 * Interface representing a generic Knowledge Provider.
 * This decouples the source of training rules (e.g., local database, expert system, or LLM)
 * from the recommendation engine.
 */
interface KnowledgeProvider {
    /**
     * Returns a recommended rep range, set count, or progression description based on key indicators.
     */
    fun getTrainingPrinciple(indicator: String): String
}
