package com.fitness.app.data.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseNameMapperTest {

    @Test
    fun `leg press routes machine only when explicitly qualified`() {
        assertEquals("leg_press_free_weight", ExerciseNameMapper.map("Leg press")?.slug)
        assertEquals("leg_press_free_weight", ExerciseNameMapper.map("Leg press (free weight)")?.slug)
        assertEquals("leg_press", ExerciseNameMapper.map("Leg press machine")?.slug)
        assertEquals("leg_press", ExerciseNameMapper.map("Leg press (machine)")?.slug)
    }

    @Test
    fun `calf raise routes machine only when explicitly qualified`() {
        assertEquals("calf_raise_free_weight", ExerciseNameMapper.map("Calf raises")?.slug)
        assertEquals("calf_raise_free_weight", ExerciseNameMapper.map("Calf raises (free weight)")?.slug)
        assertEquals("calf_raise", ExerciseNameMapper.map("Calf raises machine")?.slug)
    }

    @Test
    fun `smith squat stays separate from barbell squat`() {
        assertEquals("back_squat", ExerciseNameMapper.map("Low bar squat")?.slug)
        assertEquals("smith_squat", ExerciseNameMapper.map("Low bar squat smith")?.slug)
        assertEquals("smith_squat", ExerciseNameMapper.map("Smith squat")?.slug)
    }
}
