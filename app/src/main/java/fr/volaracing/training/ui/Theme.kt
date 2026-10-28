package fr.volaracing.training.ui

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---- Theme provisoire inspire de l'interface Vola Racing (resultats live) ----
// Tout est ici : a remplacer par la charte exacte quand elle sera disponible.
val VBg = Color(0xFFF2F5F8)        // fond de page
val VSurface = Color(0xFFFFFFFF)   // cartes et lignes
val VText = Color(0xFF111418)      // texte
val VGray = Color(0xFF6B7683)      // texte secondaire
val VBand = Color(0xFF8ED0F6)      // bandeau bleu clair (en-tetes)
val VAccent = Color(0xFF2E9AD8)    // bleu : selection, courbe A
val VOrange = Color(0xFFFF5A1F)    // orange Vola : courbe B
val VDark = Color(0xFF0B0D10)      // noir (barre laterale, boutons, ecran de course)
val VChip = Color(0xFFFFF1B8)      // jaune des temps
val VGreen = Color(0xFF2E9E5B)
val VRed = Color(0xFFD64541)
val VOr = Color(0xFFFBEFC2)        // tuiles type medailles
val VViolet = Color(0xFFE6E2F5)
val VArgent = Color(0xFFE2E4E8)
val VBronze = Color(0xFFF9DCCB)

// TODO: police condensee de la charte Vola (fichier .ttf dans res/font).
val VCondensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
val VBody = FontFamily.SansSerif

private val typo = Typography(
    displayLarge = TextStyle(fontFamily = VCondensed, fontWeight = FontWeight.Bold, fontSize = 72.sp),
    displaySmall = TextStyle(fontFamily = VCondensed, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = VCondensed, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = VCondensed, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = VCondensed, fontWeight = FontWeight.Bold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = VBody, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = VBody, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = VCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp),
)

@Composable
fun VolaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = VDark, onPrimary = Color.White,
            secondary = VAccent, onSecondary = Color.White,
            background = VBg, onBackground = VText,
            surface = VSurface, onSurface = VText,
            surfaceVariant = VSurface, onSurfaceVariant = VGray,
            outline = VGray,
        ),
        typography = typo,
        content = content,
    )
}

/** Couleurs des tracés superposés (le premier = bleu, le second = orange Vola, ...). */
val TracePalette = listOf(VAccent, VOrange, VGreen, Color(0xFF8E44AD), Color(0xFFD64541), Color(0xFF16A085))

/** Couleurs des voies quand un seul run est affiché. */
val ChanPalette = listOf(Color(0xFF2E9AD8), Color(0xFFFF5A1F), Color(0xFF2E9E5B), Color(0xFF8E44AD), Color(0xFFD64541), Color(0xFF16A085))
