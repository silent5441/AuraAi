package com.rk.activities.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.rk.components.GlobalToolbarActions
import com.rk.components.compose.appbars.TerminalHeader
import com.rk.components.isPermanentDrawer
import com.rk.drawer.DrawerViewModel
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.terminal.isV
import com.rk.utils.toast
import kotlinx.coroutines.launch

@Composable
fun XedTopBar(
    drawerState: DrawerState,
    viewModel: MainViewModel,
    drawerViewModel: DrawerViewModel,
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    val currentTab =
        if (viewModel.tabs.isNotEmpty()) {
            if (isV) {
                viewModel.tabs[viewModel.currentTabIndex]
            } else {
                viewModel.tabs.getOrNull(viewModel.currentTabIndex)
            }
        } else {
            null
        }

    val title = currentTab?.tabTitle?.value ?: strings.app_name.getString()

    AnimatedVisibility(visible = viewModel.showTopBar, enter = expandVertically(), exit = shrinkVertically()) {
        TerminalHeader(
            title = title,
            modifier =
                Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                    )
                },
            actions = {
                if (!isPermanentDrawer) {
                    IconButton(
                        onClick = {
                            scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() }
                        }
                    ) {
                        Icon(Icons.Outlined.Menu, null)
                    }
                }

                GlobalToolbarActions(viewModel, drawerViewModel)

                if (currentTab != null) {
                    currentTab.apply { Actions() }
                } else if (viewModel.tabs.isNotEmpty()) {
                    toast(strings.unknown_error)
                }
            },
        )
    }
}