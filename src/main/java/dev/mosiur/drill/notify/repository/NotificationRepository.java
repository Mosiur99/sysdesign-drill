package dev.mosiur.drill.notify.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import dev.mosiur.drill.notify.domain.NotificationKind;
import dev.mosiur.drill.notify.model.ClaimedNotification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {

    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean enqueue(NotificationKind kind, String dedupeKey, String payload, Instant now) {
        return jdbc.update("""
                insert into notification (kind, dedupe_key, payload, next_attempt_at, created_at)
                values (?, ?, ?, ?, ?)
                on conflict (dedupe_key) do nothing
                """, kind.name(), dedupeKey, payload, Timestamp.from(now), Timestamp.from(now)) == 1;
    }

    public List<ClaimedNotification> claimDue(Instant now, int limit) {
        return jdbc.query("""
                update notification set status = 'SENDING', claimed_at = ?, attempts = attempts + 1
                where id in (select id from notification
                             where status = 'PENDING' and next_attempt_at <= ?
                             order by id
                             for update skip locked
                             limit ?)
                returning id, payload, attempts
                """, (row, index) -> new ClaimedNotification(row.getLong(1), row.getString(2), row.getInt(3)),
                Timestamp.from(now), Timestamp.from(now), limit);
    }

    public void markSent(long id, Instant now) {
        jdbc.update("update notification set status = 'SENT', sent_at = ?, last_error = null where id = ?",
                Timestamp.from(now), id);
    }

    public void markRetry(long id, Instant nextAttempt, String error) {
        jdbc.update("update notification set status = 'PENDING', next_attempt_at = ?, last_error = ? where id = ?",
                Timestamp.from(nextAttempt), error, id);
    }

    public void markFailed(long id, String error) {
        jdbc.update("update notification set status = 'FAILED', last_error = ? where id = ?", error, id);
    }

    public int releaseStale(Instant claimedBefore) {
        return jdbc.update("update notification set status = 'PENDING' where status = 'SENDING' and claimed_at < ?",
                Timestamp.from(claimedBefore));
    }
}
