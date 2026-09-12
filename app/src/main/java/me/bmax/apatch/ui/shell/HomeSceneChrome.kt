package me.bmax.apatch.ui.shell

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
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

private val ClockFormatterHour = DateTimeFormatter.ofPattern("HH")
private val ClockFormatterMinute = DateTimeFormatter.ofPattern("mm")

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
        if (state == null) {
            return@Box
        }
        HomeWallpaperImage(
            state = state,
            modifier = Modifier.fillMaxSize().blur(32.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f)))
    }
}

@Composable
internal fun HomeSceneRail(
    navController: NavHostController,
    capabilities: AsterNavigationCapabilities,
    onAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = navController.rememberDestinationsNavigator()

    Column(
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        SceneClock()
        Spacer(Modifier.height(24.dp))
        SceneBattery()
        Spacer(Modifier.weight(1f))
        visiblePrimaryDestinations(capabilities)
            .filter { it != PrimaryDestination.Home }
            .forEach { destination ->
                val selected = navController.isCurrentPrimaryDestination(destination)
                SceneRailItem(
                    selected = selected,
                    icon = destination.icon,
                    label = stringResource(destination.label),
                    onClick = {
                        navigatePrimary(
                            navigator = navigator,
                            destination = destination,
                            isCurrentDestination = selected,
                        )
                    },
                )
            }
        Spacer(Modifier.height(12.dp))
        SceneRailItem(
            selected = false,
            icon = MiuixIcons.Photos,
            label = stringResource(me.bmax.apatch.R.string.home_appearance),
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
    label: String?,
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
                contentDescription = label,
                modifier = Modifier.size(23.dp),
                tint = SceneOnWallpaper.copy(alpha = alpha),
            )
        }

    }
}

@Composable
private fun SceneClock() {
    val now by produceState(initialValue = LocalTime.now(), key1 = Unit) {
        while (true) {
            value = LocalTime.now()
            delay(20_000)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = now.format(ClockFormatterHour),
            style = TextStyle(
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                color = SceneOnWallpaper.copy(alpha = 0.96f),
                shadow = SceneTextShadow,
            ),
        )
        Box(
            Modifier
                .padding(vertical = 6.dp)
                .width(16.dp)
                .height(1.dp)
                .background(SceneOnWallpaper.copy(alpha = 0.42f))
        )
        Text(
            text = now.format(ClockFormatterMinute),
            style = TextStyle(
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                color = SceneOnWallpaper.copy(alpha = 0.96f),
                shadow = SceneTextShadow,
            ),
        )
    }
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
