package com.darkrockstudios.apps.hammer.common.intro

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.romanNumeral
import com.darkrockstudios.apps.hammer.common.components.intro.Intro
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.mouseDragPaging
import com.darkrockstudios.apps.hammer.common.compose.onKeyShortcut
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdQuietButton
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.intro_back
import com.darkrockstudios.apps.hammer.intro_get_started
import com.darkrockstudios.apps.hammer.intro_next
import com.darkrockstudios.apps.hammer.intro_skip
import com.darkrockstudios.apps.hammer.intro_slide_position

private val CompactHeightThreshold = 600.dp
private val WideThreshold = 720.dp

/** Height kept under a hero slide's drawing for the gap, the caption line and the slide's padding. */
private val HeroCaptionRoom = 112.dp

@Composable
fun IntroUi(component: Intro, modifier: Modifier = Modifier) {
	val state by component.state.subscribeAsState()
	val slides = Intro.Slide.entries
	val pagerState = rememberPagerState(initialPage = state.slide) { slides.size }

	// The component owns the slide and the pager follows it. While the pager rests on a page other
	// than the component's slide and is not on its way there, a swipe or drag put it there, so the
	// component follows instead. Decompose's ChildPages can't do this: it reports any page it rests on.
	val following = remember { PagerFollow() }
	LaunchedEffect(state.slide) {
		val slide = state.slide
		if (pagerState.currentPage == slide) return@LaunchedEffect
		try {
			pagerState.animateScrollToPage(slide)
		} finally {
			// Finished, or a drag took over; unless a newer slide already has, the pager is free again.
			if (following.wanted == slide) following.target = null
		}
	}
	LaunchedEffect(pagerState, component) {
		snapshotFlow { PagerRest(pagerState.isScrollInProgress, pagerState.settledPage, state.slide) }.collect { rest ->
			// A new slide from the component comes first: the pager is about to follow it.
			if (rest.slide != following.wanted) {
				following.wanted = rest.slide
				following.target = if (pagerState.currentPage == rest.slide) null else rest.slide
			} else if (!rest.scrolling && following.target == null && rest.page != rest.slide) {
				component.showSlide(rest.page)
			}
		}
	}

	val focusRequester = remember { FocusRequester() }
	LaunchedEffect(Unit) { focusRequester.requestFocus() }

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background)
			.safeDrawingPadding()
			.focusRequester(focusRequester)
			.focusable()
			.onKeyShortcut(Key.DirectionRight) { if (!state.isLast) component.next() }
			.onKeyShortcut(Key.DirectionLeft, action = component::back),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(Ui.TOP_BAR_HEIGHT)
				.padding(horizontal = Ui.Padding.XL),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.End,
		) {
			if (!state.isLast) {
				HdQuietButton(onClick = component::leave, label = Res.string.intro_skip.get())
			}
		}

		HorizontalPager(
			state = pagerState,
			modifier = Modifier.weight(1f).fillMaxWidth().mouseDragPaging(pagerState),
		) { page ->
			IntroSlide(
				slide = slides[page],
				active = pagerState.currentPage == page,
			)
		}

		HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
			verticalAlignment = Alignment.CenterVertically,
		) {
			IntroPageIndicator(pagerState, modifier = Modifier.weight(1f))
			Row(horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
				if (!state.isFirst) {
					HdHairlineButton(label = Res.string.intro_back.get(), onClick = component::back)
				}
				HdHairlineButton(
					label = (if (state.isLast) Res.string.intro_get_started else Res.string.intro_next).get(),
					onClick = component::next,
					emphasised = true,
				)
			}
		}
	}
}

@Composable
private fun IntroSlide(slide: Intro.Slide, active: Boolean) {
	BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
		val short = maxHeight < CompactHeightThreshold
		val wide = maxWidth >= WideThreshold
		val roomy = wide && !short
		val gutter = if (wide) Ui.Padding.XXL else Ui.Padding.XL
		val drawingRoom = when {
			slide.hero -> maxHeight - HeroCaptionRoom
			roomy -> maxHeight * 0.5f
			short -> IllustrationHeightShort
			else -> IllustrationHeight
		}
		val sketch = layoutSketch(slide, roomWidth = maxWidth - gutter * 2, roomHeight = drawingRoom)
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.heightIn(min = maxHeight)
				.padding(horizontal = gutter, vertical = Ui.Padding.XL),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center,
		) {
			IntroIllustration(slide, active, sketch.box, Modifier.size(sketch.size))
			Spacer(Modifier.height(if (short || slide.hero) Ui.Padding.XL else Ui.Padding.XXL))
			Column(
				modifier = Modifier
					.widthIn(max = if (roomy || slide.hero) Ui.MaxWidth.Modal else Ui.MaxWidth.DialogNarrow)
					.fillMaxWidth(),
			) {
				// A hero slide is unnumbered, so the feature slides read § I to § V.
				if (!slide.hero) {
					HdMonoLabel(text = "§ ${romanNumeral(slide.ordinal)}")
					Spacer(Modifier.height(Ui.Padding.M))
				}
				slide.title?.let { title ->
					Text(
						text = title.get(),
						style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
						color = MaterialTheme.colorScheme.onSurface,
						modifier = Modifier.semantics { heading() },
					)
					Spacer(Modifier.height(Ui.Padding.L))
				}
				Text(
					text = slide.body.get(),
					style = if (roomy && !slide.hero) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = if (slide.hero) TextAlign.Center else null,
					modifier = Modifier.fillMaxWidth(),
				)
			}
		}
	}
}

@Composable
private fun IntroPageIndicator(pagerState: PagerState, modifier: Modifier = Modifier) {
	val position = Res.string.intro_slide_position.get(pagerState.currentPage + 1, pagerState.pageCount)
	Row(
		modifier = modifier.semantics { contentDescription = position },
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		repeat(pagerState.pageCount) { index ->
			val current = index == pagerState.currentPage
			val width by animateDpAsState(if (current) 24.dp else 8.dp, label = "introIndicatorWidth")
			val color by animateColorAsState(
				if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
				label = "introIndicatorColor",
			)
			Box(Modifier.size(width = width, height = 3.dp).background(color))
		}
	}
}

private class PagerFollow {
	/** The component's slide as last seen. */
	var wanted: Int = -1

	/** The slide the pager is scrolling to for the component, while it does. */
	var target: Int? = null
}

private data class PagerRest(val scrolling: Boolean, val page: Int, val slide: Int)
