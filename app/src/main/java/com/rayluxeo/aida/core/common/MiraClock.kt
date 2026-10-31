package com.rayluxeo.aida.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Injected so relative-time parsing ("kal shokal 10 tay") is testable without wall-clock flakiness. */
interface MiraClock {
    fun now(): Instant
    fun zone(): ZoneId
    fun today(): LocalDate = LocalDateTime.ofInstant(now(), zone()).toLocalDate()
    fun localNow(): LocalDateTime = LocalDateTime.ofInstant(now(), zone())
}

class SystemMiraClock : MiraClock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
