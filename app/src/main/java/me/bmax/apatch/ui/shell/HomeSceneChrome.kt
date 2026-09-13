package me.bmax.apatch.ui.shell

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import me.bmax.apatch.R
import me.bmax.apatch.ui.home.HomeWallpaperImage
import me.bmax.apatch.ui.home.HomeWallpaperState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Lets the home screen hand the scene rail a way to open the wallpaper sheet without moving the
 * sheet itself out of the destination that owns it.
 */
internal class HomeSceneHostState {
    var openAppearance: (() -> Unit)? = null
}

internal val LocalHomeSceneHostState = staticCompositionLocalOf<HomeSceneHostState?> { null }

/** The rail clock and battery repeat the status bar, so the appearance sheet can drop them. */
internal const val SceneRailClockFlag = "scene_rail_clock"

/** Labels help on a photo, but they also crowd it; the appearance sheet can drop them. */
internal const val SceneRailLabelsFlag = "scene_rail_labels"

/** Which shape the scene clock takes; [SceneClockStyle] holds the values. */
internal const val SceneClockStyleFlag = "scene_clock_style"

/**
 * The scene clock has a few shapes, and the rail is narrow enough that they are genuinely
 * different designs rather than one design with options. The stored string is read through
 * [fromValue], so a value written by a newer version falls back instead of breaking the rail.
 */
internal enum class SceneClockStyle(
    val value: String,
    @param:StringRes val label: Int,
    @param:StringRes val summary: Int,
) {
    /** Hours over minutes, the shape the rail has always drawn. */
    Stacked(
        "stacked",
        R.string.home_scene_clock_style_stacked,
        R.string.home_scene_clock_style_stacked_summary,
    ),

    /** One line of time with the date under it. */
    Inline(
        "inline",
        R.string.home_scene_clock_style_inline,
        R.string.home_scene_clock_style_inline_summary,
    ),

    /** A watch face, with the date under it. */
    Analog(
        "analog",
        R.string.home_scene_clock_style_analog,
        R.string.home_scene_clock_style_analog_summary,
    ),

    /** The date alone, for someone who reads the time in the status bar. */
    DateOnly(
        "date",
        R.string.home_scene_clock_style_date,
        R.string.home_scene_clock_style_date_summary,
    );

    companion object {
        val Default = Stacked

        fun fromValue(value: String?): SceneClockStyle =
            entries.firstOrNull { it.value == value } ?: Default
    }
}

private val ClockFormatterHour = DateTimeFormatter.ofPattern("HH")
private val ClockFormatterHour12 = DateTimeFormatter.ofPattern("h")
private val ClockFormatterMinute = DateTimeFormatter.ofPattern("mm")
private val ClockFormatterTime = DateTimeFormatter.ofPattern("HH:mm")
private val ClockFormatterTime12 = DateTimeFormatter.ofPattern("h:mm")

/** The hour on its own, in the shape [is24Hour] asks for. */
internal fun sceneHourText(time: LocalTime, is24Hour: Boolean): String =
    time.format(if (is24Hour) ClockFormatterHour else ClockFormatterHour12)

/** The whole time, in the shape [is24Hour] asks for. */
internal fun sceneTimeText(time: LocalTime, is24Hour: Boolean): String =
    time.format(if (is24Hour) ClockFormatterTime else ClockFormatterTime12)

/** The half of the day, or null while a 24-hour clock says it with the number alone. */
internal fun sceneMeridiemText(time: LocalTime, is24Hour: Boolean, locale: Locale): String? =
    if (is24Hour) null else time.format(DateTimeFormatter.ofPattern("a", locale))

/**
 * Whether the phone reads a 12-hour clock. It is a setting someone can flip with the scene already
 * on screen, so it is watched instead of read once at composition.
 */
@Composable
private fun rememberSystem24Hour(): State<Boolean> {
    val context = LocalContext.current
    return produceState(initialValue = DateFormat.is24HourFormat(context), key1 = context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = DateFormat.is24HourFormat(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.TIME_12_24),
            false,
            observer,
        )
        awaitDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
}

@Composable
internal fun HomeSceneBackdrop(
    state: HomeWallpaperState?,
    railWidth: Dp,
    windowWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().clipToBounds()) {
        // Fallback scene when no image is chosen yet: a quiet theme gradient keeps the layout
        // readable instead of showing an empty void.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MiuixTheme.colorScheme.primaryContainer,
                            MiuixTheme.colorScheme.secondaryContainer,
                            MiuixTheme.colorScheme.background,
                        )
                    )
                )
        )
        if (state != null) {
            HomeWallpaperImage(
                state = state,
                modifier = Modifier.fillMaxSize().blur(32.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f)))
        }
        // The rail draws white on whatever the wallpaper happens to be. Darken just the strip it
        // sits in and let the shade dissolve towards the page, so a bright photo cannot swallow
        // the clock and the icons, and the image stays untouched everywhere else.
        Box(
            Modifier
                .fillMaxHeight()
                .width(railWidth + 20.dp)
                .background(
                    Brush.horizontalGradient(
                        // Hold the shade across the rail and dissolve it under the page card, so
                        // the strip reads as one quiet plate instead of a soft left vignette.
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.42f),
                            0.72f to Color.Black.copy(alpha = 0.32f),
                            1f to Color.Transparent,
                        ),
                    )
                )
        )
    }
}

@Composable
internal fun HomeSceneRail(
    destinations: List<PrimaryDestination>,
    currentDestination: PrimaryDestination?,
    onSelectDestination: (PrimaryDestination) -> Unit,
    onAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val showSceneClock by rememberVisualFlag(SceneRailClockFlag, true)
        val clockStyleValue by rememberVisualChoice(
            SceneClockStyleFlag,
            SceneClockStyle.Default.value,
        )
        Spacer(Modifier.height(28.dp))
        if (showSceneClock) {
            SceneClock(SceneClockStyle.fromValue(clockStyleValue))
            Spacer(Modifier.height(24.dp))
            SceneBattery()
        }
        Spacer(Modifier.weight(1f))
        val showLabels by rememberVisualFlag(SceneRailLabelsFlag, true)
        destinations
            .filter { it != PrimaryDestination.Home }
            .forEach { destination ->
                val selected = currentDestination == destination
                SceneRailItem(
                    selected = selected,
                    icon = destination.icon,
                    label = stringResource(destination.label),
                    showLabel = showLabels,
                    onClick = { onSelectDestination(destination) },
                )
            }
        Spacer(Modifier.height(12.dp))
        SceneRailItem(
            selected = false,
            icon = MiuixIcons.Photos,
            label = stringResource(me.bmax.apatch.R.string.home_appearance),
            showLabel = showLabels,
            onClick = onAppearance,
        )
        Spacer(
            Modifier
                .height(20.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        )
    }
}

@Composable
private fun SceneRailItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (selected) 1f else 0.74f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (selected) SceneOnWallpaper.copy(alpha = 0.22f) else Color.Transparent
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (showLabel) null else label,
                modifier = Modifier.size(23.dp),
                tint = SceneOnWallpaper.copy(alpha = alpha),
            )
        }
        // Labels matter more here than in a themed bar: the icons sit on a photo, and
        // "kernel patch" and "system patch" are not self-explaining shapes. They stay optional
        // because a photo someone likes is worth leaving alone.
        if (showLabel) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = SceneOnWallpaper.copy(alpha = alpha * 0.88f),
                    shadow = SceneTextShadow,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SceneClock(style: SceneClockStyle) {
    // The date is part of three of the four styles, so the tick keeps a date as well as a time.
    val now by produceState(initialValue = LocalDateTime.now(), key1 = Unit) {
        while (true) {
            value = LocalDateTime.now()
            delay(20_000)
        }
    }
    // Read from the configuration rather than the process default, so a language change is a
    // recomposition instead of something the clock only notices the next time it ticks.
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour by rememberSystem24Hour()
    val weekday = remember(now.dayOfWeek, locale) {
        now.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale)
    }
    val date = remember(now.toLocalDate(), locale) { sceneDate(now, locale) }
    val hour = remember(now, is24Hour) { sceneHourText(now.toLocalTime(), is24Hour) }
    val time = remember(now, is24Hour) { sceneTimeText(now.toLocalTime(), is24Hour) }
    val meridiem = remember(now, is24Hour, locale) {
        sceneMeridiemText(now.toLocalTime(), is24Hour, locale)
    }
    // A 12-hour clock has a second thing to say about the time, and the rail is narrow enough that
    // it cannot go on the same line as the numbers; it joins the date instead, which is the line
    // three of the four styles draw underneath.
    val dateLine = if (meridiem == null) {
        date
    } else {
        stringResource(R.string.home_scene_clock_meridiem_date, meridiem, date)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (style) {
            SceneClockStyle.Stacked -> {
                // This shape has no date line to hide the marker in, so it takes a line of its own
                // above the numbers, where it reads as the start of the time.
                if (meridiem != null) {
                    SceneClockLine(meridiem, 11.sp, alpha = 0.82f)
                    Spacer(Modifier.height(4.dp))
                }
                SceneClockLine(hour, 30.sp)
                Box(
                    Modifier
                        .padding(vertical = 6.dp)
                        .width(16.dp)
                        .height(1.dp)
                        .background(SceneOnWallpaper.copy(alpha = 0.42f))
                )
                SceneClockLine(now.format(ClockFormatterMinute), 30.sp)
            }

            SceneClockStyle.Inline -> {
                // 22sp keeps "HH:mm" inside the narrowest rail the shell can ask for.
                SceneClockLine(time, 22.sp)
                Spacer(Modifier.height(4.dp))
                SceneClockLine(dateLine, 11.sp, alpha = 0.82f)
            }

            SceneClockStyle.Analog -> {
                SceneClockDial(now.toLocalTime())
                Spacer(Modifier.height(6.dp))
                SceneClockLine(dateLine, 11.sp, alpha = 0.82f)
            }

            SceneClockStyle.DateOnly -> {
                // Nothing here says a time, so nothing here marks the half of the day either.
                SceneClockLine(weekday, 20.sp)
                Spacer(Modifier.height(2.dp))
                SceneClockLine(date, 11.sp, alpha = 0.82f)
            }
        }
    }
}

/**
 * One line of the clock. It fills the rail so that a long date is shortened instead of drawn past
 * the rail and over the page behind it, and centres itself because of that width.
 */
@Composable
private fun SceneClockLine(text: String, fontSize: TextUnit, alpha: Float = 0.96f) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = fontSize,
            fontWeight = FontWeight.Light,
            color = SceneOnWallpaper.copy(alpha = alpha),
            shadow = SceneTextShadow,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A watch face: a faint ring, four marks at the quarters and two hands. The ring and the marks are
 * kept quiet so that the hands, which are what a dial is actually read from, stay the only solid
 * thing about it.
 */
@Composable
private fun SceneClockDial(time: LocalTime) {
    Canvas(Modifier.size(46.dp)) {
        val ring = 1.4.dp.toPx()
        val radius = size.minDimension / 2f - ring
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = SceneOnWallpaper.copy(alpha = 0.5f),
            radius = radius,
            center = center,
            style = Stroke(width = ring),
        )
        repeat(4) { index ->
            val angle = Math.toRadians((index * 90f - 90f).toDouble())
            drawCircle(
                color = SceneOnWallpaper.copy(alpha = 0.55f),
                radius = 1.dp.toPx(),
                center = center + Offset(
                    (cos(angle) * (radius - 5.dp.toPx())).toFloat(),
                    (sin(angle) * (radius - 5.dp.toPx())).toFloat(),
                ),
            )
        }

        // The hour hand creeps with the minutes, so the face never looks stopped.
        val minuteAngle = Math.toRadians((time.minute * 6f - 90f).toDouble())
        val hourAngle = Math.toRadians(
            ((time.hour % 12) * 30f + time.minute * 0.5f - 90f).toDouble()
        )
        drawLine(
            color = SceneOnWallpaper.copy(alpha = 0.94f),
            start = center,
            end = center + Offset(
                (cos(hourAngle) * radius * 0.5f).toFloat(),
                (sin(hourAngle) * radius * 0.5f).toFloat(),
            ),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = SceneOnWallpaper.copy(alpha = 0.94f),
            start = center,
            end = center + Offset(
                (cos(minuteAngle) * radius * 0.78f).toFloat(),
                (sin(minuteAngle) * radius * 0.78f).toFloat(),
            ),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * The month and day, written the way the phone's own locale writes it: the platform is asked for
 * the shortest month-and-day pattern it uses, with a plain month and day as the fallback for the
 * rare locale it has no pattern for.
 */
private fun sceneDate(now: LocalDateTime, locale: Locale): String {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd") ?: "MMM d"
    return now.format(DateTimeFormatter.ofPattern(pattern, locale))
}

@Composable
private fun SceneBattery() {
    val context = LocalContext.current
    var percent by remember { mutableIntStateOf(readBatteryPercent(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            percent = readBatteryPercent(context)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.width(28.dp).height(14.dp)) {
            val knobWidth = size.width * 0.11f
            val gap = 1.5.dp.toPx()
            val bodyWidth = size.width - knobWidth - gap
            val strokeWidth = 1.5.dp.toPx()
            val radius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
            drawRoundRect(
                color = SceneOnWallpaper.copy(alpha = 0.88f),
                topLeft = Offset.Zero,
                size = Size(bodyWidth, size.height),
                cornerRadius = radius,
                style = Stroke(width = strokeWidth),
            )
            drawRoundRect(
                color = SceneOnWallpaper.copy(alpha = 0.88f),
                topLeft = Offset(bodyWidth + gap, size.height * 0.31f),
                size = Size(knobWidth, size.height * 0.38f),
                cornerRadius = CornerRadius(knobWidth * 0.5f, knobWidth * 0.5f),
            )
            val inset = strokeWidth * 1.9f
            val fillWidth = (bodyWidth - inset * 2f) * (percent / 100f)
            if (fillWidth > 0f) {
                drawRoundRect(
                    color = SceneOnWallpaper.copy(alpha = 0.92f),
                    topLeft = Offset(inset, inset),
                    size = Size(fillWidth, size.height - inset * 2f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = "$percent%",
            style = TextStyle(
                fontSize = 12.sp,
                color = SceneOnWallpaper.copy(alpha = 0.82f),
                shadow = SceneTextShadow,
            ),
        )
    }
}

private fun readBatteryPercent(context: Context): Int {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return 0
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) {
        return 0
    }
    return ((level * 100f) / scale).toInt().coerceIn(0, 100)
}

private val SceneOnWallpaper = Color.White

private val SceneTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.42f),
    offset = Offset(0f, 1f),
    blurRadius = 6f,
)
