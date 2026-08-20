package com.fitness.app.domain.usecase

import com.fitness.app.data.db.entities.ExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestPartnerSwapTest {

    private fun ex(id: Long, name: String, equipment: String) =
        ExerciseEntity(id = id, name = name, primaryMuscle = "X", equipment = equipment)

    @Test
    fun `picks first alternative matching the new equipment by order`() {
        val partner = ex(1, "Calf Raise (Machine)", "Machine")
        val newExercise = ex(9, "Leg Press (Free Weight)", "Free Weight")
        val alts = listOf(
            ex(2, "Seated Calf Raise (Machine)", "Machine"),
            ex(3, "Standing Calf Raise (Free Weight)", "Free Weight"),
            ex(4, "Another Free Weight Calf", "Free Weight")
        )
        assertEquals(3L, suggestPartnerSwap(partner, alts, newExercise)?.id)
    }

    @Test
    fun `returns null when no alternative matches the new equipment`() {
        val partner = ex(1, "Calf Raise (Machine)", "Machine")
        val newExercise = ex(9, "Leg Press (Free Weight)", "Free Weight")
        val alts = listOf(ex(2, "Seated Calf Raise (Machine)", "Machine"))
        assertNull(suggestPartnerSwap(partner, alts, newExercise))
    }

    @Test
    fun `returns null when partner already uses the new equipment`() {
        val partner = ex(1, "Calf Raise (Free Weight)", "Free Weight")
        val newExercise = ex(9, "Leg Press (Free Weight)", "Free Weight")
        val alts = listOf(ex(3, "Standing Calf Raise (Free Weight)", "Free Weight"))
        assertNull(suggestPartnerSwap(partner, alts, newExercise))
    }

    @Test
    fun `excludes the new exercise itself from suggestions`() {
        val partner = ex(1, "Calf Raise (Machine)", "Machine")
        val newExercise = ex(9, "Leg Press (Free Weight)", "Free Weight")
        val alts = listOf(
            ex(9, "Leg Press (Free Weight)", "Free Weight"),
            ex(3, "Standing Calf Raise (Free Weight)", "Free Weight")
        )
        assertEquals(3L, suggestPartnerSwap(partner, alts, newExercise)?.id)
    }
}
