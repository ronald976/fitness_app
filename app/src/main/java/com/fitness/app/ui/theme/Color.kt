package com.fitness.app.ui.theme

import androidx.compose.ui.graphics.Color

// Direction A — "Active" (Strava/Nike). Vibrant orange, warm off-white surfaces.

// Brand
val AccentOrange = Color(0xFFFC5200)
val AccentOrangeDim = Color(0x33FC5200) // 20% — for hover/tints
val OnAccent = Color(0xFFFFFFFF)
val Success = Color(0xFF1F9D55)

// Light surfaces
val BgLight = Color(0xFFF6F5F1)
val SurfaceLight = Color(0xFFFFFFFF)
val Surface2Light = Color(0xFFF0EEE8)
val FgLight = Color(0xFF15151A)
val FgDimLight = Color(0x8C15151A)   // ~55%
val FgFaintLight = Color(0x1415151A) // ~8%
val LineLight = Color(0x1215151A)    // ~7%

// Dark surfaces
val BgDark = Color(0xFF0E0E10)
val SurfaceDark = Color(0xFF1A1A1D)
val Surface2Dark = Color(0xFF222226)
val FgDark = Color(0xFFF5F4F0)
val FgDimDark = Color(0x99F5F4F0)   // ~60%
val FgFaintDark = Color(0x1FF5F4F0) // ~12%
val LineDark = Color(0x1AF5F4F0)    // ~10%

// Accent palette for icon tiles (per workout-name hash)
val TileOrange = AccentOrange
val TileSky = Color(0xFF0EA5E9)
val TilePurple = Color(0xFF7C3AED)
val TileGreen = Color(0xFF10B981)
val TileAmber = Color(0xFFF59E0B)

val TilePalette = listOf(TileOrange, TileSky, TilePurple, TileGreen, TileAmber)
