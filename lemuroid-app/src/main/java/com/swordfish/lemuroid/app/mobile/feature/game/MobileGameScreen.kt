package com.swordfish.lemuroid.app.mobile.feature.game

import android.graphics.RectF
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelTouchControls.Companion.MENU_LOADING_ANIMATION_MILLIS
import com.swordfish.lemuroid.app.shared.settings.HapticFeedbackMode
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.LemuroidPadTheme
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.GlassSurface
import com.swordfish.touchinput.radial.ui.LemuroidButtonPressFeedback
import gg.padkit.PadKit
import gg.padkit.config.HapticFeedbackType
import gg.padkit.inputstate.InputState

@Composable
fun MobileGameScreen(viewModel: BaseGameScreenViewModel) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = constraints.maxWidth > constraints.maxHeight

        LaunchedEffect(isLandscape) {
            val orientation =
                if (isLandscape) {
                    TouchControllerSettingsManager.Orientation.LANDSCAPE
                } else {
                    TouchControllerSettingsManager.Orientation.PORTRAIT
                }
            viewModel.onScreenOrientationChanged(orientation)
        }

        val controllerConfigState = viewModel.getTouchControllerConfig().collectAsState(null)
        val touchControlsVisibleState = viewModel.isTouchControllerVisible().collectAsState(false)
        val touchControllerSettingsState =
            viewModel
                .getTouchControlsSettings(LocalDensity.current, WindowInsets.displayCutout)
                .collectAsState(null)

        val touchControllerSettings = touchControllerSettingsState.value
        val currentControllerConfig = controllerConfigState.value

        val tiltConfiguration = viewModel.getTiltConfiguration().collectAsState(TiltConfiguration.Disabled)
        val tiltSimulatedStates = viewModel.getSimulatedTiltEvents().collectAsState(InputState())
        val tiltSimulatedControls = remember { derivedStateOf { tiltConfiguration.value.controlIds() } }

        val touchGamePads = currentControllerConfig?.getTouchControllerConfig()
        val leftGamePad = touchGamePads?.leftComposable
        val rightGamePad = touchGamePads?.rightComposable

        val hapticFeedbackMode =
            viewModel
                .getTouchHapticFeedbackMode()
                .collectAsState(HapticFeedbackMode.NONE)

        val padHapticFeedback =
            when (hapticFeedbackMode.value) {
                HapticFeedbackMode.NONE -> HapticFeedbackType.NONE
                HapticFeedbackMode.PRESS -> HapticFeedbackType.PRESS
                HapticFeedbackMode.PRESS_RELEASE -> HapticFeedbackType.PRESS_RELEASE
            }

        val fullScreenPosition = remember { mutableStateOf<Rect?>(null) }
        val viewportPosition = remember { mutableStateOf<Rect?>(null) }
        val moveScreenMode = remember { mutableStateOf(false) }

        // True while a physical controller is connected: the touch controls are hidden then.
        val controllerConnected = !touchControlsVisibleState.value

        // With a physical controller there are no touch controls to edit, so the
        // "Edit Controls" menu entry opens the move-screen mode directly.
        val editControlsShown = viewModel.isEditControlShown().collectAsState(false)
        LaunchedEffect(editControlsShown.value, controllerConnected) {
            if (editControlsShown.value && controllerConnected) {
                viewModel.showEditControls(false)
                moveScreenMode.value = true
            }
        }

        PadKit(
            modifier = Modifier.fillMaxSize(),
            onInputEvents = { viewModel.handleVirtualInputEvent(it) },
            hapticFeedbackType = padHapticFeedback,
            simulatedState = tiltSimulatedStates,
            simulatedControlIds = tiltSimulatedControls,
        ) {
            val localContext = LocalContext.current
            val lifecycle = LocalLifecycleOwner.current

            AndroidView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { fullScreenPosition.value = it.boundsInRoot() },
                factory = {
                    viewModel.createRetroView(localContext, lifecycle)
                },
            )

            val fullPos = fullScreenPosition.value
            val viewPos = viewportPosition.value

            val screenOffsetX =
                if (controllerConnected) {
                    touchControllerSettings?.controllerScreenOffsetX ?: 0f
                } else {
                    touchControllerSettings?.screenOffsetX ?: 0f
                }
            val screenOffsetY =
                if (controllerConnected) {
                    touchControllerSettings?.controllerScreenOffsetY ?: 0f
                } else {
                    touchControllerSettings?.screenOffsetY ?: 0f
                }

            LaunchedEffect(fullPos, viewPos, screenOffsetX, screenOffsetY) {
                val gameView = viewModel.retroGameView.retroGameViewFlow()
                if (fullPos == null || viewPos == null) return@LaunchedEffect
                val viewport =
                    computeGameViewport(fullPos, viewPos, screenOffsetX, screenOffsetY)
                gameView.viewport = viewport
            }

            ConstraintLayout(
                modifier = Modifier.fillMaxSize(),
                constraintSet =
                    GameScreenLayout.buildConstraintSet(
                        isLandscape,
                        currentControllerConfig?.allowTouchOverlay ?: true,
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .layoutId(GameScreenLayout.CONSTRAINTS_GAME_VIEW)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                            .onGloballyPositioned { viewportPosition.value = it.boundsInRoot() },
                )

                val isVisible =
                    touchControllerSettings != null &&
                        currentControllerConfig != null &&
                        touchControlsVisibleState.value

                if (isVisible) {
                    CompositionLocalProvider(LocalLemuroidPadTheme provides LemuroidPadTheme()) {
                        if (!isLandscape) {
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER),
                            )
                        } else if (!currentControllerConfig.allowTouchOverlay) {
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_CONTAINER),
                            )
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_CONTAINER),
                            )
                        }

                        leftGamePad?.invoke(
                            this,
                            Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_PAD),
                            touchControllerSettings,
                        )
                        rightGamePad?.invoke(
                            this,
                            Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_PAD),
                            touchControllerSettings,
                        )

                        GameScreenRunningCentralMenu(
                            modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_GAME_CONTAINER),
                            controllerConfig = currentControllerConfig,
                            touchControllerSettings = touchControllerSettings,
                            viewModel = viewModel,
                            onMoveScreen = { moveScreenMode.value = true },
                        )
                    }
                }
            }
        }

        val moveFullPos = fullScreenPosition.value
        val moveViewPos = viewportPosition.value
        if (moveScreenMode.value && touchControllerSettings != null && moveFullPos != null && moveViewPos != null) {
            MoveGameScreenOverlay(
                viewModel = viewModel,
                settings = touchControllerSettings,
                controllerConnected = controllerConnected,
                fullPos = moveFullPos,
                viewPos = moveViewPos,
                onDone = { moveScreenMode.value = false },
            )
        }

        val isLoading =
            viewModel.loadingState
                .collectAsState(true)
                .value

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun PadContainer(modifier: Modifier = Modifier) {
    val theme = LocalLemuroidPadTheme.current
    GlassSurface(
        modifier = modifier,
        cornerRadius = theme.level0CornerRadius,
        fillColor = theme.level0Fill,
        shadowColor = theme.level0Shadow,
        shadowWidth = theme.level0ShadowWidth,
    )
}

private fun computeGameViewport(
    fullPos: Rect,
    viewPos: Rect,
    offsetX: Float,
    offsetY: Float,
): RectF {
    val left = (viewPos.left - fullPos.left) / fullPos.width
    val top = (viewPos.top - fullPos.top) / fullPos.height
    val right = (viewPos.right - fullPos.left) / fullPos.width
    val bottom = (viewPos.bottom - fullPos.top) / fullPos.height
    return RectF(left + offsetX, top + offsetY, right + offsetX, bottom + offsetY)
}

@Composable
private fun MoveGameScreenOverlay(
    viewModel: BaseGameScreenViewModel,
    settings: TouchControllerSettingsManager.Settings,
    controllerConnected: Boolean,
    fullPos: Rect,
    viewPos: Rect,
    onDone: () -> Unit,
) {
    val currentSettings = rememberUpdatedState(settings)
    var offsetX by remember {
        mutableFloatStateOf(
            if (controllerConnected) settings.controllerScreenOffsetX else settings.screenOffsetX,
        )
    }
    var offsetY by remember {
        mutableFloatStateOf(
            if (controllerConnected) settings.controllerScreenOffsetY else settings.screenOffsetY,
        )
    }

    fun store(
        x: Float,
        y: Float,
    ) {
        val base = currentSettings.value
        viewModel.updateTouchControllerSettings(
            if (controllerConnected) {
                base.copy(controllerScreenOffsetX = x, controllerScreenOffsetY = y)
            } else {
                base.copy(screenOffsetX = x, screenOffsetY = y)
            },
        )
    }

    // The game is fitted inside the viewport, which can be as large as the whole display
    // (landscape). So instead of keeping the viewport fully on screen, we only keep its
    // center on screen. That always leaves room to move, and Reset brings it back.
    val centerX = ((viewPos.center.x - fullPos.left) / fullPos.width)
    val centerY = ((viewPos.center.y - fullPos.top) / fullPos.height)
    val minX = -centerX
    val maxX = 1f - centerX
    val minY = -centerY
    val maxY = 1f - centerY

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(fullPos, viewPos, controllerConnected) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x / fullPos.width).coerceIn(minX, maxX)
                        offsetY = (offsetY + dragAmount.y / fullPos.height).coerceIn(minY, maxY)
                        store(offsetX, offsetY)
                    }
                },
    ) {
        Card(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                    .padding(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "Drag to move the game screen", modifier = Modifier.padding(start = 8.dp))
                TextButton(
                    onClick = {
                        offsetX = 0f
                        offsetY = 0f
                        store(0f, 0f)
                    },
                ) {
                    Text(text = stringResource(R.string.touch_customize_button_reset))
                }
                TextButton(onClick = onDone) {
                    Text(text = stringResource(R.string.touch_customize_button_done))
                }
            }
        }
    }
}

@Composable
private fun GameScreenRunningCentralMenu(
    modifier: Modifier = Modifier,
    viewModel: BaseGameScreenViewModel,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
    controllerConfig: ControllerConfig,
    onMoveScreen: () -> Unit,
) {
    val menuPressed = viewModel.isMenuPressed().collectAsState(false)
    Box(
        modifier = modifier.wrapContentSize(),
        contentAlignment = Alignment.Center,
    ) {
        LemuroidButtonPressFeedback(
            pressed = menuPressed.value,
            animationDurationMillis = MENU_LOADING_ANIMATION_MILLIS,
            icon = R.drawable.button_menu,
        )
        MenuEditTouchControls(viewModel, controllerConfig, touchControllerSettings, onMoveScreen)
    }
}

@Composable
private fun MenuEditTouchControls(
    viewModel: BaseGameScreenViewModel,
    controllerConfig: ControllerConfig,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
    onMoveScreen: () -> Unit,
) {
    val showEditControls = viewModel.isEditControlShown().collectAsState(false)
    if (!showEditControls.value) return

    Dialog(onDismissRequest = { viewModel.showEditControls(false) }) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MenuEditTouchControlRow(Icons.Default.OpenInFull, "Scale", 0f) {
                    Slider(
                        value = touchControllerSettings.scale,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(scale = it),
                            )
                        },
                    )
                }
                MenuEditTouchControlRow(Icons.Default.Height, "Horizontal Margin", 90f) {
                    Slider(
                        value = touchControllerSettings.marginX,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(marginX = it),
                            )
                        },
                    )
                }
                MenuEditTouchControlRow(Icons.Default.Height, "Vertical Margin", 0f) {
                    Slider(
                        value = touchControllerSettings.marginY,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(marginY = it),
                            )
                        },
                    )
                }
                if (controllerConfig.allowTouchRotation) {
                    MenuEditTouchControlRow(Icons.Default.RotateLeft, "Rotate", 0f) {
                        Slider(
                            value = touchControllerSettings.rotation,
                            onValueChange = {
                                viewModel.updateTouchControllerSettings(
                                    touchControllerSettings.copy(rotation = it),
                                )
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = {
                            viewModel.showEditControls(false)
                            onMoveScreen()
                        },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(text = "Move screen")
                    }
                    TextButton(
                        onClick = { viewModel.resetTouchControls() },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(text = stringResource(R.string.touch_customize_button_reset))
                    }
                    TextButton(
                        onClick = { viewModel.showEditControls(false) },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(text = stringResource(R.string.touch_customize_button_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuEditTouchControlRow(
    icon: ImageVector,
    label: String,
    rotation: Float,
    slider: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            modifier = Modifier.rotate(rotation),
            imageVector = icon,
            contentDescription = label,
        )
        slider()
    }
}
