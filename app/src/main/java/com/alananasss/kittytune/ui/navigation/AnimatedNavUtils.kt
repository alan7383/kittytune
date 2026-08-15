    package com.alananasss.kittytune.ui.navigation

    import android.os.Build
    import android.view.RoundedCorner
    import androidx.compose.animation.AnimatedVisibilityScope
    import androidx.compose.animation.EnterExitState
    import androidx.compose.animation.core.animateDp
    import androidx.compose.animation.core.keyframes
    import androidx.compose.animation.core.tween
    import androidx.compose.foundation.layout.Box
    import androidx.compose.foundation.layout.fillMaxSize
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.remember
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.platform.LocalDensity
    import androidx.compose.ui.unit.Dp
    import androidx.compose.ui.unit.dp
    import androidx.navigation.NamedNavArgument
    import androidx.navigation.NavBackStackEntry
    import androidx.navigation.NavGraphBuilder
    import androidx.navigation.compose.composable

    @Composable
    fun getScreenCornerRadius(): Dp {
        val context = LocalContext.current
        val density = LocalDensity.current

        return remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE)
                        as android.view.WindowManager
                val windowInsets = windowManager.currentWindowMetrics.windowInsets
                val roundedCorner = windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                if (roundedCorner != null) {
                    with(density) { roundedCorner.radius.toDp() }
                } else {
                    28.dp
                }
            } else {
                28.dp
            }
        }
    }

    @Composable
    fun AnimatedVisibilityScope.ClippedScreen(
        content: @Composable () -> Unit
    ) {
        val deviceCornerRadius = getScreenCornerRadius()

        val cornerRadius by transition.animateDp(
            transitionSpec = {
                when {
                    EnterExitState.PreEnter isTransitioningTo EnterExitState.Visible -> {
                        keyframes {
                            durationMillis = 400
                            deviceCornerRadius at 0
                            deviceCornerRadius at 300
                            0.dp at 400
                        }
                    }
                    EnterExitState.Visible isTransitioningTo EnterExitState.PostExit -> {
                        tween(50)
                    }
                    else -> tween(50)
                }
            },
            label = "screenCornerRadius"
        ) { state ->
            when (state) {
                EnterExitState.PreEnter -> deviceCornerRadius
                EnterExitState.Visible -> 0.dp
                EnterExitState.PostExit -> deviceCornerRadius
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius))
        ) {
            content()
        }
    }

    fun NavGraphBuilder.clippedComposable(
        route: String,
        arguments: List<NamedNavArgument> = emptyList(),
        content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit
    ) {
        composable(route, arguments) { entry ->
            ClippedScreen {
                content(entry)
            }
        }
    }

