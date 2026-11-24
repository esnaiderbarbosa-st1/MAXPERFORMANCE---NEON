package com.example.domain.recomp

import android.content.Context
import com.example.data.model.BioimpedanceIndicatorAssessment
import com.example.data.model.BioimpedanceRecord
import com.example.data.model.IndicatorStatus
import com.example.data.model.PhysiologicalGoalProgress
import com.example.data.model.RecompClassification
import com.example.data.model.RecompDeltaItem
import com.example.data.model.RecompStepCardSummary
import com.example.data.model.UserProfileData
import com.example.data.model.calculateAutoBmiOrNull
import com.example.data.model.resolveValidHeightCmOrNull
import com.example.data.model.resolveValidWeightKgOrNull
import com.example.utils.WeightCalculator
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Motor independente de Recomposição Corporal (`MAX RECOMP`) para balanças de bioimpedância
 * padrão **Fitdays** e **Relaxmedic**.
 *
 * Responsabilidades:
 * 1. Persistência JSON autônoma em `SharedPreferences` (`"max_recomp_prefs"`) com seed e migração
 *    automática da linha do tempo real de 3 etapas do atleta Esnaider (1,87 m, 50 anos):
 *    - Marco Zero (`30/04/2026 - 06:30` • Histórico Relaxmedic • `95,9 kg`)
 *    - Check-in #2 (`13/10/2026 - 06:30` • Relaxmedic Oficial • `85,7 kg`)
 *    - Check-in #3 Atual (`29/10/2026 - 06:32` • Fitdays Oficial • `84,7 kg`)
 * 2. Parser inteligente (`parseFitdaysTextOrImageMetadata`) de textos/relatórios colados ou OCR
 *    do Fitdays / Relaxmedic em português, com conversão automática `kg -> %` para relatórios Relaxmedic.
 * 3. Avaliação individual dos 14 indicadores fisiológicos e barras de progresso das 4 metas-chave.
 * 4. Construção da linha do tempo (`buildRecompTimelineCards`) com cálculo de Ganhos vs. Reduções,
 *    classificação de Recomposição Corporal e alerta de condutividade elétrica da água corporal.
 */
object BodyRecompEngine {

    private const val PREFS_NAME = "max_recomp_prefs"
    private const val KEY_RECORDS_JSON = "bioimpedance_records_json_v1"
    private const val KEY_TIMELINE_MIGRATION_VERSION = "timeline_migration_version"
    private const val CURRENT_MIGRATION_VERSION = 4 // v4: deduplicação de check-ins no mesmo dia + reparo Tronco/Massa Gorda/Data

    // Metas fisiológicas pessoais de referência (usadas apenas quando BioimpedanceReferences.hasPersonalGoals(profile) == true)
    const val TARGET_WATER_LITERS_PER_DAY = 4.2f
    const val TARGET_BODY_FAT_PERCENT = 18.0f
    const val TARGET_VISCERAL_FAT_INDEX = 9.0f
    const val TARGET_SKELETAL_MUSCLE_PERCENT = 45.0f
    const val TARGET_BODY_WATER_PERCENT = 55.0f

    /**
     * 1. Marco Zero (`30/04/2026 - 06:30 • Histórico Relaxmedic` — início da jornada):
     * Peso: 95.9 kg | IMC: 27.4 | Gordura Corporal: 27.3% (26.2 kg) | Taxa Muscular: 68.8% |
     * Massa Livre de Gordura: 69.7 kg | Gordura Subcutânea: 19.4% | Gordura Visceral: 13 |
     * Água Corporal: 50.2% | Músculo Esquelético: 38.9% (37.3 kg) | Massa Muscular: 66.0 kg |
     * Massa Óssea: 3.7 kg | Proteína: 18.6% | TMB: 1910 kcal | Idade Metabólica: 52 anos.
     */
    fun createOfficialBaselineRecord(): BioimpedanceRecord {
        val ts = BioimpedanceImageParser.buildTimestamp(30, 4, 2026, 6, 30)
        return BioimpedanceRecord(
            id = "baseline_esnaider_20260430_0630",
            timestampMillis = ts,
            dateTimeLabel = "30/04/2026 - 06:30",
            athleteName = "Esnaider",
            sourceApp = "Histórico Relaxmedic",
            weightKg = 95.9f,
            bmi = 27.4f,
            bodyFatPercent = 27.3f,
            muscleRatePercent = 68.8f,
            fatFreeMassKg = 69.7f,
            subcutaneousFatPercent = 19.4f,
            visceralFatIndex = 13.0f,
            bodyWaterPercent = 50.2f,
            skeletalMusclePercent = 38.9f,
            muscleMassKg = 66.0f,
            boneMassKg = 3.7f,
            proteinPercent = 18.6f,
            bmrKcal = 1910,
            metabolicAge = 52,
            segmentalSummary = "Marco Zero • Início da jornada de recomposição híbrida (95,9 kg • 27,3% BF)"
        )
    }

    /**
     * 2. Check-in #2 (`13/10/2026 - 06:30 • Relaxmedic Oficial`):
     * Peso: 85.7 kg | IMC: 24.5 | Gordura Corporal: 23.3% (20.0 kg) | Taxa Muscular: 72.7% |
     * Massa Livre de Gordura: 65.7 kg | Gordura Subcutânea: 16.7% | Gordura Visceral: 11 |
     * Água Corporal: 52.4% (44.9 kg) | Músculo Esquelético: 40.6% (34.8 kg) | Massa Muscular: 62.3 kg |
     * Massa Óssea: 3.4 kg | Proteína: 20.3% (17.4 kg) | TMB: 1789 kcal | Idade Metabólica: 49 anos |
     * Dados Segmentares: Braços 1.1 kg musc / 1.1 kg gord, Tronco 9.3 kg musc / 9.4 kg gord, Pernas 3.4 kg musc / 3.3 kg gord.
     */
    fun createOfficialCheckIn2RelaxmedicRecord(): BioimpedanceRecord {
        val ts = BioimpedanceImageParser.buildTimestamp(13, 10, 2026, 6, 30)
        return BioimpedanceRecord(
            id = "checkin2_esnaider_20261013_0630_relaxmedic",
            timestampMillis = ts,
            dateTimeLabel = "13/10/2026 - 06:30",
            athleteName = "Esnaider",
            sourceApp = "Relaxmedic Oficial",
            weightKg = 85.7f,
            bmi = 24.5f,
            bodyFatPercent = 23.3f,
            muscleRatePercent = 72.7f,
            fatFreeMassKg = 65.7f,
            subcutaneousFatPercent = 16.7f,
            visceralFatIndex = 11.0f,
            bodyWaterPercent = 52.4f,
            skeletalMusclePercent = 40.6f,
            muscleMassKg = 62.3f,
            boneMassKg = 3.4f,
            proteinPercent = 20.3f,
            bmrKcal = 1789,
            metabolicAge = 49,
            segmentalSummary = "Segmentar Relaxmedic • Braços: 1,1 kg músc / 1,1 kg gord • Tronco: 9,3 kg músc / 9,4 kg gord • Pernas: 3,4 kg músc / 3,3 kg gord"
        )
    }

    /**
     * 3. Check-in #3 Atual (`29/10/2026 - 06:32 • Fitdays Oficial`):
     * Peso: 84.7 kg | IMC: 24.5 | Gordura Corporal: 23.0% (19.5 kg) | Taxa Muscular: 73.0% |
     * Massa Livre de Gordura: 65.2 kg | Gordura Subcutânea: 16.5% | Gordura Visceral: 11 |
     * Água Corporal: 52.8% | Músculo Esquelético: 40.9% (34.6 kg) | Massa Muscular: 61.8 kg |
     * Massa Óssea: 3.4 kg | Proteína: 20.2% | TMB: 1778 kcal | Idade Metabólica: 49 anos |
     * Dados Segmentares (Limb Dados): Braços 1.1 kg musc (5.6% gord), Tronco 9.2 kg musc (45.7% gord), Pernas 3.4 kg musc (17.0% gord).
     */
    fun createOfficialCheckIn3FitdaysRecord(): BioimpedanceRecord {
        val ts = BioimpedanceImageParser.buildTimestamp(29, 10, 2026, 6, 32)
        return BioimpedanceRecord(
            id = "checkin3_esnaider_20261029_0632_fitdays",
            timestampMillis = ts,
            dateTimeLabel = "29/10/2026 - 06:32",
            athleteName = "Esnaider",
            sourceApp = "Fitdays Oficial",
            weightKg = 84.7f,
            bmi = 24.5f,
            bodyFatPercent = 23.0f,
            muscleRatePercent = 73.0f,
            fatFreeMassKg = 65.2f,
            subcutaneousFatPercent = 16.5f,
            visceralFatIndex = 11.0f,
            bodyWaterPercent = 52.8f,
            skeletalMusclePercent = 40.9f,
            muscleMassKg = 61.8f,
            boneMassKg = 3.4f,
            proteinPercent = 20.2f,
            bmrKcal = 1778,
            metabolicAge = 49,
            segmentalSummary = "Limb Dados Fitdays • Braços: 1,1 kg músc (5,6% gord) • Tronco: 9,2 kg músc (45,7% gord) • Pernas: 3,4 kg músc (17,0% gord)"
        )
    }

    /**
     * Retorna a linha do tempo oficial completa de 3 medições reais do atleta Esnaider:
     * 30/04/2026 (95.9 kg) -> 13/10/2026 (85.7 kg) -> 29/10/2026 (84.7 kg).
     */
    fun createOfficialDefaultTimeline(): List<BioimpedanceRecord> = listOf(
        createOfficialBaselineRecord(),
        createOfficialCheckIn2RelaxmedicRecord(),
        createOfficialCheckIn3FitdaysRecord()
    )

    /**
     * Cria um registro em branco vinculado ao perfil ativo (nome, foto e opcionalmente peso/IMC do perfil),
     * sem herdar métricas de outros perfis. Quando `includeProfileWeight = false` (ex.: falha de OCR),
     * todas as medições ficam zeradas (exibidas como "--").
     */
    fun createBlankProfileRecord(
        profile: UserProfileData?,
        nowMillis: Long = System.currentTimeMillis(),
        includeProfileWeight: Boolean = true,
        photoUri: String? = null
    ): BioimpedanceRecord {
        val sdf = SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale.forLanguageTag("pt-BR"))
        val weightKg = if (includeProfileWeight) resolveValidWeightKgOrNull(profile) ?: 0f else 0f
        val heightCm = resolveValidHeightCmOrNull(profile)
        val bmi = if (includeProfileWeight) {
            calculateAutoBmiOrNull(weightKg.takeIf { it > 0f }, heightCm)?.let { round1(it) } ?: 0f
        } else {
            0f
        }
        val resolvedPhotoUri = photoUri?.takeIf { it.isNotBlank() }
            ?: profile?.foto?.takeIf { it.isNotBlank() }
        return BioimpedanceRecord(
            id = "recomp_blank_${nowMillis}",
            timestampMillis = nowMillis,
            dateTimeLabel = sdf.format(Date(nowMillis)),
            athleteName = profile?.nome?.takeIf { it.isNotBlank() } ?: "Atleta",
            sourceApp = "Sem registro",
            weightKg = weightKg,
            bmi = bmi,
            bodyFatPercent = 0f,
            muscleRatePercent = 0f,
            fatFreeMassKg = 0f,
            subcutaneousFatPercent = 0f,
            visceralFatIndex = 0f,
            bodyWaterPercent = 0f,
            skeletalMusclePercent = 0f,
            muscleMassKg = 0f,
            boneMassKg = 0f,
            proteinPercent = 0f,
            bmrKcal = 0,
            metabolicAge = 0,
            photoUri = resolvedPhotoUri,
            segmentalSummary = ""
        )
    }

    /**
     * Retorna um registro em branco vinculado ao perfil ativo (sem injetar dados históricos de terceiros).
     */
    fun defaultFallbackRecord(
        profile: UserProfileData? = WeightCalculator.activeUserProfile,
        nowMillis: Long = System.currentTimeMillis()
    ): BioimpedanceRecord {
        return createBlankProfileRecord(profile, nowMillis)
    }

    /**
     * Carrega todos os registros de bioimpedância ordenados cronologicamente (do Marco Zero ao mais recente).
     * Histórico vazio permanece vazio — nunca injeta automaticamente registros de terceiros.
     */
    fun loadRecords(
        context: Context,
        profile: UserProfileData? = WeightCalculator.activeUserProfile
    ): List<BioimpedanceRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_RECORDS_JSON, null)
        val savedMigrationVer = prefs.getInt(KEY_TIMELINE_MIGRATION_VERSION, 0)

        if (rawJson.isNullOrBlank()) {
            return emptyList()
        }

        return try {
            val array = JSONArray(rawJson)
            val list = mutableListOf<BioimpedanceRecord>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(fromJson(obj, profile))
            }
            val healed = migrateOrFixDuplicateBaselineRecords(
                loaded = list,
                savedMigrationVersion = savedMigrationVer,
                profile = profile
            )
            if (healed != list || savedMigrationVer < CURRENT_MIGRATION_VERSION) {
                saveRecords(context, healed)
                prefs.edit().putInt(KEY_TIMELINE_MIGRATION_VERSION, CURRENT_MIGRATION_VERSION).apply()
            }
            healed.lastOrNull()?.let { syncGlobalAthleteWeightAndBmr(it) }
            healed
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Rotina de migração/deduplicação:
     * Limpa registros legados inválidos (92.4 kg) e deduplica check-ins repetidos no mesmo dia,
     * sem nunca semear registros históricos de terceiros.
     */
    internal fun migrateOrFixDuplicateBaselineRecords(
        loaded: List<BioimpedanceRecord>,
        savedMigrationVersion: Int,
        profile: UserProfileData? = WeightCalculator.activeUserProfile
    ): List<BioimpedanceRecord> {
        if (loaded.isEmpty()) return emptyList()

        // Remove todos os registros legados de 92.4 kg (que geravam variação 0.0 kg)
        val nonLegacyRecords = loaded.filterNot { rec ->
            abs(rec.weightKg - 92.4f) < 0.08f ||
                rec.id.contains("20260528") ||
                rec.dateTimeLabel.contains("28/05/2026")
        }

        val baseList = nonLegacyRecords.sortedBy { it.timestampMillis }

        // Deduplicação inteligente: se houver múltiplos check-ins no mesmo dia (ex: 03/11/2026) e mesmo peso (ex: 85,4 kg),
        // ou registros consecutivos idênticos (mesmo peso + mesmo BF%), mantém apenas o registro mais completo/atualizado.
        val deduplicated = mutableListOf<BioimpedanceRecord>()
        for (rec in baseList) {
            val recDate = extractDateOnly(rec.dateTimeLabel)
            val existingSameDayIdx = deduplicated.indexOfFirst { existing ->
                val existingDate = extractDateOnly(existing.dateTimeLabel)
                (existingDate.isNotEmpty() && existingDate == recDate && abs(existing.weightKg - rec.weightKg) < 0.08f) ||
                    (existing.id != "baseline_esnaider_20260430_0630" &&
                        abs(existing.weightKg - rec.weightKg) < 0.05f &&
                        abs(existing.bodyFatPercent - rec.bodyFatPercent) < 0.05f &&
                        existing.bmrKcal == rec.bmrKcal)
            }
            if (existingSameDayIdx >= 0) {
                val prev = deduplicated[existingSameDayIdx]
                // Preserva o horário real do exame (ex: 06:05) caso um dos registros tivesse hora de upload
                val preferredDateLabel = if (prev.dateTimeLabel.contains("06:05")) prev.dateTimeLabel else rec.dateTimeLabel
                val preferredTs = BioimpedanceImageParser.parseDateTimeMillis(preferredDateLabel, rec.timestampMillis)
                deduplicated[existingSameDayIdx] = rec.copy(peso = rec.weightKg).let {
                    BioimpedanceRecord(
                        id = prev.id,
                        timestampMillis = preferredTs,
                        dateTimeLabel = preferredDateLabel,
                        athleteName = rec.athleteName,
                        sourceApp = rec.sourceApp,
                        weightKg = rec.weightKg,
                        bmi = rec.bmi,
                        bodyFatPercent = rec.bodyFatPercent,
                        muscleRatePercent = rec.muscleRatePercent,
                        fatFreeMassKg = rec.fatFreeMassKg,
                        subcutaneousFatPercent = rec.subcutaneousFatPercent,
                        visceralFatIndex = rec.visceralFatIndex,
                        bodyWaterPercent = rec.bodyWaterPercent,
                        skeletalMusclePercent = rec.skeletalMusclePercent,
                        muscleMassKg = rec.muscleMassKg,
                        boneMassKg = rec.boneMassKg,
                        proteinPercent = rec.proteinPercent,
                        bmrKcal = rec.bmrKcal,
                        metabolicAge = rec.metabolicAge,
                        photoUri = rec.photoUri ?: prev.photoUri,
                        segmentalSummary = rec.segmentalSummary.ifBlank { prev.segmentalSummary },
                        explicitFatMassKg = rec.explicitFatMassKg ?: prev.explicitFatMassKg
                    )
                }
            } else {
                deduplicated.add(rec)
            }
        }

        return if (deduplicated.isEmpty()) {
            emptyList()
        } else {
            deduplicated.sortedBy { it.timestampMillis }
        }
    }

    private fun extractDateOnly(dateTimeLabel: String): String {
        val m = Regex("([0-3]\\d/[0-1]\\d/20\\d{2})").find(dateTimeLabel)
        return m?.groupValues?.getOrNull(1) ?: dateTimeLabel.take(10).trim()
    }

    /**
     * Sincroniza imediatamente o peso atual (`84.7 kg`) e a TMB (`1778 kcal`) do atleta com o perfil
     * global em memória (`WeightCalculator.activeUserProfile`) usado pelo `MAX RUN` e `MAX GYM`.
     */
    fun syncGlobalAthleteWeightAndBmr(latest: BioimpedanceRecord) {
        runCatching {
            val currentProfile = WeightCalculator.activeUserProfile
                ?: com.example.data.model.UserProfileData.PRESET_ESNAIDER
            WeightCalculator.activeUserProfile = currentProfile.copy(
                peso = latest.weightKg,
                weightKg = latest.weightKg,
                imc = latest.bmi,
                percentualGordura = latest.bodyFatPercent,
                taxaMuscular = latest.muscleRatePercent,
                massaMagra = latest.fatFreeMassKg,
                gorduraSubcutanea = latest.subcutaneousFatPercent,
                gorduraVisceral = latest.visceralFatIndex,
                aguaCorporal = latest.bodyWaterPercent,
                massaMuscularEsqueletica = latest.skeletalMusclePercent,
                massaMuscular = latest.muscleMassKg,
                massaOssea = latest.boneMassKg,
                proteina = latest.proteinPercent,
                tmb = latest.bmrKcal,
                idadeMetabolica = latest.metabolicAge
            )
        }
    }

    /**
     * Salva a lista completa de registros de bioimpedância em `SharedPreferences`.
     */
    fun saveRecords(context: Context, records: List<BioimpedanceRecord>) {
        val sorted = records.sortedBy { it.timestampMillis }
        val array = JSONArray()
        sorted.forEach { record ->
            array.put(toJson(record))
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECORDS_JSON, array.toString())
            .putInt(KEY_TIMELINE_MIGRATION_VERSION, CURRENT_MIGRATION_VERSION)
            .apply()
        sorted.lastOrNull()?.let { syncGlobalAthleteWeightAndBmr(it) }
    }

    /**
     * Adiciona ou atualiza uma medição de bioimpedância com deduplicação inteligente:
     * Se o atleta importar um relatório com a mesma data (`dd/MM/yyyy`) e mesmo peso (`85,4 kg`)
     * de um check-in já salvo no mesmo dia, sobrescreve/atualiza o registro daquele dia!
     */
    fun upsertRecord(context: Context, record: BioimpedanceRecord): List<BioimpedanceRecord> {
        val current = loadRecords(context).toMutableList()
        val newDateOnly = extractDateOnly(record.dateTimeLabel)

        val existingIdx = current.indexOfFirst { existing ->
            val existingDateOnly = extractDateOnly(existing.dateTimeLabel)
            existing.id == record.id ||
                (existingDateOnly.isNotEmpty() &&
                    existingDateOnly == newDateOnly &&
                    abs(existing.weightKg - record.weightKg) < 0.08f) ||
                (existing.id != "baseline_esnaider_20260430_0630" &&
                    abs(existing.weightKg - record.weightKg) < 0.05f &&
                    abs(existing.bodyFatPercent - record.bodyFatPercent) < 0.05f &&
                    existing.bmrKcal == record.bmrKcal)
        }

        if (existingIdx >= 0) {
            val existingId = current[existingIdx].id
            current[existingIdx] = BioimpedanceRecord(
                id = existingId,
                timestampMillis = record.timestampMillis,
                dateTimeLabel = record.dateTimeLabel,
                athleteName = record.athleteName,
                sourceApp = record.sourceApp,
                weightKg = record.weightKg,
                bmi = record.bmi,
                bodyFatPercent = record.bodyFatPercent,
                muscleRatePercent = record.muscleRatePercent,
                fatFreeMassKg = record.fatFreeMassKg,
                subcutaneousFatPercent = record.subcutaneousFatPercent,
                visceralFatIndex = record.visceralFatIndex,
                bodyWaterPercent = record.bodyWaterPercent,
                skeletalMusclePercent = record.skeletalMusclePercent,
                muscleMassKg = record.muscleMassKg,
                boneMassKg = record.boneMassKg,
                proteinPercent = record.proteinPercent,
                bmrKcal = record.bmrKcal,
                metabolicAge = record.metabolicAge,
                photoUri = record.photoUri ?: current[existingIdx].photoUri,
                segmentalSummary = record.segmentalSummary.ifBlank { current[existingIdx].segmentalSummary },
                explicitFatMassKg = record.explicitFatMassKg
            )
        } else {
            current.add(record)
        }
        val sorted = current.sortedBy { it.timestampMillis }
        saveRecords(context, sorted)
        return sorted
    }

    /**
     * Remove um registro pelo ID, permitindo esvaziar o histórico sem re-injetar dados de terceiros.
     */
    fun deleteRecord(
        context: Context,
        recordId: String,
        profile: UserProfileData? = WeightCalculator.activeUserProfile
    ): List<BioimpedanceRecord> {
        val current = loadRecords(context, profile).filterNot { it.id == recordId }
        saveRecords(context, current)
        return current
    }

    /**
     * Cria um rascunho pré-preenchido com os valores da última pesagem para facilitar o
     * Check-in Rápido Semanal (ou um registro em branco do perfil quando não houver histórico).
     */
    fun createWeeklyCheckInDraft(
        latestRecord: BioimpedanceRecord?,
        nowMillis: Long = System.currentTimeMillis(),
        profile: UserProfileData? = WeightCalculator.activeUserProfile
    ): BioimpedanceRecord {
        val base = latestRecord ?: defaultFallbackRecord(profile, nowMillis)
        val sdf = SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale.forLanguageTag("pt-BR"))
        return BioimpedanceRecord(
            id = "recomp_${nowMillis}",
            timestampMillis = nowMillis,
            dateTimeLabel = sdf.format(Date(nowMillis)),
            athleteName = base.athleteName,
            sourceApp = base.sourceApp,
            weightKg = base.weightKg,
            bmi = base.bmi,
            bodyFatPercent = base.bodyFatPercent,
            muscleRatePercent = base.muscleRatePercent,
            fatFreeMassKg = base.fatFreeMassKg,
            subcutaneousFatPercent = base.subcutaneousFatPercent,
            visceralFatIndex = base.visceralFatIndex,
            bodyWaterPercent = base.bodyWaterPercent,
            skeletalMusclePercent = base.skeletalMusclePercent,
            muscleMassKg = base.muscleMassKg,
            boneMassKg = base.boneMassKg,
            proteinPercent = base.proteinPercent,
            bmrKcal = base.bmrKcal,
            metabolicAge = base.metabolicAge,
            photoUri = null,
            segmentalSummary = base.segmentalSummary
        )
    }

    /**
     * Extrai os 14 indicadores de um texto colado ou OCR de relatório Fitdays / Relaxmedic em português.
     * Suporta conversão automática `Relaxmedic` (`kg -> %`) quando Água Corporal, Proteína, Músculo
     * ou Músculo Esquelético estão informados em `kg` em vez de `%`.
     */
    fun parseFitdaysTextOrImageMetadata(
        rawText: String,
        fallbackBase: BioimpedanceRecord? = null,
        nowMillis: Long = System.currentTimeMillis(),
        profileHeightCm: Float? = resolveValidHeightCmOrNull(WeightCalculator.activeUserProfile)?.toFloat(),
        profile: UserProfileData? = WeightCalculator.activeUserProfile
    ): BioimpedanceRecord {
        val base = fallbackBase ?: createBlankProfileRecord(
            profile = profile,
            nowMillis = nowMillis,
            includeProfileWeight = false
        )
        return BioimpedanceImageParser.parseRelaxmedicOrFitdaysStructuredText(
            reconstructedText = rawText,
            fallbackBase = base,
            photoUri = base.photoUri,
            nowMillis = nowMillis,
            profileHeightCm = profileHeightCm
        ).record
    }

    /**
     * Avalia os 14 indicadores da bioimpedância parametrizados pelo perfil ativo:
     * - IMC: faixas da OMS para todos os perfis.
     * - Peso: faixa saudável calculada pela altura válida do perfil (`healthyWeightRangeKg`); sem altura válida, sem referência.
     * - Demais indicadores: referências e status pessoais apenas quando `BioimpedanceReferences.hasPersonalGoals(profile)` é true;
     *   para demais perfis, exibe apenas o valor com "Sem referência" e `IndicatorStatus.NEUTRO`.
     */
    fun evaluateIndicators(
        record: BioimpedanceRecord,
        profile: UserProfileData? = WeightCalculator.activeUserProfile
    ): List<BioimpedanceIndicatorAssessment> {
        val ptBr = Locale.forLanguageTag("pt-BR")
        val hasPersonalGoals = BioimpedanceReferences.hasPersonalGoals(profile)
        val targetBodyFat = profile?.metaPercentualGordura?.takeIf { it > 0f }
        val targetMuscleRate = profile?.metaPercentualMusculo?.takeIf { it > 0f }
        val profileHeightCm = resolveValidHeightCmOrNull(profile)
        val healthyWeightRange = BioimpedanceReferences.healthyWeightRangeKg(profileHeightCm)

        val weightRefText = healthyWeightRange?.let { range ->
            String.format(ptBr, "Ref: %.1f – %.1f kg", range.start, range.endInclusive)
        } ?: "Sem referência"
        val weightStatus = when {
            record.weightKg <= 0f || healthyWeightRange == null -> IndicatorStatus.NEUTRO
            record.weightKg < healthyWeightRange.start -> IndicatorStatus.BAIXO
            record.weightKg <= healthyWeightRange.endInclusive -> IndicatorStatus.SAUDAVEL
            else -> IndicatorStatus.ALTO
        }
        val hasRatioData = record.weightKg > 0f && record.muscleMassKg > 0f && record.bodyFatPercent > 0f

        return listOf(
            BioimpedanceIndicatorAssessment(
                indexNumber = 1,
                titlePt = "Peso",
                formattedValue = if (record.weightKg > 0f) String.format(ptBr, "%.1f kg", record.weightKg) else "--",
                secondaryValue = if (hasRatioData) String.format(ptBr, "Relação M/G: %.2fx", record.leanToFatRatio) else null,
                referenceTargetText = weightRefText,
                status = weightStatus
            ),
            BioimpedanceIndicatorAssessment(
                indexNumber = 2,
                titlePt = "IMC",
                formattedValue = if (record.bmi > 0f) String.format(ptBr, "%.1f", record.bmi) else "--",
                referenceTargetText = "Normal: 18,5 – 24,9",
                status = if (record.bmi > 0f) BioimpedanceReferences.classifyBmi(record.bmi) else IndicatorStatus.NEUTRO
            ),
            BioimpedanceIndicatorAssessment(
                indexNumber = 3,
                titlePt = "Gordura Corporal",
                formattedValue = if (record.bodyFatPercent > 0f) String.format(ptBr, "%.1f%%", record.bodyFatPercent) else "--",
                secondaryValue = if (record.bodyFatPercent > 0f && record.weightKg > 0f) {
                    String.format(ptBr, "%.1f kg lipídios", record.fatMassKg)
                } else null,
                referenceTargetText = targetBodyFat?.let { String.format(ptBr, "Meta < %.1f%%", it) } ?: "Sem referência",
                status = if (targetBodyFat == null || record.bodyFatPercent <= 0f) {
                    IndicatorStatus.NEUTRO
                } else {
                    when {
                        record.bodyFatPercent < 10f -> IndicatorStatus.BAIXO
                        record.bodyFatPercent <= targetBodyFat -> IndicatorStatus.EXCELENTE
                        record.bodyFatPercent <= targetBodyFat + 3f -> IndicatorStatus.MEDIO
                        else -> IndicatorStatus.ALTO
                    }
                }
            ),
            BioimpedanceIndicatorAssessment(
                indexNumber = 4,
                titlePt = "Taxa Muscular",
                formattedValue = if (record.muscleRatePercent > 0f) String.format(ptBr, "%.1f%%", record.muscleRatePercent) else "--",
                referenceTargetText = targetMuscleRate?.let { String.format(ptBr, "Ideal > %.1f%%", it) } ?: "Sem referência",
                status = if (targetMuscleRate == null || record.muscleRatePercent <= 0f) {
                    IndicatorStatus.NEUTRO
                } else {
                    when {
                        record.muscleRatePercent < (targetMuscleRate - 9f) -> IndicatorStatus.BAIXO
                        record.muscleRatePercent < targetMuscleRate -> IndicatorStatus.MEDIO
                        else -> IndicatorStatus.EXCELENTE
                    }
                }
            ),
            BioimpedanceIndicatorAssessment(
                indexNumber = 5,
                titlePt = "Massa Livre de Gordura",
                formattedValue = if (record.fatFreeMassKg > 0f) String.format(ptBr, "%.1f kg", record.fatFreeMassKg) else "--",
                referenceTargetText = if (hasPersonalGoals) "Preservar >= 65,0 kg" else "Sem referência",
                status = if (!hasPersonalGoals || record.fatFreeMassKg <= 0f) {
                    IndicatorStatus.NEUTRO
                } else {
                    if (record.fatFreeMassKg >= 65f) IndicatorStatus.MEDIO else IndicatorStatus.BAIXO
                }
            ),
            BioimpedanceIndicatorAssessment(
                indexNumber = 6,
                titlePt = "Gordura Subcutânea",
                formattedValue = if (record.subcutaneousFatPercent > 0f) String.format(ptBr, "%.1f%%", record.subcutaneousFatPercent) else "--",
                referenceTargetText = if (hasPersonalGoals) "Ideal: 8,6 – 16,7%" else "Sem referência",
                status = if (!hasPersonalGoals || record.subcutaneousFatPercent <= 0f) {
                    IndicatorStatus.NEUTRO
                } else {
                    when {
                        record.subcutaneousFatPercent < 8.6f -> IndicatorStatus.BAIXO
                        record.subcutaneousFatPercent <= 16.7f -> IndicatorStatus.MEDIO
                        else -> IndicatorStatus.ALTO
                    }
                }
            ),
            BioimpedanceIndicatorAssessment(
                indexNumber = 7,
                titlePt = "Gordura Visceral",
                formattedValue = if (record.visceralFatIndex > 0f) String.format(ptBr, "%.0f", record.visceralFatIndex) else "--",
                referenceTargetText = if (hasPersonalGoals) "Saudável <= 9" else "Sem referência",
                status = if (!hasPersonalGoals || record.visceralFatIndex <= 0f) {
                    IndicatorStatus.NEUTRO
                } else {
                    when {
                        record.visceralFatIndex <= 9f -> IndicatorStatus.MEDIO
                        record.visceralFatIndex <= 14f -> IndicatorStatus.MEDIO_RISCO
                        else -> IndicatorStatus.ALTO
                    }
                }
            ),
            BioimpedanceIndicatorAssessment(
                indexNumber = 8,
                titlePt = "Água Corporal",
                formattedValue = if (record.bodyWaterPercent > 0f) String.format(ptBr, "%.1f%%", record.bodyWaterPercent) else "--",
                secondaryValue = if (record.bodyWaterPercent > 0f && record.weightKg > 0f) {
                    String.format(
                        ptBr,
                        "%.1f kg hídricos",
                        BioimpedanceImageParser.convertPercentMetricToKg(record.bodyWaterPercent, record.weightKg)
                    )
                } else null,
                referenceTargetText = if (hasPersonalGoals) "Meta >= 55,0%" else "Sem referência",
                status = if (!hasPersonalGoals || record.bodyWaterPercent <= 0f) {
                    IndicatorStatus.NEUTRO
                } else {
                    when {
                        record.bodyWaterPercent < 55.0f -> IndicatorStatus.DESIDRATADO
                        record.bodyWaterPercent <= 65.0f -> IndicatorStatus.MEDIO
                        else -> IndicatorStatus.EXCELENTE
                    }
                }
            ),
            BioimpedanceIndicatorAssessment(
                indexNumber = 9,
                titlePt = "Músculo Esquelético",
                formattedValue = if (record.skeletalMusclePercent > 0f) String.format(ptBr, "%.1f%%", record.skeletalMusclePercent) else "--",
                secondaryValue = if (record.skeletalMusclePercent > 0f && record.weightKg > 0f) {
                    String.format(ptBr, "%.1f kg contrátil", record.skeletalMuscleKg)
                } else null,
                referenceTargetText = if (hasPersonalGoals) "Meta >= 45,0%" else "Sem referência",
                status = if (!hasPersonalGoals || record.skeletalMusclePercent <= 0f) {
                    IndicatorStatus.NEUTRO
                } else {
                    when {
                        record.skeletalMusclePercent < 40.0f -> IndicatorStatus.BAIXO
                        record.skeletalMusclePercent < 45.0f -> IndicatorStatus.MEDIO
                        else -> IndicatorStatus.EXCELENTE
                    }
                }
            ),
            BioimpedanceIndicatorAssessment(
                indexNumber = 10,
                titlePt = "Massa Muscular",
                formattedValue = if (record.muscleMassKg > 0f) String.format(ptBr, "%.1f kg", record.muscleMassKg) else "--",
                referenceTargetText = if (hasPersonalGoals) "Ref > 60,0 kg" else "Sem referência",
                status = if (!hasPersonalGoals || record.muscleMassKg <= 0f) {
                    IndicatorStatus.NEUTRO
                } else {
                    when {
                        record.muscleMassKg < 58.0f -> IndicatorStatus.BAIXO
                        record.muscleMassKg <= 66.0f -> IndicatorStatus.MEDIO
                        else -> IndicatorStatus.EXCELENTE
                    }
                }
            ),
            BioimpedanceIndicatorAssessment(
                indexNumber = 11,
                titlePt = "Massa Óssea",
                formattedValue = if (record.boneMassKg > 0f) String.format(ptBr, "%.1f kg", record.boneMassKg) else "--",
                referenceTargetText = if (hasPersonalGoals) "Normal: 3,1 – 3,7 kg" else "Sem referência",
                status = if (!hasPersonalGoals || record.boneMassKg <= 0f) {
                    IndicatorStatus.NEUTRO
                } else {
                    when {
                        record.boneMassKg < 2.9f -> IndicatorStatus.BAIXO
                        record.boneMassKg <= 3.9f -> IndicatorStatus.MEDIO
                        else -> IndicatorStatus.EXCELENTE
                    }
                }
            ),
            BioimpedanceIndicatorAssessment(
                indexNumber = 12,
                titlePt = "Proteína",
                formattedValue = if (record.proteinPercent > 0f) String.format(ptBr, "%.1f%%", record.proteinPercent) else "--",
                secondaryValue = if (record.proteinPercent > 0f && record.weightKg > 0f) {
                    String.format(
                        ptBr,
                        "%.1f kg proteicos",
                        BioimpedanceImageParser.convertPercentMetricToKg(record.proteinPercent, record.weightKg)
                    )
                } else null,
                referenceTargetText = if (hasPersonalGoals) "Ideal >= 16,0%" else "Sem referência",
                status = if (!hasPersonalGoals || record.proteinPercent <= 0f) {
                    IndicatorStatus.NEUTRO
                } else {
                    when {
                        record.proteinPercent < 16.0f -> IndicatorStatus.BAIXO
                        record.proteinPercent <= 20.0f -> IndicatorStatus.MEDIO
                        else -> IndicatorStatus.EXCELENTE
                    }
                }
            ),
            BioimpedanceIndicatorAssessment(
                indexNumber = 13,
                titlePt = "TMB (Basal)",
                formattedValue = if (record.bmrKcal > 0) "${record.bmrKcal} kcal" else "--",
                referenceTargetText = if (hasPersonalGoals) "Faixa: 1750+ kcal" else "Sem referência",
                status = if (!hasPersonalGoals || record.bmrKcal <= 0) {
                    IndicatorStatus.NEUTRO
                } else {
                    if (record.bmrKcal >= 1750) IndicatorStatus.MEDIO else IndicatorStatus.BAIXO
                }
            ),
            BioimpedanceIndicatorAssessment(
                indexNumber = 14,
                titlePt = "Idade Metabólica",
                formattedValue = if (record.metabolicAge > 0) "${record.metabolicAge} anos" else "--",
                referenceTargetText = if (hasPersonalGoals) "Menor que 50 anos" else "Sem referência",
                status = if (!hasPersonalGoals || record.metabolicAge <= 0) {
                    IndicatorStatus.NEUTRO
                } else {
                    if (record.metabolicAge < 50) IndicatorStatus.EXCELENTE else IndicatorStatus.ALTO
                }
            )
        )
    }

    /**
     * Constrói as 4 barras de metas fisiológicas estratégicas apenas para perfis com metas pessoais.
     * Para perfis sem metas pessoais, retorna lista vazia (sem calcular barras).
     * O progresso usa o Marco Zero real (`baseline`) quando disponível, ou razão direta em relação à meta
     * (sem pontos de partida fixos).
     */
    fun buildPhysiologicalGoalBars(
        current: BioimpedanceRecord,
        profile: UserProfileData? = WeightCalculator.activeUserProfile,
        baseline: BioimpedanceRecord? = null
    ): List<PhysiologicalGoalProgress> {
        if (!BioimpedanceReferences.hasPersonalGoals(profile)) return emptyList()
        val ptBr = Locale.forLanguageTag("pt-BR")

        fun progressLowerBetter(value: Float, target: Float, start: Float?): Float {
            if (value <= 0f) return 0.08f
            if (value <= target) return 1f
            if (start != null && start > target && start > value) {
                return ((start - value) / (start - target)).coerceIn(0.08f, 1f)
            }
            return (target / value).coerceIn(0.08f, 1f)
        }

        fun progressHigherBetter(value: Float, target: Float, start: Float?): Float {
            if (value <= 0f || target <= 0f) return 0.08f
            if (value >= target) return 1f
            if (start != null && start in 0.1f..<target && value > start) {
                return ((value - start) / (target - start)).coerceIn(0.08f, 1f)
            }
            return (value / target).coerceIn(0.08f, 1f)
        }

        val targetBodyFat = profile?.metaPercentualGordura?.takeIf { it > 0f } ?: TARGET_BODY_FAT_PERCENT
        val bfProgress = progressLowerBetter(current.bodyFatPercent, targetBodyFat, baseline?.bodyFatPercent)
        val bfDiff = (current.bodyFatPercent - targetBodyFat).coerceAtLeast(0f)
        val bfKgToLose = round1((bfDiff / 100f) * current.weightKg)

        val viscProgress = progressLowerBetter(current.visceralFatIndex, TARGET_VISCERAL_FAT_INDEX, baseline?.visceralFatIndex)
        val viscDiff = (current.visceralFatIndex - TARGET_VISCERAL_FAT_INDEX).coerceAtLeast(0f)

        val smProgress = progressHigherBetter(current.skeletalMusclePercent, TARGET_SKELETAL_MUSCLE_PERCENT, baseline?.skeletalMusclePercent)
        val smDiff = (TARGET_SKELETAL_MUSCLE_PERCENT - current.skeletalMusclePercent).coerceAtLeast(0f)

        val waterProgress = progressHigherBetter(current.bodyWaterPercent, TARGET_BODY_WATER_PERCENT, baseline?.bodyWaterPercent)
        val waterDiff = (TARGET_BODY_WATER_PERCENT - current.bodyWaterPercent).coerceAtLeast(0f)

        return listOf(
            PhysiologicalGoalProgress(
                id = "goal_body_fat",
                title = "Gordura Corporal",
                currentValue = current.bodyFatPercent,
                targetValue = targetBodyFat,
                unit = "%",
                isLowerBetter = true,
                progressFraction = bfProgress,
                currentFormatted = String.format(ptBr, "%.1f%% (%.1f kg)", current.bodyFatPercent, current.fatMassKg),
                targetFormatted = String.format(ptBr, "< %.1f%%", targetBodyFat),
                deltaRemainingText = if (bfDiff <= 0f) {
                    "Meta atingida!"
                } else {
                    String.format(ptBr, "Faltam -%.1f%% (~%.1f kg)", bfDiff, bfKgToLose)
                },
                status = if (current.bodyFatPercent <= targetBodyFat) IndicatorStatus.EXCELENTE else IndicatorStatus.ALTO
            ),
            PhysiologicalGoalProgress(
                id = "goal_visceral_fat",
                title = "Gordura Visceral",
                currentValue = current.visceralFatIndex,
                targetValue = TARGET_VISCERAL_FAT_INDEX,
                unit = "pts",
                isLowerBetter = true,
                progressFraction = viscProgress,
                currentFormatted = String.format(ptBr, "Nível %.0f", current.visceralFatIndex),
                targetFormatted = "<= 9",
                deltaRemainingText = if (viscDiff <= 0f) {
                    "Faixa cardioprotetora!"
                } else {
                    String.format(ptBr, "Reduzir -%.0f pts", viscDiff)
                },
                status = if (current.visceralFatIndex <= TARGET_VISCERAL_FAT_INDEX) IndicatorStatus.EXCELENTE else IndicatorStatus.MEDIO_RISCO
            ),
            PhysiologicalGoalProgress(
                id = "goal_skeletal_muscle",
                title = "Músculo Esquelético",
                currentValue = current.skeletalMusclePercent,
                targetValue = TARGET_SKELETAL_MUSCLE_PERCENT,
                unit = "%",
                isLowerBetter = false,
                progressFraction = smProgress,
                currentFormatted = String.format(ptBr, "%.1f%% (%.1f kg)", current.skeletalMusclePercent, current.skeletalMuscleKg),
                targetFormatted = ">= 45,0%",
                deltaRemainingText = if (smDiff <= 0f) {
                    "Meta contrátil atingida!"
                } else {
                    String.format(ptBr, "Subir +%.1f%%", smDiff)
                },
                status = if (current.skeletalMusclePercent >= TARGET_SKELETAL_MUSCLE_PERCENT) IndicatorStatus.EXCELENTE else IndicatorStatus.BAIXO
            ),
            PhysiologicalGoalProgress(
                id = "goal_body_water",
                title = "Água Corporal",
                currentValue = current.bodyWaterPercent,
                targetValue = TARGET_BODY_WATER_PERCENT,
                unit = "%",
                isLowerBetter = false,
                progressFraction = waterProgress,
                currentFormatted = String.format(ptBr, "%.1f%%", current.bodyWaterPercent),
                targetFormatted = ">= 55,0%",
                deltaRemainingText = if (waterDiff <= 0f) {
                    "Hidratação celular ideal!"
                } else {
                    String.format(ptBr, "Elevar +%.1f%%", waterDiff)
                },
                status = if (current.bodyWaterPercent >= TARGET_BODY_WATER_PERCENT) IndicatorStatus.EXCELENTE else IndicatorStatus.DESIDRATADO
            )
        )
    }

    /**
     * Gera a lista de Cards de Degrau de Evolução (`RecompStepCardSummary`) em ordem reversa
     * (mais recente no topo, até o Marco Zero #1 na base) para exibição na Linha do Tempo.
     */
    fun buildRecompTimelineCards(
        records: List<BioimpedanceRecord>,
        profile: UserProfileData? = WeightCalculator.activeUserProfile
    ): List<RecompStepCardSummary> {
        if (records.isEmpty()) return emptyList()
        val sorted = records.sortedBy { it.timestampMillis }
        val baseline = sorted.first()
        val summariesChronological = sorted.mapIndexed { idx, current ->
            // O modo "Vs. Anterior" sempre compara com a última leitura distinta anterior, evitando 0,0% e 0,0 kg
            val previous = if (idx > 0) {
                val currDate = extractDateOnly(current.dateTimeLabel)
                (idx - 1 downTo 0).firstNotNullOfOrNull { prevIdx ->
                    val candidate = sorted[prevIdx]
                    val candDate = extractDateOnly(candidate.dateTimeLabel)
                    if (abs(candidate.weightKg - current.weightKg) >= 0.05f ||
                        abs(candidate.bodyFatPercent - current.bodyFatPercent) >= 0.05f ||
                        candDate != currDate
                    ) {
                        candidate
                    } else {
                        null
                    }
                } ?: sorted[idx - 1]
            } else {
                null
            }
            buildStepSummary(
                stepIndex = idx + 1,
                current = current,
                previous = previous,
                baseline = baseline
            )
        }
        return summariesChronological.reversed()
    }

    /**
     * Calcula todos os deltas de Ganhos e Reduções entre `current` e `previous` (além do acumulado vs `baseline`).
     * Utiliza rótulos enxutos (`"Peso Total"`, `"Gordura Real"`, `"Taxa Muscular"`, `"Músculo Esq."`, `"Água Corporal"`)
     * para evitar qualquer truncamento nos sub-cards da linha do tempo.
     */
    fun buildStepSummary(
        stepIndex: Int,
        current: BioimpedanceRecord,
        previous: BioimpedanceRecord?,
        baseline: BioimpedanceRecord
    ): RecompStepCardSummary {
        val ptBr = Locale.forLanguageTag("pt-BR")
        val isBaseline = previous == null

        val deltaWeightKg = if (previous != null) round1(current.weightKg - previous.weightKg) else 0f
        val deltaFatMassKg = if (previous != null) round1(current.fatMassKg - previous.fatMassKg) else 0f
        val deltaBodyFatPercent = if (previous != null) round1(current.bodyFatPercent - previous.bodyFatPercent) else 0f
        val deltaMuscleRatePercent = if (previous != null) round1(current.muscleRatePercent - previous.muscleRatePercent) else 0f
        val deltaMuscleMassKg = if (previous != null) round1(current.muscleMassKg - previous.muscleMassKg) else 0f
        val deltaSkeletalMuscleKg = if (previous != null) round1(current.skeletalMuscleKg - previous.skeletalMuscleKg) else 0f
        val deltaSkeletalMusclePercent = if (previous != null) round1(current.skeletalMusclePercent - previous.skeletalMusclePercent) else 0f
        val deltaSubcutaneousPercent = if (previous != null) round1(current.subcutaneousFatPercent - previous.subcutaneousFatPercent) else 0f
        val deltaVisceralFat = if (previous != null) round1(current.visceralFatIndex - previous.visceralFatIndex) else 0f
        val deltaWaterPercent = if (previous != null) round1(current.bodyWaterPercent - previous.bodyWaterPercent) else 0f
        val deltaBmrKcal = if (previous != null) current.bmrKcal - previous.bmrKcal else 0
        val deltaMetabolicAge = if (previous != null) current.metabolicAge - previous.metabolicAge else 0

        val cumulativeDeltaWeightKg = round1(current.weightKg - baseline.weightKg)
        val cumulativeDeltaFatMassKg = round1(current.fatMassKg - baseline.fatMassKg)
        val cumulativeDeltaSkeletalMuscleKg = round1(current.skeletalMuscleKg - baseline.skeletalMuscleKg)

        val classification = classifyRecompStep(
            isBaseline = isBaseline,
            deltaWeightKg = deltaWeightKg,
            deltaFatMassKg = deltaFatMassKg,
            deltaBodyFatPercent = deltaBodyFatPercent,
            deltaMuscleRatePercent = deltaMuscleRatePercent,
            deltaMuscleMassKg = deltaMuscleMassKg,
            deltaSkeletalMuscleKg = deltaSkeletalMuscleKg,
            deltaSkeletalMusclePercent = deltaSkeletalMusclePercent,
            deltaWaterPercent = deltaWaterPercent
        )

        val gainsList = mutableListOf<RecompDeltaItem>()
        val lossesList = mutableListOf<RecompDeltaItem>()

        if (isBaseline) {
            gainsList.add(
                RecompDeltaItem(
                    metricLabel = "Massa Muscular",
                    formattedDelta = String.format(ptBr, "%.1f kg (%.1f%%)", current.muscleMassKg, current.muscleRatePercent),
                    isPositivePhysiology = true,
                    highlightColorHex = 0xFF00E676
                )
            )
            gainsList.add(
                RecompDeltaItem(
                    metricLabel = "Músculo Esq.",
                    formattedDelta = String.format(ptBr, "%.1f kg (%.1f%%)", current.skeletalMuscleKg, current.skeletalMusclePercent),
                    isPositivePhysiology = true,
                    highlightColorHex = 0xFF00B0FF
                )
            )
            gainsList.add(
                RecompDeltaItem(
                    metricLabel = "Proteína & TMB",
                    formattedDelta = String.format(ptBr, "%.1f%% • %d kcal", current.proteinPercent, current.bmrKcal),
                    isPositivePhysiology = true,
                    highlightColorHex = 0xFF00E5FF
                )
            )

            lossesList.add(
                RecompDeltaItem(
                    metricLabel = "Gordura Inicial",
                    formattedDelta = String.format(ptBr, "%.1f kg (%.1f%%)", current.fatMassKg, current.bodyFatPercent),
                    isPositivePhysiology = false,
                    highlightColorHex = 0xFFFFB300
                )
            )
            lossesList.add(
                RecompDeltaItem(
                    metricLabel = "Gordura Visceral",
                    formattedDelta = String.format(ptBr, "Nível %.0f (Meta <= 9)", current.visceralFatIndex),
                    isPositivePhysiology = false,
                    highlightColorHex = 0xFFFF7043
                )
            )
            lossesList.add(
                RecompDeltaItem(
                    metricLabel = "Água Celular",
                    formattedDelta = String.format(ptBr, "%.1f%% (Meta >= 55%%)", current.bodyWaterPercent),
                    isPositivePhysiology = false,
                    highlightColorHex = 0xFFFF5252
                )
            )
        } else {
            // 1. Taxa Muscular Relativa (%) e Músculo Esquelético (%)
            if (deltaMuscleRatePercent >= 0f) {
                gainsList.add(
                    RecompDeltaItem(
                        metricLabel = "Taxa Muscular",
                        formattedDelta = String.format(ptBr, "%s%% (%.1f%%)", signedFloat(deltaMuscleRatePercent), current.muscleRatePercent),
                        isPositivePhysiology = true,
                        highlightColorHex = 0xFF00E676
                    )
                )
            } else {
                lossesList.add(
                    RecompDeltaItem(
                        metricLabel = "Taxa Muscular",
                        formattedDelta = String.format(ptBr, "%s%% (%.1f%%)", signedFloat(deltaMuscleRatePercent), current.muscleRatePercent),
                        isPositivePhysiology = false,
                        highlightColorHex = 0xFFFF5252
                    )
                )
            }

            if (deltaSkeletalMusclePercent >= 0f) {
                gainsList.add(
                    RecompDeltaItem(
                        metricLabel = "Músculo Esq.",
                        formattedDelta = String.format(ptBr, "%s%% (%.1f kg)", signedFloat(deltaSkeletalMusclePercent), current.skeletalMuscleKg),
                        isPositivePhysiology = true,
                        highlightColorHex = 0xFF00E676
                    )
                )
            } else {
                lossesList.add(
                    RecompDeltaItem(
                        metricLabel = "Músculo Esq.",
                        formattedDelta = String.format(ptBr, "%s%% (%.1f kg)", signedFloat(deltaSkeletalMusclePercent), current.skeletalMuscleKg),
                        isPositivePhysiology = false,
                        highlightColorHex = 0xFFFF5252
                    )
                )
            }

            // 2. Água Corporal (%)
            if (deltaWaterPercent >= 0f) {
                gainsList.add(
                    RecompDeltaItem(
                        metricLabel = "Água Corporal",
                        formattedDelta = String.format(ptBr, "%s%% (%.1f%%)", signedFloat(deltaWaterPercent), current.bodyWaterPercent),
                        isPositivePhysiology = true,
                        highlightColorHex = 0xFF00B0FF
                    )
                )
            } else {
                lossesList.add(
                    RecompDeltaItem(
                        metricLabel = "Água Corporal",
                        formattedDelta = String.format(ptBr, "%s%% (%.1f%%)", signedFloat(deltaWaterPercent), current.bodyWaterPercent),
                        isPositivePhysiology = false,
                        highlightColorHex = 0xFFFFB300
                    )
                )
            }

            // 3. Relação Músculo/Gordura (M/G)
            val prevRatio = previous?.leanToFatRatio ?: current.leanToFatRatio
            val deltaRatio = ((current.leanToFatRatio - prevRatio) * 100f).roundToInt() / 100f
            if (deltaRatio >= 0f) {
                gainsList.add(
                    RecompDeltaItem(
                        metricLabel = "Relação M/G",
                        formattedDelta = String.format(ptBr, "%.2fx (+%.2f)", current.leanToFatRatio, deltaRatio),
                        isPositivePhysiology = true,
                        highlightColorHex = 0xFF00E5FF
                    )
                )
            }

            // 4. Redução de Gordura Real (kg e % BF) -> entra em REDUÇÕES / QUEIMA
            if (deltaFatMassKg <= 0f) {
                lossesList.add(
                    RecompDeltaItem(
                        metricLabel = "Gordura Real",
                        formattedDelta = String.format(ptBr, "%s kg (%s%%)", signedFloat(deltaFatMassKg), signedFloat(deltaBodyFatPercent)),
                        isPositivePhysiology = true,
                        highlightColorHex = 0xFF00E5FF
                    )
                )
            } else {
                gainsList.add(
                    RecompDeltaItem(
                        metricLabel = "Acúmulo Adiposo",
                        formattedDelta = String.format(ptBr, "%s kg (%s%%)", signedFloat(deltaFatMassKg), signedFloat(deltaBodyFatPercent)),
                        isPositivePhysiology = false,
                        highlightColorHex = 0xFFFF5252
                    )
                )
            }

            // 5. Redução/Variação de Peso Total (vs anterior e acumulado vs Marco Zero sem cortar "total")
            if (deltaWeightKg <= 0f) {
                lossesList.add(
                    RecompDeltaItem(
                        metricLabel = "Peso Total",
                        formattedDelta = if (abs(cumulativeDeltaWeightKg - deltaWeightKg) >= 0.2f) {
                            String.format(ptBr, "%s kg (%s kg)", signedFloat(deltaWeightKg), signedFloat(cumulativeDeltaWeightKg))
                        } else {
                            String.format(ptBr, "%s kg (%.1f kg)", signedFloat(deltaWeightKg), current.weightKg)
                        },
                        isPositivePhysiology = true,
                        highlightColorHex = 0xFF00E676
                    )
                )
            } else {
                gainsList.add(
                    RecompDeltaItem(
                        metricLabel = "Peso Total",
                        formattedDelta = if (abs(cumulativeDeltaWeightKg - deltaWeightKg) >= 0.2f) {
                            String.format(ptBr, "%s kg (%s kg)", signedFloat(deltaWeightKg), signedFloat(cumulativeDeltaWeightKg))
                        } else {
                            String.format(ptBr, "%s kg (%.1f kg)", signedFloat(deltaWeightKg), current.weightKg)
                        },
                        isPositivePhysiology = deltaBodyFatPercent <= 0f,
                        highlightColorHex = if (deltaBodyFatPercent <= 0f) 0xFF00E676 else 0xFFFFB300
                    )
                )
            }

            // 6. Gordura Subcutânea / Visceral / Idade Metabólica
            if (deltaSubcutaneousPercent < 0f) {
                lossesList.add(
                    RecompDeltaItem(
                        metricLabel = "Gord. Subcutânea",
                        formattedDelta = String.format(ptBr, "%s%% (%.1f%%)", signedFloat(deltaSubcutaneousPercent), current.subcutaneousFatPercent),
                        isPositivePhysiology = true,
                        highlightColorHex = 0xFF00B0FF
                    )
                )
            }
            if (deltaVisceralFat < 0f) {
                lossesList.add(
                    RecompDeltaItem(
                        metricLabel = "Gordura Visceral",
                        formattedDelta = String.format(ptBr, "%s pts (Nível %.0f)", signedFloat(deltaVisceralFat), current.visceralFatIndex),
                        isPositivePhysiology = true,
                        highlightColorHex = 0xFF00E676
                    )
                )
            }
            if (deltaMetabolicAge < 0) {
                lossesList.add(
                    RecompDeltaItem(
                        metricLabel = "Idade Metabólica",
                        formattedDelta = "${signedInt(deltaMetabolicAge)} anos (${current.metabolicAge}a)",
                        isPositivePhysiology = true,
                        highlightColorHex = 0xFF00E676
                    )
                )
            }
        }

        val hydrationWarning = buildHydrationConductivityWarning(current)
        val verdict = buildCoachPhysiologyVerdict(
            isBaseline = isBaseline,
            current = current,
            classification = classification,
            deltaWeightKg = deltaWeightKg,
            deltaFatMassKg = deltaFatMassKg,
            deltaSkeletalMusclePercent = deltaSkeletalMusclePercent,
            cumulativeDeltaWeightKg = cumulativeDeltaWeightKg,
            cumulativeDeltaFatMassKg = cumulativeDeltaFatMassKg
        )

        return RecompStepCardSummary(
            stepIndex = stepIndex,
            isBaseline = isBaseline,
            current = current,
            previous = previous,
            baseline = baseline,
            deltaWeightKg = deltaWeightKg,
            deltaFatMassKg = deltaFatMassKg,
            deltaBodyFatPercent = deltaBodyFatPercent,
            deltaMuscleMassKg = deltaMuscleMassKg,
            deltaSkeletalMuscleKg = deltaSkeletalMuscleKg,
            deltaSkeletalMusclePercent = deltaSkeletalMusclePercent,
            deltaVisceralFat = deltaVisceralFat,
            deltaWaterPercent = deltaWaterPercent,
            deltaBmrKcal = deltaBmrKcal,
            deltaMetabolicAge = deltaMetabolicAge,
            cumulativeDeltaWeightKg = cumulativeDeltaWeightKg,
            cumulativeDeltaFatMassKg = cumulativeDeltaFatMassKg,
            cumulativeDeltaSkeletalMuscleKg = cumulativeDeltaSkeletalMuscleKg,
            recompClassification = classification,
            gainsList = gainsList,
            lossesList = lossesList,
            coachPhysiologyVerdict = verdict,
            hydrationWarning = hydrationWarning
        )
    }

    /**
     * Classifica o degrau de evolução considerando tanto grandezas absolutas (kg) quanto relativas (%):
     * Em fases de emagrecimento expressivo (ex: -10,2 kg ou -1,0 kg), se a Taxa Muscular (%) e o
     * Músculo Esquelético (%) subiram enquanto a Gordura Corporal (kg e %) caiu, classifica como
     * `OURO_RECOMPOSICAO_PURA` ou `CUTTING_PRESERVADO`.
     */
    private fun classifyRecompStep(
        isBaseline: Boolean,
        deltaWeightKg: Float,
        deltaFatMassKg: Float,
        deltaBodyFatPercent: Float,
        deltaMuscleRatePercent: Float,
        deltaMuscleMassKg: Float,
        deltaSkeletalMuscleKg: Float,
        deltaSkeletalMusclePercent: Float,
        deltaWaterPercent: Float
    ): RecompClassification {
        if (isBaseline) return RecompClassification.MARCO_ZERO_BASE

        val lostFat = deltaFatMassKg < -0.05f || deltaBodyFatPercent < -0.05f
        val improvedRelativeMuscle = deltaMuscleRatePercent > 0.05f || deltaSkeletalMusclePercent > 0.05f
        val gainedAbsoluteMuscle = deltaSkeletalMuscleKg >= 0f || deltaMuscleMassKg >= 0f

        return when {
            deltaWaterPercent <= -1.0f ->
                RecompClassification.ALERTA_DESIDRATACAO_OU_CATABOLISMO
            lostFat && (gainedAbsoluteMuscle || improvedRelativeMuscle) && deltaWeightKg >= -2.5f ->
                RecompClassification.OURO_RECOMPOSICAO_PURA
            lostFat && improvedRelativeMuscle ->
                RecompClassification.CUTTING_PRESERVADO
            (deltaSkeletalMuscleKg > 0.05f || deltaMuscleMassKg > 0.1f) && deltaBodyFatPercent <= 0.25f ->
                RecompClassification.BULKING_LIMPO
            lostFat ->
                RecompClassification.CUTTING_PRESERVADO
            else ->
                RecompClassification.ALERTA_DESIDRATACAO_OU_CATABOLISMO
        }
    }

    /**
     * Explicação biofísica crítica para balanças de bioimpedância tetrapolar/octapolar:
     * Quando a água corporal está abaixo de 55% (ex: 52,8%), a menor hidratação intracelular
     * reduz a condutividade da corrente elétrica nos tecidos magros.
     */
    fun buildHydrationConductivityWarning(
        record: BioimpedanceRecord,
        profile: UserProfileData? = WeightCalculator.activeUserProfile
    ): String? {
        if (!BioimpedanceReferences.hasPersonalGoals(profile) || record.bodyWaterPercent <= 0f) return null
        val targetWaterLiters = profile?.metaAguaLitros?.takeIf { it > 0f } ?: return null
        if (record.bodyWaterPercent >= TARGET_BODY_WATER_PERCENT) return null
        val ptBr = Locale.forLanguageTag("pt-BR")
        val deficit = TARGET_BODY_WATER_PERCENT - record.bodyWaterPercent
        return String.format(
            ptBr,
            "Água corporal em %.1f%% (-%.1f%% abaixo do piso ideal de 55,0%%). Em balanças Fitdays/Relaxmedic, a corrente elétrica percorre a água intracelular do músculo: elevar a hidratação para %.1f L/dia melhora a leitura do Músculo Esquelético (%.1f%%) e reduz a impedância aparente.",
            record.bodyWaterPercent,
            deficit,
            targetWaterLiters,
            record.skeletalMusclePercent
        )
    }

    private fun buildCoachPhysiologyVerdict(
        isBaseline: Boolean,
        current: BioimpedanceRecord,
        classification: RecompClassification,
        deltaWeightKg: Float,
        deltaFatMassKg: Float,
        deltaSkeletalMusclePercent: Float,
        cumulativeDeltaWeightKg: Float,
        cumulativeDeltaFatMassKg: Float
    ): String {
        val ptBr = Locale.forLanguageTag("pt-BR")
        if (isBaseline) {
            return String.format(
                ptBr,
                "Marco Zero estabelecido em %s com %.1f kg (27,3%% BF / 26,2 kg de massa gorda e visceral 13). Ponto de partida da periodização híbrida (MAX RUN + MAX GYM) com meta de eliminar ~9,5 kg de gordura preservando a densidade contrátil e reduzindo a idade metabólica de %d anos.",
                current.dateTimeLabel,
                current.weightKg,
                current.metabolicAge
            )
        }

        return when (classification) {
            RecompClassification.OURO_RECOMPOSICAO_PURA -> String.format(
                ptBr,
                "Recomposição Padrão Ouro! Redução de %s kg na balança (%s kg de gordura eliminada) com evolução da fração de Músculo Esquelético para %.1f%% (%s%%) e Taxa Muscular em %.1f%%. Acumulado desde o Marco Zero: %s kg de peso e %s kg de gordura pura eliminados!",
                signedFloat(deltaWeightKg),
                signedFloat(deltaFatMassKg),
                current.skeletalMusclePercent,
                signedFloat(deltaSkeletalMusclePercent),
                current.muscleRatePercent,
                signedFloat(cumulativeDeltaWeightKg),
                signedFloat(cumulativeDeltaFatMassKg)
            )
            RecompClassification.CUTTING_PRESERVADO -> String.format(
                ptBr,
                "Cutting de Alta Eficiência: corte de %s kg de peso total e %s kg de gordura pura, elevando a Taxa Muscular para %.1f%% e reduzindo a Gordura Visceral para nível %.0f e Idade Metabólica para %d anos.",
                signedFloat(deltaWeightKg),
                signedFloat(deltaFatMassKg),
                current.muscleRatePercent,
                current.visceralFatIndex,
                current.metabolicAge
            )
            RecompClassification.BULKING_LIMPO -> String.format(
                ptBr,
                "Síntese muscular positiva (%s%% de músculo esquelético) com controle adiposo. Excelente resposta anabólica ao volume de treino.",
                signedFloat(deltaSkeletalMusclePercent)
            )
            RecompClassification.ALERTA_DESIDRATACAO_OU_CATABOLISMO -> String.format(
                ptBr,
                "Atenção à hidratação celular (%.1f%% de água): oscilações negativas de massa muscular na bioimpedância quase sempre refletem depleção de glicogênio + água intracelular pós-corrida.",
                current.bodyWaterPercent
            )
            RecompClassification.MARCO_ZERO_BASE -> ""
        }
    }

    private fun round1(value: Float): Float = (value * 10f).roundToInt() / 10f

    private fun signedFloat(value: Float): String {
        val ptBr = Locale.forLanguageTag("pt-BR")
        val clean = if (abs(value) < 0.05f) 0f else value
        return if (clean > 0f) {
            String.format(ptBr, "+%.1f", clean)
        } else {
            String.format(ptBr, "%.1f", clean)
        }
    }

    private fun signedInt(value: Int): String = if (value > 0) "+$value" else "$value"

    private fun toJson(record: BioimpedanceRecord): JSONObject {
        return JSONObject().apply {
            put("id", record.id)
            put("timestampMillis", record.timestampMillis)
            put("dateTimeLabel", record.dateTimeLabel)
            put("athleteName", record.athleteName)
            put("sourceApp", record.sourceApp)
            put("weightKg", record.weightKg.toDouble())
            put("bmi", record.bmi.toDouble())
            put("bodyFatPercent", record.bodyFatPercent.toDouble())
            put("muscleRatePercent", record.muscleRatePercent.toDouble())
            put("fatFreeMassKg", record.fatFreeMassKg.toDouble())
            put("subcutaneousFatPercent", record.subcutaneousFatPercent.toDouble())
            put("visceralFatIndex", record.visceralFatIndex.toDouble())
            put("bodyWaterPercent", record.bodyWaterPercent.toDouble())
            put("skeletalMusclePercent", record.skeletalMusclePercent.toDouble())
            put("muscleMassKg", record.muscleMassKg.toDouble())
            put("boneMassKg", record.boneMassKg.toDouble())
            put("proteinPercent", record.proteinPercent.toDouble())
            put("bmrKcal", record.bmrKcal)
            put("metabolicAge", record.metabolicAge)
            put("photoUri", record.photoUri ?: "")
            put("segmentalSummary", record.segmentalSummary)
            if (record.explicitFatMassKg != null) {
                put("explicitFatMassKg", record.explicitFatMassKg.toDouble())
            }
        }
    }

    internal fun fromJson(
        obj: JSONObject,
        profile: UserProfileData? = WeightCalculator.activeUserProfile
    ): BioimpedanceRecord {
        val fallback = createBlankProfileRecord(
            profile = profile,
            nowMillis = 0L,
            includeProfileWeight = false
        )
        val explicitFat = if (obj.has("explicitFatMassKg")) {
            obj.optDouble("explicitFatMassKg", 0.0).toFloat().takeIf { it > 0.5f }
        } else {
            null
        }
        return BioimpedanceRecord(
            id = obj.optString("id", fallback.id),
            timestampMillis = obj.optLong("timestampMillis", fallback.timestampMillis),
            dateTimeLabel = obj.optString("dateTimeLabel", ""),
            athleteName = obj.optString("athleteName", fallback.athleteName),
            sourceApp = obj.optString("sourceApp", ""),
            weightKg = obj.optDouble("weightKg", 0.0).toFloat(),
            bmi = obj.optDouble("bmi", 0.0).toFloat(),
            bodyFatPercent = obj.optDouble("bodyFatPercent", 0.0).toFloat(),
            muscleRatePercent = obj.optDouble("muscleRatePercent", 0.0).toFloat(),
            fatFreeMassKg = obj.optDouble("fatFreeMassKg", 0.0).toFloat(),
            subcutaneousFatPercent = obj.optDouble("subcutaneousFatPercent", 0.0).toFloat(),
            visceralFatIndex = obj.optDouble("visceralFatIndex", 0.0).toFloat(),
            bodyWaterPercent = obj.optDouble("bodyWaterPercent", 0.0).toFloat(),
            skeletalMusclePercent = obj.optDouble("skeletalMusclePercent", 0.0).toFloat(),
            muscleMassKg = obj.optDouble("muscleMassKg", 0.0).toFloat(),
            boneMassKg = obj.optDouble("boneMassKg", 0.0).toFloat(),
            proteinPercent = obj.optDouble("proteinPercent", 0.0).toFloat(),
            bmrKcal = obj.optInt("bmrKcal", 0),
            metabolicAge = obj.optInt("metabolicAge", 0),
            photoUri = obj.optString("photoUri", "").takeIf { it.isNotBlank() },
            segmentalSummary = obj.optString("segmentalSummary", ""),
            explicitFatMassKg = explicitFat
        )
    }
}
