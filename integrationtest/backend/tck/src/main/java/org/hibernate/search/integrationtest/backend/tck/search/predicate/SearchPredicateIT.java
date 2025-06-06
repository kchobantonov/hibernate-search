/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.integrationtest.backend.tck.search.predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.search.util.impl.integrationtest.common.assertion.SearchResultAssert.assertThatQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.hibernate.search.engine.backend.common.DocumentReference;
import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.engine.backend.document.IndexObjectFieldReference;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaObjectField;
import org.hibernate.search.engine.backend.types.ObjectStructure;
import org.hibernate.search.engine.search.predicate.SearchPredicate;
import org.hibernate.search.engine.search.predicate.dsl.PredicateFinalStep;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactoryExtension;
import org.hibernate.search.engine.search.query.SearchQuery;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.extension.SearchSetupHelper;
import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.SimpleMappedIndex;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingScope;
import org.hibernate.search.util.impl.test.annotation.TestForIssue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.assertj.core.api.ThrowableAssert;

class SearchPredicateIT {

	private static final String DOCUMENT_1 = "doc1";
	private static final String DOCUMENT_2 = "doc2";
	private static final String EMPTY = "empty";

	private static final String STRING_1 = "Irving";
	private static final String STRING_2 = "Auster";

	@RegisterExtension
	public final SearchSetupHelper setupHelper = SearchSetupHelper.create();

	private final SimpleMappedIndex<IndexBinding> mainIndex =
			SimpleMappedIndex.of( IndexBinding::new ).name( "main" );
	private final SimpleMappedIndex<IndexBinding> otherIndex =
			// Using the same mapping here. But a different mapping would work the same.
			// What matters here is that is a different index.
			SimpleMappedIndex.of( IndexBinding::new ).name( "other" );
	private final SimpleMappedIndex<IndexBinding> anotherIndex =
			// Using the same mapping here. But a different mapping would work the same.
			// What matters here is that is a different index.
			SimpleMappedIndex.of( IndexBinding::new ).name( "another" );

	@BeforeEach
	void setup() {
		setupHelper.start().withIndexes( mainIndex, otherIndex, anotherIndex ).setup();

		initData();
	}

	@Test
	void where_searchPredicate() {
		StubMappingScope scope = mainIndex.createScope();

		SearchPredicate predicate = scope.predicate().match().field( "string" ).matching( STRING_1 ).toPredicate();

		SearchQuery<DocumentReference> query = scope.query()
				.where( predicate )
				.toQuery();

		assertThatQuery( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );
	}

	@Test
	void where_lambda() {
		StubMappingScope scope = mainIndex.createScope();

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.match().field( "string" ).matching( STRING_1 ) )
				.toQuery();

		assertThatQuery( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );
	}

	@Test
	void reuseRootPredicateInstance_onScopeTargetingSameIndexes() {
		StubMappingScope scope = mainIndex.createScope();
		SearchPredicate predicate = scope
				.predicate().match().field( "string" ).matching( STRING_1 ).toPredicate();

		SearchQuery<DocumentReference> query = scope.query()
				.where( predicate )
				.toQuery();

		assertThatQuery( query ).hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );

		// reuse the same predicate instance on the same scope
		query = scope.query()
				.where( predicate )
				.toQuery();

		assertThatQuery( query ).hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );

		// reuse the same predicate instance on a different scope,
		// targeting the same index
		query = mainIndex.createScope().query()
				.where( predicate )
				.toQuery();

		assertThatQuery( query ).hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );

		predicate = mainIndex.createScope( otherIndex )
				.predicate().match().field( "string" ).matching( STRING_1 ).toPredicate();

		// reuse the same predicate instance on a different scope,
		// targeting same indexes
		query = otherIndex.createScope( mainIndex ).query()
				.where( predicate )
				.toQuery();

		assertThatQuery( query ).hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );
	}

	@Test
	void reuseRootPredicateInstance_onScopeTargetingDifferentIndexes() {
		StubMappingScope scope = mainIndex.createScope();
		SearchPredicate predicate = scope
				.predicate().match().field( "string" ).matching( STRING_1 ).toPredicate();

		// reuse the same predicate instance on a different scope,
		// targeting a different index
		assertFailScope( () -> mainIndex.createScope( otherIndex ).query()
				.where( predicate )
				.toQuery(),
				Set.of( otherIndex.name() ),
				Set.of( mainIndex.name() ),
				Set.of( otherIndex.name() )
		);

		// reuse the same predicate instance on a different scope,
		// targeting different indexes
		assertFailScope( () -> mainIndex.createScope( otherIndex ).query()
				.where( predicate )
				.toQuery(),
				Set.of( mainIndex.name(), otherIndex.name() ),
				Set.of( mainIndex.name() ),
				Set.of( otherIndex.name() )
		);
		assertFailScope( () -> otherIndex.createScope( mainIndex ).query()
				.where( predicate )
				.toQuery(),
				Set.of( mainIndex.name(), otherIndex.name() ),
				Set.of( mainIndex.name() ),
				Set.of( otherIndex.name() )
		);

		scope = mainIndex.createScope( otherIndex );
		SearchPredicate predicate2 = scope
				.predicate().match().field( "string" ).matching( STRING_1 ).toPredicate();

		assertThatCode( () -> mainIndex.createScope( otherIndex ).query()
				.where( predicate2 )
				.toQuery() )
				.doesNotThrowAnyException();

		assertFailScope( () -> otherIndex.createScope( anotherIndex ).query()
				.where( predicate2 )
				.toQuery(),
				Set.of( otherIndex.name(), anotherIndex.name() ),
				Set.of( mainIndex.name(), otherIndex.name() ),
				Set.of( mainIndex.name() )
		);

		assertFailScope( () -> mainIndex.createScope( anotherIndex ).query()
				.where( predicate2 )
				.toQuery(),
				Set.of( mainIndex.name(), anotherIndex.name() ),
				Set.of( mainIndex.name(), otherIndex.name() ),
				Set.of( otherIndex.name() )
		);

		scope = mainIndex.createScope( otherIndex, anotherIndex );
		SearchPredicate predicate3 = scope
				.predicate().match().field( "string" ).matching( STRING_1 ).toPredicate();
		assertThatCode( () -> mainIndex.createScope( otherIndex ).query()
				.where( predicate3 )
				.toQuery() )
				.doesNotThrowAnyException();
		assertThatCode( () -> otherIndex.createScope().query()
				.where( predicate3 )
				.toQuery() )
				.doesNotThrowAnyException();
		assertThatCode( () -> anotherIndex.createScope().query()
				.where( predicate3 )
				.toQuery() )
				.doesNotThrowAnyException();

		assertThatCode( () -> otherIndex.createScope( mainIndex ).query()
				.where( predicate3 )
				.toQuery() )
				.doesNotThrowAnyException();
		assertThatCode( () -> anotherIndex.createScope( mainIndex ).query()
				.where( predicate3 )
				.toQuery() )
				.doesNotThrowAnyException();
		assertThatCode( () -> anotherIndex.createScope( otherIndex ).query()
				.where( predicate3 )
				.toQuery() )
				.doesNotThrowAnyException();
	}

	private static void assertFailScope(ThrowableAssert.ThrowingCallable query, Set<String> scope,
			Set<String> predicate, Set<String> differences) {
		List<String> messageParts = new ArrayList<>();
		messageParts.add( "Invalid search predicate" );
		messageParts.add( "You must build the predicate from a scope targeting indexes " );
		messageParts.addAll( scope );
		messageParts.add( "the given predicate was built from a scope targeting " );
		messageParts.addAll( predicate );
		messageParts.add( "where indexes [" );
		messageParts.addAll( differences );
		messageParts.add( "] are missing" );

		assertThatThrownBy( query )
				.isInstanceOf( SearchException.class )
				.hasMessageContainingAll( messageParts.toArray( String[]::new ) );
	}

	@Test
	void reuseNonRootPredicateInstance_onScopeTargetingSameIndexes() {
		StubMappingScope scope = mainIndex.createScope();
		final SearchPredicate predicate = scope
				.predicate().match().field( "string" ).matching( STRING_1 ).toPredicate();

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.bool().must( predicate ) )
				.toQuery();

		assertThatQuery( query ).hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );

		// reuse the same predicate instance on the same scope
		query = scope.query()
				.where( f -> f.bool().must( predicate ) )
				.toQuery();

		assertThatQuery( query ).hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );

		// reuse the same predicate instance on a different scope,
		// targeting the same index
		query = mainIndex.createScope().query()
				.where( f -> f.bool().must( predicate ) )
				.toQuery();

		assertThatQuery( query ).hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );

		final SearchPredicate multiIndexScopedPredicate = mainIndex.createScope( otherIndex )
				.predicate().match().field( "string" ).matching( STRING_1 ).toPredicate();

		// reuse the same predicate instance on a different scope,
		// targeting same indexes
		query = otherIndex.createScope( mainIndex ).query()
				.where( f -> f.bool().must( multiIndexScopedPredicate ) )
				.toQuery();

		assertThatQuery( query ).hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );

		query = otherIndex.createScope( mainIndex ).query()
				.where( f -> f.or(
						multiIndexScopedPredicate,
						f.match().field( "string" ).matching( STRING_2 ).toPredicate()
				) )
				.toQuery();

		assertThatQuery( query ).hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1, DOCUMENT_2 );
	}

	@Test
	void reuseNonRootPredicateInstance_onScopeTargetingDifferentIndexes() {
		StubMappingScope scope = mainIndex.createScope();
		SearchPredicate predicate = scope
				.predicate().match().field( "string" ).matching( STRING_1 ).toPredicate();

		// reuse the same predicate instance on a different scope,
		// targeting a different index
		assertThatThrownBy( () -> otherIndex.createScope().query()
				.where( f -> f.bool().must( predicate ) )
				.toQuery() )
				.isInstanceOf( SearchException.class )
				.hasMessageContainingAll( "Invalid search predicate",
						"You must build the predicate from a scope targeting indexes ", otherIndex.name(),
						"the given predicate was built from a scope targeting ", mainIndex.name() );

		// reuse the same predicate instance on a different scope,
		// targeting different indexes
		assertThatThrownBy( () -> mainIndex.createScope( otherIndex ).query()
				.where( f -> f.bool().must( predicate ) )
				.toQuery() )
				.isInstanceOf( SearchException.class )
				.hasMessageContainingAll( "Invalid search predicate",
						"You must build the predicate from a scope targeting indexes ",
						mainIndex.name(), otherIndex.name(),
						"the given predicate was built from a scope targeting ", mainIndex.name() );
	}

	@Test
	void extension() {
		StubMappingScope scope = mainIndex.createScope();
		SearchQuery<DocumentReference> query;

		// Mandatory extension, supported
		query = scope.query()
				.where( f -> f.extension( new SupportedExtension<>() )
						.extendedPredicate( "string", STRING_1 )
				)
				.toQuery();
		assertThatQuery( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );

		// Mandatory extension, unsupported
		assertThatThrownBy(
				() -> scope.predicate().extension( new UnSupportedExtension<>() )
		)
				.isInstanceOf( SearchException.class );

		// Conditional extensions with orElse - two, both supported
		query = scope.query()
				.where( f -> f.extension()
						.ifSupported(
								new SupportedExtension<>(),
								extended -> extended.extendedPredicate( "string", STRING_1 )
						)
						.ifSupported(
								new SupportedExtension<>(),
								shouldNotBeCalled()
						)
						.orElseFail()
				)
				.toQuery();
		assertThatQuery( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );

		// Conditional extensions with orElse - two, second supported
		query = scope.query()
				.where( f -> f.extension()
						.ifSupported(
								new UnSupportedExtension<>(),
								shouldNotBeCalled()
						)
						.ifSupported(
								new SupportedExtension<>(),
								extended -> extended.extendedPredicate( "string", STRING_1 )
						)
						.orElse(
								shouldNotBeCalled()
						)
				)
				.toQuery();
		assertThatQuery( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );

		// Conditional extensions with orElse - two, both unsupported
		query = scope.query()
				.where( f -> f.extension()
						.ifSupported(
								new UnSupportedExtension<>(),
								shouldNotBeCalled()
						)
						.ifSupported(
								new UnSupportedExtension<>(),
								shouldNotBeCalled()
						)
						.orElse(
								c -> c.match().field( "string" ).matching( STRING_1 )
						)
				)
				.toQuery();
		assertThatQuery( query )
				.hasDocRefHitsAnyOrder( mainIndex.typeName(), DOCUMENT_1 );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-4162")
	void toAbsolutePath() {
		assertThat( mainIndex.createScope().predicate().toAbsolutePath( "string" ) )
				.isEqualTo( "string" );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-4162")
	void toAbsolutePath_withRoot() {
		assertThat( mainIndex.createScope().predicate().withRoot( "flattened" ).toAbsolutePath( "string" ) )
				.isEqualTo( "flattened.string" );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-4162")
	void toAbsolutePath_null() {
		assertThatThrownBy( () -> mainIndex.createScope().predicate().toAbsolutePath( null ) )
				.isInstanceOf( IllegalArgumentException.class )
				.hasMessageContaining( "'relativeFieldPath' must not be null" );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-4162")
	void toAbsolutePath_withRoot_null() {
		assertThatThrownBy( () -> mainIndex.createScope().predicate().withRoot( "flattened" ).toAbsolutePath( null ) )
				.isInstanceOf( IllegalArgumentException.class )
				.hasMessageContaining( "'relativeFieldPath' must not be null" );
	}

	private void initData() {
		mainIndex.bulkIndexer()
				.add( DOCUMENT_1, document -> {
					document.addValue( mainIndex.binding().string, STRING_1 );
				} )
				.add( DOCUMENT_2, document -> {
					document.addValue( mainIndex.binding().string, STRING_2 );
				} )
				.add( EMPTY, document -> {} )
				.join();
	}

	private static <T, R> Function<T, R> shouldNotBeCalled() {
		return ignored -> {
			throw new IllegalStateException( "This should not be called" );
		};
	}

	private static class IndexBinding {
		final IndexFieldReference<String> string;
		final ObjectFieldBinding flattened;

		IndexBinding(IndexSchemaElement root) {
			string = root.field( "string", f -> f.asString() ).toReference();
			flattened = new ObjectFieldBinding( root.objectField( "flattened", ObjectStructure.FLATTENED ) );
		}
	}

	private static class ObjectFieldBinding {
		final IndexObjectFieldReference self;
		final IndexFieldReference<String> string;

		ObjectFieldBinding(IndexSchemaObjectField objectField) {
			string = objectField.field( "string", f -> f.asString() ).toReference();
			self = objectField.toReference();
		}
	}

	private static class SupportedExtension<SR> implements SearchPredicateFactoryExtension<SR, MyExtendedFactory<SR>> {
		@Override
		public Optional<MyExtendedFactory<SR>> extendOptional(SearchPredicateFactory<SR> original) {
			assertThat( original ).isNotNull();
			return Optional.of( new MyExtendedFactory<>( original ) );
		}
	}

	private static class UnSupportedExtension<SR> implements SearchPredicateFactoryExtension<SR, MyExtendedFactory<SR>> {
		@Override
		public Optional<MyExtendedFactory<SR>> extendOptional(SearchPredicateFactory<SR> original) {
			assertThat( original ).isNotNull();
			return Optional.empty();
		}
	}

	private static class MyExtendedFactory<SR> {
		private final SearchPredicateFactory<SR> delegate;

		MyExtendedFactory(SearchPredicateFactory<SR> delegate) {
			this.delegate = delegate;
		}

		public PredicateFinalStep extendedPredicate(String fieldName, String value) {
			return delegate.match().field( fieldName ).matching( value );
		}
	}
}
