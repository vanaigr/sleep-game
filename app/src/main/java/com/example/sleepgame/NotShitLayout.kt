package com.example.sleepgame

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt

private data class FreePositionData(
    val x: Float,
    val y: Float,
    val width: Float?,
    val height: Float?,
)

private class FreePositionModifier(
    private val data: FreePositionData,
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = data
}

fun Modifier.freePosition(
    x: Float,
    y: Float,
    width: Float? = null,
    height: Float? = null,
): Modifier = then(
    FreePositionModifier(
        FreePositionData(
            x = x,
            y = y,
            width = width,
            height = height,
        )
    )
)

@Composable
fun FreeLayout(
    innerUnits: Offset,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, constraints ->
        val parentWidth = constraints.maxWidth
        val parentHeight = constraints.maxHeight

        val children = measurables.map { measurable ->
            val data = measurable.parentData as? FreePositionData
                ?: FreePositionData(
                    x = 0f,
                    y = 0f,
                    width = null,
                    height = null,
                )

            val fixedWidth = data.width?.let {
                (parentWidth * it / innerUnits.x).roundToInt().coerceAtLeast(0)
            }

            val fixedHeight = data.height?.let {
                (parentHeight * it / innerUnits.y).roundToInt().coerceAtLeast(0)
            }

            val childConstraints = Constraints(
                minWidth = fixedWidth ?: 0,
                maxWidth = fixedWidth ?: Constraints.Infinity,
                minHeight = fixedHeight ?: 0,
                maxHeight = fixedHeight ?: Constraints.Infinity,
            )

            val placeable = measurable.measure(childConstraints)

            ChildPlacement(
                placeable = placeable,
                x = (parentWidth * data.x / innerUnits.x).roundToInt(),
                y = (parentHeight * data.y / innerUnits.y).roundToInt(),
            )
        }

        layout(parentWidth, parentHeight) {
            children.forEach { child ->
                child.placeable.place(
                    x = child.x,
                    y = child.y,
                )
            }
        }
    }
}

private data class ChildPlacement(
    val placeable: androidx.compose.ui.layout.Placeable,
    val x: Int,
    val y: Int,
)