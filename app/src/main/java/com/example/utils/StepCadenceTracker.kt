package com.example.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * =========================================================================
 * STEP CADENCE TRACKER (SPRINT-SENS-GPS-01)
 * Rastreador nativo de cadência e passos baseado no Sensor.TYPE_STEP_DETECTOR.
 * Expõe cadência em tempo real (SPM), total de passos e estado de movimento
 * para Sensor Fusion anti-drift no GPS.
 * =========================================================================
 */
object StepCadenceTracker : SensorEventListener {
    private const val TAG = "StepCadenceTracker"

    private val _currentCadenceSpm = MutableStateFlow(0)
    val currentCadenceSpm: StateFlow<Int> = _currentCadenceSpm.asStateFlow()

    private val _totalSteps = MutableStateFlow(0)
    val totalSteps: StateFlow<Int> = _totalSteps.asStateFlow()

    @Volatile
    var hasStepSensor: Boolean = false
        private set

    @Volatile
    private var lastStepTimestampMs: Long = 0L

    private val stepTimestamps = ConcurrentLinkedQueue<Long>()
    private var sensorManager: SensorManager? = null
    private var stepDetectorSensor: Sensor? = null
    @Volatile
    private var isListening = false

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var decayJob: Job? = null

    fun start(context: Context) {
        if (isListening) return

        try {
            val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            sensorManager = sm
            val sensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
            stepDetectorSensor = sensor

            if (sensor != null) {
                hasStepSensor = true
                val registered = sm?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST) ?: false
                if (!registered) {
                    Log.w(TAG, "Falha ao registrar listener do Sensor.TYPE_STEP_DETECTOR; ativando modo fallback")
                    hasStepSensor = false
                } else {
                    Log.i(TAG, "Sensor.TYPE_STEP_DETECTOR registrado com sucesso")
                }
            } else {
                Log.i(TAG, "Sensor.TYPE_STEP_DETECTOR indisponível neste dispositivo; modo fallback ativado")
                hasStepSensor = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao iniciar StepCadenceTracker", e)
            hasStepSensor = false
        }

        isListening = true
        startDecayLoop()
    }

    fun stop() {
        if (!isListening) return
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao desregistrar listener do sensor", e)
        }
        decayJob?.cancel()
        decayJob = null
        isListening = false
    }

    fun reset() {
        stop()
        _totalSteps.value = 0
        _currentCadenceSpm.value = 0
        lastStepTimestampMs = 0L
        stepTimestamps.clear()
    }

    /**
     * Retorna true se houve passos nos últimos 3.5 segundos.
     * Retorna true por fallback se o hardware não possuir o sensor de passos,
     * se o listener não estiver ativo ou se nenhum passo tiver sido reportado (ex: sem permissão ACTIVITY_RECOGNITION).
     */
    fun isPhysicallyMoving(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!isListening || !hasStepSensor) return true
        if (lastStepTimestampMs == 0L) return true
        return (nowMs - lastStepTimestampMs) <= 4500L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_STEP_DETECTOR) return

        val now = System.currentTimeMillis()
        lastStepTimestampMs = now
        _totalSteps.value += 1

        stepTimestamps.add(now)

        // Descarta timestamps com mais de 15 segundos
        val cutoff = now - 15000L
        while (stepTimestamps.peek()?.let { it < cutoff } == true) {
            stepTimestamps.poll()
        }

        recalculateCadence(now)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun recalculateCadence(nowMs: Long) {
        val count = stepTimestamps.size
        if (count < 2) {
            if (count == 1 && (nowMs - lastStepTimestampMs) <= 3500L) {
                _currentCadenceSpm.value = 60
            } else {
                _currentCadenceSpm.value = 0
            }
            return
        }

        val oldest = stepTimestamps.peek() ?: nowMs
        val durationMs = (nowMs - oldest).coerceAtLeast(1000L)
        val spm = ((count - 1) * 60000L / durationMs).toInt()
        _currentCadenceSpm.value = spm.coerceIn(0, 240)
    }

    private fun startDecayLoop() {
        decayJob?.cancel()
        decayJob = scope.launch {
            while (isActive && isListening) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val cutoff = now - 15000L

                while (stepTimestamps.peek()?.let { it < cutoff } == true) {
                    stepTimestamps.poll()
                }

                if (lastStepTimestampMs > 0L && (now - lastStepTimestampMs) > 4000L) {
                    _currentCadenceSpm.value = 0
                } else if (hasStepSensor) {
                    recalculateCadence(now)
                }
            }
        }
    }
}
