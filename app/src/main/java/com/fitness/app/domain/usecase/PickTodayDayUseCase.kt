package com.fitness.app.domain.usecase

import com.fitness.app.data.db.dao.PlanWithDays
import com.fitness.app.data.db.dao.SessionWithExercises
import javax.inject.Inject

/**
 * Picks the plan day to feature as "today's workout".
 *
 * Strategy: rotate forward from the most recent completed session that was
 * driven by a plan day. So if the user just did Pull A, today shows Legs A.
 * Falls back to the first day (lowest dayIndex) when there's no eligible
 * history — fresh users, custom-only history, or a plan whose previous
 * cycle's day was deleted.
 */
class PickTodayDayUseCase @Inject constructor() {
    operator fun invoke(
        plan: PlanWithDays?,
        recentSessions: List<SessionWithExercises>
    ): Long? {
        if (plan == null) return null
        val orderedDays = plan.days.sortedBy { it.day.dayIndex }
        if (orderedDays.isEmpty()) return null

        val planDayIds = orderedDays.map { it.day.id }
        val lastDayId = recentSessions
            .firstOrNull { it.session.planDayId in planDayIds }
            ?.session?.planDayId

        if (lastDayId == null) return orderedDays.first().day.id

        val lastIndex = orderedDays.indexOfFirst { it.day.id == lastDayId }
        if (lastIndex < 0) return orderedDays.first().day.id
        return orderedDays[(lastIndex + 1) % orderedDays.size].day.id
    }
}
