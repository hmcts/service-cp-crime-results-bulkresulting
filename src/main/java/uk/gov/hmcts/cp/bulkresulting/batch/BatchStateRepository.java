package uk.gov.hmcts.cp.bulkresulting.batch;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The search's reads of the batch store (solution design §6 step 3, §9).
 *
 * <ul>
 *     <li><b>In flight:</b> a hearing with a row in PENDING, IN_PROGRESS or UNCONFIRMED is hidden.</li>
 *     <li><b>Failed to share:</b> the label comes from the hearing's latest row that is not
 *     NOT_TAKEN, when that row is DRAFT_IN_PROGRESS, CONFLICT, SLOTS_UNAVAILABLE, TECHNICAL or
 *     NOT_SHARED.</li>
 *     <li><b>In-flight count:</b> the caller's own in-flight hearings, for the banner.</li>
 * </ul>
 */
public class BatchStateRepository {

    /** Statuses of a hearing still being worked on by a batch. */
    public static final List<String> IN_FLIGHT = List.of("PENDING", "IN_PROGRESS", "UNCONFIRMED");

    /** Statuses of a bulk attempt that did not share. */
    public static final List<String> FAILED_TO_SHARE =
            List.of("DRAFT_IN_PROGRESS", "CONFLICT", "SLOTS_UNAVAILABLE", "TECHNICAL", "NOT_SHARED");

    private final JdbcClient jdbcClient;

    /**
     * Creates the repository.
     *
     * @param jdbcClient the JDBC client
     */
    public BatchStateRepository(final JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Which of the hearings are in flight in any batch.
     *
     * @param hearingIds the hearings
     * @return those in flight
     */
    public Set<UUID> inFlight(final Collection<UUID> hearingIds) {
        final Set<UUID> result = new HashSet<>();
        if (!hearingIds.isEmpty()) {
            result.addAll(jdbcClient.sql("""
                            SELECT DISTINCT hearing_id FROM bulk_resulting_hearing
                            WHERE hearing_id IN (:hearingIds) AND status IN (:statuses)
                            """)
                    .param("hearingIds", hearingIds)
                    .param("statuses", IN_FLIGHT)
                    .query(UUID.class)
                    .list());
        }
        return result;
    }

    /**
     * Which of the hearings' latest bulk attempt did not share.
     *
     * @param hearingIds the hearings
     * @return those to label "Failed to share"
     */
    public Set<UUID> failedToShare(final Collection<UUID> hearingIds) {
        final Set<UUID> result = new HashSet<>();
        if (!hearingIds.isEmpty()) {
            result.addAll(jdbcClient.sql("""
                            SELECT latest.hearing_id FROM (
                                SELECT DISTINCT ON (hearing_id) hearing_id, status
                                FROM bulk_resulting_hearing
                                WHERE hearing_id IN (:hearingIds) AND status <> 'NOT_TAKEN'
                                ORDER BY hearing_id, updated_at DESC
                            ) latest
                            WHERE latest.status IN (:statuses)
                            """)
                    .param("hearingIds", hearingIds)
                    .param("statuses", FAILED_TO_SHARE)
                    .query(UUID.class)
                    .list());
        }
        return result;
    }

    /**
     * How many hearings the user has in flight.
     *
     * @param userId the user's id
     * @return the count
     */
    public int inFlightCountFor(final UUID userId) {
        return jdbcClient.sql("""
                        SELECT count(*) FROM bulk_resulting_hearing h
                        JOIN bulk_resulting b ON b.bulk_id = h.bulk_id
                        WHERE b.created_by = :userId AND h.status IN (:statuses)
                        """)
                .param("userId", userId)
                .param("statuses", IN_FLIGHT)
                .query(Integer.class)
                .single();
    }
}
