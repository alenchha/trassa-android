package ru.trassa.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.trassa.R
import ru.trassa.geometry.RouteType
import ru.trassa.theme.TrassaColors
import ru.trassa.theme.TrassaDimens
import ru.trassa.theme.TrassaType
import ru.trassa.theme.color
import ru.trassa.theme.title

// ── Кнопки (UI KIT: оранжевая, тёмная с обводкой) ─────────────────────────

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = TrassaType.Button,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(TrassaDimens.ButtonHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(TrassaDimens.Radius))
            .background(TrassaColors.Accent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = TrassaColors.OnAccent, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bordered: Boolean = true,
    style: TextStyle = TrassaType.Button,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(TrassaDimens.ButtonHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(TrassaDimens.Radius))
            .background(TrassaColors.Surface)
            .then(if (bordered) Modifier.border(1.dp, TrassaColors.Border, RoundedCornerShape(TrassaDimens.Radius)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = TrassaColors.TextPrimary, textAlign = TextAlign.Center)
    }
}

/** Квадратная кнопка 56×56 («Закрыть», «Список») */
@Composable
fun IconSquareButton(icon: Painter, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(TrassaDimens.IconButton)
            .clip(RoundedCornerShape(TrassaDimens.Radius))
            .background(TrassaColors.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = TrassaColors.TextPrimary, modifier = Modifier.size(24.dp))
    }
}

// ── Шапка экрана: «Назад» + заголовок 18/600 ───────────────────────────────

@Composable
fun NavHeader(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_back), "Назад", tint = TrassaColors.TextPrimary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(title, style = TrassaType.Headline, color = TrassaColors.TextPrimary, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

// ── Карточки и плашки ──────────────────────────────────────────────────────

fun Modifier.card(stroke: Color? = null, radius: Dp = TrassaDimens.Radius): Modifier =
    this.clip(RoundedCornerShape(radius))
        .background(TrassaColors.Surface)
        .then(if (stroke != null) Modifier.border(BorderStroke(1.dp, stroke), RoundedCornerShape(radius)) else Modifier)

/** Карточка-подсказка: заголовок 16/600 + текст 13/400 */
@Composable
fun InfoCard(title: String, text: String, modifier: Modifier = Modifier, icon: Painter? = null) {
    Row(modifier.fillMaxWidth().card().padding(TrassaDimens.CardPadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) Icon(icon, null, tint = TrassaColors.Accent, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = TrassaType.Button, color = TrassaColors.TextPrimary)
            Text(text, style = TrassaType.FootnoteRegular, color = TrassaColors.TextSecondary)
        }
    }
}

/** Капслок-метка («ДО ОТДЕЛКИ», «12 ТРАСС СОХРАНЕНО») */
@Composable
fun TagLabel(text: String, color: Color = TrassaColors.Accent, background: Color = TrassaColors.Background, modifier: Modifier = Modifier) {
    Box(modifier.height(28.dp).clip(RoundedCornerShape(6.dp)).background(background).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
        Text(text.uppercase(), style = TrassaType.Label, color = color)
    }
}

/** Плашка поверх камеры */
@Composable
fun OverlayPill(modifier: Modifier = Modifier, radius: Dp = TrassaDimens.SmallRadius, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(radius)).background(TrassaColors.Background).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Чип выбора типа трассы: цветная точка + название, выбранный - с обводкой цвета трассы */
@Composable
fun RouteTypeChip(type: RouteType, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(TrassaDimens.ButtonHeight)
            .clip(RoundedCornerShape(TrassaDimens.Radius))
            .background(TrassaColors.Surface)
            .border(1.dp, if (selected) type.color else TrassaColors.Border, RoundedCornerShape(TrassaDimens.Radius))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(TrassaDimens.RouteDot).clip(CircleShape).background(type.color))
        Text(type.title, style = TrassaType.Subheadline, color = TrassaColors.TextPrimary)
    }
}

/** Индикатор качества: 4 столбика 8/12/16/20 */
@Composable
fun QualityBars(level: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        repeat(4) { i ->
            Box(Modifier.width(4.dp).height((8 + i * 4).dp).clip(RoundedCornerShape(1.dp))
                .background(if (i < level) TrassaColors.Safe else TrassaColors.Border))
        }
    }
}

/** Плашка «Безопасно» / «Трасса в 8 см - электрика» */
@Composable
fun DrillResultCard(dangerous: Boolean, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val color = if (dangerous) TrassaColors.Danger else TrassaColors.Safe
    Column(
        modifier.fillMaxWidth().heightIn(min = 104.dp).card(stroke = color).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = TrassaType.Title3, color = color)
        Text(subtitle, style = TrassaType.FootnoteRegular, color = TrassaColors.TextPrimary)
    }
}

/** Прицел 56×56 */
@Composable
fun Reticle(color: Color = TrassaColors.Accent, modifier: Modifier = Modifier) {
    Box(
        modifier.size(TrassaDimens.Reticle).clip(RoundedCornerShape(TrassaDimens.ReticleRadius)).background(TrassaColors.Overlay),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_target), null, tint = color, modifier = Modifier.size(24.dp))
    }
}

/** Уголки рамки поиска */
@Composable
fun CornerBrackets(modifier: Modifier, color: Color = TrassaColors.Accent, length: Dp = 32.dp, stroke: Dp = 4.dp) {
    Canvas(modifier) {
        val l = length.toPx()
        val s = stroke.toPx()
        val w = size.width
        val h = size.height
        fun line(a: Offset, b: Offset) = drawLine(color, a, b, s, StrokeCap.Square)
        line(Offset(0f, 0f), Offset(l, 0f)); line(Offset(0f, 0f), Offset(0f, l))
        line(Offset(w, 0f), Offset(w - l, 0f)); line(Offset(w, 0f), Offset(w, l))
        line(Offset(0f, h), Offset(l, h)); line(Offset(0f, h), Offset(0f, h - l))
        line(Offset(w, h), Offset(w - l, h)); line(Offset(w, h), Offset(w, h - l))
    }
}

/** Поле ввода в стиле макета: подпись сверху, тёмное поле 64 dp */
@Composable
fun LabeledField(label: String, placeholder: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, singleLine: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = TrassaType.SubheadlineRegular, color = TrassaColors.TextSecondary)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).card().padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) Text(placeholder, style = TrassaType.Body.copy(fontSize = TrassaType.Card17.fontSize), color = TrassaColors.TextMuted)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                textStyle = TrassaType.Callout.copy(fontSize = TrassaType.Card17.fontSize, color = TrassaColors.TextPrimary),
                cursorBrush = SolidColor(TrassaColors.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Фото из файла или запасное фото из макета */
@Composable
fun PhotoBox(path: String?, modifier: Modifier, placeholder: Int = R.drawable.wall_rough) {
    val bitmap = remember(path) {
        path?.let { p -> runCatching { BitmapFactory.decodeFile(p, BitmapFactory.Options().apply { inSampleSize = 2 }) }.getOrNull() }
    }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    } else {
        Image(painterResource(placeholder), null, modifier, contentScale = ContentScale.Crop)
    }
}

/** Простой диалог ввода текста (переименование, заметка) */
@Composable
fun TextInputDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val state = remember { androidx.compose.runtime.mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TrassaColors.Surface,
        title = { Text(title, style = TrassaType.Headline, color = TrassaColors.TextPrimary) },
        text = {
            BasicTextField(
                value = state.value,
                onValueChange = { state.value = it },
                textStyle = TrassaType.Body.copy(color = TrassaColors.TextPrimary),
                cursorBrush = SolidColor(TrassaColors.Accent),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(TrassaColors.Background).padding(14.dp),
            )
        },
        confirmButton = {
            Text(confirm, style = TrassaType.Subheadline, color = TrassaColors.Accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onConfirm(state.value); onDismiss() }.padding(12.dp))
        },
        dismissButton = {
            Text("Отмена", style = TrassaType.Subheadline, color = TrassaColors.TextSecondary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(12.dp))
        },
    )
}

/** Подтверждение удаления */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TrassaColors.Surface,
        title = { Text(title, style = TrassaType.Headline, color = TrassaColors.TextPrimary) },
        text = { Text(text, style = TrassaType.FootnoteRegular, color = TrassaColors.TextSecondary) },
        confirmButton = {
            Text(confirm, style = TrassaType.Subheadline, color = TrassaColors.Danger,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onConfirm(); onDismiss() }.padding(12.dp))
        },
        dismissButton = {
            Text("Отмена", style = TrassaType.Subheadline, color = TrassaColors.TextSecondary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(12.dp))
        },
    )
}

/** Простое меню действий (переименовать / удалить …) */
data class MenuAction(val title: String, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
fun ActionsMenu(expanded: Boolean, onDismiss: () -> Unit, actions: List<MenuAction>) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = TrassaColors.Surface,
    ) {
        actions.forEach { a ->
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(a.title, style = TrassaType.SubheadlineRegular, color = if (a.destructive) TrassaColors.Danger else TrassaColors.TextPrimary) },
                onClick = { onDismiss(); a.onClick() },
            )
        }
    }
}

