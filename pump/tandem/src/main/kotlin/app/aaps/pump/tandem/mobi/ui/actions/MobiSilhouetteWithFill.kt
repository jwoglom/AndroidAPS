package app.aaps.pump.tandem.mobi.ui.actions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.mobi.ui.theme.TMobiScreensTheme

private const val VIEWBOX = 128f
private const val WINDOW_LEFT = 46.17f
private const val WINDOW_TOP = 56.58f
private const val WINDOW_WIDTH = 36.31f
private const val WINDOW_HEIGHT = 37.93f

@Composable
fun MobiSilhouetteWithFill(
    fillFraction: Float,
    modifier: Modifier = Modifier,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    silhouetteTint: Color? = null,
) {
    val clamped = fillFraction.coerceIn(0f, 1f)
    Box(modifier = modifier.aspectRatio(1f)) {
        Image(
            painter = painterResource(R.drawable.mobi),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            colorFilter = silhouetteTint?.let { ColorFilter.tint(it) },
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            val winLeft = size.width * (WINDOW_LEFT / VIEWBOX)
            val winTop = size.height * (WINDOW_TOP / VIEWBOX)
            val winWidth = size.width * (WINDOW_WIDTH / VIEWBOX)
            val winHeight = size.height * (WINDOW_HEIGHT / VIEWBOX)
            val fillHeight = winHeight * clamped
            drawRect(
                color = fillColor,
                topLeft = Offset(winLeft, winTop + (winHeight - fillHeight)),
                size = Size(winWidth, fillHeight),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MobiSilhouetteWithFillPreview_Full() {
    TMobiScreensTheme { MobiSilhouetteWithFill(fillFraction = 1.0f, modifier = Modifier) }
}

@Preview(showBackground = true)
@Composable
private fun MobiSilhouetteWithFillPreview_Half() {
    TMobiScreensTheme { MobiSilhouetteWithFill(fillFraction = 0.5f, modifier = Modifier) }
}

@Preview(showBackground = true)
@Composable
private fun MobiSilhouetteWithFillPreview_Empty() {
    TMobiScreensTheme { MobiSilhouetteWithFill(fillFraction = 0f, modifier = Modifier) }
}
