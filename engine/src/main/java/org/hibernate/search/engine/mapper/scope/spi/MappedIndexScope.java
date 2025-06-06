/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.mapper.scope.spi;

import org.hibernate.search.engine.backend.scope.IndexScopeExtension;
import org.hibernate.search.engine.backend.session.spi.BackendSessionContext;
import org.hibernate.search.engine.search.aggregation.dsl.SearchAggregationFactory;
import org.hibernate.search.engine.search.highlighter.dsl.SearchHighlighterFactory;
import org.hibernate.search.engine.search.loading.spi.SearchLoadingContextBuilder;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.engine.search.projection.dsl.SearchProjectionFactory;
import org.hibernate.search.engine.search.query.dsl.SearchQuerySelectStep;
import org.hibernate.search.engine.search.sort.dsl.SearchSortFactory;

/**
 * @param <SR> Scope root type.
 * @param <R> The type of entity references, i.e. the type of hits returned by
 * {@link SearchQuerySelectStep#selectEntityReference() reference queries},
 * or the type of objects returned for {@link SearchProjectionFactory#entityReference() entity reference projections}.
 * @param <E> The type of entities, i.e. the type of hits returned by
 * {@link SearchQuerySelectStep#selectEntity() entity queries}
 * or the type of objects returned for {@link SearchProjectionFactory#entity() entity projections}.
 */
public interface MappedIndexScope<SR, R, E> {

	/*
	 * IMPLEMENTATION NOTE: we *must* only accept a loading context with the same R/E type parameters as this class,
	 * otherwise some casts in EntityProjectionOptionsStepImpl and EntityReferenceProjectionOptionsStepImpl
	 * will be wrong.
	 * In particular, we cannot accept a LoadingContextBuilder<R, T> with any T.
	 */
	<LOS> SearchQuerySelectStep<SR, ?, R, E, LOS, SearchProjectionFactory<SR, R, E>, ?> search(
			BackendSessionContext sessionContext,
			SearchLoadingContextBuilder<E, LOS> loadingContextBuilder);

	SearchPredicateFactory<SR> predicate();

	SearchSortFactory<SR> sort();

	/*
	 * IMPLEMENTATION NOTE: we *must* return a factory with the same R/E type arguments as this class,
	 * otherwise some casts in EntityProjectionOptionsStepImpl and EntityReferenceProjectionOptionsStepImpl
	 * will be wrong.
	 */
	SearchProjectionFactory<SR, R, E> projection();

	SearchAggregationFactory<SR> aggregation();

	SearchHighlighterFactory highlighter();

	<T> T extension(IndexScopeExtension<T> extension);
}
