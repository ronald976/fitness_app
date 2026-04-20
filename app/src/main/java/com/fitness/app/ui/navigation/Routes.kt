package com.fitness.app.ui.navigation

object Routes {
    const val Home = "home"
    const val Plans = "plans"
    const val PlanDetail = "plan/{planId}"
    const val PlanEdit = "plan/{planId}/edit"
    const val ActiveWorkout = "workout/{sessionId}"
    const val History = "history"

    fun planDetail(planId: Long) = "plan/$planId"
    fun planEdit(planId: Long) = "plan/$planId/edit"
    fun activeWorkout(sessionId: Long) = "workout/$sessionId"
}
