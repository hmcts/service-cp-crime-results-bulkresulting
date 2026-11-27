package uk.gov.hmcts.cp.bulkresulting.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import uk.gov.hmcts.cp.bulkresulting.batch.BatchStateRepository;
import uk.gov.hmcts.cp.bulkresulting.cp.Court;
import uk.gov.hmcts.cp.bulkresulting.cp.CpHttpClient;
import uk.gov.hmcts.cp.bulkresulting.cp.HearingQueryClient;
import uk.gov.hmcts.cp.bulkresulting.cp.ListingClient;
import uk.gov.hmcts.cp.bulkresulting.cp.ProgressionClient;
import uk.gov.hmcts.cp.bulkresulting.cp.ReferenceDataClient;
import uk.gov.hmcts.cp.bulkresulting.cp.UsersGroupsClient;
import uk.gov.hmcts.cp.bulkresulting.search.BoundedParallel;
import uk.gov.hmcts.cp.bulkresulting.search.CandidateHearing;
import uk.gov.hmcts.cp.bulkresulting.search.HearingSearchService;
import uk.gov.hmcts.cp.bulkresulting.search.SearchFilterFactory;

/** Wires the search from the bound {@code bulkresulting.*} settings. */
@Configuration(proxyBeanMethods = false)
public class ServiceConfiguration {

    /**
     * The clock that decides what today is.
     *
     * @param properties the settings
     * @return the clock
     */
    @Bean
    public Clock clock(final BulkResultingProperties properties) {
        return Clock.system(properties.search().timeZone());
    }

    /**
     * The Common Platform HTTP client.
     *
     * @param properties the settings
     * @return the client
     */
    @Bean
    public CpHttpClient cpHttpClient(final BulkResultingProperties properties) {
        final BulkResultingProperties.Cp cp = properties.cp();
        return new CpHttpClient(cp.baseUrl(), cp.connectTimeout(), cp.readTimeout(), cp.maxAttempts(),
                cp.initialBackoff(), cp.maxBackoff(), wait -> Thread.sleep(wait.toMillis()));
    }

    /**
     * The executor the parallel dependency calls run on.
     *
     * @return a virtual-thread executor
     */
    @Bean(destroyMethod = "close")
    public ExecutorService dependencyCallExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * The batch store reads.
     *
     * @param jdbcClient the JDBC client
     * @return the repository
     */
    @Bean
    public BatchStateRepository batchStateRepository(final JdbcClient jdbcClient) {
        return new BatchStateRepository(jdbcClient);
    }

    /**
     * The filter factory.
     *
     * @param clock      the clock
     * @param properties the settings
     * @return the factory
     */
    @Bean
    public SearchFilterFactory searchFilterFactory(final Clock clock, final BulkResultingProperties properties) {
        return new SearchFilterFactory(clock, properties.search().searchWindowDays());
    }

    /**
     * The hearing search.
     *
     * @param http       the Common Platform HTTP client
     * @param batchState the batch store
     * @param executor   the executor for parallel calls
     * @param properties the settings
     * @return the service
     */
    @Bean
    public HearingSearchService hearingSearchService(final CpHttpClient http, final BatchStateRepository batchState,
                                                     final ExecutorService executor,
                                                     final BulkResultingProperties properties) {
        final BulkResultingProperties.Search search = properties.search();
        final Cache<HearingSearchService.CandidateKey, List<CandidateHearing>> candidates = Caffeine.newBuilder()
                .expireAfterWrite(search.candidateCacheTtl())
                .maximumSize(search.candidateCacheMaxEntries())
                .build();
        final Cache<String, Court> courts = Caffeine.newBuilder()
                .expireAfterWrite(search.courtCacheTtl())
                .maximumSize(search.candidateCacheMaxEntries())
                .build();
        return new HearingSearchService(
                new HearingSearchService.Clients(new UsersGroupsClient(http), new ReferenceDataClient(http),
                        new ListingClient(http), new HearingQueryClient(http), new ProgressionClient(http)),
                batchState, new BoundedParallel(executor, properties.cp().parallelism()), candidates, courts,
                new HearingSearchService.Limits(search.listingMaxResults(), search.pageSize()));
    }
}
