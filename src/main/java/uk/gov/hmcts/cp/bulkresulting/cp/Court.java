package uk.gov.hmcts.cp.bulkresulting.cp;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A court centre from reference data.
 *
 * @param courtCentreId  the court centre id
 * @param ouCode         the OU code
 * @param courtRoomNames courtroom names by courtroom id
 */
public record Court(UUID courtCentreId, String ouCode, Map<UUID, String> courtRoomNames) {

    /**
     * Copies the names.
     *
     * @param courtCentreId  the court centre id
     * @param ouCode         the OU code
     * @param courtRoomNames courtroom names
     */
    public Court {
        courtRoomNames = Map.copyOf(courtRoomNames);
    }

    /**
     * A courtroom's name.
     *
     * @param courtRoomId the courtroom id
     * @return the name, if the courtroom is at this court
     */
    public Optional<String> courtRoomName(final UUID courtRoomId) {
        return Optional.ofNullable(courtRoomNames.get(courtRoomId));
    }

    /**
     * Whether a courtroom is at this court.
     *
     * @param courtRoomId the courtroom id
     * @return true when it is
     */
    public boolean hasCourtRoom(final UUID courtRoomId) {
        return courtRoomNames.containsKey(courtRoomId);
    }
}
