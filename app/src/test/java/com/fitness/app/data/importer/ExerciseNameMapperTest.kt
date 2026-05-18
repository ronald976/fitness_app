package com.fitness.app.data.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseNameMapperTest {

    @Test
    fun `leg press routes machine only when explicitly qualified`() {
        assertEquals("leg_press_free_weight", ExerciseNameMapper.map("Leg press")?.slug)
        assertEquals("leg_press_free_weight", ExerciseNameMapper.map("Leg press (free weight)")?.slug)
        assertEquals("leg_press_free_weight", ExerciseNameMapper.map("Single leg leg press")?.slug)
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

    @Test
    fun `smith pressing variants stay separate from barbell presses`() {
        assertEquals("bench_press", ExerciseNameMapper.map("Bench press")?.slug)
        assertEquals("smith_bench_press", ExerciseNameMapper.map("Bench smith")?.slug)
        assertEquals("incline_bench_press", ExerciseNameMapper.map("Incline bench")?.slug)
        assertEquals("incline_smith_press", ExerciseNameMapper.map("Incline DB Smith")?.slug)
        assertEquals("incline_smith_press", ExerciseNameMapper.map("Incline bench smith")?.slug)
        assertEquals("incline_smith_press", ExerciseNameMapper.map("Smith incline press")?.slug)
        assertEquals("machine_chest_press", ExerciseNameMapper.map("Machine chest press (free weight)")?.slug)
        assertEquals("overhead_press", ExerciseNameMapper.map("Press")?.slug)
        assertEquals("smith_overhead_press", ExerciseNameMapper.map("Press smith")?.slug)
        assertEquals("machine_shoulder_press", ExerciseNameMapper.map("Shoulder press")?.slug)
    }

    @Test
    fun `smith row and hip thrust stay separate from barbell variants`() {
        assertEquals("barbell_row", ExerciseNameMapper.map("Barbell rows")?.slug)
        assertEquals("smith_row", ExerciseNameMapper.map("Smith rows (catch up)")?.slug)
        assertEquals("chest_supported_row", ExerciseNameMapper.map("Machine rows")?.slug)
        assertEquals("seated_cable_row", ExerciseNameMapper.map("Cable rows")?.slug)
        assertEquals("shrug", ExerciseNameMapper.map("Barbell shrugs")?.slug)
        assertEquals("dumbbell_shrug", ExerciseNameMapper.map("Dumbbell shrugs")?.slug)
        assertEquals("hip_thrust", ExerciseNameMapper.map("Hip thrust")?.slug)
        assertEquals("smith_hip_thrust", ExerciseNameMapper.map("Hip thrust (Smith)")?.slug)
    }

    @Test
    fun `lat pulldown defaults to the machine backed exercise`() {
        assertEquals("lat_pulldown", ExerciseNameMapper.map("Lat pulldown")?.slug)
        assertEquals("lat_pulldown", ExerciseNameMapper.map("Cable pulldowns")?.slug)
        assertEquals("unilateral_lat_pulldown", ExerciseNameMapper.map("HS unilat pulldowns")?.slug)
    }

    @Test
    fun `pull and chin variants keep bodyweight and assisted histories separate`() {
        assertEquals("pull_up", ExerciseNameMapper.map("Pull ups bwx4")?.slug)
        assertEquals("chin_up", ExerciseNameMapper.map("Chin ups bwx10")?.slug)
        assertEquals("assisted_pull_up", ExerciseNameMapper.map("Ass. Pull ups")?.slug)
        assertEquals("assisted_chin_up", ExerciseNameMapper.map("Ass. Chin ups")?.slug)
        assertEquals("assisted_chin_up", ExerciseNameMapper.map("Machine chin ups biceps")?.slug)
    }

    @Test
    fun `cable and dumbbell accessory variants stay separate`() {
        assertEquals("cable_lateral_raise", ExerciseNameMapper.map("Cable lat raises")?.slug)
        assertEquals("lateral_raise", ExerciseNameMapper.map("Dumbbell lat raises")?.slug)
        assertEquals("cable_overhead_tricep_ext", ExerciseNameMapper.map("Cable overhead triceps extensions")?.slug)
        assertEquals("cable_overhead_tricep_ext", ExerciseNameMapper.map("Cable triceps extensions")?.slug)
        assertEquals("overhead_tricep_ext", ExerciseNameMapper.map("Dumbbell overhead triceps extensions")?.slug)
        assertEquals("barbell_overhead_tricep_ext", ExerciseNameMapper.map("Barbell overhead triceps extensions")?.slug)
        assertEquals("cable_rear_delt_fly", ExerciseNameMapper.map("Reverse cable flye")?.slug)
        assertEquals("rear_delt_fly", ExerciseNameMapper.map("Rear delt fly")?.slug)
    }

    @Test
    fun `cable bicep curl stays separate from barbell curl`() {
        assertEquals("barbell_curl", ExerciseNameMapper.map("Barbell curl")?.slug)
        assertEquals("cable_bicep_curl", ExerciseNameMapper.map("Cable bicep curl")?.slug)
        assertEquals("cable_bicep_curl", ExerciseNameMapper.map("Cable curls")?.slug)
        assertEquals("cable_bicep_curl", ExerciseNameMapper.map("Cable biceps")?.slug)
    }
}
