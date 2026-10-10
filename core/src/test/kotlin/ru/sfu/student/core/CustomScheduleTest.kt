package ru.sfu.student.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CustomScheduleTest {
    private val anchor = LocalDate.of(2026, 10, 5)
    private val zone = ZoneOffset.UTC
    private fun own(repeat: Int = 0, until: String? = null) = CustomLesson(1,
        CustomLessonDetails("Предмет", "10:00", "11:30", anchor.toString(), repeat, until))

    @Test fun onceIsOnlyOnItsDateAndFarFutureStartIsFound() {
        val far = own().copy(data = own().data.copy(startDate = "2028-04-03"))
        assertFalse(far.occurs(anchor, anchor, 1))
        assertTrue(far.occurs(LocalDate.of(2028, 4, 3), anchor, 1))
        assertFalse(far.occurs(LocalDate.of(2028, 4, 10), anchor, 1))
        assertEquals(Instant.parse("2028-04-03T10:00:00Z"), far.nextStart(Instant.parse("2026-10-05T11:00:00Z"), anchor, 1, zone))
        assertNull(far.nextStart(Instant.parse("2028-04-03T10:00:00Z"), anchor, 1, zone))
    }
    @Test fun cycleWeeksEveryWeekBoundsAndExclusionsAreApplied() {
        assertTrue(own(1).occurs(anchor, anchor, 1))
        assertFalse(own(1).occurs(anchor.plusWeeks(1), anchor, 1))
        assertTrue(own(2).occurs(anchor.plusWeeks(1), anchor, 1))
        assertFalse(own(2).occurs(anchor, anchor, 1))
        val all = own(3, "2026-10-19").copy(excludedDates = setOf(anchor.plusWeeks(1)))
        assertTrue(all.occurs(anchor, anchor, 1))
        assertFalse(all.occurs(anchor.minusWeeks(1), anchor, 1))
        assertFalse(all.occurs(anchor.plusDays(1), anchor, 1))
        assertFalse(all.occurs(anchor.plusWeeks(1), anchor, 1))
        assertTrue(all.occurs(anchor.plusWeeks(2), anchor, 1))
        assertFalse(all.occurs(anchor.plusWeeks(3), anchor, 1))
        assertEquals(Instant.parse("2026-10-19T10:00:00Z"), all.nextStart(Instant.parse("2026-10-05T11:00:00Z"), anchor, 1, zone))
        assertNull(all.nextStart(Instant.parse("2026-10-19T11:00:00Z"), anchor, 1, zone))
    }
    @Test fun mergesWithRegularScheduleAndGeneratesOnlyActualUpcomingDates() {
        val regular = Lesson(1, 1, 1, "09:00", "09:45", "Предмет", "", "", "", "", "")
        val own = own(3, "2026-10-12")
        assertEquals(listOf(1L, -1L), CustomSchedule.day(listOf(regular), listOf(own), anchor, anchor, 1).lessons.map { it.id })
        val future = CustomSchedule.upcoming(listOf(own), null, "Предмет", Instant.parse("2026-10-05T09:00:00Z"), anchor, 1, zone)
        assertEquals(listOf(anchor, anchor.plusWeeks(1)), future.map { it.date })
        assertTrue(CustomSchedule.upcoming(listOf(own), null, "Другой", Instant.parse("2026-10-05T09:00:00Z"), anchor, 1, zone).isEmpty())
    }
    @Test fun localBindingStaysStableAfterEditingWithoutMatchingAnImportedClass() {
        val first = own().template()
        val changed = own().copy(data = own().data.copy(startTime = "12:00", subject = "Новое имя")).template()
        assertEquals(LessonBinding.key(first), LessonBinding.key(changed))
        assertEquals(changed, LessonBinding.resolve(first.id, LessonBinding.key(first), listOf(changed)))
        assertNull(LessonBinding.resolve(first.id, LessonBinding.key(first), listOf(first.copy(id = 23))))
    }
    @Test fun rejectsEmptySubjectInvalidTimeAndEndBeforeStartDate() {
        listOf(own().data.copy(subject = " "), own().data.copy(endTime = "09:00"),
            own().data.copy(untilDate = "2026-10-04"), own().data.copy(repeat = 5), own().data.copy(subgroup = 0)).forEach { bad ->
            try { bad.validate(); fail("Invalid class accepted: $bad") } catch (_: IllegalArgumentException) { }
        }
    }
}
