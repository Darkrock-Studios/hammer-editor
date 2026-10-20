package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Surface texture for tonal regions and cards, a prototype dimension alongside [HdGrouping].
 * Kept far below text contrast: dyslexia guidance asks for plain backgrounds behind text,
 * so any shipped texture needs an off switch. Wells stay plain.
 */
enum class HdTexture {
	None,

	/** Fine random paper grain. */
	Grain,

	/** Laid paper: close horizontal laid lines crossed by sparse vertical chain lines. */
	Laid,

	/** A fine halftone dot grid. */
	Dots,
}

val LocalHdTexture = staticCompositionLocalOf { HdTexture.None }

/** A tonal surface fill in [shape], with [LocalHdTexture] laid over it. */
@Composable
internal fun Modifier.hdSurface(color: Color, shape: Shape = RectangleShape): Modifier {
	val base = background(color, shape)
	val texture = LocalHdTexture.current
	if (texture == HdTexture.None) return base
	val ink = MaterialTheme.colorScheme.onSurface
	val density = LocalDensity.current.density
	val brush = remember(texture, ink, density) { textureBrush(texture, ink, density) }
	return base.drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), brush) }
}

private fun textureBrush(texture: HdTexture, ink: Color, density: Float): Brush {
	fun px(dp: Float) = (dp * density).roundToInt().coerceAtLeast(1)
	val paint = Paint()
	val tile: ImageBitmap = when (texture) {
		HdTexture.Grain -> {
			val size = px(48f)
			ImageBitmap(size, size).also { bitmap ->
				val canvas = Canvas(bitmap)
				val random = Random(7)
				val dot = px(0.5f).toFloat()
				for (y in 0 until size step dot.toInt()) {
					for (x in 0 until size step dot.toInt()) {
						val a = random.nextFloat()
						if (a < 0.45f) continue
						paint.color = ink.copy(alpha = (a - 0.45f) * 0.07f)
						canvas.drawRect(x.toFloat(), y.toFloat(), x + dot, y + dot, paint)
					}
				}
			}
		}

		HdTexture.Laid -> {
			val w = px(26f)
			val h = px(3f)
			ImageBitmap(w, h).also { bitmap ->
				val canvas = Canvas(bitmap)
				val line = px(0.5f).toFloat()
				paint.color = ink.copy(alpha = 0.035f)
				canvas.drawRect(0f, 0f, w.toFloat(), line, paint)
				paint.color = ink.copy(alpha = 0.03f)
				canvas.drawRect(0f, 0f, line, h.toFloat(), paint)
			}
		}

		HdTexture.Dots -> {
			val size = px(5f)
			ImageBitmap(size, size).also { bitmap ->
				paint.color = ink.copy(alpha = 0.07f)
				Canvas(bitmap).drawCircle(Offset(size / 2f, size / 2f), 0.6f * density, paint)
			}
		}

		HdTexture.None -> error("no texture")
	}
	return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}
