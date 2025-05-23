/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.search.predicate.dsl;

/**
 * The step in a "range" predicate definition where the limits of the range to match can be set
 * (see the superinterface {@link RangePredicateMatchingStep}),
 * or optional parameters for the last targeted field(s) can be set,
 * or more target fields can be added.
 *
 * @param <SR> Scope root type.
 * @param <S> The "self" type (the actual exposed type of this step).
 * @param <N> The type of the next step.
 */
public interface RangePredicateFieldMoreStep<
		SR,
		S extends RangePredicateFieldMoreStep<SR, ?, N>,
		N extends RangePredicateOptionsStep<?>>
		extends RangePredicateFieldMoreGenericStep<SR, S, N, String, Object>,
		RangePredicateMatchingStep<N> {

	/**
	 * Target the given field in the range predicate,
	 * as an alternative to the already-targeted fields.
	 * <p>
	 * See {@link RangePredicateFieldStep#field(String)} for more information about targeting fields.
	 *
	 * @param fieldPath The <a href="SearchPredicateFactory.html#field-paths">path</a> to the index field
	 * to apply the predicate on.
	 * @return The next step.
	 *
	 * @see RangePredicateFieldStep#field(String)
	 */
	default S field(String fieldPath) {
		return fields( fieldPath );
	}

	/**
	 * Target the given fields in the range predicate,
	 * as an alternative to the already-targeted fields.
	 * <p>
	 * See {@link RangePredicateFieldStep#fields(String...)} for more information about targeting fields.
	 *
	 * @param fieldPaths The <a href="SearchPredicateFactory.html#field-paths">paths</a> to the index fields
	 * to apply the predicate on.
	 * @return The next step.
	 *
	 * @see RangePredicateFieldStep#fields(String...)
	 */
	S fields(String... fieldPaths);
}
