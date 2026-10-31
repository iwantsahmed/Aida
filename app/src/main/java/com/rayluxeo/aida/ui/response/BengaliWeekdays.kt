package com.rayluxeo.aida.ui.response

import java.time.DayOfWeek

/**
 * The seven Bengali weekday names, once.
 *
 * They were written out twice — in `RepeatWording` for the sentences Mira speaks and in
 * `TaskTimeFormatter` for the dates she prints — with the same seven strings in two different
 * orders. That is the shape §7.12 exists to forbid, and here it carried a specific hazard on top
 * of the usual drift: one list was indexed `DayOfWeek.value - 1` so that Monday sat at zero, the
 * other `DayOfWeek.value % 7` so that Sunday did. Two orders, two arithmetic conventions, and an
 * off-by-one in either would not crash — it would quietly tell Abu his task was due on the wrong
 * day.
 *
 * Keyed by [DayOfWeek] rather than by position, so there is no index to get wrong and no
 * convention for a caller to have to know.
 *
 * Only the full forms live here. The short ones the chips use ("বৃহঃ") and the medium one the
 * recurrence picker uses ("বৃহস্পতি") are genuinely different registers for genuinely different
 * amounts of room, and folding them together would make a sentence read like a label.
 */
internal fun bengaliWeekdayName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "সোমবার"
    DayOfWeek.TUESDAY -> "মঙ্গলবার"
    DayOfWeek.WEDNESDAY -> "বুধবার"
    DayOfWeek.THURSDAY -> "বৃহস্পতিবার"
    DayOfWeek.FRIDAY -> "শুক্রবার"
    DayOfWeek.SATURDAY -> "শনিবার"
    DayOfWeek.SUNDAY -> "রবিবার"
}
