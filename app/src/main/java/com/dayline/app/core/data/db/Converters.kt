package com.dayline.app.core.data.db

import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Dates are stored as sortable primitives so range queries work in SQL:
 * LocalDate -> epoch day, LocalTime -> second of day, Instant -> epoch millis,
 * LocalDateTime -> ISO-8601 text, weekday sets -> bit mask.
 */
class Converters {
    @TypeConverter fun localDateToLong(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun longToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun localTimeToInt(value: LocalTime?): Int? = value?.toSecondOfDay()
    @TypeConverter fun intToLocalTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun localDateTimeToString(value: LocalDateTime?): String? = value?.toString()
    @TypeConverter fun stringToLocalDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    @TypeConverter fun weekdaysToInt(value: Set<DayOfWeek>): Int = value.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
    @TypeConverter fun intToWeekdays(value: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { value and (1 shl (it.value - 1)) != 0 }.toSet()
}
