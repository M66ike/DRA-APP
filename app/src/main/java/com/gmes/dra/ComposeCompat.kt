package com.gmes.dra

import androidx.compose.runtime.Composable

/**
 * Keeps the home-screen call site compatible with the Material 3 FAB API used by this project.
 */
@Composable
fun ExtendedFloatingActionButton(
    onClick: () -> Unit,
    text: @Composable () -> Unit
) {
    androidx.compose.material3.ExtendedFloatingActionButton(onClick = onClick) {
        text()
    }
}
