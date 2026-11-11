package ru.trassa.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.trassa.geometry.RouteType

/**
 * Дизайн-токены из макета Figma «Трасса / UI KIT».
 * Совпадают с iOS-версией (Trassa/Theme/Theme.swift).
 */
object TrassaColors {
    /** Фон экранов - #101214 */
    val Background = Color(0xFF101214)
    /** Карточки, поля, вторичные кнопки - #1C1F23 */
    val Surface = Color(0xFF1C1F23)
    /** Обводка - #34393F */
    val Border = Color(0xFF34393F)
    /** Основной текст - #F6F7F9 */
    val TextPrimary = Color(0xFFF6F7F9)
    /** Вторичный текст - #AAB2BC */
    val TextSecondary = Color(0xFFAAB2BC)
    /** Неактивный текст - #5B6169 */
    val TextMuted = Color(0xFF5B6169)
    /** Акцент - #FF6B00 */
    val Accent = Color(0xFFFF6B00)
    val OnAccent = Color(0xFF101214)
    /** Плашки поверх камеры - #101214, 75 % */
    val Overlay = Color(0xBF101214)
    /** Затемнение камеры - #101214, 16 % */
    val CameraDim = Color(0x29101214)

    val Safe = Color(0xFF52DBA0)
    val Danger = Color(0xFFFF5D60)
    /** Красная зона ±15 см - #FF5D60, 24 % */
    val DangerZone = Color(0x3DFF5D60)

    // Светлый лист PDF
    val Paper = Color(0xFFF2F1ED)
    val PaperWall = Color(0xFFE0E1DC)
    val PaperDanger = Color(0xFFBA3333)
}

object RouteColors {
    val Electric = Color(0xFFFFD447)
    val Water = Color(0xFF48A8FF)
    val Heating = Color(0xFFFF5D60)
    val LowVoltage = Color(0xFF52DBA0)

    // Тёмные варианты для печати на светлом листе
    val ElectricPrint = Color(0xFFB48700)
    val WaterPrint = Color(0xFF1877BC)
    val HeatingPrint = Color(0xFFCE4343)
    val LowVoltagePrint = Color(0xFF178858)
}

object TrassaDimens {
    val ScreenPadding = 24.dp
    val CardPadding = 16.dp
    val CardSpacing = 16.dp
    val ButtonHeight = 56.dp
    val IconButton = 56.dp
    val Radius = 12.dp
    val SmallRadius = 8.dp
    val ReticleRadius = 10.dp
    val Reticle = 56.dp
    val ProjectPhotoHeight = 132.dp
    val RoomThumb = 72.dp
    val RouteDot = 10.dp
    val RouteStripe = 4.dp
}

/** Название и цвета типа трассы */
val RouteType.title: String
    get() = when (this) {
        RouteType.ELECTRIC -> "Электрика"
        RouteType.WATER -> "Вода"
        RouteType.HEATING -> "Отопление"
        RouteType.LOW_VOLTAGE -> "Слаботочка"
    }

/** Для фразы «8 см до кабеля» */
val RouteType.objectNoun: String
    get() = when (this) {
        RouteType.ELECTRIC -> "кабеля"
        RouteType.WATER -> "трубы"
        RouteType.HEATING -> "трубы отопления"
        RouteType.LOW_VOLTAGE -> "слаботочного кабеля"
    }

val RouteType.color: Color
    get() = when (this) {
        RouteType.ELECTRIC -> RouteColors.Electric
        RouteType.WATER -> RouteColors.Water
        RouteType.HEATING -> RouteColors.Heating
        RouteType.LOW_VOLTAGE -> RouteColors.LowVoltage
    }

val RouteType.printColor: Color
    get() = when (this) {
        RouteType.ELECTRIC -> RouteColors.ElectricPrint
        RouteType.WATER -> RouteColors.WaterPrint
        RouteType.HEATING -> RouteColors.HeatingPrint
        RouteType.LOW_VOLTAGE -> RouteColors.LowVoltagePrint
    }
