/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.elasticsearch.types.sort.impl;

import org.hibernate.search.backend.elasticsearch.gson.impl.JsonAccessor;
import org.hibernate.search.backend.elasticsearch.gson.impl.JsonObjectAccessor;
import org.hibernate.search.backend.elasticsearch.logging.impl.QueryLog;
import org.hibernate.search.backend.elasticsearch.search.common.impl.AbstractElasticsearchValueFieldSearchQueryElementFactory;
import org.hibernate.search.backend.elasticsearch.search.common.impl.ElasticsearchSearchIndexScope;
import org.hibernate.search.backend.elasticsearch.search.common.impl.ElasticsearchSearchIndexValueFieldContext;
import org.hibernate.search.backend.elasticsearch.search.sort.impl.ElasticsearchSearchSortCollector;
import org.hibernate.search.backend.elasticsearch.types.codec.impl.ElasticsearchFieldCodec;
import org.hibernate.search.engine.search.common.SortMode;
import org.hibernate.search.engine.search.sort.SearchSort;
import org.hibernate.search.engine.search.sort.dsl.SortOrder;
import org.hibernate.search.engine.search.sort.spi.DistanceSortBuilder;
import org.hibernate.search.engine.spatial.GeoPoint;

import com.google.gson.JsonObject;

public class ElasticsearchDistanceSort extends AbstractElasticsearchDocumentValueSort {

	private static final JsonObjectAccessor GEO_DISTANCE_ACCESSOR = JsonAccessor.root().property( "_geo_distance" ).asObject();

	private final GeoPoint center;
	private final ElasticsearchFieldCodec<GeoPoint> codec;

	private ElasticsearchDistanceSort(Builder builder) {
		super( builder );
		center = builder.center;
		codec = builder.field.type().codec();
	}

	@Override
	protected void doToJsonSorts(ElasticsearchSearchSortCollector collector, JsonObject innerObject) {
		innerObject.add( absoluteFieldPath, codec.encode( center ) );
		// If there are multiple target indexes, or if the field is dynamic,
		// some target indexes may not have this field in their mapping (yet),
		// and in that case Elasticsearch would raise an exception.
		// Instruct ES to behave as if the field had no value in that case.
		searchSyntax.requestGeoDistanceSortIgnoreUnmapped( innerObject );

		JsonObject outerObject = new JsonObject();
		GEO_DISTANCE_ACCESSOR.set( outerObject, innerObject );
		collector.collectDistanceSort( outerObject, absoluteFieldPath, center );
	}

	public static class Factory
			extends AbstractElasticsearchValueFieldSearchQueryElementFactory<DistanceSortBuilder, GeoPoint> {
		@Override
		public DistanceSortBuilder create(ElasticsearchSearchIndexScope<?> scope,
				ElasticsearchSearchIndexValueFieldContext<GeoPoint> field) {
			return new Builder( scope, field );
		}
	}

	private static class Builder extends AbstractBuilder<GeoPoint> implements DistanceSortBuilder {
		private GeoPoint center;

		private boolean missingFirst = false;
		private boolean missingLast = false;

		private Builder(ElasticsearchSearchIndexScope<?> scope, ElasticsearchSearchIndexValueFieldContext<GeoPoint> field) {
			super( scope, field );
		}

		@Override
		public void center(GeoPoint center) {
			this.center = center;
		}

		@Override
		public void missingFirst() {
			this.missingFirst = true;
		}

		@Override
		public void missingLast() {
			this.missingLast = true;
		}

		@Override
		public void missingHighest() {
			// we don't need to do anything: this is the default and only possible behavior with Elasticsearch.
		}

		@Override
		public void missingLowest() {
			throw SortOrder.DESC.equals( order )
					? QueryLog.INSTANCE.missingLowestOnDescSortNotSupported( field.eventContext() )
					: QueryLog.INSTANCE.missingLowestOnAscSortNotSupported( field.eventContext() );
		}

		@Override
		public void missingAs(GeoPoint value) {
			throw QueryLog.INSTANCE.missingAsOnSortNotSupported( field.eventContext() );
		}

		@Override
		public void mode(SortMode mode) {
			switch ( mode ) {
				case MIN:
				case MAX:
				case AVG:
				case MEDIAN:
					super.mode( mode );
					break;
				case SUM:
				default:
					throw QueryLog.INSTANCE.invalidSortModeForDistanceSort( mode, field.eventContext() );
			}
		}

		@Override
		public SearchSort build() {
			if ( missingFirst && ( order == null || SortOrder.ASC.equals( order ) ) ) {
				throw QueryLog.INSTANCE.missingFirstOnAscSortNotSupported( field.eventContext() );
			}
			if ( missingLast && SortOrder.DESC.equals( order ) ) {
				throw QueryLog.INSTANCE.missingLastOnDescSortNotSupported( field.eventContext() );
			}

			return new ElasticsearchDistanceSort( this );
		}
	}
}
