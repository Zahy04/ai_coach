package cz.rzahr.aicoach.data.repo

import cz.rzahr.aicoach.data.db.dao.LlmRequestDao
import cz.rzahr.aicoach.data.db.dao.LlmToolCallDao
import cz.rzahr.aicoach.data.db.entity.LlmRequestEntity
import cz.rzahr.aicoach.data.db.entity.LlmToolCallEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Telemetrie LLM pro benchmark modelů (issue #6): latence, 429/přetížení,
 * tokeny a odhady makro. Zápis je vždy best-effort – když se to nezdaří,
 * chat běží dál.
 */
@Singleton
class LlmStatsRepository @Inject constructor(
    private val requestDao: LlmRequestDao,
    private val toolCallDao: LlmToolCallDao
) {

    fun observeRequestCount(): Flow<Int> = requestDao.observeCount()

    fun observeToolCallCount(): Flow<Int> = toolCallDao.observeCount()

    suspend fun allRequests(): List<LlmRequestEntity> = requestDao.allAsc()

    suspend fun allToolCalls(): List<LlmToolCallEntity> = toolCallDao.allAsc()

    suspend fun record(request: LlmRequestEntity) {
        runCatching { requestDao.insert(request) }
    }

    suspend fun record(call: LlmToolCallEntity) {
        runCatching { toolCallDao.insert(call) }
    }
}
