package me.bmax.apatch.ui.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle

/**
 * How a page is carried on and off the screen.
 *
 * A page that is pushed rather than chosen takes the whole screen with it, so the reader keeps the
 * sense of a stack growing to the right: the page arrives from the right edge, the page underneath
 * leaves through the left one. The three parts of that move run on their own clocks, and that is
 * what gives the arrival its depth rather than its flatness: the page is already fading in while it
 * is still sliding, it comes in a little too large and settles into place, and the page it lands on
 * steps back a little as it is covered.
 *
 * Tabs are not a stack, so a tab replacing a tab is a dissolve, and the two of them only shift a
 * little rather than crossing the whole width. A tab is still reached and left by the same move,
 * because the shell keeps the tabs at the root of the stack rather than beside each other.
 */
internal class AsterNavigationTransitions(
    private val primaryRoutes: Set<String>,
) : NavHostAnimatedDestinationStyle() {
    private val fade = tween<Float>(
        durationMillis = PageFadeMillis,
        easing = FastOutSlowInEasing,
    )
    private val offset = tween<IntOffset>(
        durationMillis = PageSlideMillis,
        easing = FastOutSlowInEasing,
    )
    private val scale = tween<Float>(
        durationMillis = PageScaleMillis,
        easing = FastOutSlowInEasing,
    )
    private val tabFade = tween<Float>(durationMillis = TabFadeMillis)

    /** A page is pushed when it is not one of the tabs the shell keeps at the root. */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isPushed(
        route: String?,
    ): Boolean = route != null && route !in primaryRoutes

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.bothTabs(): Boolean =
        !isPushed(initialState.destination.route) && !isPushed(targetState.destination.route)

    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        {
            if (isPushed(targetState.destination.route)) {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = offset) +
                    fadeIn(animationSpec = fade) +
                    scaleIn(initialScale = PageArriveScale, animationSpec = scale)
            } else {
                fadeIn(animationSpec = tabFade)
            }
        }

    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        {
            if (bothTabs()) {
                fadeOut(animationSpec = tabFade)
            } else {
                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = offset) +
                    fadeOut(animationSpec = fade) +
                    scaleOut(targetScale = PageCoveredScale, animationSpec = scale)
            }
        }

    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        {
            if (bothTabs()) {
                slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn(animationSpec = tabFade)
            } else {
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = offset) +
                    fadeIn(animationSpec = fade) +
                    scaleIn(initialScale = PageCoveredScale, animationSpec = scale)
            }
        }

    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        {
            if (bothTabs()) {
                scaleOut(targetScale = TabLeaveScale) + fadeOut(animationSpec = tabFade)
            } else {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = offset) +
                    fadeOut(animationSpec = fade) +
                    scaleOut(targetScale = PageArriveScale, animationSpec = scale)
            }
        }
}

// The page fades in faster than it slides and settles its size last, so the three parts are seen
// one after another rather than all at once.
private const val PageFadeMillis = 300
private const val PageSlideMillis = 400
private const val PageScaleMillis = 500

// A page arrives slightly too large and leaves slightly small; the page it covers does the reverse.
private const val PageArriveScale = 1.15f
private const val PageCoveredScale = 0.95f

// A tab is not a page being pushed, so it keeps the plainer move it has always had.
private const val TabFadeMillis = 340
private const val TabLeaveScale = 0.9f
