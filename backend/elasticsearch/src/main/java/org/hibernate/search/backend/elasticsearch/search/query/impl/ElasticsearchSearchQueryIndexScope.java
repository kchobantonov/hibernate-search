/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.elasticsearch.search.query.impl;

import org.hibernate.search.backend.elasticsearch.search.aggregation.dsl.ElasticsearchSearchAggregationFactory;
import org.hibernate.search.backend.elasticsearch.search.aggregation.impl.ElasticsearchSearchAggregationIndexScope;
import org.hibernate.search.backend.elasticsearch.search.predicate.dsl.ElasticsearchSearchPredicateFactory;
import org.hibernate.search.backend.elasticsearch.search.predicate.impl.ElasticsearchSearchPredicateIndexScope;
import org.hibernate.search.backend.elasticsearch.search.projection.dsl.ElasticsearchSearchProjectionFactory;
import org.hibernate.search.backend.elasticsearch.search.projection.impl.ElasticsearchSearchProjectionIndexScope;
import org.hibernate.search.backend.elasticsearch.search.sort.dsl.ElasticsearchSearchSortFactory;
import org.hibernate.search.backend.elasticsearch.search.sort.impl.ElasticsearchSearchSortIndexScope;
import org.hibernate.search.engine.backend.session.spi.BackendSessionContext;
import org.hibernate.search.engine.search.loading.spi.SearchLoadingContextBuilder;
import org.hibernate.search.engine.search.projection.SearchProjection;
import org.hibernate.search.engine.search.query.spi.SearchQueryIndexScope;

public interface ElasticsearchSearchQueryIndexScope<S extends ElasticsearchSearchQueryIndexScope<?>>
		extends SearchQueryIndexScope<S>,
		ElasticsearchSearchPredicateIndexScope<S>, ElasticsearchSearchSortIndexScope<S>,
		ElasticsearchSearchProjectionIndexScope<S>, ElasticsearchSearchAggregationIndexScope<S> {

	@Override
	<P> ElasticsearchSearchQueryBuilder<P> select(BackendSessionContext sessionContext,
			SearchLoadingContextBuilder<?, ?> loadingContextBuilder, SearchProjection<P> projection);

	@Override
	<SR> ElasticsearchSearchPredicateFactory<SR> predicateFactory();

	@Override
	<SR> ElasticsearchSearchSortFactory<SR> sortFactory();

	@Override
	<SR, R, E> ElasticsearchSearchProjectionFactory<SR, R, E> projectionFactory();

	@Override
	<SR> ElasticsearchSearchAggregationFactory<SR> aggregationFactory();

}
