package com.palmistrylab.api.palm;

import com.palmistrylab.api.common.InMemoryLruCache;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 分析会话状态（TD §13.1）：内存 LRU + 数据库恢复双保险，
 * 保证深度解锁与稀有印记查询不因重启/内存淘汰失效。
 */
@Service
public class PalmSessionService {

  private static final int SESSION_CACHE_MAX = 20_000;

  private final InMemoryLruCache<String, SessionState> sessions = new InMemoryLruCache<>(SESSION_CACHE_MAX);
  private final SessionRecordRepository sessionRecordRepository;

  public PalmSessionService(SessionRecordRepository sessionRecordRepository) {
    this.sessionRecordRepository = sessionRecordRepository;
  }

  public record SessionState(
      String sessionId,
      String handType,
      String rareMark,
      Instant createdAt) {
  }

  public void put(SessionState state) {
    sessions.put(state.sessionId(), state);
  }

  /** 取会话；内存未命中时从数据库恢复。不存在则抛出业务异常。 */
  public SessionState require(String sessionId) {
    SessionState session = sessions.get(sessionId);
    if (session == null) {
      session = restoreFromRepository(sessionId);
    }
    if (session == null) {
      throw new IllegalArgumentException("会话不存在或已过期: " + sessionId);
    }
    return session;
  }

  private SessionState restoreFromRepository(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return null;
    }
    Optional<SessionRecordEntity> record = sessionRecordRepository.findById(sessionId);
    if (record.isEmpty()) {
      return null;
    }
    SessionRecordEntity entity = record.get();
    SessionState restored = new SessionState(
        entity.getSessionId(),
        entity.getSubject(),
        entity.getRareMark() == null ? "凤凰眼" : entity.getRareMark(),
        entity.getCreatedAt() == null ? Instant.now() : entity.getCreatedAt());
    sessions.put(sessionId, restored);
    return restored;
  }
}
