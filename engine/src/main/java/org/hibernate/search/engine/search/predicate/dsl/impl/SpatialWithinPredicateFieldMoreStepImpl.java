/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.search.predicate.dsl.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.hibernate.search.engine.search.common.spi.SearchIndexScope;
import org.hibernate.search.engine.search.predicate.SearchPredicate;
import org.hibernate.search.engine.search.predicate.dsl.SpatialWithinPredicateFieldMoreStep;
import org.hibernate.search.engine.search.predicate.dsl.SpatialWithinPredicateOptionsStep;
import org.hibernate.search.engine.search.predicate.dsl.spi.SearchPredicateDslContext;
import org.hibernate.search.engine.search.predicate.spi.PredicateTypeKeys;
import org.hibernate.search.engine.search.predicate.spi.SearchPredicateBuilder;
import org.hibernate.search.engine.search.predicate.spi.SpatialWithinBoundingBoxPredicateBuilder;
import org.hibernate.search.engine.search.predicate.spi.SpatialWithinCirclePredicateBuilder;
import org.hibernate.search.engine.search.predicate.spi.SpatialWithinPolygonPredicateBuilder;
import org.hibernate.search.engine.search.reference.predicate.SpatialPredicateFieldReference;
import org.hibernate.search.engine.spatial.DistanceUnit;
import org.hibernate.search.engine.spatial.GeoBoundingBox;
import org.hibernate.search.engine.spatial.GeoPoint;
import org.hibernate.search.engine.spatial.GeoPolygon;
import org.hibernate.search.util.common.impl.CollectionHelper;
import org.hibernate.search.util.common.impl.Contracts;

class SpatialWithinPredicateFieldMoreStepImpl<SR>
		implements
		SpatialWithinPredicateFieldMoreStep<SR,
				SpatialWithinPredicateFieldMoreStepImpl<SR>,
				SpatialWithinPredicateOptionsStep<?>>,
		AbstractBooleanMultiFieldPredicateCommonState.FieldSetState {

	private final CommonState<SR> commonState;

	private final List<String> fieldPaths;

	private final List<SearchPredicateBuilder> predicateBuilders;

	private Float fieldSetBoost;

	SpatialWithinPredicateFieldMoreStepImpl(CommonState<SR> commonState, List<String> fieldPaths) {
		this.commonState = commonState;
		this.commonState.add( this );
		this.fieldPaths = CollectionHelper.toImmutableList( fieldPaths );
		this.predicateBuilders = new ArrayList<>( fieldPaths.size() );
	}

	@Override
	public SpatialWithinPredicateFieldMoreStepImpl<SR> fields(String... fieldPaths) {
		return new SpatialWithinPredicateFieldMoreStepImpl<>( commonState, Arrays.asList( fieldPaths ) );
	}

	@SuppressWarnings("unchecked")
	@Override
	public SpatialWithinPredicateFieldMoreStepImpl<SR> fields(SpatialPredicateFieldReference<? super SR>... fieldReferences) {
		List<String> fieldPaths = new ArrayList<>( fieldReferences.length );
		for ( SpatialPredicateFieldReference<? super SR> fieldReference : fieldReferences ) {
			fieldPaths.add( fieldReference.absolutePath() );
		}
		return new SpatialWithinPredicateFieldMoreStepImpl<>( commonState, fieldPaths );
	}

	@Override
	public SpatialWithinPredicateFieldMoreStepImpl<SR> boost(float boost) {
		this.fieldSetBoost = boost;
		return this;
	}

	@Override
	public SpatialWithinPredicateOptionsStep<?> circle(GeoPoint center, double radius, DistanceUnit unit) {
		Contracts.assertNotNull( center, "center" );
		Contracts.assertNotNull( radius, "radius" );
		Contracts.assertNotNull( unit, "unit" );

		return commonState.circle( center, radius, unit );
	}

	@Override
	public SpatialWithinPredicateOptionsStep<?> polygon(GeoPolygon polygon) {
		Contracts.assertNotNull( polygon, "polygon" );

		return commonState.polygon( polygon );
	}

	@Override
	public SpatialWithinPredicateOptionsStep<?> boundingBox(GeoBoundingBox boundingBox) {
		Contracts.assertNotNull( boundingBox, "boundingBox" );

		return commonState.boundingBox( boundingBox );
	}

	@Override
	public void contributePredicates(Consumer<SearchPredicate> collector) {
		for ( SearchPredicateBuilder predicateBuilder : predicateBuilders ) {
			// Perform last-minute changes, since it's the last call that will be made on this field set state
			commonState.applyBoostAndConstantScore( fieldSetBoost, predicateBuilder );

			collector.accept( predicateBuilder.build() );
		}
	}

	private void generateWithinCircleQueryBuilders(GeoPoint center, double radius, DistanceUnit unit) {
		SearchIndexScope<?> scope = commonState.scope();
		for ( String fieldPath : fieldPaths ) {
			SpatialWithinCirclePredicateBuilder predicateBuilder =
					scope.fieldQueryElement( fieldPath, PredicateTypeKeys.SPATIAL_WITHIN_CIRCLE );
			predicateBuilder.circle( center, radius, unit );
			predicateBuilders.add( predicateBuilder );
		}
	}

	private void generateWithinPolygonQueryBuilders(GeoPolygon polygon) {
		SearchIndexScope<?> scope = commonState.scope();
		for ( String fieldPath : fieldPaths ) {
			SpatialWithinPolygonPredicateBuilder predicateBuilder =
					scope.fieldQueryElement( fieldPath, PredicateTypeKeys.SPATIAL_WITHIN_POLYGON );
			predicateBuilder.polygon( polygon );
			predicateBuilders.add( predicateBuilder );
		}
	}

	private void generateWithinBoundingBoxQueryBuilders(GeoBoundingBox boundingBox) {
		SearchIndexScope<?> scope = commonState.scope();
		for ( String fieldPath : fieldPaths ) {
			SpatialWithinBoundingBoxPredicateBuilder predicateBuilder =
					scope.fieldQueryElement( fieldPath, PredicateTypeKeys.SPATIAL_WITHIN_BOUNDING_BOX );
			predicateBuilder.boundingBox( boundingBox );
			predicateBuilders.add( predicateBuilder );
		}
	}

	static class CommonState<SR>
			extends AbstractBooleanMultiFieldPredicateCommonState<CommonState<SR>, SpatialWithinPredicateFieldMoreStepImpl<SR>>
			implements SpatialWithinPredicateOptionsStep<CommonState<SR>> {

		CommonState(SearchPredicateDslContext<?> dslContext) {
			super( dslContext );
		}

		SpatialWithinPredicateOptionsStep<?> circle(GeoPoint center, double radius, DistanceUnit unit) {
			for ( SpatialWithinPredicateFieldMoreStepImpl<SR> fieldSetState : getFieldSetStates() ) {
				fieldSetState.generateWithinCircleQueryBuilders( center, radius, unit );
			}

			return this;
		}

		SpatialWithinPredicateOptionsStep<?> polygon(GeoPolygon polygon) {
			for ( SpatialWithinPredicateFieldMoreStepImpl<SR> fieldSetState : getFieldSetStates() ) {
				fieldSetState.generateWithinPolygonQueryBuilders( polygon );
			}

			return this;
		}

		SpatialWithinPredicateOptionsStep<?> boundingBox(GeoBoundingBox boundingBox) {
			for ( SpatialWithinPredicateFieldMoreStepImpl<SR> fieldSetState : getFieldSetStates() ) {
				fieldSetState.generateWithinBoundingBoxQueryBuilders( boundingBox );
			}

			return this;
		}

		@Override
		protected CommonState<SR> thisAsS() {
			return this;
		}
	}
}
