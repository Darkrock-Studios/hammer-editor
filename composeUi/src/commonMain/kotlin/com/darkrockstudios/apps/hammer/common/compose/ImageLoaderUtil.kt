package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import com.seiko.imageloader.intercept.Interceptor
import com.seiko.imageloader.model.ImageResult
import com.seiko.imageloader.model.NullRequestData
import com.seiko.imageloader.util.LogPriority
import com.seiko.imageloader.util.Logger
import io.github.aakira.napier.Napier

class ImageLoaderNapierLogger(private val setPriority: LogPriority) : Logger {

	override fun isLoggable(priority: LogPriority): Boolean {
		return setPriority.ordinal <= priority.ordinal
	}

	override fun log(priority: LogPriority, tag: String, data: Any?, throwable: Throwable?, message: String) {
		when (priority) {
			LogPriority.VERBOSE -> Napier.v { message }
			LogPriority.DEBUG -> Napier.d { message }
			LogPriority.INFO -> Napier.i { message }
			LogPriority.WARN -> Napier.w { message }
			LogPriority.ERROR -> Napier.e(throwable = throwable, message = message)
			LogPriority.ASSERT -> assert(false) { message }
		}
	}
}

/**
 * return empty painter if request is null or empty
 */
object NullDataInterceptor : Interceptor {

	override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
		val data = chain.request.data
		if (data === NullRequestData || data is String && data.isEmpty()) {
			return ImageResult.Painter(
				request = chain.request,
				painter = EmptyPainter,
			)
		}
		return chain.proceed(chain.request)
	}

	private object EmptyPainter : Painter() {
		override val intrinsicSize: Size get() = Size.Unspecified
		override fun DrawScope.onDraw() {}
	}
}