/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.search.predicate.dsl;


import org.hibernate.search.engine.search.reference.predicate.SpatialPredicateFieldReference;

/**
 * The initial step in a "within" predicate definition, where the target field can be set.
 *
 * @param <N> The type of the next step.
 */
public interface SpatialWithinPredicateFieldStep<SR, N extends SpatialWithinPredicateFieldMoreStep<SR, ?, ?>> {

	/**
	 * Target the given field in the "within" predicate.
	 * <p>
	 * Multiple fields may be targeted by the same predicate:
	 * the predicate will match if <em>any</em> targeted field matches.
	 *
	 * @param fieldPath The <a href="SearchPredicateFactory.html#field-paths">path</a> to the index field
	 * to apply the predicate on.
	 * @return The next step.
	 */
	default N field(String fieldPath) {
		return fields( fieldPath );
	}

	/**
	 * Target the given fields in the "within" predicate.
	 * <p>
	 * Equivalent to {@link #field(String)} followed by multiple calls to
	 * {@link RangePredicateFieldMoreStep#field(String)},
	 * the only difference being that calls to {@link RangePredicateFieldMoreStep#boost(float)}
	 * and other field-specific settings on the returned step will only need to be done once
	 * and will apply to all the fields passed to this method.
	 *
	 * @param fieldPaths The <a href="SearchPredicateFactory.html#field-paths">paths</a> to the index fields
	 * to apply the predicate on.
	 * @return The next step.
	 *
	 * @see #field(String)
	 */
	N fields(String... fieldPaths);

	/**
	 * Target the given field in the "within" predicate.
	 * <p>
	 * Multiple fields may be targeted by the same predicate:
	 * the predicate will match if <em>any</em> targeted field matches.
	 *
	 * @param fieldReference The field reference representing a <a href="SearchPredicateFactory.html#field-references">definition</a> of the index field
	 * to apply the predicate on.
	 * @return The next step.
	 */
	@SuppressWarnings("unchecked")
	default N field(SpatialPredicateFieldReference<? super SR> fieldReference) {
		return fields( fieldReference );
	}

	/**
	 * Target the given fields in the "within" predicate.
	 * <p>
	 * Equivalent to {@link #field(String)} followed by multiple calls to
	 * {@link RangePredicateFieldMoreStep#field(String)},
	 * the only difference being that calls to {@link RangePredicateFieldMoreStep#boost(float)}
	 * and other field-specific settings on the returned step will only need to be done once
	 * and will apply to all the fields passed to this method.
	 *
	 * @param fieldReferences The field references representing <a href="SearchPredicateFactory.html#field-paths">paths</a> to the index fields
	 * to apply the predicate on.
	 * @return The next step.
	 *
	 * @see #field(String)
	 */
	@SuppressWarnings("unchecked")
	N fields(SpatialPredicateFieldReference<? super SR>... fieldReferences);

}
