package com.example.utils

import android.content.Context
import com.example.data.model.GymProfile
import com.example.data.model.UserProfileData
import com.example.data.model.resolveWeightKg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.round

object WeightCalculator {

    private const val PREFS_NAME = "gym_weights_library"

    const val LBS_TO_KG = 0.45359237f
    const val BILATERAL_TO_SINGLE_DUMBBELL_FACTOR = 0.32f
    const val MAX_INITIAL_SINGLE_DUMBBELL_KG = 26.0f

    /**
     * As 6 Categorias Reais de Equipamento (MAX GYM):
     */
    val ALL_EQUIPMENT_CATEGORIES = listOf(
        "Halteres",
        "Polia / Torre de Placas",
        "Máquina Articulada / Anilhas",
        "Barra / Smith",
        "Leg Press (Anilhas)",
        "Peso Corporal"
    )

    /**
     * Saltos de incremento rápido em KG:
     */
    val GRAVL_STEP_PRESETS = listOf(1.0f, 2.0f, 2.5f, 3.2f, 5.0f, 10.0f, 20.0f)

    /**
     * Saltos de incremento rápido em LIBRAS (LBS) para torres/máquinas importadas:
     */
    val LBS_STEP_PRESETS = listOf(5.0f, 10.0f, 15.0f, 20.0f, 25.0f)

    /**
     * Presets de Tara / Peso Base do Braço da Máquina (em KG):
     */
    val TARE_WEIGHT_PRESETS = listOf(0.0f, 10.0f, 15.0f, 20.0f, 25.0f)

    private val categoryCache = ConcurrentHashMap<String, String>()
    private val stepCache = ConcurrentHashMap<String, Float>()
    private val explicitCustomStepCache = ConcurrentHashMap<String, Boolean>()
    private val lbsModeCache = ConcurrentHashMap<String, Boolean>()
    private val stepLbsCache = ConcurrentHashMap<String, Float>()
    private val tareWeightKgCache = ConcurrentHashMap<String, Float>()
    private val tareOnlyModeCache = ConcurrentHashMap<String, Boolean>()

    private val _equipmentConfigVersion = MutableStateFlow(0L)
    val equipmentConfigVersion: StateFlow<Long> = _equipmentConfigVersion.asStateFlow()

    @Volatile
    var activeUserProfile: UserProfileData? = null

    private fun normalizeKey(exerciseName: String): String {
        return exerciseName.trim().lowercase(Locale.ROOT)
    }

    private fun canonicalizeCategory(rawCat: String): String {
        val lower = rawCat.lowercase(Locale.ROOT)
        return when {
            lower.contains("peso corporal") || lower.contains("bodyweight") || lower.contains("calistenia") ->
                "Peso Corporal"
            lower.contains("halter") ->
                "Halteres"
            lower.contains("polia") || lower.contains("cabo") || lower.contains("tijolinho") || lower.contains("torre") ||
                (lower.contains("leg press") && (lower.contains("cabo") || lower.contains("pilha"))) ->
                "Polia / Torre de Placas"
            lower.contains("leg press") ->
                "Leg Press (Anilhas)"
            lower.contains("articulad") || lower.contains("anilha") || lower.contains("máquina") || lower.contains("maquina") ->
                "Máquina Articulada / Anilhas"
            lower.contains("barra") || lower.contains("smith") ->
                "Barra / Smith"
            else -> rawCat
        }
    }

    fun defaultStepForCategory(category: String, currentWeight: Float = 20f): Float {
        val catLower = category.lowercase(Locale.ROOT)
        return when {
            catLower.contains("peso corporal") -> 2.0f
            catLower.contains("halter") -> if (currentWeight >= 10f) 2.0f else 1.0f
            catLower.contains("leg press") && catLower.contains("anilha") -> 10.0f
            catLower.contains("leg press") && (catLower.contains("cabo") || catLower.contains("pilha")) -> 5.0f
            catLower.contains("articulad") || catLower.contains("anilha") -> 5.0f
            catLower.contains("polia") || catLower.contains("cabo") || catLower.contains("tijolinho") || catLower.contains("torre") -> 5.0f
            catLower.contains("barra") || catLower.contains("smith") -> 2.5f
            else -> 2.5f
        }
    }

    /**
     * Infere uma das 6 Categorias Reais de Equipamento a partir do nome do exercício e tag de equipamento.
     */
    fun inferDetailedEquipmentCategory(exerciseName: String, rawEquipment: String? = null): String {
        val key = normalizeKey(exerciseName)
        categoryCache[key]?.let { return it }

        val nameLower = exerciseName.lowercase(Locale.ROOT)
        val eqLower = (rawEquipment ?: "").lowercase(Locale.ROOT)
        val fullLower = "$nameLower $eqLower"
        val isMachineOrCableExplicit = nameLower.contains("máquina") || nameLower.contains("maquina") ||
            nameLower.contains("polia") || nameLower.contains("cabo") || nameLower.contains("corda") ||
            nameLower.contains("pulley") || nameLower.contains("cross")

        return when {
            // 1. Peso Corporal (Abdominais solo/barra/paralela, prancha, flexão, barra fixa, calistenia)
            (!isMachineOrCableExplicit && (
                nameLower.contains("solo") || nameLower.contains("prancha") || nameLower.contains("plank") ||
                    nameLower.contains("barra fixa") || nameLower.contains("flexão") || nameLower.contains("flexao") ||
                    nameLower.contains("abdominal") || nameLower.contains("crunch") ||
                    nameLower.contains("elevação de pernas") || nameLower.contains("elevacao de pernas") ||
                    nameLower.contains("dead bug") || nameLower.contains("bird dog") ||
                    nameLower.contains("vácuo") || nameLower.contains("vacuo") ||
                    nameLower.contains("superman") || nameLower.contains("mergulho") ||
                    nameLower.contains("pistola") || nameLower.contains("pistol") ||
                    eqLower.contains("peso corporal") || eqLower.contains("bodyweight")
                )) -> "Peso Corporal"

            // 2. Leg Press
            fullLower.contains("leg press") && (fullLower.contains("cabo") || fullLower.contains("pilha") || fullLower.contains("select")) ->
                "Polia / Torre de Placas"
            fullLower.contains("leg press") || fullLower.contains("leg 45") ->
                "Leg Press (Anilhas)"

            // 3. Halteres (incluindo Agachamento Sumô e Goblet que usam haltere por padrão visual/catálogo)
            ((nameLower.contains("agachamento") && (nameLower.contains("sumô") || nameLower.contains("sumo") || nameLower.contains("goblet") || nameLower.contains("globet"))) &&
                !nameLower.contains("barra") && !nameLower.contains("smith")) ->
                "Halteres"
            fullLower.contains("halter") || fullLower.contains("dumbbell") ||
                fullLower.contains("búlgaro") || fullLower.contains("bulgaro") ||
                fullLower.contains("arnold") || fullLower.contains("serrote") ||
                fullLower.contains("martelo") || fullLower.contains("elevação lateral") ||
                fullLower.contains("rosca alternada") || (fullLower.contains("crucifixo") && !fullLower.contains("máquina") && !fullLower.contains("polia")) ->
                "Halteres"

            // 4. Máquina Articulada / Anilhas explícita (ex: Puxada Alta Máquina Anilhas, Supino Declinado Máquina Total Health)
            fullLower.contains("anilha") || fullLower.contains("articulad") || fullLower.contains("total health") ||
                fullLower.contains("plate_loaded") || fullLower.contains("plate loaded") ||
                (fullLower.contains("puxada") && (fullLower.contains("máquina") || fullLower.contains("maquina"))) ->
                "Máquina Articulada / Anilhas"

            // 5. Polia / Torre de Placas
            fullLower.contains("polia") || fullLower.contains("cabo") || fullLower.contains("cable") ||
                fullLower.contains("pulley") || fullLower.contains("corda") || fullLower.contains("crossover") ||
                fullLower.contains("cross over") || fullLower.contains("puxada") || fullLower.contains("remada baixa") ||
                fullLower.contains("face pull") || fullLower.contains("tijolinho") || fullLower.contains("torre") ->
                "Polia / Torre de Placas"

            // 6. Outras Máquinas Guiadas / Articuladas
            fullLower.contains("hack") ||
                fullLower.contains("cadeira") || fullLower.contains("mesa") || fullLower.contains("extensora") ||
                fullLower.contains("flexora") || fullLower.contains("abdutora") || fullLower.contains("adutora") ||
                fullLower.contains("voador") || fullLower.contains("pec deck") || fullLower.contains("peck deck") ||
                fullLower.contains("máquina") || fullLower.contains("maquina") || fullLower.contains("apoio no peito") ->
                "Máquina Articulada / Anilhas"

            // 6. Barra / Smith
            fullLower.contains("barra") || fullLower.contains("smith") || fullLower.contains("supino") ||
                fullLower.contains("agachamento") || fullLower.contains("terra") || fullLower.contains("stiff") ||
                fullLower.contains("rdl") || fullLower.contains("romeno") || fullLower.contains("elevação pélvica") ||
                fullLower.contains("hip thrust") ->
                "Barra / Smith"

            else -> "Máquina Articulada / Anilhas"
        }
    }

    fun getSavedEquipmentCategory(
        context: Context?,
        exerciseName: String,
        fallbackEquipment: String? = null
    ): String {
        val key = normalizeKey(exerciseName)
        categoryCache[key]?.let { return it }

        if (context != null && key.isNotEmpty()) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedCat = prefs.getString("eq_cat_$key", null)
            if (!savedCat.isNullOrBlank()) {
                val canonical = canonicalizeCategory(savedCat)
                categoryCache[key] = canonical
                return canonical
            }
        }
        return inferDetailedEquipmentCategory(exerciseName, fallbackEquipment)
    }

    fun isLbsMode(context: Context?, exerciseName: String): Boolean {
        val key = normalizeKey(exerciseName)
        lbsModeCache[key]?.let { return it }
        if (context != null && key.isNotEmpty()) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isLbs = prefs.getBoolean("eq_unit_lbs_$key", false)
            lbsModeCache[key] = isLbs
            return isLbs
        }
        return false
    }

    fun getSavedStepLbs(context: Context?, exerciseName: String): Float {
        val key = normalizeKey(exerciseName)
        stepLbsCache[key]?.let { return it }
        if (context != null && key.isNotEmpty()) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stepLbs = prefs.getFloat("eq_step_lbs_$key", 10f).coerceIn(1f, 100f)
            stepLbsCache[key] = stepLbs
            return stepLbs
        }
        return 10f
    }

    fun getSavedTareWeightKg(context: Context?, exerciseName: String): Float {
        val key = normalizeKey(exerciseName)
        tareWeightKgCache[key]?.let { return it }
        if (context != null && key.isNotEmpty()) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val tare = prefs.getFloat("eq_tare_kg_$key", 0f).coerceIn(0f, 100f)
            tareWeightKgCache[key] = tare
            return tare
        }
        return 0f
    }

    fun isTareOnlyMode(context: Context?, exerciseName: String): Boolean {
        val key = normalizeKey(exerciseName)
        tareOnlyModeCache[key]?.let { return it }
        if (context != null && key.isNotEmpty()) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val tareOnly = prefs.getBoolean("eq_tare_only_$key", false)
            tareOnlyModeCache[key] = tareOnly
            return tareOnly
        }
        return false
    }

    fun hasExplicitCustomStep(context: Context?, exerciseName: String): Boolean {
        val key = normalizeKey(exerciseName)
        if (isLbsMode(context, exerciseName)) return true
        explicitCustomStepCache[key]?.let { return it }
        if (context != null && key.isNotEmpty()) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val hasCustom = prefs.getBoolean("eq_custom_flag_$key", false) ||
                prefs.contains("eq_step_$key") ||
                !prefs.getString("weight_${exerciseName.trim()}", null).isNullOrBlank()
            explicitCustomStepCache[key] = hasCustom
            return hasCustom
        }
        return false
    }

    fun getSavedWeightStep(
        context: Context?,
        exerciseName: String,
        fallbackEquipment: String? = null,
        currentWeight: Float = 20f
    ): Float {
        val key = normalizeKey(exerciseName)
        if (isLbsMode(context, exerciseName)) {
            val lbs = getSavedStepLbs(context, exerciseName)
            return lbs * LBS_TO_KG
        }

        val cachedStep = stepCache[key]
        val isExplicit = explicitCustomStepCache[key] ?: false

        if (cachedStep != null && cachedStep > 0f) {
            val cat = categoryCache[key] ?: inferDetailedEquipmentCategory(exerciseName, fallbackEquipment)
            // Se for Halteres sem incremento customizado explícito, respeita 1kg (<10kg) e 2kg (>=10kg)
            if (!isExplicit && cat.contains("Halter", ignoreCase = true)) {
                return if (currentWeight >= 10f) 2.0f else 1.0f
            }
            return cachedStep
        }

        if (context != null && key.isNotEmpty()) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedStep = prefs.getFloat("eq_step_$key", -1f)
            val savedCustomFlag = prefs.getBoolean("eq_custom_flag_$key", false)
            if (savedStep > 0f) {
                stepCache[key] = savedStep
                explicitCustomStepCache[key] = savedCustomFlag
                val cat = getSavedEquipmentCategory(context, exerciseName, fallbackEquipment)
                if (!savedCustomFlag && cat.contains("Halter", ignoreCase = true)) {
                    return if (currentWeight >= 10f) 2.0f else 1.0f
                }
                return savedStep
            }
            // Compatibilidade com chave legada "weight_<exerciseName>"
            val legacyStr = prefs.getString("weight_${exerciseName.trim()}", null)
                ?.replace(',', '.')
                ?.toFloatOrNull()
            if (legacyStr != null && legacyStr > 0f) {
                stepCache[key] = legacyStr
                explicitCustomStepCache[key] = true
                return legacyStr
            }
        }

        val category = getSavedEquipmentCategory(context, exerciseName, fallbackEquipment)
        return defaultStepForCategory(category, currentWeight)
    }

    fun saveExerciseEquipmentConfig(
        context: Context,
        exerciseName: String,
        category: String,
        stepKg: Float,
        isCustomStep: Boolean = true,
        useLbs: Boolean = false,
        stepLbs: Float = 10f,
        tareWeightKg: Float = 0f,
        tareOnlyMode: Boolean = false
    ) {
        val key = normalizeKey(exerciseName)
        if (key.isEmpty()) return
        val canonicalCat = canonicalizeCategory(category)
        val validStepLbs = stepLbs.coerceIn(1f, 100f)
        val effectiveStepKg = if (useLbs) {
            (validStepLbs * LBS_TO_KG).coerceIn(0.25f, 50f)
        } else {
            stepKg.coerceIn(0.25f, 50f)
        }
        val validTare = tareWeightKg.coerceIn(0f, 100f)

        categoryCache[key] = canonicalCat
        stepCache[key] = effectiveStepKg
        explicitCustomStepCache[key] = isCustomStep || useLbs
        lbsModeCache[key] = useLbs
        stepLbsCache[key] = validStepLbs
        tareWeightKgCache[key] = validTare
        tareOnlyModeCache[key] = tareOnlyMode

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val formattedLegacy = if (effectiveStepKg % 1f == 0f) effectiveStepKg.toInt().toString() else effectiveStepKg.toString()
        prefs.edit()
            .putString("eq_cat_$key", canonicalCat)
            .putFloat("eq_step_$key", effectiveStepKg)
            .putBoolean("eq_custom_flag_$key", isCustomStep || useLbs)
            .putBoolean("eq_unit_lbs_$key", useLbs)
            .putFloat("eq_step_lbs_$key", validStepLbs)
            .putFloat("eq_tare_kg_$key", validTare)
            .putBoolean("eq_tare_only_$key", tareOnlyMode)
            .putString("weight_${exerciseName.trim()}", formattedLegacy)
            .apply()

        _equipmentConfigVersion.value = System.currentTimeMillis()
    }

    fun formatStepNumber(stepKg: Float): String {
        val ptBr = Locale("pt", "BR")
        return when {
            kotlin.math.abs(stepKg - round(stepKg)) < 0.01f -> "${round(stepKg).toInt()}"
            kotlin.math.abs(stepKg * 10f - round(stepKg * 10f)) < 0.05f -> String.format(ptBr, "%.1f", stepKg)
            else -> String.format(ptBr, "%.1f", stepKg)
        }
    }

    fun formatStepPillText(stepKg: Float): String {
        return "Δ +${formatStepNumber(stepKg)} kg ⚙"
    }

    fun formatShortCategoryLabel(category: String): String {
        val lower = category.lowercase(Locale.ROOT)
        return when {
            lower.contains("peso corporal") -> "Peso Corporal"
            lower.contains("halter") -> "Halteres"
            lower.contains("polia") || lower.contains("cabo") || lower.contains("torre") -> "Polia/Torre"
            lower.contains("leg press") && lower.contains("anilha") -> "Leg (Anilhas)"
            lower.contains("leg press") -> "Leg (Torre)"
            lower.contains("articulad") || lower.contains("máquina") || lower.contains("maquina") -> "Máq./Anilhas"
            lower.contains("barra") || lower.contains("smith") -> "Barra/Smith"
            else -> category.take(12)
        }
    }

    fun formatCleanEquipmentBadgeLabel(
        context: Context?,
        exerciseName: String,
        fallbackEquipment: String? = null
    ): String {
        val savedCat = getSavedEquipmentCategory(context, exerciseName, fallbackEquipment)
        val lower = savedCat.lowercase(Locale.ROOT)
        return when {
            lower.contains("peso corporal") -> "Peso Corporal"
            lower.contains("halter") -> "Halteres"
            lower.contains("polia") || lower.contains("cabo") || lower.contains("torre") -> "Polia / Placas"
            lower.contains("leg press") -> "Leg Press"
            lower.contains("articulad") || lower.contains("máquina") || lower.contains("maquina") -> "Máquina"
            lower.contains("barra") || lower.contains("smith") -> "Barra"
            else -> savedCat.take(16)
        }
    }

    fun isBodyweightExercise(context: Context?, exerciseName: String, fallbackEquipment: String? = null): Boolean {
        val savedCat = getSavedEquipmentCategory(context, exerciseName, fallbackEquipment)
        return savedCat.contains("Peso Corporal", ignoreCase = true) ||
            inferEquipment(exerciseName, fallbackEquipment).equals("Peso Corporal", ignoreCase = true)
    }

    /**
     * Identifica se o exercício utiliza 2 halteres simultâneos ou execução unilateral por haltere/lado,
     * devendo exibir "kg (cada halter)" e contabilizar o volume real do par (× 2) nas métricas.
     */
    fun isPerDumbbellWeight(
        context: Context?,
        exerciseName: String,
        fallbackEquipment: String? = null
    ): Boolean {
        val nameLower = exerciseName.lowercase(Locale.ROOT)
        val savedCat = getSavedEquipmentCategory(context, exerciseName, fallbackEquipment).lowercase(Locale.ROOT)
        val inferred = inferEquipment(exerciseName, fallbackEquipment).lowercase(Locale.ROOT)
        // Agachamento Goblet ou Sumô com 1 único haltere central não é "cada halter"
        if (nameLower.contains("goblet") || nameLower.contains("globet") ||
            (nameLower.contains("sumô") && !nameLower.contains("2 halteres")) ||
            (nameLower.contains("sumo") && !nameLower.contains("2 halteres"))
        ) {
            return false
        }
        return savedCat.contains("halter") || inferred.contains("halter") ||
            nameLower.contains("halter") || nameLower.contains("dumbbell") ||
            nameLower.contains("martelo") || nameLower.contains("rosca alternada") ||
            nameLower.contains("serrote") || nameLower.contains("arnold")
    }

    /**
     * Identifica se as repetições são executadas/contadas para cada lado (unilateral ou halteres independentes).
     */
    fun isEachSideReps(
        context: Context?,
        exerciseName: String,
        fallbackEquipment: String? = null
    ): Boolean {
        val nameLower = exerciseName.lowercase(Locale.ROOT)
        if (isPerDumbbellWeight(context, exerciseName, fallbackEquipment)) return true
        return nameLower.contains("unilateral") || nameLower.contains("alternad") ||
            nameLower.contains("búlgaro") || nameLower.contains("bulgaro") ||
            nameLower.contains("afundo") || nameLower.contains("passada") ||
            nameLower.contains("avanço") || nameLower.contains("serrote") ||
            nameLower.contains("coice") || nameLower.contains("cada lado")
    }

    /**
     * Retorna o multiplicador biomecânico de volume da série:
     * - 2 para exercícios com par de halteres ou unilaterais ("cada halter" / "cada lado")
     * - 1 para barras, máquinas bilaterais, polias bilaterais ou haltere único (Goblet)
     */
    fun getVolumeMultiplierForExercise(
        context: Context?,
        exerciseName: String,
        fallbackEquipment: String? = null
    ): Int {
        return if (isPerDumbbellWeight(context, exerciseName, fallbackEquipment) ||
            isEachSideReps(context, exerciseName, fallbackEquipment)
        ) {
            2
        } else {
            1
        }
    }

    fun calculateSetVolumeKg(
        context: Context?,
        exerciseName: String,
        fallbackEquipment: String? = null,
        weightKg: Float,
        reps: Int
    ): Float {
        if (weightKg <= 0f || reps <= 0) return 0f
        val mult = getVolumeMultiplierForExercise(context, exerciseName, fallbackEquipment)
        return weightKg * reps * mult
    }

    /**
     * Formata o texto principal de carga e o sufixo de unidade para a linha da série (S1, S2, S3):
     * - Peso Corporal com 0 kg -> ("Peso Corporal", "")
     * - Modo Libras (LBS) -> ("45,4 kg", "(100 lbs)")
     * - Modo Apenas Tara -> ("15", "kg (Tara)")
     * - Halteres -> ("14", "kg (cada halter)")
     * - Padrão -> ("40", "kg")
     */
    fun formatWeightDisplayPairForSet(
        context: Context?,
        exerciseName: String,
        fallbackEquipment: String?,
        weightKg: Float
    ): Pair<String, String> {
        val ptBr = Locale("pt", "BR")
        val isBw = isBodyweightExercise(context, exerciseName, fallbackEquipment)
        if (isBw && weightKg <= 0.05f) {
            return "Peso Corporal" to ""
        }
        val useLbs = isLbsMode(context, exerciseName)
        if (useLbs && weightKg > 0f) {
            val lbsInt = round(weightKg / LBS_TO_KG).toInt()
            val kgStr = String.format(ptBr, "%.1f", weightKg)
            return "$kgStr kg" to "($lbsInt lbs)"
        }
        val tareOnly = isTareOnlyMode(context, exerciseName)
        val tareKg = getSavedTareWeightKg(context, exerciseName)
        if (tareOnly && tareKg > 0f && kotlin.math.abs(weightKg - tareKg) < 0.2f) {
            return formatStepNumber(weightKg) to "kg (Tara)"
        }
        if (isPerDumbbellWeight(context, exerciseName, fallbackEquipment)) {
            return formatStepNumber(weightKg) to "kg (cada halter)"
        }
        return formatStepNumber(weightKg) to "kg"
    }

    /**
     * Subtítulo discreto exibido abaixo do nome do exercício no card principal:
     * Ex: "Máquina Articulada / Anilhas • Pulo +5 kg" ou "Polia / Torre de Placas • +15 lbs (6,8 kg)"
     */
    fun formatEquipmentSubtitle(
        context: Context?,
        exerciseName: String,
        fallbackEquipment: String? = null,
        currentWeight: Float = 20f
    ): String {
        val ptBr = Locale("pt", "BR")
        val cat = getSavedEquipmentCategory(context, exerciseName, fallbackEquipment)
        if (cat.contains("Peso Corporal", ignoreCase = true)) {
            return if (currentWeight > 0.05f) {
                "Peso Corporal • Carga Extra +${formatStepNumber(currentWeight)} kg"
            } else {
                "Peso Corporal • Sem carga externa"
            }
        }
        if (isLbsMode(context, exerciseName)) {
            val stepLbs = getSavedStepLbs(context, exerciseName)
            val stepKg = stepLbs * LBS_TO_KG
            return "$cat • Torre em Libras (+${formatStepNumber(stepLbs)} lbs ≈ +${String.format(ptBr, "%.1f", stepKg)} kg)"
        }
        val tareKg = getSavedTareWeightKg(context, exerciseName)
        val tareOnly = isTareOnlyMode(context, exerciseName)
        val stepKg = getSavedWeightStep(context, exerciseName, fallbackEquipment, currentWeight)
        return when {
            tareOnly && tareKg > 0f -> "$cat • Peso Base da Máquina (${formatStepNumber(tareKg)} kg)"
            tareKg > 0f -> "$cat • Tara ${formatStepNumber(tareKg)} kg • Pulo +${formatStepNumber(stepKg)} kg"
            else -> "$cat • Pulo +${formatStepNumber(stepKg)} kg"
        }
    }

    /**
     * Reference baseline starting weight per exercise and equipment type (before biometric scaling).
     */
    fun baseReferenceWeightForExercise(exerciseName: String, equipment: String): Float {
        val nameLower = exerciseName.lowercase()
        val inferredEq = inferEquipment(exerciseName, equipment).lowercase()
        val eqLower = (equipment + " " + inferredEq).lowercase()
        val isDumbbell = nameLower.contains("halter") || eqLower.contains("halter") || eqLower.contains("dumbbell") ||
            (nameLower.contains("agachamento") && (nameLower.contains("sumô") || nameLower.contains("sumo") || nameLower.contains("goblet") || nameLower.contains("globet")))
        val isMachineOrCable = eqLower.contains("máquina") || eqLower.contains("maquina") || eqLower.contains("polia") || eqLower.contains("cabo")

        if (inferredEq.contains("peso corporal") || inferredEq.contains("bodyweight") ||
            nameLower.contains("prancha") || nameLower.contains("flexão") || nameLower.contains("flexao") ||
            nameLower.contains("barra fixa") || nameLower.contains("corrida") || nameLower.contains("caminhada") ||
            nameLower.contains("alongamento") || nameLower.contains("mobilidade") ||
            (nameLower.contains("abdominal") && !isMachineOrCable)
        ) {
            return 0.0f
        }

        when {
            nameLower.contains("leg press") -> return 60.0f
            nameLower.contains("búlgaro") || nameLower.contains("bulgaro") || nameLower.contains("afundo") || nameLower.contains("passada") || nameLower.contains("avanço") -> {
                return if (isDumbbell) 12.0f else 30.0f
            }
            nameLower.contains("agachamento") && !isDumbbell -> return 40.0f
            nameLower.contains("agachamento") && isDumbbell -> return 14.0f
            (nameLower.contains("levantamento terra") || nameLower.contains("terra") || nameLower.contains("romeno") || nameLower.contains("rdl")) && !isDumbbell -> return 40.0f
            (nameLower.contains("terra") || nameLower.contains("romeno") || nameLower.contains("rdl") || nameLower.contains("stiff")) && isDumbbell -> return 14.0f
            nameLower.contains("stiff") -> return 35.0f
            nameLower.contains("elevação pélvica") || nameLower.contains("elevacao pelvica") || nameLower.contains("hip thrust") -> return 40.0f
            nameLower.contains("abdutora") || nameLower.contains("adutora") -> return 30.0f
            nameLower.contains("glúteo") || nameLower.contains("gluteo") -> return if (inferredEq.contains("polia")) 15.0f else 25.0f
            nameLower.contains("supino declinado") && (nameLower.contains("total health") || isMachineOrCable || nameLower.contains("anilha")) -> return 50.0f
            nameLower.contains("supino") && isDumbbell -> return 14.0f
            nameLower.contains("supino") && isMachineOrCable -> return 50.0f
            nameLower.contains("supino") -> return 35.0f
            nameLower.contains("crucifixo") && isDumbbell -> return 10.0f
            nameLower.contains("crossover") || nameLower.contains("cross over") -> return 15.0f
            nameLower.contains("crucifixo") || nameLower.contains("voador") || nameLower.contains("pec deck") -> return 30.0f
            (nameLower.contains("desenvolvimento") || nameLower.contains("arnold")) && isDumbbell -> return 12.0f
            nameLower.contains("desenvolvimento") -> return 25.0f
            nameLower.contains("puxada") && (nameLower.contains("anilha") || nameLower.contains("máquina") || nameLower.contains("maquina") || nameLower.contains("articulad")) -> return 60.0f
            nameLower.contains("puxada") || nameLower.contains("pulley") -> return 52.0f
            (nameLower.contains("remada") || nameLower.contains("serrote")) && isDumbbell -> return 14.0f
            nameLower.contains("remada") && (nameLower.contains("barra") || eqLower.contains("barra")) -> return 35.0f
            nameLower.contains("remada") -> return 30.0f
            nameLower.contains("face pull") -> return 15.0f
            nameLower.contains("encolhimento") -> return if (isDumbbell) 18.0f else 40.0f
            nameLower.contains("cadeira extensora") || nameLower.contains("extensora") -> return 61.0f
            nameLower.contains("mesa flexora") || nameLower.contains("cadeira flexora") || nameLower.contains("flexora") -> return 36.0f
            nameLower.contains("panturrilha") -> return 35.0f
            (nameLower.contains("tríceps") || nameLower.contains("triceps")) && isDumbbell -> return 8.0f
            nameLower.contains("tríceps") || nameLower.contains("triceps") -> return 15.0f
            nameLower.contains("martelo") && isDumbbell -> return 14.0f
            (nameLower.contains("rosca") || nameLower.contains("martelo")) && isDumbbell -> return 12.0f
            nameLower.contains("rosca") -> return 15.0f
            nameLower.contains("elevação lateral") || nameLower.contains("elevacao lateral") -> return 10.0f
            nameLower.contains("elevação frontal") || nameLower.contains("elevacao frontal") -> return 8.0f
        }

        return defaultStartingWeightForEquipment(inferredEq)
    }

    /**
     * Retorna a carga base consolidada oficial do atleta (Esnaider • 30/10/2026 e 31/10/2026) quando aplicável.
     * Regra de Ouro para Halteres: o valor representa SEMPRE 1 único haltere (por mão).
     */
    fun getConsolidatedAthleteLoadKg(exerciseName: String): Float? {
        val lower = exerciseName.trim().lowercase(Locale.ROOT)
        return when {
            lower.contains("supino inclinado") && (lower.contains("halter") || lower.contains("dumbbell")) -> 22.0f
            lower.contains("supino declinado") && (lower.contains("total health") || lower.contains("máquina") || lower.contains("maquina") || lower.contains("anilha")) -> 50.0f
            (lower.contains("terra romeno") || lower.contains("rdl")) && !lower.contains("halter") -> 60.0f
            lower.contains("standing hip thrust") -> 63.6f
            lower.contains("leg press horizontal") -> 75.0f
            lower.contains("remada curvada") && (lower.contains("barra") || !lower.contains("halter")) -> 45.0f
            (lower.contains("remada sentada") || lower.contains("remada na máquina") || lower.contains("remada na maquina") || lower.contains("remada baixa")) -> 45.0f
            lower.contains("desenvolvimento") && (lower.contains("máquina") || lower.contains("maquina")) -> 20.0f
            lower.contains("abdominal infra") || lower.contains("abdominal crunch solo") || lower.contains("prancha") -> 0.0f
            lower.contains("puxada alta") && (lower.contains("anilha") || lower.contains("máquina") || lower.contains("maquina") || lower.contains("articulad")) -> 60.0f
            (lower.contains("puxada alta") || lower.contains("puxada frontal")) && (lower.contains("polia") || lower.contains("pulley") || !lower.contains("anilha")) -> 52.0f
            (lower.contains("elevação lateral") || lower.contains("elevacao lateral")) && (lower.contains("halter") || !lower.contains("polia")) -> 10.0f
            lower.contains("cadeira extensora") || lower == "extensora" -> 61.0f
            lower.contains("cadeira flexora") || lower.contains("mesa flexora") -> 36.0f
            lower.contains("rosca martelo") -> 14.0f
            else -> null
        }
    }

    /**
     * Converte uma carga bilateral de barra/máquina para a estimativa segura de 1 único haltere (por mão),
     * aplicando o fator biomecânico de 0.32x e o teto de segurança inicial de 26 kg por haltere.
     */
    fun estimateSingleDumbbellFromBilateralLoad(bilateralLoadKg: Float): Float {
        if (bilateralLoadKg <= 0f) return 12.0f
        val raw = (bilateralLoadKg * BILATERAL_TO_SINGLE_DUMBBELL_FACTOR).coerceIn(4.0f, MAX_INITIAL_SINGLE_DUMBBELL_KG)
        return roundToAvailableWeight(raw, "Halteres").coerceAtMost(MAX_INITIAL_SINGLE_DUMBBELL_KG)
    }

    fun defaultStartingWeightForExercise(
        exerciseName: String,
        equipment: String,
        userProfile: UserProfileData? = activeUserProfile
    ): Float {
        val profile = userProfile ?: activeUserProfile
        val isDefaultAthlete = profile == null ||
            profile.nome.contains("Esnaider", ignoreCase = true) ||
            profile.nome.startsWith("Atleta", ignoreCase = true)

        if (isDefaultAthlete) {
            getConsolidatedAthleteLoadKg(exerciseName)?.let { consolidated ->
                return consolidated
            }
        }

        val inferredEq = inferEquipment(exerciseName, equipment)
        val baseReference = baseReferenceWeightForExercise(exerciseName, inferredEq)
        if (baseReference <= 0f) return 0f

        if (profile == null) {
            return roundToAvailableWeight(baseReference, inferredEq)
        }

        val isFemale = profile.sexo.trim().equals("Feminino", ignoreCase = true) ||
            profile.sexo.trim().equals("F", ignoreCase = true) ||
            profile.sexo.trim().equals("Mulher", ignoreCase = true)

        return calculateBiometricStartingWeight(
            exerciseName = exerciseName,
            equipment = inferredEq,
            weightKg = resolveWeightKg(profile),
            isFemale = isFemale,
            age = profile.idade,
            experienceLevel = profile.nivelExperiencia
        )
    }

    fun defaultStartingWeightForEquipment(equipment: String): Float {
        val eq = equipment.lowercase()
        return when {
            eq.contains("barra") || eq.contains("smith") || eq.contains("barbell") -> 20.0f
            eq.contains("halter") || eq.contains("dumbbell") -> 10.0f
            eq.contains("máquina") || eq.contains("maquina") || eq.contains("machine") -> 20.0f
            eq.contains("polia") || eq.contains("cabo") || eq.contains("cable") -> 15.0f
            eq.contains("peso corporal") || eq.contains("bodyweight") -> 0.0f
            else -> 10.0f
        }
    }

    fun roundToAvailableWeight(
        weight: Float,
        equipment: String,
        gymProfile: GymProfile? = null,
        preserveExactHistoricalKg: Boolean = false
    ): Float {
        if (weight <= 0f) return 0f
        if (preserveExactHistoricalKg) {
            return (round(weight * 10f) / 10f).coerceAtLeast(0f)
        }
        val eq = equipment.lowercase()

        return when {
            eq.contains("halter") || eq.contains("dumbbell") -> {
                val rawInt = kotlin.math.round(weight).toInt()
                if (rawInt <= 10) {
                    rawInt.coerceAtLeast(1).toFloat()
                } else {
                    val even = if (rawInt % 2 != 0) rawInt + 1 else rawInt
                    even.coerceAtLeast(12).toFloat()
                }
            }
            eq.contains("polia") || eq.contains("cabo") || eq.contains("cable") || eq.contains("placa") -> {
                // Preserva cargas inteiras vindas de pilhas em libras (ex: 61 kg = 135 lbs) se já forem inteiras
                if (kotlin.math.abs(weight - round(weight)) < 0.05f && round(weight).toInt() % 5 != 0) {
                    return round(weight)
                }
                val rounded = kotlin.math.round(weight / 5.0f) * 5.0f
                rounded.coerceAtLeast(5.0f)
            }
            eq.contains("barra") || eq.contains("smith") || eq.contains("barbell") -> {
                val rounded = round(weight / 2.5f) * 2.5f
                rounded.coerceAtLeast(10.0f)
            }
            eq.contains("máquina") || eq.contains("maquina") || eq.contains("machine") ||
                eq.contains("leg press") || eq.contains("hack") -> {
                // Se o peso já for um inteiro exato consolidado (ex: 61 kg na Cadeira Extensora), preserva sem forçar múltiplo de 2.5
                if (kotlin.math.abs(weight - round(weight)) < 0.05f && (round(weight).toInt() * 10) % 25 != 0) {
                    return round(weight)
                }
                val rounded = round(weight / 2.5f) * 2.5f
                rounded.coerceAtLeast(2.5f)
            }
            eq.contains("peso corporal") || eq.contains("bodyweight") -> {
                val rounded = round(weight * 2f) / 2f
                rounded.coerceAtLeast(0f)
            }
            else -> {
                val rounded = round(weight / 2.5f) * 2.5f
                rounded.coerceAtLeast(0f)
            }
        }
    }

    /**
     * Calcula o próximo peso ao tocar em + ou - respeitando primeiro o Incremento Customizado Estilo Gravl
     * salvo para o exercício (ex: 1 kg, 2 kg, 2,5 kg, 3,2 kg, 5 kg, 10 kg, 20 kg), ou a regra comercial da categoria.
     */
    fun stepWeightForEquipment(
        currentWeight: Float,
        increase: Boolean,
        equipment: String,
        exerciseName: String = "",
        context: Context? = null
    ): Float {
        val key = normalizeKey(exerciseName)
        val hasCustom = hasExplicitCustomStep(context, exerciseName)
        val savedCat = getSavedEquipmentCategory(context, exerciseName, equipment)

        if (hasCustom) {
            val customStep = getSavedWeightStep(context, exerciseName, equipment, currentWeight)
            val rawNext = if (increase) currentWeight + customStep else (currentWeight - customStep).coerceAtLeast(0f)
            return (round(rawNext * 10f) / 10f).coerceAtLeast(0f)
        }

        val eq = (equipment + " " + savedCat + " " + inferEquipment(exerciseName, equipment)).lowercase()

        if (eq.contains("halter") || eq.contains("dumbbell")) {
            val currentInt = kotlin.math.round(currentWeight).toInt().coerceAtLeast(1)
            return if (increase) {
                if (currentInt < 10) {
                    (currentInt + 1).toFloat()
                } else {
                    val evenBase = if (currentInt % 2 != 0) currentInt - 1 else currentInt
                    (evenBase + 2).toFloat()
                }
            } else {
                if (currentInt <= 10) {
                    (currentInt - 1).coerceAtLeast(1).toFloat()
                } else {
                    val nextDown = if (currentInt % 2 != 0) currentInt - 1 else currentInt - 2
                    nextDown.coerceAtLeast(10).toFloat()
                }
            }
        }

        val step = getSavedWeightStep(context, exerciseName, equipment, currentWeight)
        val rawNext = if (increase) {
            currentWeight + step
        } else {
            (currentWeight - step).coerceAtLeast(0f)
        }
        return (round(rawNext * 10f) / 10f).coerceAtLeast(0f)
    }

    fun calculateBiometricStartingWeight(
        exerciseName: String,
        equipment: String,
        weightKg: Float = 75f,
        isFemale: Boolean = false,
        age: Int = 30,
        experienceLevel: String = "Intermediário"
    ): Float {
        val inferredEq = inferEquipment(exerciseName, equipment)
        val baseExerciseWeight = baseReferenceWeightForExercise(exerciseName, inferredEq)
        if (baseExerciseWeight <= 0f) return 0f

        val nameLower = exerciseName.lowercase()
        val isLowerBody = nameLower.contains("leg") || nameLower.contains("agachamento") ||
            nameLower.contains("terra") || nameLower.contains("stiff") ||
            nameLower.contains("romeno") || nameLower.contains("rdl") ||
            nameLower.contains("extensora") || nameLower.contains("flexora") ||
            nameLower.contains("panturrilha") || nameLower.contains("pélvica") ||
            nameLower.contains("pelvica") || nameLower.contains("hip thrust") ||
            nameLower.contains("abdutora") || nameLower.contains("adutora") ||
            nameLower.contains("glúteo") || nameLower.contains("gluteo") ||
            nameLower.contains("hack") || nameLower.contains("passada") ||
            nameLower.contains("afundo") || nameLower.contains("búlgaro") ||
            nameLower.contains("bulgaro") || nameLower.contains("avanço")

        val clampedWeight = weightKg.coerceIn(40f, 150f)
        val weightFactor = 0.45f + 0.55f * (clampedWeight / 75.0f)

        val genderFactor = if (isFemale) {
            if (isLowerBody) 0.72f else 0.52f
        } else {
            1.0f
        }

        val ageFactor = if (age > 45) {
            (1.0f - ((age - 45) * 0.006f)).coerceIn(0.75f, 1.0f)
        } else {
            1.0f
        }

        val expFactor = when (experienceLevel.lowercase().trim()) {
            "iniciante", "beginner", "iniciante / recreativo" -> 0.70f
            "avançado", "avancado", "advanced", "elite" -> 1.22f
            else -> 1.0f
        }

        val calculated = baseExerciseWeight * weightFactor * genderFactor * ageFactor * expFactor
        val rounded = roundToAvailableWeight(calculated, inferredEq)
        return if (inferredEq.contains("halter", ignoreCase = true) || inferredEq.contains("dumbbell", ignoreCase = true)) {
            rounded.coerceAtMost(MAX_INITIAL_SINGLE_DUMBBELL_KG)
        } else {
            rounded
        }
    }

    fun calculateProgressedWeight(
        baseWeight: Float,
        progressionType: String = "Linear",
        equipment: String = "Barra",
        isLowerBodyCompound: Boolean = false,
        gymProfile: GymProfile? = null
    ): Float {
        if (baseWeight <= 0f) return defaultStartingWeightForEquipment(equipment)

        val increment = when (progressionType.lowercase()) {
            "manutencao", "manutenção", "maintenance" -> 0.0f
            "descarregamento", "deload" -> -(baseWeight * 0.10f)
            else -> {
                val eq = equipment.lowercase()
                when {
                    eq.contains("halter") || eq.contains("dumbbell") -> 2.0f
                    isLowerBodyCompound -> 5.0f
                    else -> 2.5f
                }
            }
        }

        val targetWeight = baseWeight + increment
        return roundToAvailableWeight(targetWeight, equipment, gymProfile)
    }

    /**
     * Infers equipment category ("Barra", "Halteres", "Polia", "Máquina", "Peso Corporal", "Smith")
     * based on saved preference, exercise name, and explicit equipment tag.
     */
    fun inferEquipment(exerciseName: String, equipment: String? = null): String {
        val key = normalizeKey(exerciseName)
        val savedDetailed = categoryCache[key]
        if (!savedDetailed.isNullOrBlank()) {
            val lower = savedDetailed.lowercase(Locale.ROOT)
            return when {
                lower.contains("halter") -> "Halteres"
                lower.contains("polia") || lower.contains("cabo") -> "Polia"
                lower.contains("barra") || lower.contains("smith") -> "Barra"
                lower.contains("leg press") || lower.contains("máquina") || lower.contains("articulad") -> "Máquina"
                else -> savedDetailed
            }
        }

        val eqLower = (equipment ?: "").lowercase()
        val primaryName = exerciseName.substringBefore("(").lowercase()
        val fullLower = exerciseName.lowercase()

        when {
            primaryName.contains("smith") -> return "Smith"
            (primaryName.contains("barra fixa") || primaryName.contains("peso corporal") || primaryName.contains("solo") ||
                primaryName.contains("prancha") || primaryName.contains("flexão") || primaryName.contains("flexao") ||
                (primaryName.contains("abdominal") && !primaryName.contains("máquina") && !primaryName.contains("polia") && !primaryName.contains("cabo"))) -> return "Peso Corporal"
            (primaryName.contains("agachamento") && (primaryName.contains("sumô") || primaryName.contains("sumo") || primaryName.contains("goblet") || primaryName.contains("globet")) && !primaryName.contains("barra")) -> return "Halteres"
            primaryName.contains("barra") || primaryName.contains("barbell") || primaryName.contains("rdl") || primaryName.contains("romeno") -> return "Barra"
            primaryName.contains("halter") || primaryName.contains("dumbbell") || primaryName.contains("búlgaro") || primaryName.contains("bulgaro") || primaryName.contains("arnold") || primaryName.contains("serrote") || primaryName.contains("martelo") -> return "Halteres"
            primaryName.contains("polia") || primaryName.contains("cabo") || primaryName.contains("cable") || primaryName.contains("pulley") || primaryName.contains("corda") || primaryName.contains("crossover") || primaryName.contains("cross over") -> return "Polia"
            primaryName.contains("máquina") || primaryName.contains("maquina") || primaryName.contains("machine") || primaryName.contains("articulad") || primaryName.contains("anilha") || primaryName.contains("total health") || primaryName.contains("leg press") || primaryName.contains("cadeira") || primaryName.contains("mesa") || primaryName.contains("hack") || primaryName.contains("voador") || primaryName.contains("pec deck") -> return "Máquina"
        }

        if (eqLower.contains("smith")) return "Smith"
        if (eqLower.contains("barra") || eqLower.contains("barbell")) return "Barra"
        if (eqLower.contains("halter") || eqLower.contains("dumbbell")) return "Halteres"
        if (eqLower.contains("polia") || eqLower.contains("cabo") || eqLower.contains("cable") || eqLower.contains("torre")) return "Polia"
        if (eqLower.contains("máquina") || eqLower.contains("maquina") || eqLower.contains("machine") || eqLower.contains("placa") || eqLower.contains("leg press")) return "Máquina"
        if (eqLower.contains("peso corporal") || eqLower.contains("bodyweight")) return "Peso Corporal"

        return when {
            fullLower.contains("smith") -> "Smith"
            (fullLower.contains("agachamento") && (fullLower.contains("sumô") || fullLower.contains("sumo") || fullLower.contains("goblet") || fullLower.contains("globet"))) ||
                fullLower.contains("halter") || fullLower.contains("dumbbell") ||
                fullLower.contains("crucifixo") || fullLower.contains("afundo") || fullLower.contains("passada") ||
                fullLower.contains("rosca alternada") || fullLower.contains("elevação lateral") || fullLower.contains("encolhimento") ||
                fullLower.contains("tríceps coice") || fullLower.contains("tríceps francês") -> "Halteres"

            fullLower.contains("pulley") || fullLower.contains("polia") || fullLower.contains("cabo") ||
                fullLower.contains("cable") || fullLower.contains("cross over") || fullLower.contains("crossover") ||
                fullLower.contains("tríceps corda") || fullLower.contains("remada baixa") || fullLower.contains("puxada") || fullLower.contains("torre") -> "Polia"

            fullLower.contains("leg press") || fullLower.contains("máquina") || fullLower.contains("maquina") ||
                fullLower.contains("extensora") || fullLower.contains("flexora") || fullLower.contains("hack") ||
                fullLower.contains("panturrilha") || fullLower.contains("cadeira") || fullLower.contains("mesa flexora") ||
                fullLower.contains("voador") || fullLower.contains("pec deck") || fullLower.contains("apoio no peito") -> "Máquina"

            fullLower.contains("abdominal") || fullLower.contains("solo") || fullLower.contains("flexão") || fullLower.contains("flexao") ||
                fullLower.contains("prancha") || fullLower.contains("suspenso") || fullLower.contains("paralela") ||
                fullLower.contains("barra fixa") || fullLower.contains("afundo corporal") -> "Peso Corporal"

            fullLower.contains("barra") || fullLower.contains("barbell") ||
                fullLower.contains("supino") || fullLower.contains("agachamento livre") || fullLower.contains("agachamento") ||
                fullLower.contains("levantamento terra") || fullLower.contains("terra") ||
                fullLower.contains("stiff") || fullLower.contains("remada curvada") ||
                fullLower.contains("rosca direta") || fullLower.contains("elevação pélvica") || fullLower.contains("hip thrust") -> "Barra"

            else -> if (equipment.isNullOrBlank() || equipment.equals("Outros", ignoreCase = true)) "Barra" else equipment
        }
    }
}
