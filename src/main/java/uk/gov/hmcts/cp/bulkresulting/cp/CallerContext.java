package uk.gov.hmcts.cp.bulkresulting.cp;

import java.util.Objects;
import java.util.Optional;

/**
 * Who is calling: the user the gateway authenticated, and the UI's session correlation id when it
 * sent one. Every Common Platform call is made as this user.
 *
 * @param userId        the user's {@code CJSCPPUID}
 * @param correlationId the {@code CPPCLIENTCORRELATIONID}, if any
 */
public record CallerContext(String userId, Optional<String> correlationId) {

    /**
     * Validates the user id.
     *
     * @param userId        the user's {@code CJSCPPUID}
     * @param correlationId the correlation id, if any
     */
    public CallerContext {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(correlationId, "correlationId");
    }
}
