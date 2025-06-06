/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.search.query.spi;

import org.hibernate.search.engine.backend.session.spi.BackendSessionContext;
import org.hibernate.search.engine.search.aggregation.dsl.SearchAggregationFactory;
import org.hibernate.search.engine.search.aggregation.spi.SearchAggregationIndexScope;
import org.hibernate.search.engine.search.common.spi.SearchIndexScope;
import org.hibernate.search.engine.search.highlighter.dsl.SearchHighlighterFactory;
import org.hibernate.search.engine.search.loading.spi.SearchLoadingContextBuilder;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.engine.search.predicate.spi.SearchPredicateIndexScope;
import org.hibernate.search.engine.search.projection.SearchProjection;
import org.hibernate.search.engine.search.projection.dsl.SearchProjectionFactory;
import org.hibernate.search.engine.search.projection.spi.SearchProjectionIndexScope;
import org.hibernate.search.engine.search.sort.dsl.SearchSortFactory;
import org.hibernate.search.engine.search.sort.spi.SearchSortIndexScope;

public interface SearchQueryIndexScope<S extends SearchQueryIndexScope<?>>
		extends SearchIndexScope<S>, SearchPredicateIndexScope<S>, SearchSortIndexScope<S>,
		SearchProjectionIndexScope<S>, SearchAggregationIndexScope<S> {

	<P> SearchQueryBuilder<P> select(BackendSessionContext sessionContext,
			SearchLoadingContextBuilder<?, ?> loadingContextBuilder, SearchProjection<P> projection);

	<SR> SearchPredicateFactory<SR> predicateFactory();

	<SR> SearchSortFactory<SR> sortFactory();

	<SR, R, E> SearchProjectionFactory<SR, R, E> projectionFactory();

	<SR> SearchAggregationFactory<SR> aggregationFactory();

	SearchHighlighterFactory highlighterFactory();

}
