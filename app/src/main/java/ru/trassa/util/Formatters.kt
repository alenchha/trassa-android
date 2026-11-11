package ru.trassa.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Форматирование по-русски: «3,2 м», «8 см», «4 трассы» */
object Formatters {
    private val ru = Locale.forLanguageTag("ru-RU")

    fun meters(value: Float): String = String.format(ru, "%.1f м", value)

    fun centimeters(value: Float): String = "${(value * 100).roundToInt()} см"

    fun dayMonth(millis: Long): String = SimpleDateFormat("d MMMM", ru).format(Date(millis))
    fun fullDate(millis: Long): String = SimpleDateFormat("d MMMM yyyy", ru).format(Date(millis))
    fun numeric(millis: Long): String = SimpleDateFormat("dd.MM.yyyy", ru).format(Date(millis))

    /** Склонение: 1 трасса, 2 трассы, 5 трасс */
    fun plural(n: Int, one: String, few: String, many: String): String {
        val n10 = n % 10
        val n100 = n % 100
        val word = when {
            n10 == 1 && n100 != 11 -> one
            n10 in 2..4 && n100 !in 12..14 -> few
            else -> many
        }
        return "$n $word"
    }

    fun routes(n: Int) = plural(n, "трасса", "трассы", "трасс")
    fun rooms(n: Int) = plural(n, "комната", "комнаты", "комнат")
    fun projects(n: Int) = plural(n, "объект", "объекта", "объектов")
    fun points(n: Int) = plural(n, "точка", "точки", "точек")
    fun pages(n: Int) = plural(n, "страница", "страницы", "страниц")
}
