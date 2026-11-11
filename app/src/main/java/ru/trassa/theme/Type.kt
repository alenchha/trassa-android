package ru.trassa.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ru.trassa.R

/** Inter - как в макете */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Межстрочный интервал в макете - 125 % от кегля */
private fun inter(size: Int, weight: FontWeight): TextStyle =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.25f).sp, letterSpacing = TextUnit.Unspecified)

object TrassaType {
    val Hero = inter(30, FontWeight.Bold)          // «Стены помнят.»
    val LargeTitle = inter(32, FontWeight.Bold)    // «Комнаты»
    val Title = inter(27, FontWeight.Bold)         // заголовок карточки трассы
    val Title28 = inter(28, FontWeight.Bold)
    val Title2 = inter(22, FontWeight.Bold)        // нижняя панель AR
    val Title3 = inter(21, FontWeight.Bold)        // плашка результата
    val Brand = inter(24, FontWeight.Bold)         // «ТРАССА»
    val Headline = inter(18, FontWeight.SemiBold)  // навбар, карточки
    val Card17 = inter(17, FontWeight.SemiBold)
    val Button = inter(16, FontWeight.SemiBold)
    val Callout = inter(15, FontWeight.Medium)
    val Body = inter(15, FontWeight.Normal)
    val Subheadline = inter(14, FontWeight.SemiBold)
    val SubheadlineRegular = inter(14, FontWeight.Normal)
    val ButtonSmall = inter(14, FontWeight.Bold)
    val Footnote = inter(13, FontWeight.SemiBold)
    val FootnoteRegular = inter(13, FontWeight.Normal)
    val Caption = inter(12, FontWeight.Normal)
    val CaptionBold = inter(12, FontWeight.SemiBold)
    val Small = inter(11, FontWeight.Medium)
    val Label = inter(11, FontWeight.Bold)         // «ЗАПИСЬ»
    val MicroLabel = inter(10, FontWeight.Bold)
}
