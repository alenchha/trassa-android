package ru.trassa.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.trassa.R
import ru.trassa.ar.TrackingHint
import ru.trassa.data.ReferencePointEntity
import ru.trassa.theme.TrassaColors
import ru.trassa.theme.TrassaDimens
import ru.trassa.theme.TrassaType
import ru.trassa.ui.components.IconSquareButton
import ru.trassa.ui.components.PhotoBox
import ru.trassa.ui.components.PrimaryButton
import ru.trassa.ui.components.QualityBars
import ru.trassa.ui.components.SecondaryButton

/** Верхняя панель AR: «Закрыть», название комнаты, режим, кнопка справа */
@Composable
fun ArTopBar(title: String, mode: String, modeColor: Color, onClose: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(TrassaColors.Background).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconSquareButton(painterResource(R.drawable.ic_close), "Закрыть", onClose)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = TrassaType.Headline, color = TrassaColors.TextPrimary, maxLines = 1)
            Text(mode, style = TrassaType.Label, color = modeColor)
        }
        trailing()
    }
}

/** Карточки «Качество» и подсказка */
@Composable
fun HintCards(hint: TrackingHint, message: String = hint.message, trailing: String? = null) {
    Column(Modifier.padding(horizontal = TrassaDimens.ScreenPadding).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(TrassaColors.Background).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            QualityBars(hint.level)
            Text(hint.quality, style = TrassaType.Footnote, color = TrassaColors.TextPrimary, modifier = Modifier.weight(1f))
            trailing?.let { Text(it, style = TrassaType.MicroLabel, color = TrassaColors.TextSecondary) }
        }
        Text(
            message, style = TrassaType.Callout, color = TrassaColors.TextPrimary,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TrassaColors.Background).padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** Подсказки, какие ориентиры выбрать */
const val REFERENCE_TIPS = "Выберите то, что не изменится после ремонта: угол стены, край дверного или оконного проёма, угол у потолка. Ориентиры - в разных местах, хотя бы на 30 см друг от друга по горизонтали."

/**
 * Панель совмещения: фото ориентира, сделанное при записи, и кнопка «Это здесь».
 * Используется при просмотре и при дописывании трасс в комнату.
 */
@Composable
fun AlignPanel(references: List<ReferencePointEntity>, index: Int, canMark: Boolean, onMark: () -> Unit, onCancel: () -> Unit) {
    val ref = references.getOrNull(index)
    Column(Modifier.fillMaxWidth().background(TrassaColors.Background).padding(horizontal = TrassaDimens.ScreenPadding).padding(top = 20.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PhotoBox(ref?.photoPath, Modifier.size(84.dp).clip(RoundedCornerShape(TrassaDimens.SmallRadius)), R.drawable.wall_finished)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ориентир ${index + 1} из ${references.size}", style = TrassaType.Title2, color = TrassaColors.TextPrimary)
                Text("Наведите прицел на то же место, что на фото, и нажмите «Это здесь».", style = TrassaType.FootnoteRegular, color = TrassaColors.TextSecondary)
            }
        }
        PrimaryButton("Это здесь", onMark, Modifier.padding(top = 20.dp), enabled = canMark)
        SecondaryButton("Отменить", onCancel, Modifier.padding(top = 8.dp))
    }
}

/** Миниатюры отмеченных ориентиров */
@Composable
fun ReferenceThumbs(photos: List<Bitmap?>, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            val bmp = photos.getOrNull(i)
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(TrassaColors.Surface), contentAlignment = Alignment.Center) {
                if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(56.dp), contentScale = ContentScale.Crop)
                else Text("${i + 1}", style = TrassaType.Headline, color = TrassaColors.TextMuted)
            }
        }
    }
}

/** Вырезает центр снимка (вокруг прицела) - фото ориентира */
fun Bitmap.centerCrop(fraction: Float = 0.45f, maxSize: Int = 480): Bitmap {
    val side = (minOf(width, height) * fraction).toInt().coerceAtLeast(1)
    val crop = Bitmap.createBitmap(this, (width - side) / 2, (height - side) / 2, side, side)
    return if (side > maxSize) Bitmap.createScaledBitmap(crop, maxSize, maxSize, true) else crop
}

/** Уменьшенное превью комнаты */
fun Bitmap.scaledPreview(maxSide: Int = 1280): Bitmap {
    val s = maxSide.toFloat() / maxOf(width, height)
    return if (s >= 1f) this else Bitmap.createScaledBitmap(this, (width * s).toInt(), (height * s).toInt(), true)
}
