package me.bmax.apatch.ui.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle

internal class AsterNavigationTransitions(
    private val primaryRoutes: Set<String>,
) : NavHostAnimatedDestinationStyle() {
    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        {
            if (targetState.destination.route !in primaryRoutes) {
                slideInHorizontally(initialOffsetX = { it })
            } else {
                fadeIn(animationSpec = tween(340))
            }
        }

    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        {
            if (
                initialState.destination.route in primaryRoutes &&
                targetState.destination.route !in primaryRoutes
            ) {
                slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut()
            } else {
                fadeOut(animationSpec = tween(340))
            }
        }

    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        {
            if (targetState.destination.route in primaryRoutes) {
                slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()
            } else {
                fadeIn(animationSpec = tween(340))
            }
        }

    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        {
            if (initialState.destination.route !in primaryRoutes) {
                scaleOut(targetScale = 0.9f) + fadeOut()
            } else {
                fadeOut(animationSpec = tween(340))
            }
        }
}
