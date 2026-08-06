package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.darkrockstudios.apps.hammer.*
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusApplicationScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// Splash is always dark and brand-forward, independent of the app theme.
// Matching the background to the logo's own tile lets the hammer float seamlessly.
private val SplashBackground = Color(0xFF2A2F2D)
private val Rust = Color(0xFFB7410E)
private val TitleColor = Color(0xFFEDE0DB)
private val SubtitleColor = Color(0xFFB9ACA6)
private val TrackColor = Color(0x33FFFFFF)

private const val SplashDurationMs = 600

/**
 * Borderless splash window shown while the main window spins up. Fills the
 * progress bar over [SplashDurationMs], then calls [onFinished].
 */
@Composable
internal fun NucleusApplicationScope.SplashWindow(onFinished: () -> Unit) {
	val windowState = rememberWindowState(
		size = DpSize(600.dp, 380.dp),
		position = WindowPosition(Alignment.Center),
	)

	DecoratedWindow(
		onCloseRequest = onFinished,
		state = windowState,
		title = "",
		undecorated = true,
		resizable = false,
	) {
		var filling by remember { mutableStateOf(false) }

		// One wall-clock wait, timed off the UI thread: a stalled renderer must not be
		// able to stretch the hand-off, and the hand-off must not ride the frame clock.
		LaunchedEffect(Unit) {
			filling = true
			withContext(Dispatchers.Default) { delay(SplashDurationMs.toLong()) }
			onFinished()
		}

		SplashScreen(if (filling) 1f else 0f)
	}
}

@Composable
private fun SplashScreen(progress: Float) {
	val typewriter = FontFamily(Font(Res.font.Kingthings_Trypewriter_2))
	val mono = FontFamily(Font(Res.font.IBMPlexMono_SemiBold))

	val animatedProgress by animateFloatAsState(
		targetValue = progress,
		animationSpec = tween(durationMillis = SplashDurationMs, easing = LinearEasing),
	)

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(SplashBackground),
		contentAlignment = Alignment.Center,
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 64.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			Image(
				painter = painterResource(Res.drawable.hammer_icon),
				contentDescription = null,
				modifier = Modifier.size(116.dp)
			)

			Spacer(Modifier.height(16.dp))

			Text(
				text = stringResource(Res.string.app_name),
				fontFamily = typewriter,
				fontWeight = FontWeight.Bold,
				fontSize = 52.sp,
				color = TitleColor
			)

			Spacer(Modifier.height(2.dp))

			Text(
				text = stringResource(Res.string.about_description),
				fontFamily = mono,
				fontSize = 15.sp,
				color = SubtitleColor,
				textAlign = TextAlign.Center
			)

			Spacer(Modifier.height(36.dp))

			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(6.dp)
					.clip(CircleShape)
					.background(TrackColor)
			) {
				Box(
					modifier = Modifier
						.fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
						.height(6.dp)
						.clip(CircleShape)
						.background(Rust)
				)
			}
		}
	}
}
