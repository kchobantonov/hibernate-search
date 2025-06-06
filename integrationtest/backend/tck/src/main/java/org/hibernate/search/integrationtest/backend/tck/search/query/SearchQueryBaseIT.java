/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.integrationtest.backend.tck.search.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.search.util.impl.integrationtest.common.assertion.SearchResultAssert.assertThatQuery;
import static org.hibernate.search.util.impl.integrationtest.common.assertion.SearchResultAssert.assertThatResult;
import static org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMapperUtils.documentProvider;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.search.engine.backend.common.DocumentReference;
import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.engine.backend.session.spi.BackendSessionContext;
import org.hibernate.search.engine.backend.types.Aggregable;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.engine.search.aggregation.AggregationKey;
import org.hibernate.search.engine.search.common.NamedValues;
import org.hibernate.search.engine.search.loading.spi.SearchLoadingContext;
import org.hibernate.search.engine.search.loading.spi.SearchLoadingContextBuilder;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.engine.search.query.SearchQuery;
import org.hibernate.search.engine.search.query.SearchQueryExtension;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.query.SearchResultTotal;
import org.hibernate.search.engine.search.query.dsl.SearchQueryDslExtension;
import org.hibernate.search.engine.search.query.dsl.SearchQueryOptionsStep;
import org.hibernate.search.engine.search.query.dsl.SearchQuerySelectStep;
import org.hibernate.search.engine.search.query.dsl.SearchQueryWhereStep;
import org.hibernate.search.engine.search.query.spi.SearchQueryIndexScope;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.extension.SearchSetupHelper;
import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.SimpleMappedIndex;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingScope;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubSearchLoadingContext;
import org.hibernate.search.util.impl.test.annotation.TestForIssue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SearchQueryBaseIT {

	@RegisterExtension
	public final SearchSetupHelper setupHelper = SearchSetupHelper.create();

	private final SimpleMappedIndex<IndexBinding> index = SimpleMappedIndex.of( IndexBinding::new );

	@BeforeEach
	void setup() {
		setupHelper.start().withIndex( index ).setup();
	}

	@Test
	void getQueryString() {
		StubMappingScope scope = index.createScope();

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.match().field( "string" ).matching( "platypus" ) )
				.toQuery();

		assertThat( query.queryString() ).contains( "platypus" );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-4183")
	void tookAndTimedOut() {
		SearchQuery<DocumentReference> query = matchAllSortedByScoreQuery()
				.toQuery();

		SearchResult<DocumentReference> result = query.fetchAll();

		assertThat( result.took() ).isBetween( Duration.ZERO, Duration.of( 1, ChronoUnit.MINUTES ) );
		assertThat( result.timedOut() ).isFalse();
	}

	@Test
	void resultTotal() {
		initData( 5000 );

		SearchResult<DocumentReference> fetch = matchAllSortedByScoreQuery()
				.fetch( 10 );

		SearchResultTotal resultTotal = fetch.total();
		assertThat( resultTotal.isHitCountExact() ).isTrue();
		assertThat( resultTotal.isHitCountLowerBound() ).isFalse();
		assertThat( resultTotal.hitCount() ).isEqualTo( 5000 );
		assertThat( resultTotal.hitCountLowerBound() ).isEqualTo( 5000 );
	}

	@Test
	void resultTotal_totalHitCountThreshold() {
		initData( 5000 );

		SearchResult<DocumentReference> fetch = matchAllWithConditionSortedByScoreQuery()
				.totalHitCountThreshold( 100 )
				.toQuery()
				.fetch( 10 );

		SearchResultTotal resultTotal = fetch.total();
		assertThat( resultTotal.isHitCountExact() ).isFalse();
		assertThat( resultTotal.isHitCountLowerBound() ).isTrue();
		assertThat( resultTotal.hitCountLowerBound() ).isLessThanOrEqualTo( 5000 );

		assertThatThrownBy( () -> resultTotal.hitCount() )
				.isInstanceOf( SearchException.class )
				.hasMessageContaining(
						"Unable to provide the exact total hit count: only a lower-bound approximation is available.",
						"This is generally the result of setting query options such as a timeout or the total hit count threshold",
						"unset these options, or retrieve the lower-bound hit count approximation"
				);
	}

	@Test
	void resultTotal_totalHitCountThreshold_veryHigh() {
		initData( 5000 );

		SearchResult<DocumentReference> fetch = matchAllWithConditionSortedByScoreQuery()
				.totalHitCountThreshold( 5000 )
				.toQuery()
				.fetch( 10 );

		SearchResultTotal resultTotal = fetch.total();
		assertThat( resultTotal.isHitCountExact() ).isTrue();
		assertThat( resultTotal.isHitCountLowerBound() ).isFalse();
		assertThat( resultTotal.hitCount() ).isEqualTo( 5000 );
		assertThat( resultTotal.hitCountLowerBound() ).isEqualTo( 5000 );
	}

	@Test
	void extension() {
		initData( 2 );

		SearchQuery<DocumentReference> query = matchAllSortedByScoreQuery().toQuery();

		// Mandatory extension, supported
		QueryWrapper<DocumentReference> extendedQuery = query.extension( new SupportedQueryExtension<>() );
		assertThatResult( extendedQuery.extendedFetch() ).fromQuery( query )
				.hasDocRefHitsAnyOrder( index.typeName(), "0", "1" );

		// Mandatory extension, unsupported
		assertThatThrownBy(
				() -> query.extension( new UnSupportedQueryExtension<>() )
		)
				.isInstanceOf( SearchException.class );
	}

	@Test
	void context_extension() {
		initData( 5 );

		StubMappingScope scope = index.createScope();
		SearchQuery<DocumentReference> query;

		// Mandatory extension, supported
		query = scope.query()
				.extension( new SupportedQueryDslExtension<>() )
				.extendedFeature( "string", "value1", "value2" );
		assertThatQuery( query )
				.hasDocRefHitsExactOrder( index.typeName(), "1", "2" );

		// Mandatory extension, unsupported
		assertThatThrownBy(
				() -> scope.query()
						.extension( new UnSupportedQueryDslExtension<>() )
		)
				.isInstanceOf( SearchException.class );
	}

	@Test
	void queryParameters_nulls() {
		StubMappingScope scope = index.createScope();
		assertThatCode( () -> scope.query()
				.where( SearchPredicateFactory::matchAll )
				.param( "some name", null )
				.toQuery() )
				.doesNotThrowAnyException();

		assertThatThrownBy( () -> scope.query()
				.where( SearchPredicateFactory::matchAll )
				.param( "", 1 )
				.toQuery() )
				.isInstanceOf( IllegalArgumentException.class )
				.hasMessageContainingAll( "'parameter' must not be null or empty" );

		assertThatThrownBy( () -> scope.query()
				.where( SearchPredicateFactory::matchAll )
				.param( null, 1 )
				.toQuery() )
				.isInstanceOf( IllegalArgumentException.class )
				.hasMessageContainingAll( "'parameter' must not be null or empty" );
	}

	@Test
	void queryParameters_access() {
		StubMappingScope scope = index.createScope();
		AtomicInteger counter = new AtomicInteger( 0 );
		AggregationKey<Map<String, Long>> key = AggregationKey.of( "key" );
		scope.query()
				.select( f -> f.withParameters( params -> assertParameters( params, f, counter )
						.field( "string" ) ) )
				.where( f -> f.withParameters( ctx -> assertParameters( ctx, f, counter ).matchAll() ) )
				.sort( f -> f.withParameters( ctx -> assertParameters( ctx, f, counter ).score() ) )
				.aggregation( key, f -> f.withParameters(
						ctx -> assertParameters( ctx, f, counter ).terms().field( "string", String.class ) ) )
				.param( "p1", null )
				.param( "p2", 1 )
				.param( "p3", 2.0f )
				.param( "p4", "text" )
				.param( "p5", LocalDate.of( 2002, 02, 20 ) )
				.param( "p6", new byte[] { 1, 2, 3 } )
				.toQuery();
		assertThat( counter ).hasValue( 4 );
	}

	@Test
	void queryParameters_wrongType() {
		StubMappingScope scope = index.createScope();
		assertThatThrownBy(
				() -> scope.query().select( f -> f.withParameters( params -> {
					params.get( "p1", Double.class );
					return f.field( "string" );
				} ) )
						.where( SearchPredicateFactory::matchAll )
						.param( "p1", "string" )
						.toQuery() )
				.isInstanceOf( SearchException.class )
				.hasMessageContainingAll( "Expecting value of query parameter 'p1' to be of type ",
						java.lang.Double.class.getName(),
						", but instead got a value of type ",
						java.lang.String.class.getName() );
	}

	@Test
	void queryParameters_missing() {
		StubMappingScope scope = index.createScope();
		assertThatThrownBy(
				() -> scope.query().select( f -> f.withParameters( params -> {
					params.get( "no-such-parameter", Double.class );
					return f.field( "string" );
				} ) )
						.where( SearchPredicateFactory::matchAll )
						.param( "p1", "string" )
						.toQuery() )
				.isInstanceOf( SearchException.class )
				.hasMessageContainingAll(
						"Query parameter 'no-such-parameter' is not set. Use `.param(..)` methods on the query to set any parameters that the query requires" );

	}

	private static <T> T assertParameters(NamedValues params, T factory, AtomicInteger counter) {
		counter.incrementAndGet();
		assertThat( params.get( "p1", Object.class ) ).isNull();
		assertThat( params.get( "p2", Integer.class ) ).isEqualTo( 1 );
		assertThat( params.get( "p3", Float.class ) ).isEqualTo( 2.0f );
		assertThat( params.get( "p4", String.class ) ).isEqualTo( "text" );
		assertThat( params.get( "p5", LocalDate.class ) ).isEqualTo( LocalDate.of( 2002, 02, 20 ) );
		assertThat( params.get( "p6", byte[].class ) ).containsExactly( 1, 2, 3 );

		return factory;
	}

	private SearchQueryOptionsStep<?, ?, DocumentReference, ?, ?, ?> matchAllSortedByScoreQuery() {
		return index.query()
				.where( f -> f.matchAll() );
	}

	/**
	 * @return A query that matches all documents, but still has a condition (not a MatchAllDocsQuery).
	 * Necessary when we want to test the total hit count with a total hit count threshold,
	 * because optimizations are possible with MatchAllDocsQuery that would allow Hibernate Search
	 * to return an exact total hit count in constant time, ignoring the total hit count threshold.
	 */
	private SearchQueryOptionsStep<?, ?, DocumentReference, ?, ?, ?> matchAllWithConditionSortedByScoreQuery() {
		return index.query()
				.where( f -> f.exists().field( "string" ) );
	}

	private void initData(int documentCount) {
		index.bulkIndexer()
				.add( documentCount, i -> documentProvider(
						String.valueOf( i ),
						document -> document.addValue( index.binding().string, "value" + i )
				) )
				.join();
	}

	private static class IndexBinding {
		final IndexFieldReference<String> string;

		IndexBinding(IndexSchemaElement root) {
			string = root
					.field( "string",
							f -> f.asString().projectable( Projectable.YES ).aggregable( Aggregable.YES )
									.sortable( Sortable.YES ) )
					.toReference();
		}
	}

	private static class QueryWrapper<H> {
		private final SearchQuery<H> query;

		private QueryWrapper(SearchQuery<H> query) {
			this.query = query;
		}

		public SearchResult<H> extendedFetch() {
			return query.fetchAll();
		}
	}

	private static class SupportedQueryExtension<H> implements SearchQueryExtension<QueryWrapper<H>, H> {
		@Override
		public Optional<QueryWrapper<H>> extendOptional(SearchQuery<H> original,
				SearchLoadingContext<?> loadingContext) {
			assertThat( original ).isNotNull();
			assertThat( loadingContext ).isNotNull().isInstanceOf( StubSearchLoadingContext.class );
			return Optional.of( new QueryWrapper<>( original ) );
		}
	}

	private static class UnSupportedQueryExtension<H> implements SearchQueryExtension<QueryWrapper<H>, H> {
		@Override
		public Optional<QueryWrapper<H>> extendOptional(SearchQuery<H> original,
				SearchLoadingContext<?> loadingContext) {
			assertThat( original ).isNotNull();
			assertThat( loadingContext ).isNotNull().isInstanceOf( StubSearchLoadingContext.class );
			return Optional.empty();
		}
	}

	private static class SupportedQueryDslExtension<SR, R, E, LOS>
			implements
			SearchQueryDslExtension<SR, MyExtendedDslContext<SR, E>, R, E, LOS> {
		@Override
		public Optional<MyExtendedDslContext<SR, E>> extendOptional(SearchQuerySelectStep<SR, ?, R, E, LOS, ?, ?> original,
				SearchQueryIndexScope<?> scope, BackendSessionContext sessionContext,
				SearchLoadingContextBuilder<E, LOS> loadingContextBuilder) {
			assertThat( original ).isNotNull();
			assertThat( scope ).isNotNull();
			assertThat( sessionContext ).isNotNull();
			assertThat( loadingContextBuilder ).isNotNull();
			return Optional.of( new MyExtendedDslContext<SR, E>( original.selectEntity() ) );
		}
	}

	private static class UnSupportedQueryDslExtension<SR, R, E, LOS>
			implements
			SearchQueryDslExtension<SR, MyExtendedDslContext<SR, E>, R, E, LOS> {
		@Override
		public Optional<MyExtendedDslContext<SR, E>> extendOptional(SearchQuerySelectStep<SR, ?, R, E, LOS, ?, ?> original,
				SearchQueryIndexScope<?> scope, BackendSessionContext sessionContext,
				SearchLoadingContextBuilder<E, LOS> loadingContextBuilder) {
			assertThat( original ).isNotNull();
			assertThat( scope ).isNotNull();
			assertThat( sessionContext ).isNotNull();
			assertThat( loadingContextBuilder ).isNotNull();
			return Optional.empty();
		}
	}

	private static class MyExtendedDslContext<SR, T> {
		private final SearchQueryWhereStep<SR, ?, T, ?, ?> delegate;

		MyExtendedDslContext(SearchQueryWhereStep<SR, ?, T, ?, ?> delegate) {
			this.delegate = delegate;
		}

		public SearchQuery<T> extendedFeature(String fieldName, String value1, String value2) {
			return delegate.where( f -> f.or(
					f.match().field( fieldName ).matching( value1 ),
					f.match().field( fieldName ).matching( value2 )
			) )
					.sort( f -> f.field( fieldName ) )
					.toQuery();
		}
	}
}
