package com.fitness.app.ui.navigation

object Routes {
    const val Home = "home"
    const val Profile = "profile"
    const val Plans = "plans"
    const val PlanDetail = "plan/{planId}"
    const val PlanEdit = "plan/{planId}/edit"
    const val ActiveWorkout = "workout/{sessionId}"
    const val History = "history"
    const val SessionDetail = "session/{sessionId}"
    const val Dashboard = "dashboard"
    const val Settings = "settings"

    /** Roots that show the bottom nav bar. */
    val BottomBarRoots = setOf(Home, History, Profile)

    fun planDetail(planId: Long) = "plan/$planId"
    fun planEdit(planId: Long) = "plan/$planId/edit"
    fun activeWorkout(sessionId: Long) = "workout/$sessionId"
    fun sessionDetail(sessionId: Long) = "session/$sessionId"
}
