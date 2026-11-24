package com.space.app.ui.screens.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.space.app.R
import com.space.app.domain.layout.ChartLayout
import com.space.app.domain.layout.LayeredLayout
import com.space.app.domain.layout.PositionedNode
import com.space.app.domain.model.NodeKind
import com.space.app.domain.model.Plan
import com.space.app.domain.model.PlanNode
import com.space.app.ui.theme.KindDecision
import com.space.app.ui.theme.KindDecisionDark
import com.space.app.ui.theme.KindGoal
import com.space.app.ui.theme.KindGoalDark
import com.space.app.ui.theme.KindRisk
import com.space.app.ui.theme.KindRiskDark
import com.space.app.ui.theme.KindStep
import com.space.app.ui.theme.KindStepDark
import com.space.app.ui.rememberReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min

private const val REVEAL_BASE_MS = 180
private const val REVEAL_PER_RANK_MS = 70
private const val EDGE_DRAW_MS = 420
private const val NODE_REVEAL_MS = 420
private const val FIT_MS = 380

// A 16-node plan sitting in one rank is roughly 3100dp wide, which fits a
// 320dp viewport at ~0.09. The floor must sit below that or the first pinch
// would snap the chart larger instead of zooming out smoothly.
private const val MIN_SCALE = 0.08f
private const val MAX_SCALE = 3.0f

private fun kindColor(kind: NodeKind, dark: Boolean): Color = when (kind) {
    NodeKind.GOAL -> if (dark) KindGoalDark else KindGoal
    NodeKind.STEP -> if (dark) KindStepDark else KindStep
    NodeKind.DECISION -> if (dark) KindDecisionDark else KindDecision
    NodeKind.RISK -> if (dark) KindRiskDark else KindRisk
}

@Composable
private fun kindShape(kind: NodeKind) = when (kind) {
    NodeKind.GOAL -> MaterialTheme.shapes.large
    NodeKind.STEP -> MaterialTheme.shapes.medium
    NodeKind.DECISION -> MaterialTheme.shapes.small
    NodeKind.RISK -> MaterialTheme.shapes.extraSmall
}

@Composable
private fun kindLabel(kind: NodeKind): String = stringResource(
    when (kind) {
        NodeKind.GOAL -> R.string.kind_goal
        NodeKind.STEP -> R.string.kind_step
        NodeKind.DECISION -> R.string.kind_decision
        NodeKind.RISK -> R.string.kind_risk
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    plan: Plan,
    onBack: () -> Unit
) {
    val layout = remember(plan) { LayeredLayout.layout(plan) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    val dark = isSystemInDarkTheme()
    val reduceMotion = rememberReduceMotion()

    // Saved so the transform and the open sheet survive rotation and process death.
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var hasFitted by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var transformJob by remember { mutableStateOf<Job?>(null) }

    val offset = Offset(offsetX, offsetY)
    fun setOffset(value: Offset) {
        offsetX = value.x
        offsetY = value.y
    }

    val contentW = with(density) { layout.width.dp.toPx() }
    val contentH = with(density) { layout.height.dp.toPx() }
    val drawable = layout.positions.isNotEmpty()

    fun fittedScale(): Float {
        if (viewport == IntSize.Zero || contentW <= 0f || contentH <= 0f) return 1f
        // Clamp *after* the breathing room, otherwise a very wide plan fits
        // below MIN_SCALE and the next pinch snaps it larger.
        return (min(viewport.width / contentW, viewport.height / contentH) * 0.9f)
            .coerceIn(MIN_SCALE, MAX_SCALE)
    }

    fun fittedOffset(s: Float) = Offset(
        (viewport.width - contentW * s) / 2f,
        (viewport.height - contentH * s) / 2f
    )

    // Keep the chart's centre inside the viewport so panning can never lose it.
    fun clampOffset(candidate: Offset, s: Float): Offset {
        if (viewport == IntSize.Zero || !drawable) return candidate
        // The previous implementation clamped the content's *centre* to [0, viewport],
        // which let the left/top edge fall inside the screen and silently hid the
        // right/bottom content when the chart was larger than the viewport. Clamp the
        // content's top-left edge instead: it must stay within
        // [viewport.width - contentW*s, 0] (and similarly for height), so the
        // content is never clipped at either edge.
        val minX = kotlin.math.min(0f, viewport.width - contentW * s)
        val maxX = kotlin.math.max(0f, viewport.width - contentW * s)
        val minY = kotlin.math.min(0f, viewport.height - contentH * s)
        val maxY = kotlin.math.max(0f, viewport.height - contentH * s)
        return Offset(
            candidate.x.coerceIn(minX, maxX),
            candidate.y.coerceIn(minY, maxY)
        )
    }

    /** Zooms about a fixed screen point so the chart grows under the fingers. */
    fun zoomAbout(pivot: Offset, factor: Float, pan: Offset = Offset.Zero) {
        val next = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val anchored = pivot + pan - (pivot - offset) * (next / scale)
        scale = next
        setOffset(clampOffset(anchored, next))
    }

    // Any gesture beats an in-flight programmatic transform.
    fun cancelTransform() {
        transformJob?.cancel()
        transformJob = null
    }

    fun animateToFit() {
        if (!drawable || viewport == IntSize.Zero) return
        val targetScale = fittedScale()
        val targetOffset = fittedOffset(targetScale)
        val startScale = scale
        val startOffset = offset
        transformJob = scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(FIT_MS, easing = FastOutSlowInEasing)
            ) { t, _ ->
                scale = startScale + (targetScale - startScale) * t
                setOffset(
                    Offset(
                        startOffset.x + (targetOffset.x - startOffset.x) * t,
                        startOffset.y + (targetOffset.y - startOffset.y) * t
                    )
                )
            }
        }
    }

    // Settle into the fitted view once, from slightly zoomed out.
    LaunchedEffect(viewport, drawable) {
        if (hasFitted || !drawable || viewport == IntSize.Zero) return@LaunchedEffect
        hasFitted = true
        val targetScale = fittedScale()
        setOffset(fittedOffset(targetScale))
        // Under reduced motion, drop straight to the fitted scale.
        if (reduceMotion) {
            scale = targetScale
            return@LaunchedEffect
        }
        val startScale = targetScale * 0.9f
        scale = startScale
        transformJob = scope.launch {
            animate(
                initialValue = startScale,
                targetValue = targetScale,
                animationSpec = tween(460, easing = FastOutSlowInEasing)
            ) { value, _ ->
                scale = value
            }
        }
    }

    // One Animatable per edge. The delay lives inside the spec, not in a raw
    // coroutine delay, so it collapses when the platform disables animations.
    val edgeFractions = remember(layout) { layout.edges.map { Animatable(0f) } }
    layout.edges.forEachIndexed { i, edge ->
        val startDelay = REVEAL_BASE_MS +
            (layout.positions[edge.from]?.rank ?: 0) * REVEAL_PER_RANK_MS + 170
        LaunchedEffect(edge) {
            edgeFractions[i].animateTo(
                targetValue = 1f,
                animationSpec = if (reduceMotion) {
                    tween(durationMillis = 0)
                } else {
                    tween(
                        durationMillis = EDGE_DRAW_MS,
                        delayMillis = startDelay,
                        easing = FastOutSlowInEasing
                    )
                }
            )
        }
    }

    val orderedNodes = remember(layout) {
        layout.positions.values.sortedWith(compareBy({ it.rank }, { it.orderInRank }))
    }

    val chartDescription = pluralStringResource(R.plurals.chart_steps, orderedNodes.size)
    val zoomInLabel = stringResource(R.string.chart_zoom_in)
    val zoomOutLabel = stringResource(R.string.chart_zoom_out)
    val recenterLabel = stringResource(R.string.chart_recenter)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .onSizeChanged { viewport = it }
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, _ ->
                    cancelTransform()
                    zoomAbout(pivot = centroid, factor = zoom, pan = pan)
                }
            }
            .semantics {
                contentDescription = chartDescription
                customActions = listOf(
                    CustomAccessibilityAction(zoomInLabel) {
                        cancelTransform()
                        zoomAbout(Offset(viewport.width / 2f, viewport.height / 2f), 1.3f)
                        true
                    },
                    CustomAccessibilityAction(zoomOutLabel) {
                        cancelTransform()
                        zoomAbout(Offset(viewport.width / 2f, viewport.height / 2f), 0.77f)
                        true
                    },
                    CustomAccessibilityAction(recenterLabel) {
                        animateToFit()
                        true
                    }
                )
            }
    ) {
        if (!drawable) {
            Text(
                text = stringResource(R.string.chart_empty),
                style = typography.bodyLarge,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
            ) {
                EdgeLayer(
                    layout = layout,
                    fractions = edgeFractions,
                    color = colorScheme.outline,
                    labelColor = colorScheme.onSurfaceVariant,
                    labelBackground = colorScheme.background,
                    measure = textMeasurer,
                    modifier = Modifier.size(layout.width.dp, layout.height.dp)
                )

                orderedNodes.forEachIndexed { index, entry ->
                    key(entry.node.id) {
                        NodeView(
                            entry = entry,
                            position = index + 1,
                            total = orderedNodes.size,
                            selected = selectedId == entry.node.id,
                            appearDelayMs = REVEAL_BASE_MS + entry.rank * REVEAL_PER_RANK_MS,
                            onTap = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedId = entry.node.id
                            }
                        )
                    }
                }
            }
        }

        // Overlay controls: back, plan title, fit to screen.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(8.dp)
        ) {
            ChartControl(
                modifier = Modifier.align(Alignment.TopStart),
                onClick = onBack
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = colorScheme.onSurface
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // Inset so the pill can never slide under the two controls.
                    .widthIn(max = 220.dp),
                shape = CircleShape,
                color = colorScheme.surface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, colorScheme.outline)
            ) {
                Text(
                    text = plan.title,
                    style = typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }

            ChartControl(
                modifier = Modifier.align(Alignment.TopEnd),
                onClick = { animateToFit() }
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = recenterLabel,
                    tint = colorScheme.onSurface
                )
            }
        }
    }

    selectedId?.let { id ->
        layout.positions[id]?.node?.let { node ->
            ModalBottomSheet(
                onDismissRequest = { selectedId = null },
                containerColor = colorScheme.surface,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                NodeDetailContent(node = node, tint = kindColor(node.kind, dark))
            }
        }
    }
}

@Composable
private fun ChartControl(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, colorScheme.outline)
    ) {
        IconButton(onClick = onClick, content = content)
    }
}

/**
 * Edges are drawn from each source node's bottom edge to the target's top edge.
 * Every allocation here is hoisted out of the loop and reset per edge, and the
 * per-edge fractions are read inside the draw scope so an animating edge
 * invalidates the canvas without recomposing the chart.
 */
@Composable
private fun EdgeLayer(
    layout: ChartLayout,
    fractions: List<Animatable<Float, *>>,
    color: Color,
    labelColor: Color,
    labelBackground: Color,
    measure: TextMeasurer,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val strokeW = 1.5.dp.toPx()
        val labelPad = 3.dp.toPx()
        val labelCorner = CornerRadius(4.dp.toPx())
        val labelStyle = TextStyle(fontSize = 10.sp)

        val pathMeasure = PathMeasure()
        val curve = Path()
        val segment = Path()

        layout.edges.forEachIndexed { i, edge ->
            val fraction = fractions.getOrNull(i)?.value ?: 0f
            if (fraction <= 0f) return@forEachIndexed

            val from = layout.positions[edge.from] ?: return@forEachIndexed
            val to = layout.positions[edge.to] ?: return@forEachIndexed

            val start = Offset(
                (from.x + LayeredLayout.NODE_WIDTH / 2f).dp.toPx(),
                (from.y + LayeredLayout.NODE_HEIGHT).dp.toPx()
            )
            val end = Offset(
                (to.x + LayeredLayout.NODE_WIDTH / 2f).dp.toPx(),
                to.y.dp.toPx()
            )
            val midY = (start.y + end.y) / 2f

            curve.reset()
            curve.moveTo(start.x, start.y)
            curve.cubicTo(start.x, midY, end.x, midY, end.x, end.y)

            segment.reset()
            pathMeasure.setPath(curve, false)
            pathMeasure.getSegment(0f, pathMeasure.length * fraction, segment, true)

            drawPath(
                path = segment,
                color = color,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )

            val label = edge.label
            if (!label.isNullOrBlank() && fraction > 0.6f) {
                val alpha = ((fraction - 0.6f) / 0.4f).coerceIn(0f, 1f)
                val measured = measure.measure(
                    text = AnnotatedString(label),
                    style = labelStyle
                )
                val boxWidth = measured.size.width + labelPad * 2
                val boxHeight = measured.size.height + labelPad * 2
                val topLeft = Offset(
                    x = (start.x + end.x) / 2f - boxWidth / 2f,
                    y = midY - boxHeight / 2f
                )

                drawRoundRect(
                    color = labelBackground.copy(alpha = 0.92f * alpha),
                    topLeft = topLeft,
                    size = Size(boxWidth, boxHeight),
                    cornerRadius = labelCorner
                )
                drawText(
                    textLayoutResult = measured,
                    color = labelColor.copy(alpha = alpha),
                    topLeft = Offset(topLeft.x + labelPad, topLeft.y + labelPad)
                )
            }
        }
    }
}

@Composable
private fun NodeView(
    entry: PositionedNode,
    position: Int,
    total: Int,
    selected: Boolean,
    appearDelayMs: Int,
    onTap: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(entry.node.id) {
        // Delay inside the spec keeps this honouring the platform's
        // "remove animations" setting instead of always waiting.
        progress.animateTo(
            targetValue = 1f,
            animationSpec = if (reduceMotion) {
                tween(durationMillis = 0)
            } else {
                tween(
                    durationMillis = NODE_REVEAL_MS,
                    delayMillis = appearDelayMs,
                    easing = FastOutSlowInEasing
                )
            }
        )
    }

    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "node-lift"
    )

    val kind = entry.node.kind
    val tint = kindColor(kind, isSystemInDarkTheme())
    val reveal = progress.value
    val kindText = kindLabel(kind)
    val positionText = stringResource(R.string.chart_node_position, position, total)
    val openLabel = stringResource(R.string.chart_open_details)

    Surface(
        shape = kindShape(kind),
        color = colorScheme.surface,
        border = BorderStroke(
            width = 1.dp + 0.5.dp * lift,
            color = tint.copy(alpha = 0.35f + 0.3f * reveal + 0.35f * lift)
        ),
        shadowElevation = (1f + 3f * lift).dp,
        modifier = Modifier
            .offset { IntOffset(entry.x.dp.roundToPx(), entry.y.dp.roundToPx()) }
            .size(LayeredLayout.NODE_WIDTH.dp, LayeredLayout.NODE_HEIGHT.dp)
            .graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * 18.dp.toPx()
                val s = 0.96f + 0.04f * reveal + 0.03f * lift
                scaleX = s
                scaleY = s
            }
            .semantics {
                // Colour alone carries the kind, so state it for screen readers.
                contentDescription = "$positionText. ${entry.node.title}. " +
                    "$kindText. ${entry.node.summary}"
                role = Role.Button
            }
            .clickable(
                enabled = reveal > 0.5f,
                onClickLabel = openLabel,
                onClick = onTap
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(tint, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = entry.node.title,
                    style = typography.labelLarge,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entry.node.summary,
                    style = typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NodeDetailContent(node: PlanNode, tint: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Plan detail can run long, so the sheet has to scroll or the tail
            // of the text is unreachable.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(tint, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = kindLabel(node.kind),
                style = typography.labelSmall,
                color = tint
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = node.title,
            style = typography.titleLarge,
            color = colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = node.summary,
            style = typography.bodyLarge,
            color = colorScheme.onSurfaceVariant
        )
        if (node.detail.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = node.detail,
                style = typography.bodyLarge,
                color = colorScheme.onSurface
            )
        }
        if (node.subtasks.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.detail_subtasks),
                style = typography.labelLarge,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            node.subtasks.forEach { task ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(colorScheme.outline, CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = task,
                        style = typography.bodyLarge,
                        color = colorScheme.onSurface
                    )
                }
            }
        }
    }
}
