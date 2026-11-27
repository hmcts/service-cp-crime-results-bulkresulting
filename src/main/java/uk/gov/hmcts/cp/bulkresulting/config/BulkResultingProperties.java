package uk.gov.hmcts.cp.bulkresulting.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The service's settings, bound from {@code bulkresulting.*}.
 *
 * @param cp     the Common Platform client settings
 * @param search the search settings
 */
@Validated
@ConfigurationProperties("bulkresulting")
public record BulkResultingProperties(@Valid @NotNull Cp cp, @Valid @NotNull Search search) {

    /**
     * Common Platform client settings.
     *
     * @param baseUrl        host of the internal gateway; each client appends its own path
     * @param connectTimeout connect timeout per attempt
     * @param readTimeout    read timeout per attempt
     * @param maxAttempts    attempts per call, including the first
     * @param initialBackoff the first back-off between attempts
     * @param maxBackoff     the back-off cap
     * @param parallelism    concurrent calls per search
     */
    public record Cp(@NotBlank String baseUrl,
                     @NotNull Duration connectTimeout,
                     @NotNull Duration readTimeout,
                     @Min(1) int maxAttempts,
                     @NotNull Duration initialBackoff,
                     @NotNull Duration maxBackoff,
                     @Min(1) int parallelism) {
    }

    /**
     * Search settings.
     *
     * @param maxBatchSize             maximum hearings per submission, published by {@code GET /config}
     * @param searchWindowDays         how many days after today a search may reach
     * @param pageSize                 hearing rows per page
     * @param listingMaxResults        a range search reporting this many results or more is refused
     * @param candidateCacheTtl        how long listing candidates are cached per user and filter
     * @param candidateCacheMaxEntries the most cached candidate lists
     * @param courtCacheTtl            how long a court's reference data is cached
     * @param timeZone                 the zone that decides what "today" is
     */
    public record Search(@Min(1) int maxBatchSize,
                         @Min(0) int searchWindowDays,
                         @Min(1) int pageSize,
                         @Min(1) int listingMaxResults,
                         @NotNull Duration candidateCacheTtl,
                         @Min(1) long candidateCacheMaxEntries,
                         @NotNull Duration courtCacheTtl,
                         @NotNull ZoneId timeZone) {
    }
}
