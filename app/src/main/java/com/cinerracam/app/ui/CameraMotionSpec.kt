package com.cinerracam.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring

object CameraMotionSpec {
    val d90: TweenSpec<Float> = TweenSpec(durationMillis = 90, easing = EaseOut)
    val d140: TweenSpec<Float> = TweenSpec(durationMillis = 140, easing = EaseOut)
    val d220: TweenSpec<Float> = TweenSpec(durationMillis = 220, easing = EaseInOut)
    val d280: TweenSpec<Float> = TweenSpec(durationMillis = 280, easing = EaseInOut)
    val d400: TweenSpec<Float> = TweenSpec(durationMillis = 400, easing = EaseInOut)

    val shutterSpring: SpringSpec<Float> = SpringSpec(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )

    val focusRingSpring: SpringSpec<Float> = SpringSpec(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow,
    )

    val modeSlideSpring: SpringSpec<Float> = SpringSpec(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    fun <T> snappySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}

private val EaseOut: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EaseInOut: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
