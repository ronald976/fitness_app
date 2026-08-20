package com.fitness.app.domain.usecase

import com.fitness.app.data.db.entities.ExerciseEntity

/**
 * When a superset member is swapped, suggest a matching swap for its partner so the pair stays
 * coherent (e.g. machine leg press → free-weight leg press should nudge the paired calf raise to
 * its free-weight version too).
 *
 * Returns the partner's first alternative whose equipment matches [newExercise], or null when the
 * partner already uses that equipment (no change worth suggesting) or nothing matches.
 * [partnerAlternatives] is expected in the DAO's orderIdx order (best alternative first).
 */
fun suggestPartnerSwap(
    partner: ExerciseEntity,
    partnerAlternatives: List<ExerciseEntity>,
    newExercise: ExerciseEntity
): ExerciseEntity? =
    if (partner.equipment == newExercise.equipment) null
    else partnerAlternatives.firstOrNull {
        it.equipment == newExercise.equipment && it.id != newExercise.id && it.id != partner.id
    }
