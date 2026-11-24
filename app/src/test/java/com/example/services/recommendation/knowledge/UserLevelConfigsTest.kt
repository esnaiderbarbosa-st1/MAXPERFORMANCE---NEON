package com.example.services.recommendation.knowledge

import org.junit.Assert.*
import org.junit.Test

class UserLevelConfigsTest {

    @Test
    fun testAvancadoTemMinimoDe5Exercicios() {
        val config = UserLevelConfigs.getByLevel("Avançado")
        assertEquals(5, config.minMainExercises)
        assertEquals(7, config.maxMainExercises)
        assertEquals(4, config.setsPerExercise)
    }

    @Test
    fun testIntermediarioTemMinimoDe4() {
        val config = UserLevelConfigs.getByLevel("Intermediário")
        assertEquals(4, config.minMainExercises)
        assertEquals(8, config.maxMainExercises)
    }

    @Test
    fun testInicianteTemMinimoDe3() {
        val config = UserLevelConfigs.getByLevel("Iniciante")
        assertEquals(3, config.minMainExercises)
        assertEquals(6, config.maxMainExercises)
    }

    @Test
    fun testCaseInsensitive() {
        val a = UserLevelConfigs.getByLevel("avançado")
        val b = UserLevelConfigs.getByLevel("AVANÇADO")
        val c = UserLevelConfigs.getByLevel("Avançado")
        val d = UserLevelConfigs.getByLevel("AVANCADO")
        val e = UserLevelConfigs.getByLevel("Advanced")
        assertEquals(a.minMainExercises, b.minMainExercises)
        assertEquals(b.minMainExercises, c.minMainExercises)
        assertEquals(c.minMainExercises, d.minMainExercises)
        assertEquals(d.minMainExercises, e.minMainExercises)
    }

    @Test
    fun testNivelDesconhecidoCaiParaIntermediario() {
        val config = UserLevelConfigs.getByLevel("Desconhecido")
        assertEquals(4, config.minMainExercises)
    }

    @Test
    fun testTotalExercisesRangeIncluiExtras() {
        val config = UserLevelConfigs.getByLevel("Avançado")
        assertEquals(7, config.totalExercisesRange.first)
        assertEquals(9, config.totalExercisesRange.last)
    }

    @Test
    fun testAvancadoTemMetodosAvancadosLiberados() {
        val config = UserLevelConfigs.getByLevel("Avançado")
        assertTrue(config.allowedMethods.contains("cluster"))
        assertTrue(config.allowedMethods.contains("top_set"))
    }

    @Test
    fun testInicianteNaoTemMetodosAvancados() {
        val config = UserLevelConfigs.getByLevel("Iniciante")
        assertTrue(config.blockedMethods.contains("cluster"))
        assertTrue(config.blockedMethods.contains("top_set"))
    }
}
