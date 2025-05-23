/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.elasticsearch.search.aggregation.dsl.impl;

import org.hibernate.search.backend.elasticsearch.search.aggregation.dsl.ElasticsearchSearchAggregationFactory;
import org.hibernate.search.backend.elasticsearch.search.aggregation.impl.ElasticsearchSearchAggregationIndexScope;
import org.hibernate.search.backend.elasticsearch.search.predicate.dsl.ElasticsearchSearchPredicateFactory;
import org.hibernate.search.engine.search.aggregation.dsl.AggregationFinalStep;
import org.hibernate.search.engine.search.aggregation.dsl.spi.AbstractSearchAggregationFactory;
import org.hibernate.search.engine.search.aggregation.dsl.spi.SearchAggregationDslContext;

import com.google.gson.JsonObject;

public class ElasticsearchSearchAggregationFactoryImpl<SR>
		extends AbstractSearchAggregationFactory<
				SR,
				ElasticsearchSearchAggregationFactory<SR>,
				ElasticsearchSearchAggregationIndexScope<?>,
				ElasticsearchSearchPredicateFactory<SR>>
		implements ElasticsearchSearchAggregationFactory<SR> {

	public ElasticsearchSearchAggregationFactoryImpl(
			SearchAggregationDslContext<SR,
					ElasticsearchSearchAggregationIndexScope<?>,
					ElasticsearchSearchPredicateFactory<SR>> dslContext) {
		super( dslContext );
	}

	@Override
	public ElasticsearchSearchAggregationFactory<SR> withRoot(String objectFieldPath) {
		return new ElasticsearchSearchAggregationFactoryImpl<>( dslContext.rescope(
				dslContext.scope().withRoot( objectFieldPath ),
				dslContext.predicateFactory().withRoot( objectFieldPath ) ) );
	}

	@Override
	public AggregationFinalStep<JsonObject> fromJson(JsonObject jsonObject) {
		return new ElasticsearchJsonAggregationFinalStep(
				dslContext.scope().aggregationBuilders().fromJson( jsonObject )
		);
	}

	@Override
	public AggregationFinalStep<JsonObject> fromJson(String jsonString) {
		return new ElasticsearchJsonAggregationFinalStep(
				dslContext.scope().aggregationBuilders().fromJson( jsonString )
		);
	}

	// TODO HSEARCH-3661 implement extensions to the aggregation DSL for Elasticsearch

}
