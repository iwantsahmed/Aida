package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.DailyBrief
import com.rayluxeo.aida.domain.repository.BriefRepository
import javax.inject.Inject

class GetDailyBriefUseCase @Inject constructor(private val briefs: BriefRepository) {
    suspend operator fun invoke(): AppResult<DailyBrief> = briefs.todayBrief()
}
