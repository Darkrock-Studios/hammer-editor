package com.darkrockstudios.apps.hammer.common.timeline.layout

import com.darkrockstudios.apps.hammer.common.timeline.layout.LabelFitter.offset
import kotlin.math.abs
import kotlin.math.floor

/**
 * Turns a dated timeline into drawable geometry: axis, lane columns (band, sub-column bars, point
 * marks), the ledger of scenes and unlaned events, milestone rules, folds, the selection's
 * projection and hit regions. Deterministic for a given input and measurer.
 */
object TimelineLayoutEngine {
	private const val PHONE_LANE_INSET = .5f
	private const val PHONE_BAR_GAP = 1f
	private const val LANE_INSET = 8f
	private const val LANE_HEADER_PAD = 18f
	private const val LANE_HEADER_HEIGHT = 24f
	private const val POINT_INSET = 8f
	private const val MARK_REACH = 6f
	private const val LABEL_GAP = 12f
	private const val LABEL_NUDGE = 4f
	private const val LEDGER_MARK_INSET = 4f
	private const val HIT_LEFT_PAD = 6f

	private class Placed(val event: LayoutEvent, val start: Long, val end: Long?) {
		val circa: Boolean get() = event.isCirca
	}

	fun layout(input: TimelineLayoutInput, measurer: TimelineTextMeasurer): TimelineLayout {
		val m = input.metrics
		val phone = input.phone
		val width = input.widthDp
		val frame = resolveFrame(input.events, input.frame)
		val lo = frame.first
		val hi = frame.last

		val placed = clipToFrame(input.events, lo, hi)
		val laneNames = laneList(input.events, input.laneOrder)
		val laneIndex = HashMap<String, Int>(laneNames.size * 2)
		laneNames.forEachIndexed { i, name -> laneIndex[name] = i }

		val scale = buildScaleFor(input, placed, lo, hi)

		val aside = placed.filter { it.event.kind == LayoutKind.SCENE || it.event.laneName == null }
		val asideCandidates = aside.map { p -> asideCandidate(p, scale, laneIndex) }
		// Every titled laned milestone gets a ledger row at its rule, even when its band is labelled.
		val milestoneCandidates = placed
			.filter { it.event.milestone && it.event.kind != LayoutKind.SCENE && it.event.laneName != null && it.event.title.isNotBlank() }
			.map { p -> laneMilestoneCandidate(p, scale, laneIndex) }

		val axisX = if (phone) m.axisWidthPhone else m.axisWidth
		val laneRegionWidth = when {
			laneNames.isEmpty() -> 0f
			phone -> laneNames.size * m.railWidth
			else -> maxOf(0f, width - axisX - m.padRight - LedgerPlacer.ledgerWidth(asideCandidates + milestoneCandidates, measurer, m))
		}
		val laneRight = axisX + laneRegionWidth
		val ledgerX = laneRight + m.ledgerGutter
		val ledgerTextX = ledgerX + m.ledgerGutter
		val laneWidth = if (laneNames.isEmpty()) 0f else laneRegionWidth / laneNames.size

		val lanes = laneNames.mapIndexed { i, name ->
			val header = if (phone) null else {
				LabelFitter.ellipsize(name.uppercase(), laneWidth - LANE_HEADER_PAD, LabelStyle.LaneHeader, measurer)
			}
			LaneColumn(
				name = name,
				index = i,
				xDp = axisX + i * laneWidth,
				widthDp = laneWidth,
				header = header,
				headerTruncated = header != null && header != name.uppercase(),
			)
		}

		val hits = ArrayList<HitRegion>()
		for (lane in lanes) {
			if (lane.header != null) {
				hits += HitRegion(RectDp(lane.xDp, 0f, lane.widthDp, LANE_HEADER_HEIGHT), HitTarget.LaneHeader(lane.name))
			}
		}

		val bands = ArrayList<BandShape>()
		val bars = ArrayList<BarShape>()
		val points = ArrayList<PointMark>()
		val laneCandidates = ArrayList<LedgerCandidate>()
		var labelBottom = Float.NEGATIVE_INFINITY
		val byLane = placed.filter { it.event.kind != LayoutKind.SCENE && it.event.laneName != null }
			.groupBy { laneIndex.getValue(it.event.laneName!!) }
		for (lane in lanes) {
			val items = byLane[lane.index] ?: continue
			labelBottom = maxOf(
				labelBottom,
				layoutLane(input, measurer, scale, lane, items, bands, bars, points, laneCandidates, hits),
			)
		}

		val ledgerRows = layoutLedger(
			candidates = asideCandidates + milestoneCandidates + laneCandidates,
			width = width,
			ledgerX = ledgerX,
			ledgerTextX = ledgerTextX,
			measurer = measurer,
			metrics = m,
		)
		for (row in ledgerRows) hits += HitRegion(row.hitRect, HitTarget.Ledger(row.eventId))

		val milestoneRules = placed.filter { it.event.milestone }
			.map { MilestoneRule(it.event.id, scale.y(it.start)) }
			.sortedWith(compareBy<MilestoneRule>({ it.yDp }, { it.eventId }))

		val folds = scale.segments.filter { it.fold }.map { FoldBand(it.y0, it.y1) }
		val axis = AxisLayout(
			xDp = axisX,
			ticks = axisTicks(input, placed, scale, lo, hi),
			foldGlyphYs = folds.map { (it.y0 + it.y1) / 2f },
		)

		val projection = input.selectedId?.let { id ->
			placed.firstOrNull { it.event.id == id }?.let { p ->
				val y = scale.y(p.start)
				val y1 = p.end?.let { scale.y(it) }
				ProjectionOverlay(id, y, y1, RectDp(axisX, y, laneRegionWidth, (y1 ?: y) - y))
			}
		}

		val ledgerBottom = ledgerRows.maxOfOrNull { it.hitRect.bottom } ?: Float.NEGATIVE_INFINITY
		val height = maxOf(scale.height, ledgerBottom + m.padBottom, labelBottom + m.padBottom)

		return TimelineLayout(
			widthDp = width,
			heightDp = height,
			axis = axis,
			lanes = lanes,
			laneRegion = RectDp(axisX, 0f, laneRegionWidth, height),
			ledgerXDp = ledgerX,
			ledgerTextXDp = ledgerTextX,
			ledgerWidthDp = width - m.padRight - laneRight,
			folds = folds,
			milestoneRules = milestoneRules,
			bands = bands,
			bars = bars,
			points = points,
			ledgerRows = ledgerRows,
			projection = projection,
			hitRegions = hits,
		)
	}

	/** Drops events wholly outside [lo, hi] and clamps the rest to it, in (start, order, id) order. */
	private fun clipToFrame(events: List<LayoutEvent>, lo: Long, hi: Long): List<Placed> {
		val out = ArrayList<Placed>(events.size)
		for (e in events) {
			val end = e.spanEnd
			if (end == null || end == e.start) {
				if (e.start in lo..hi) out += Placed(e, e.start, end)
			} else if (end > lo && e.start < hi) {
				out += Placed(e, maxOf(e.start, lo), minOf(end, hi))
			}
		}
		out.sortWith(compareBy<Placed>({ it.start }, { it.event.order }, { it.event.id }))
		return out
	}

	/** Lanes from [laneOrder] that some event uses, then the remaining used lanes alphabetically. */
	private fun laneList(events: List<LayoutEvent>, laneOrder: List<String>): List<String> {
		val used = HashSet<String>()
		for (e in events) e.laneName?.let { used += it }
		val ordered = laneOrder.filter { it in used }.distinct()
		val rest = (used - ordered.toSet()).sorted()
		return ordered + rest
	}

	private fun buildScaleFor(input: TimelineLayoutInput, placed: List<Placed>, lo: Long, hi: Long): PiecewiseScale {
		var count = 0
		for (p in placed) count += if (p.end != null) 2 else 1
		val positions = LongArray(count)
		var i = 0
		for (p in placed) {
			positions[i++] = p.start
			p.end?.let { positions[i++] = it }
		}
		val bp = breakpoints(positions, lo, hi)
		return when (input.view) {
			TimelineView.SEQUENCE -> buildSequenceScale(bp, input.metrics)
			TimelineView.TO_SCALE -> buildScale(
				breakpoints = bp,
				spans = placed.mapNotNull { p -> p.end?.let { p.start..it } },
				lo = lo,
				hi = hi,
				pxPerUnit = medianGapPxPerUnit(bp, input.metrics.maxGap),
				metrics = input.metrics,
			)
		}
	}

	private fun asideCandidate(p: Placed, scale: PiecewiseScale, laneIndex: Map<String, Int>): LedgerCandidate {
		val e = p.event
		val mark = when {
			e.milestone -> LedgerMark.MilestoneDiamond
			e.kind == LayoutKind.SCENE -> LedgerMark.Pilcrow
			p.end != null -> LedgerMark.SpanBar
			else -> LedgerMark.Dot
		}
		return LedgerCandidate(
			eventId = e.id,
			kind = e.kind,
			anchorY = scale.y(p.start),
			anchorY1 = p.end?.let { scale.y(it) },
			title = e.title,
			sub = e.subtitle,
			style = if (e.milestone) LedgerStyle.Milestone else LedgerStyle.Muted,
			mark = mark,
			laneIndex = e.laneName?.let { laneIndex[it] },
			pinned = e.milestone,
			order = e.order,
		)
	}

	private fun laneMilestoneCandidate(p: Placed, scale: PiecewiseScale, laneIndex: Map<String, Int>): LedgerCandidate {
		val e = p.event
		return LedgerCandidate(
			eventId = e.id,
			kind = e.kind,
			anchorY = scale.y(p.start),
			anchorY1 = null,
			title = e.title,
			sub = e.subtitle,
			style = LedgerStyle.Milestone,
			mark = LedgerMark.None,
			laneIndex = laneIndex.getValue(e.laneName!!),
			pinned = true,
			order = e.order,
		)
	}

	/**
	 * Lays out one lane's spans and points, appending to the output lists. Returns the bottom of
	 * the lowest leader label.
	 */
	private fun layoutLane(
		input: TimelineLayoutInput,
		measurer: TimelineTextMeasurer,
		scale: PiecewiseScale,
		lane: LaneColumn,
		items: List<Placed>,
		bands: MutableList<BandShape>,
		bars: MutableList<BarShape>,
		points: MutableList<PointMark>,
		ledgerCandidates: MutableList<LedgerCandidate>,
		hits: MutableList<HitRegion>,
	): Float {
		val m = input.metrics
		val phone = input.phone
		val x0 = lane.xDp + if (phone) PHONE_LANE_INSET else LANE_INSET
		val bandWidth = if (phone) m.railBand else {
			floor(lane.widthDp * m.bandLaneFraction).coerceIn(m.bandMin, m.bandMax)
		}
		val barWidth = if (phone) m.railBar else m.barWidth
		val barStep = if (phone) 0f else m.barStep
		val barX0 = x0 + bandWidth + if (phone) PHONE_BAR_GAP else m.barOffset

		val y0s = FloatArray(items.size) { scale.y(items[it].start) }
		val y1s = FloatArray(items.size) { i ->
			items[i].end?.let { maxOf(scale.y(it), y0s[i] + m.minSpanHeight) } ?: y0s[i]
		}
		val spanIndices = items.indices.filter { items[it].end != null }
		val packing = LanePacker.pack(spanIndices.map { i ->
			PackItem(y0s[i], y1s[i], items[i].end!! - items[i].start, items[i].start)
		})
		val columns = IntArray(items.size)
		spanIndices.forEachIndexed { k, i -> columns[i] = packing.columns[k] }

		val labelX = barX0 + maxOf(0, packing.columnCount - 1) * barStep + LABEL_GAP
		val laneRight = lane.xDp + lane.widthDp
		val labelLineHeight = m.ledgerLineHeight

		val order = items.indices.sortedWith(compareBy<Int>({ y0s[it] }, { it }))
		val lanePoints = ArrayList<PointMark>()
		val pointHits = ArrayList<HitRegion>()
		var bandCount = 0
		var lastBottom = Float.NEGATIVE_INFINITY

		for (i in order) {
			val p = items[i]
			val e = p.event
			val y0 = y0s[i]
			val isSpan = p.end != null
			val markX: Float
			val markY: Float
			var bandLabel: BandLabel = BandLabel.None
			val rect: RectDp?
			val pointX: Float
			if (isSpan) {
				val inBand = columns[i] == 0
				val bx = if (inBand) x0 else barX0 + (columns[i] - 1) * barStep
				val bw = if (inBand) bandWidth else barWidth
				val h = y1s[i] - y0
				rect = RectDp(bx, y0, bw, h)
				markX = bx + bw
				markY = y0 + minOf(MARK_REACH, h / 2f)
				pointX = 0f
				if (inBand && !phone) {
					bandLabel = LabelFitter.fitBandLabel(e.title, bandWidth, h, measurer).offset(bx, y0)
				}
			} else {
				rect = null
				pointX = x0 + if (phone) bandWidth / 2f else bandWidth - POINT_INSET
				markX = pointX + MARK_REACH
				markY = y0
			}

			val labelled = bandLabel != BandLabel.None
			var leader: LeaderLabel? = null
			if (!labelled && !e.milestone && e.title.isNotBlank()) {
				when {
					phone -> ledgerCandidates += LedgerCandidate(
						eventId = e.id,
						kind = e.kind,
						anchorY = y0,
						anchorY1 = null,
						title = e.title,
						sub = e.subtitle,
						style = LedgerStyle.LaneCallout,
						mark = LedgerMark.LaneSwatch,
						laneIndex = lane.index,
						pinned = false,
						order = e.order,
						source = PointDp(markX, markY),
					)

					else -> {
						val textY = maxOf(y0 + if (isSpan) LABEL_GAP else LABEL_NUDGE, lastBottom + LABEL_NUDGE)
						val available = laneRight - labelX - LABEL_NUDGE
						val lines = LabelFitter.wrapToWidth(e.title, available, LabelStyle.BarLabel, measurer, 2)
						val sub = e.subtitle?.let { LabelFitter.ellipsize(it, available, LabelStyle.LedgerSub, measurer) }
						lastBottom = textY + lines.size * labelLineHeight - 2f + if (sub != null) labelLineHeight else 0f
						leader = LeaderLabel(
							lines = lines,
							sub = sub,
							xDp = labelX,
							firstBaselineDp = textY,
							leader = listOf(
								PointDp(markX + 2f, markY),
								PointDp(labelX - 8f, markY),
								PointDp(labelX - 8f, textY - 4f),
								PointDp(labelX - 3f, textY - 4f),
							),
							fullText = fullText(e.title, e.subtitle),
						)
					}
				}
			}

			if (rect != null) {
				val target = HitTarget.Band(e.id)
				if (columns[i] == 0) {
					val label = if (leader != null) BandLabel.Leader(leader) else bandLabel
					bands += BandShape(e.id, lane.index, rect, bandCount++ % 3, label, p.circa)
					hits += HitRegion(rect, target)
				} else {
					bars += BarShape(e.id, lane.index, rect, columns[i], leader, p.circa)
					val widen = maxOf(0f, m.hitSize - rect.width) / 2f
					hits += HitRegion(RectDp(rect.x - widen, rect.y, rect.width + 2 * widen, rect.height), target)
				}
				leader?.let { hits += HitRegion(labelRect(it, lastBottom, laneRight, labelLineHeight), target) }
			} else {
				val glyph = when {
					e.kind == LayoutKind.ENTRY -> PointGlyph.EntryBox
					e.milestone -> PointGlyph.MilestoneDiamond
					else -> PointGlyph.Diamond
				}
				lanePoints += PointMark(e.id, lane.index, pointX, y0, glyph, leader)
				val half = m.hitSize / 2f
				pointHits += HitRegion(RectDp(pointX - half, y0 - half, m.hitSize, m.hitSize), HitTarget.Point(e.id))
				leader?.let { hits += HitRegion(labelRect(it, lastBottom, laneRight, labelLineHeight), HitTarget.Point(e.id)) }
			}
		}
		points += lanePoints
		hits += pointHits
		return lastBottom
	}

	private fun labelRect(label: LeaderLabel, bottom: Float, laneRight: Float, lineHeight: Float): RectDp {
		val top = label.firstBaselineDp - lineHeight
		return RectDp(label.xDp, top, maxOf(0f, laneRight - label.xDp), bottom - top)
	}

	private fun fullText(title: String, sub: String?): String = if (sub.isNullOrBlank()) title else "$title · $sub"

	private fun layoutLedger(
		candidates: List<LedgerCandidate>,
		width: Float,
		ledgerX: Float,
		ledgerTextX: Float,
		measurer: TimelineTextMeasurer,
		metrics: LayoutMetrics,
	): List<LedgerRow> {
		val textWidth = maxOf(0f, width - metrics.padRight - ledgerTextX)
		val placed = LedgerPlacer.place(candidates, textWidth, measurer, metrics)
		val markX = ledgerX + LEDGER_MARK_INSET
		return placed.mapIndexed { i, row ->
			val c = row.candidate
			val elbowY = row.textY - 4f
			val leader = when {
				c.source != null -> {
					val vx = ledgerTextX - 10f - (i % 4) * 3f
					listOf(
						c.source,
						PointDp(vx, c.source.y),
						PointDp(vx, elbowY),
						PointDp(ledgerTextX - 4f, elbowY),
					)
				}

				abs(elbowY - c.anchorY) > metrics.leaderThreshold -> listOf(
					PointDp(markX + 7f, c.anchorY),
					PointDp(ledgerTextX - 9f, c.anchorY),
					PointDp(ledgerTextX - 9f, elbowY),
					PointDp(ledgerTextX - 4f, elbowY),
				)

				else -> null
			}
			LedgerRow(
				eventId = c.eventId,
				kind = c.kind,
				anchorYDp = c.anchorY,
				anchorY1Dp = c.anchorY1,
				textYDp = row.textY,
				lines = row.lines,
				sub = row.sub,
				style = c.style,
				mark = c.mark,
				laneIndex = c.laneIndex,
				leader = leader,
				hitRect = RectDp(ledgerX - HIT_LEFT_PAD, row.top, width - ledgerX + HIT_LEFT_PAD, row.height),
				fullText = fullText(c.title, c.sub),
			)
		}
	}

	/**
	 * The coarsest unit whose size divides the gap between the two closest of the ascending
	 * [positions], so consecutive rows read differently; the finest unit when none does.
	 */
	private fun sequenceTickUnit(positions: List<Long>, units: List<TickUnit>): TickUnit? {
		if (units.isEmpty()) return null
		val gap = positions.zipWithNext { a, b -> b - a }.minOrNull() ?: return units.last()
		return units.firstOrNull { gap % it.sizeInBase == 0L } ?: units.last()
	}

	private fun axisTicks(
		input: TimelineLayoutInput,
		placed: List<Placed>,
		scale: PiecewiseScale,
		lo: Long,
		hi: Long,
	): List<AxisTick> {
		val scheme = input.ticks
		val short = input.phone
		return when (input.view) {
			TimelineView.SEQUENCE -> {
				val positions = placed.map { it.start }.distinct()
				val unit = sequenceTickUnit(positions, scheme.units) ?: return emptyList()
				val ticks = ArrayList<AxisTick>(positions.size)
				for (p in positions) {
					val label = scheme.format(p, unit, short)
					if (ticks.isEmpty() || ticks.last().label != label) ticks += AxisTick(scale.y(p), label, p)
				}
				ticks
			}

			TimelineView.TO_SCALE -> {
				// The step is chosen over the stretches drawn to scale, so a fold does not swamp it.
				// Ticks inside a fold are dropped, except at the frame's own ends, which keep their label.
				val segments = scale.segments
				val foldedEnds = listOfNotNull(
					segments.firstOrNull()?.takeIf { it.fold }?.a,
					segments.lastOrNull()?.takeIf { it.fold }?.b,
				)
				val step = deriveTickUnit(scale.unfoldedLength, scheme.units, input.metrics.targetTicks, foldedEnds.size)
					?: return emptyList()
				val positions = ArrayList<Long>()
				if (segments.isEmpty()) {
					positions += scheme.positionsIn(lo..hi, step.unit, step.step)
				} else {
					for (s in segments) {
						val inSegment = if (s.fold) {
							foldedEnds.filter { (it == s.a || it == s.b) && scheme.positionsIn(it..it, step.unit, step.step).isNotEmpty() }
						} else {
							scheme.positionsIn(s.a..s.b, step.unit, step.step)
						}
						for (p in inSegment) {
							if (positions.isEmpty() || p > positions.last()) positions += p
						}
					}
				}
				positions.map { p -> AxisTick(scale.y(p), scheme.format(p, step.unit, short), p) }
			}
		}
	}
}
