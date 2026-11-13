package ru.trassa.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.window.Dialog
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
    var zoomed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(TrassaColors.Background).padding(horizontal = TrassaDimens.ScreenPadding).padding(top = 20.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ReferencePhoto(ref?.photoPath, Modifier.size(112.dp).clip(RoundedCornerShape(TrassaDimens.SmallRadius)).clickable { zoomed = true })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ориентир ${index + 1} из ${references.size}", style = TrassaType.Title2, color = TrassaColors.TextPrimary)
                Text("Красный кружок на фото - точное место ориентира. Наведите прицел туда же и нажмите «Это здесь». Нажмите на фото, чтобы увеличить.", style = TrassaType.FootnoteRegular, color = TrassaColors.TextSecondary)
            }
        }
        PrimaryButton("Это здесь", onMark, Modifier.padding(top = 20.dp), enabled = canMark)
        SecondaryButton("Отменить", onCancel, Modifier.padding(top = 8.dp))
    }
    if (zoomed) {
        // Фото ориентира на весь экран: так проще найти то же место на стене
        Dialog(onDismissRequest = { zoomed = false }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TrassaColors.Background).clickable { zoomed = false }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReferencePhoto(ref?.photoPath, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(TrassaDimens.SmallRadius)))
                Text("Ориентир ${index + 1} из ${references.size}", style = TrassaType.Title2, color = TrassaColors.TextPrimary)
                Text("Поставьте прицел в центр красного кружка. Нажмите, чтобы закрыть.", style = TrassaType.FootnoteRegular, color = TrassaColors.TextSecondary)
            }
        }
    }
}

/** Фото ориентира с отметкой места прицела */
@Composable
fun ReferencePhoto(path: String?, modifier: Modifier) {
    Box(modifier) {
        PhotoBox(path, Modifier.fillMaxSize(), R.drawable.wall_finished)
        if (path != null) ReferenceMark(Modifier.fillMaxSize())
    }
}

/**
 * Отметка ориентира в центре фото. Снимок - квадрат, вырезанный из центра экрана,
 * а прицел всегда в центре экрана, поэтому место ориентира - ровно центр фото.
 * Рисуется при показе, а не в файле - так отметка есть и на фото, снятых раньше.
 */
@Composable
fun ReferenceMark(modifier: Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension * 0.07f
        val w = maxOf(size.minDimension * 0.012f, 1.5f)
        // Перекрестие с разрывом в центре, чтобы не закрывать саму точку
        val gap = r * 1.6f
        val len = r * 3.2f
        listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f)).forEach { d ->
            drawLine(Color.White, c + d * gap, c + d * len, w * 2.2f)
            drawLine(TrassaColors.Danger, c + d * gap, c + d * len, w)
        }
        drawCircle(Color.White, r, c, style = Stroke(w * 2.2f))
        drawCircle(TrassaColors.Danger, r, c, style = Stroke(w))
        drawCircle(TrassaColors.Danger, maxOf(w * 1.2f, 2f), c)
    }
}

/** Миниатюры отмеченных ориентиров */
@Composable
fun ReferenceThumbs(photos: List<Bitmap?>, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            val bmp = photos.getOrNull(i)
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(TrassaColors.Surface), contentAlignment = Alignment.Center) {
                if (bmp != null) Box(Modifier.size(56.dp)) {
                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    ReferenceMark(Modifier.fillMaxSize())
                } else Text("${i + 1}", style = TrassaType.Headline, color = TrassaColors.TextMuted)
            }
        }
    }
}

/** Вырезает центр снимка (вокруг прицела) - фото ориентира */
fun Bitmap.centerCrop(fraction: Float = 0.75f, maxSize: Int = 720): Bitmap {
    val side = (minOf(width, height) * fraction).toInt().coerceAtLeast(1)
    val crop = Bitmap.createBitmap(this, (width - side) / 2, (height - side) / 2, side, side)
    return if (side > maxSize) Bitmap.createScaledBitmap(crop, maxSize, maxSize, true) else crop
}

/** Уменьшенное превью комнаты */
fun Bitmap.scaledPreview(maxSide: Int = 1280): Bitmap {
    val s = maxSide.toFloat() / maxOf(width, height)
    return if (s >= 1f) this else Bitmap.createScaledBitmap(this, (width * s).toInt(), (height * s).toInt(), true)
}
