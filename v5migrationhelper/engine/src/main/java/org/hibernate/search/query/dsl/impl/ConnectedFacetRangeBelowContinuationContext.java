/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.query.dsl.impl;

import org.hibernate.search.query.dsl.FacetRangeAboveContext;
import org.hibernate.search.query.dsl.FacetRangeBelowContinuationContext;
import org.hibernate.search.query.dsl.FacetRangeLimitContext;
import org.hibernate.search.query.facet.FacetingRequest;

/**
 * @author Hardy Ferentschik
 */
public class ConnectedFacetRangeBelowContinuationContext<T> extends ConnectedFacetParameterContext
		implements FacetRangeBelowContinuationContext<T> {
	private final FacetBuildingContext context;

	public ConnectedFacetRangeBelowContinuationContext(FacetBuildingContext context) {
		super( context );
		this.context = context;
	}

	@Override
	public FacetRangeBelowContinuationContext<T> excludeLimit() {
		context.setIncludeRangeEnd( false );
		return this;
	}

	@Override
	public FacetRangeAboveContext<T> above(T max) {
		context.makeRange();
		context.setRangeStart( max );
		context.setRangeEnd( null );
		return new ConnectedFacetRangeAboveContext<T>( context );
	}

	@Override
	public FacetingRequest createFacetingRequest() {
		context.makeRange();
		return context.getFacetingRequest();
	}

	@Override
	public FacetRangeLimitContext<T> from(T rangeStart) {
		context.makeRange();
		context.setRangeStart( rangeStart );
		return new ConnectedFacetRangeLimitContext<T>( context );
	}
}
