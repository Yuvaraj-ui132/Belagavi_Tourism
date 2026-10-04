package com.belagavi.tourism.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// BELAGAVI TOURISM — DESIGN SYSTEM TOKENS
// Warm Cream / Ivory + Muted Sage Editorial Travel Palette
// ============================================================

// ── Sage Green Accents (Selected states, buttons, avatars, borders) ───
val SageDeep      = Color(0xFF557C6B)          // Deep sage — high contrast on white/cream
val SageDarker    = Color(0xFF5E8B77)          // Darker visible sage — active controls
val SageMuted     = Color(0xFF7FAE9B)          // Muted sage accent
val SageSoft      = Color(0xFFA9C9BA)          // Soft sage — borders & accents
val SagePale      = Color(0xFFE8F1EC)          // Subtle sage tint for chips & card fills

// Primary aliases
val SagePrimary   = SageDarker
val SageLight     = SageMuted
val ForestPrimary = SageDarker
val ForestLight   = SageMuted
val ForestPale    = SagePale

// ── Warm Surfaces (Warm Cream / Ivory / Beige) ──────────────────────
val CreamBackground = Color(0xFFF7F2E9)        // Primary warm cream / ivory background
val CreamWarm       = Color(0xFFE9DDCB)        // Warm beige / sand / secondary surface
val WhiteSurface    = Color(0xFFFFFFFF)        // Clean white card surface
val Stone           = Color(0xFF8B6F47)        // Warm earth stone
val StoneLight      = Color(0xFFC4A882)        // Soft sand stone
val StonePale       = Color(0xFFF5F0EA)        // Light stone tint

// ── Dark Readable Typography (Never pale green for normal text) ──────
val InkPrimary   = Color(0xFF1F1F1C)           // Black / charcoal / dark brown primary text
val InkSecondary = Color(0xFF6F685E)           // Dark secondary / body text
val InkMuted     = Color(0xFF7A7268)           // Clearly legible captions & placeholders
val InkFaint     = Color(0xFFA39B8F)           // Subtle indicators

// ── Subtle Borders & Dividers ────────────────────────────────────────
val BorderLight  = Color(0x1F1F1F1C)           // Subtle dark border (~12% opacity)
val BorderFaint  = Color(0x0F1F1F1C)           // Faint dark border (~6% opacity)

// ── Status Tokens ────────────────────────────────────────────────────
val StatusSuccess  = Color(0xFF27AE60)
val StatusWarning  = Color(0xFFF59E0B)
val StatusReached  = Color(0xFFF97316)
val StatusExceeded = Color(0xFFF43F5E)
val StatusDanger   = Color(0xFFC0392B)

// ── Legacy Compatibility Aliases ────────────────────────────────────
val SaffronPrimary = Color(0xFFD96528)
val SaffronLight   = Color(0xFFE87A38)
val SaffronPale    = Color(0xFFFDF4EC)
val SaffronTint    = Color(0xFFF8E7D8)
val SaffronMuted   = Color(0xFF9E4B1A)

val LightPrimary = ForestPrimary
val LightOnPrimary = Color.White
val LightSecondary = ForestLight
val LightTertiary = Stone
val LightBackground = CreamBackground
val LightSurface = WhiteSurface
val LightOnBackground = InkPrimary
val LightOnSurface = InkPrimary

val DarkPrimary = SageMuted
val DarkOnPrimary = Color.White
val DarkSecondary = StoneLight
val DarkTertiary = StatusWarning
val DarkBackground = Color(0xFF161513)
val DarkSurface = Color(0xFF1F1E1B)
val DarkOnBackground = CreamBackground
val DarkOnSurface = CreamBackground

