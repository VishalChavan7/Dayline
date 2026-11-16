package com.dayline.app.ui.components

import kotlinx.coroutines.launch
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.Add

import androidx.compose.material.icons.automirrored.rounded.ArrowBack

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dayline.app.ui.motion.Motion
import com.dayline.app.ui.motion.pressBounce
import com.dayline.app.ui.motion.rememberHaptics
import com.dayline.app.ui.theme.Dimens
import kotlin.math.roundToInt

/** Collapse behaviour shared by every screen header. */
@Composable
fun rememberBarScroll(): TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

/**
 * The one header used by every screen, so titles always sit in the same place:
 * a 64 dp toolbar row (back button + actions), then a large title area of fixed height.
 * When content scrolls, the large title slides away and a compact title fades into the toolbar
 * (iOS-style large title).
 */
@Composable
fun DaylineTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val density = LocalDensity.current
    val typo = MaterialTheme.typography
    val largeStyle = typo.displaySmall.copy(fontSize = 34.sp, lineHeight = 42.sp)
    val subStyle = typo.titleSmall
    // Fixed area: one line of title + one line of subtitle, whether or not a subtitle is shown.
    val areaDp: Dp = with(density) { largeStyle.lineHeight.toDp() + subStyle.lineHeight.toDp() } + 14.dp
    val areaPx = with(density) { areaDp.toPx() }
    val state = scrollBehavior?.state
    SideEffect { if (state != null && state.heightOffsetLimit != -areaPx) state.heightOffsetLimit = -areaPx }
    val fraction = { if (state == null) 0f else (state.heightOffset / -areaPx).coerceIn(0f, 1f) }

    val surface = MaterialTheme.colorScheme.surface
    val scrolled = MaterialTheme.colorScheme.surfaceContainer
    val overlap = state?.overlappedFraction ?: 0f
    val bg by animateColorAsState(if (overlap > 0.01f) scrolled else surface, Motion.effects(), label = "barBg")

    Box(
        modifier
            .fillMaxWidth()
            .background(bg)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = Dimens.maxContentWidth).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (navigationIcon != null) navigationIcon() else Spacer(Modifier.width(Dimens.md))
                Box(
                    Modifier
                        .weight(1f)
                        .padding(start = 4.dp, end = 4.dp)
                        .graphicsLayer {
                            val f = fraction()
                            alpha = ((f - 0.55f) / 0.45f).coerceIn(0f, 1f)
                            translationY = (1f - alpha) * 10.dp.toPx()
                        },
                ) {
                    ProvideTextStyle(typo.titleLarge) { SingleLine { title() } }
                }
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val off = state?.heightOffset ?: 0f
                        val h = (areaPx + off).roundToInt().coerceAtLeast(0)
                        val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = areaPx.roundToInt()))
                        layout(constraints.maxWidth, h) { p.place(0, 0) }
                    },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(areaDp)
                        .padding(horizontal = Dimens.lg + 4.dp)
                        .graphicsLayer {
                            val f = fraction()
                            alpha = (1f - f * 1.6f).coerceIn(0f, 1f)
                            translationY = -(state?.heightOffset ?: 0f) * -0.35f
                            val s = 1f - 0.06f * f
                            scaleX = s; scaleY = s
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        },
                ) {
                    Box(Modifier.semantics { heading() }) {
                        ProvideTextStyle(largeStyle.copy(color = MaterialTheme.colorScheme.onSurface)) { SingleLine { title() } }
                    }
                    if (subtitle != null) {
                        ProvideTextStyle(subStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) { subtitle() }
                    }
                }
            }
        }
    }
}

/** Keeps headers on a single line no matter what the caller's Text sets. */
@Composable
private fun SingleLine(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clipToBounds()) { content() }
}

/** Back arrow used in headers. */
@Composable
fun BackButton(onBack: () -> Unit) {
    LabeledIconButton(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, tint = MaterialTheme.colorScheme.onSurface)
}

/**
 * Segmented control with a thumb that glides between options (iOS-style), with an expressive squish.
 * Labels never wrap: every option gets an equal share of the width.
 */
@Composable
fun <T> SegmentedPills(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    selectedColor: (T) -> Color? = { null },
) {
    val haptics = rememberHaptics()
    val index = options.indexOf(selected).coerceAtLeast(0)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val thumbDefault = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * index, Motion.glide(), label = "thumbX")
        val thumbColor by animateColorAsState(selectedColor(selected) ?: thumbDefault, Motion.effects(), label = "thumbColor")
        // Squish while travelling.
        val stretch = remember { Animatable(1f) }
        LaunchedEffect(index) {
            stretch.snapTo(1.08f)
            stretch.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
        }
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer { scaleX = stretch.value; scaleY = 2f - stretch.value }
                .clip(CircleShape)
                .background(thumbColor),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, o ->
                val sel = i == index
                val fg by animateColorAsState(
                    if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    Motion.effects(), label = "segFg",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClickLabel = label(o),
                        ) { if (!sel) { haptics.light(); onSelect(o) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(o),
                        style = MaterialTheme.typography.labelLarge,
                        color = fg,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = if (LocalDensity.current.fontScale > 1.15f || segment < 64.dp) 12.sp else MaterialTheme.typography.labelLarge.fontSize,
                    )
                }
            }
        }
    }
}

data class NavItem(val key: String, val label: String, val selectedIcon: ImageVector, val icon: ImageVector)

/**
 * Bottom navigation with a pill that glides to the selected destination and an icon that
 * bounces when chosen (Material 3 Expressive motion with iOS-like smoothness).
 */
@Composable
fun DaylineNavBar(items: List<NavItem>, selectedKey: String?, onSelect: (NavItem) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val index = items.indexOfFirst { it.key == selectedKey }
    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(76.dp)) {
            val slot = maxWidth / items.size
            val pillW = 60.dp
            val target = slot * index.coerceAtLeast(0) + (slot - pillW) / 2
            val x by animateDpAsState(target, Motion.glide(), label = "navPill")
            val pillAlpha by animateFloatAsState(if (index >= 0) 1f else 0f, Motion.effects(), label = "navPillAlpha")
            Box(
                Modifier
                    .offset(x = x, y = 10.dp)
                    .size(pillW, 32.dp)
                    .graphicsLayer { alpha = pillAlpha }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            )
            Row(Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, item ->
                    val selected = i == index
                    val bounce = remember { Animatable(1f) }
                    LaunchedEffect(selected) {
                        if (selected) {
                            bounce.snapTo(0.72f)
                            bounce.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 420f))
                        }
                    }
                    val tint by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        Motion.effects(), label = "navTint",
                    )
                    val labelColor by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        Motion.effects(), label = "navLabel",
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .height(76.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onClickLabel = item.label,
                            ) {
                                haptics.light()
                                onSelect(item)
                            }
                            .pressBounce(0.9f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.padding(top = 10.dp).size(60.dp, 32.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                if (selected) item.selectedIcon else item.icon,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(24.dp).graphicsLayer { scaleX = bounce.value; scaleY = bounce.value },
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = labelColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** True while the user is scrolling down; screen-level buttons tuck away like the main add button. */
val LocalScrollingDown = androidx.compose.runtime.compositionLocalOf { false }

/**
 * The app's extended action button ("New Habit", "New Note", …): flat (no shadow), the same size and
 * colours everywhere, with an expressive press and a smooth slide away while scrolling down.
 */
@Composable
fun PillFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = androidx.compose.material.icons.Icons.Rounded.Add,
    onLongClick: (() -> Unit)? = null,
    /** Menu open: the button turns dark (primary) and its + turns into an ×, like the add-task button. */
    expanded: Boolean = false,
    forceVisible: Boolean = false,
    hidden: Boolean = false,
) {
    val isHidden = (LocalScrollingDown.current || hidden) && !forceVisible
    val haptics = rememberHaptics()
    val show by animateFloatAsState(
        if (isHidden) 0f else 1f,
        androidx.compose.animation.core.tween(650, easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "pillShow",
    )
    val dark by animateFloatAsState(if (expanded) 1f else 0f, androidx.compose.animation.core.tween(220), label = "pillDark")
    val rotation by animateFloatAsState(if (expanded) 135f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 380f), label = "pillRot")
    val hop = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val container = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, dark)
    val tint = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.onPrimary, dark)
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .graphicsLayer {
                alpha = show
                translationY = (1f - show) * 140.dp.toPx() + hop.value * 14.dp.toPx()
            }
            .pressBounce(0.94f)
            .height(56.dp)
            .clip(shape)
            .then(
                // Gradient themes: the soft gradient, fading to solid primary while the menu is open.
                if (com.dayline.app.ui.theme.LocalBrandGradient.current != null && dark < 0.5f) Modifier.background(com.dayline.app.ui.theme.brandContainerBrush())
                else Modifier.background(container),
            )
            .combinedClickable(
                enabled = !isHidden,
                role = androidx.compose.ui.semantics.Role.Button,
                onLongClickLabel = if (onLongClick != null) "More options" else null,
                onLongClick = onLongClick?.let { lc ->
                    {
                        haptics.heavy()
                        scope.launch {
                            hop.animateTo(-1f, androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 1400f))
                            hop.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.32f, stiffness = 420f))
                        }
                        lc()
                    }
                },
                onClick = { haptics.light(); onClick() },
            )
            .padding(start = 18.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation })
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = tint, maxLines = 1)
    }
}
