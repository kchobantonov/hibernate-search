/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.elasticsearch.search.projection.impl;

import java.util.Arrays;

import org.hibernate.search.backend.elasticsearch.search.common.impl.ElasticsearchSearchIndexScope;
import org.hibernate.search.engine.search.loading.spi.LoadingResult;
import org.hibernate.search.engine.search.loading.spi.ProjectionHitMapper;
import org.hibernate.search.engine.search.projection.SearchProjection;
import org.hibernate.search.engine.search.projection.spi.ProjectionCompositor;
import org.hibernate.search.engine.search.projection.spi.CompositeProjectionBuilder;

import com.google.gson.JsonObject;

class ElasticsearchCompositeProjection<E, V>
		extends AbstractElasticsearchProjection<E, V> {

	private final ProjectionCompositor<E, V> compositor;
	private final ElasticsearchSearchProjection<?, ?>[] inners;

	private ElasticsearchCompositeProjection(Builder<E, V> builder) {
		super( builder.scope );
		this.compositor = builder.compositor;
		this.inners = builder.inners;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "["
				+ "inners=" + Arrays.toString( inners )
				+ ", compositor=" + compositor
				+ "]";
	}

	@Override
	public void request(JsonObject requestBody, ProjectionRequestContext context) {
		for ( ElasticsearchSearchProjection<?, ?> inner : inners ) {
			inner.request( requestBody, context );
		}
	}

	@Override
	public E extract(ProjectionHitMapper<?, ?> projectionHitMapper, JsonObject hit,
			ProjectionExtractContext context) {
		E extractedData = compositor.createInitial();

		for ( int i = 0; i < inners.length; i++ ) {
			Object extractedDataForInner = inners[i].extract( projectionHitMapper, hit, context );
			extractedData = compositor.set( extractedData, i, extractedDataForInner );
		}

		return extractedData;
	}

	@Override
	public final V transform(LoadingResult<?, ?> loadingResult, E extractedData,
			ProjectionTransformContext context) {
		E transformedData = extractedData;
		// Transform in-place
		for ( int i = 0; i < inners.length; i++ ) {
			Object extractedDataForInner = compositor.get( transformedData, i );
			Object transformedDataForInner = ElasticsearchSearchProjection.transformUnsafe( inners[i], loadingResult,
					extractedDataForInner, context );
			transformedData = compositor.set( transformedData, i, transformedDataForInner );
		}

		return compositor.finish( transformedData );
	}

	static class Builder<E, V> implements CompositeProjectionBuilder<V> {

		private final ElasticsearchSearchIndexScope<?> scope;
		private final ProjectionCompositor<E, V> compositor;
		private final ElasticsearchSearchProjection<?, ?>[] inners;

		Builder(ElasticsearchSearchIndexScope<?> scope, ProjectionCompositor<E, V> compositor,
				ElasticsearchSearchProjection<?, ?> ... inners) {
			this.scope = scope;
			this.compositor = compositor;
			this.inners = inners;
		}

		@Override
		public SearchProjection<V> build() {
			return new ElasticsearchCompositeProjection<>( this );
		}
	}
}