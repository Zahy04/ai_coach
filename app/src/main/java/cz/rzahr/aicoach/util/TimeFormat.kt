package cz.rzahr.aicoach.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateTimeFormatter = DateTimeFormatter.ofPattern("d. M. yyyy HH:mm")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("d. M. yyyy")
private val dayHeaderFormatter = DateTimeFormatter.ofPattern("EEEE d. M. yyyy")

fun Long.formatDateTime(): String =
    dateTimeFormatter.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

fun Long.formatTime(): String =
    timeFormatter.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

fun Long.formatDate(): String =
    dateFormatter.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

fun Long.formatDayHeader(): String =
    dayHeaderFormatter.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun LocalDate.formatDayHeader(): String = format(dayHeaderFormatter)
