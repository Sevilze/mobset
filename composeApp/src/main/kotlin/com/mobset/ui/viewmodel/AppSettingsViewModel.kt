package com.mobset.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobset.data.auth.AuthRepository
import com.mobset.data.profile.ProfileRepository
import com.mobset.data.profile.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppSettingsViewModel
@Inject
constructor(
    private val auth: AuthRepository,
    private val profiles: ProfileRepository
) : ViewModel() {
    val currentUser = auth.currentUser.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val currentProfile: StateFlow<UserProfile?> =
        currentUser
            .filterNotNull()
            .let { flow ->
                flow.flatMapLatest { user -> profiles.observeProfile(user.uid) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Theme dynamic color enabled by default
    val dynamicColorEnabled: StateFlow<Boolean> =
        currentProfile
            .map { p ->
                p?.themeDynamic ?: true
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Seed/accent color: derived from template if present, else from custom hex
    private val templateSeedMap: Map<String, Color> =
        mapOf(
            "red" to Color(0xFFD32F2F),
            "green" to Color(0xFF388E3C),
            "blue" to Color(0xFF1976D2),
            "yellow" to Color(0xFFF9A825),
            "purple" to Color(0xFF7B1FA2),
            "orange" to Color(0xFFF57C00),
            "teal" to Color(0xFF00796B)
        )

    val seedColor: StateFlow<Color> =
        currentProfile
            .map { p ->
                val fromTemplate = p?.themeTemplate?.lowercase()?.let { templateSeedMap[it] }
                fromTemplate ?: p?.themeAccentHex?.let { hexToColorOrNull(it) }
                    ?: Color(0xFF6750A4)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Color(0xFF6750A4))

    // Card colors: default triad (red, green, orange)
    val cardColors: StateFlow<List<Color>> =
        currentProfile
            .map { p ->
                val c1 = p?.cardColorHex1?.let { hexToColorOrNull(it) }
                val c2 = p?.cardColorHex2?.let { hexToColorOrNull(it) }
                val c3 = p?.cardColorHex3?.let { hexToColorOrNull(it) }
                listOfNotNull(c1, c2, c3).ifEmpty {
                    listOf(Color(0xFFFF0101), Color(0xFF008002), Color(0xFFFB8C00))
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                listOf(Color(0xFFFF0101), Color(0xFF008002), Color(0xFFFB8C00))
            )

    fun setDynamicColor(enabled: Boolean) {
        val user = currentUser.value ?: return
        val base = currentProfile.value
        viewModelScope.launch {
            val existing = base ?: profiles.observeProfile(user.uid).firstOrNull()
            val profile =
                (
                    existing
                        ?: UserProfile(
                            uid = user.uid,
                            displayName = null,
                            email = null,
                            photoUrl = null
                        )
                    )
                    .copy(themeDynamic = enabled)
            profiles.upsertProfile(profile)
        }
    }

    fun setAccentTemplate(template: String?) {
        val user = currentUser.value ?: return
        val base = currentProfile.value
        viewModelScope.launch {
            val existing = base ?: profiles.observeProfile(user.uid).firstOrNull()
            val profile =
                (
                    existing
                        ?: UserProfile(
                            uid = user.uid,
                            displayName = null,
                            email = null,
                            photoUrl = null
                        )
                    )
                    .copy(themeTemplate = template, themeAccentHex = null, themeDynamic = false)
            profiles.upsertProfile(profile)
        }
    }

    fun setCustomAccentHex(hex: String?) {
        val user = currentUser.value ?: return
        val clean = hex?.takeIf { isValidHex(it) }
        val base = currentProfile.value
        viewModelScope.launch {
            val existing = base ?: profiles.observeProfile(user.uid).firstOrNull()
            val profile =
                (
                    existing
                        ?: UserProfile(
                            uid = user.uid,
                            displayName = null,
                            email = null,
                            photoUrl = null
                        )
                    )
                    .copy(themeAccentHex = clean, themeTemplate = null, themeDynamic = false)
            profiles.upsertProfile(profile)
        }
    }

    fun setCardColor(index: Int, hex: String) {
        if (!isValidHex(hex)) return
        val user = currentUser.value ?: return
        val base = currentProfile.value
        viewModelScope.launch {
            val existing = base ?: profiles.observeProfile(user.uid).firstOrNull()
            val e =
                existing
                    ?: UserProfile(
                        uid = user.uid,
                        displayName = null,
                        email = null,
                        photoUrl = null
                    )
            val updated =
                when (index) {
                    0 -> e.copy(cardColorHex1 = normalizeHex(hex))
                    1 -> e.copy(cardColorHex2 = normalizeHex(hex))
                    else -> e.copy(cardColorHex3 = normalizeHex(hex))
                }
            profiles.upsertProfile(updated)
        }
    }

    // Utilities
    private fun hexToColorOrNull(hex: String): Color? = try {
        val c = normalizeHex(hex).removePrefix("#")
        val r = c.substring(0, 2).toInt(16)
        val g = c.substring(2, 4).toInt(16)
        val b = c.substring(4, 6).toInt(16)
        Color(r / 255f, g / 255f, b / 255f)
    } catch (_: Throwable) {
        null
    }

    private fun colorToHex(color: Color): String {
        val r = (color.red * 255).roundToInt().coerceIn(0, 255)
        val g = (color.green * 255).roundToInt().coerceIn(0, 255)
        val b = (color.blue * 255).roundToInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(r, g, b)
    }

    private fun isValidHex(hex: String): Boolean {
        val s = normalizeHex(hex)
        return s.matches(Regex("#[0-9a-fA-F]{6}"))
    }

    private fun normalizeHex(hex: String): String = if (hex.startsWith("#")) hex else "#$hex"

    private fun triadic(base: Color): Triple<Color, Color, Color> {
        val hsl = rgbToHsl(base)
        val h1 = (hsl.first + 120f) % 360f
        val h2 = (hsl.first + 240f) % 360f
        return Triple(
            hslToRgb(hsl.copy(first = hsl.first)),
            hslToRgb(hsl.copy(first = h1)),
            hslToRgb(hsl.copy(first = h2))
        )
    }

    fun applySplitComplementaryFromFirst() {
        val first = cardColors.value.firstOrNull() ?: return
        val (a, b, c) = splitComplementary(first)
        val user = currentUser.value ?: return
        val base = currentProfile.value
        viewModelScope.launch {
            val existing = base ?: profiles.observeProfile(user.uid).firstOrNull()
            val e =
                existing
                    ?: UserProfile(
                        uid = user.uid,
                        displayName = null,
                        email = null,
                        photoUrl = null
                    )
            val updated =
                e.copy(
                    cardColorHex1 = colorToHex(a),
                    cardColorHex2 = colorToHex(b),
                    cardColorHex3 = colorToHex(c)
                )
            profiles.upsertProfile(updated)
        }
    }

    private fun splitComplementary(base: Color): Triple<Color, Color, Color> {
        val (h, s, l) = rgbToHsl(base)
        val h1 = (h + 150f) % 360f
        val h2 = (h + 210f) % 360f // h - 150 wrapped
        return Triple(
            hslToRgb(Triple(h, s, l)),
            hslToRgb(Triple(h1, s, l)),
            hslToRgb(Triple(h2, s, l))
        )
    }

    fun applyTriadicFromFirst() {
        val first = cardColors.value.firstOrNull() ?: return
        val (a, b, c) = triadic(first)
        val user = currentUser.value ?: return
        val base = currentProfile.value
        viewModelScope.launch {
            val existing = base ?: profiles.observeProfile(user.uid).firstOrNull()
            val e =
                existing
                    ?: UserProfile(
                        uid = user.uid,
                        displayName = null,
                        email = null,
                        photoUrl = null
                    )
            val updated =
                e.copy(
                    cardColorHex1 = colorToHex(a),
                    cardColorHex2 = colorToHex(b),
                    cardColorHex3 = colorToHex(c)
                )
            profiles.upsertProfile(updated)
        }
    }

    private fun rgbToHsl(c: Color): Triple<Float, Float, Float> {
        val r = c.red
        val g = c.green
        val b = c.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        var h = 0f
        val l = (max + min) / 2f
        val d = max - min
        val s = if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
        if (d != 0f) {
            h =
                when (max) {
                    r -> 60f * (((g - b) / d) % 6f)
                    g -> 60f * (((b - r) / d) + 2f)
                    else -> 60f * (((r - g) / d) + 4f)
                }
        }
        if (h < 0f) h += 360f
        return Triple(h, s, l)
    }

    private fun hslToRgb(hsl: Triple<Float, Float, Float>): Color {
        val (h, s, l) = hsl
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) =
            when {
                h < 60f -> Triple(c, x, 0f)
                h < 120f -> Triple(x, c, 0f)
                h < 180f -> Triple(0f, c, x)
                h < 240f -> Triple(0f, x, c)
                h < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
        return Color(r1 + m, g1 + m, b1 + m)
    }
}
