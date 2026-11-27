package uk.gov.hmcts.cp.bulkresulting.cp;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Null-safe reads of optional fields from Common Platform JSON. */
public final class JsonFields {

    private JsonFields() {
    }

    /**
     * A text field.
     *
     * @param node  the object
     * @param field the field name
     * @return the text, or empty when missing, null or blank
     */
    public static Optional<String> text(final JsonNode node, final String field) {
        final JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || value.asString().isBlank()
                ? Optional.empty()
                : Optional.of(value.asString());
    }

    /**
     * A UUID field.
     *
     * @param node  the object
     * @param field the field name
     * @return the UUID, or empty when missing or not a UUID
     */
    public static Optional<UUID> uuid(final JsonNode node, final String field) {
        return text(node, field).flatMap(JsonFields::parseUuid);
    }

    /**
     * An ISO date field; a date-time value is cut to its date.
     *
     * @param node  the object
     * @param field the field name
     * @return the date, or empty when missing or not a date
     */
    public static Optional<LocalDate> date(final JsonNode node, final String field) {
        return text(node, field).flatMap(JsonFields::parseDate);
    }

    /**
     * A boolean field.
     *
     * @param node  the object
     * @param field the field name
     * @return true only when the field is the boolean true
     */
    public static boolean isTrue(final JsonNode node, final String field) {
        final JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isBoolean() && value.asBoolean();
    }

    /**
     * The elements of an array field.
     *
     * @param node  the object
     * @param field the field name
     * @return the elements, or an empty list when missing or not an array
     */
    public static List<JsonNode> array(final JsonNode node, final String field) {
        final JsonNode value = node == null ? null : node.get(field);
        final List<JsonNode> elements = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(elements::add);
        }
        return elements;
    }

    private static Optional<UUID> parseUuid(final String value) {
        Optional<UUID> result;
        try {
            result = Optional.of(UUID.fromString(value));
        } catch (final IllegalArgumentException notUuid) {
            result = Optional.empty();
        }
        return result;
    }

    private static Optional<LocalDate> parseDate(final String value) {
        Optional<LocalDate> result;
        final int dateLength = 10;
        try {
            result = Optional.of(LocalDate.parse(value.length() > dateLength ? value.substring(0, dateLength) : value));
        } catch (final DateTimeParseException notDate) {
            result = Optional.empty();
        }
        return result;
    }
}
