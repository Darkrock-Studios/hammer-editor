package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.*
import io.github.sudarshanmhasrup.splashify.ui.config.SplashScreenSize
import io.github.sudarshanmhasrup.splashify.ui.config.SplashScreenStyle
import io.github.sudarshanmhasrup.splashify.ui.splashscreen.SimpleSplashScreen
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

@Composable
fun SplashScreen() {
	val size = SplashScreenSize(
		width = 600.dp,
		height = 380.dp
	)

	val style = SplashScreenStyle(
		backgroundColor = SplashBackground,
		cornerRadius = 16.dp
	)

	val typewriter = FontFamily(Font(Res.font.Kingthings_Trypewriter_2))
	val mono = FontFamily(Font(Res.font.IBMPlexMono_SemiBold))

	SimpleSplashScreen(
		size = size,
		style = style
	) { progress ->
		val animatedProgress by animateFloatAsState(targetValue = progress)

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
