/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.search.predicate.dsl;

/**
 * The initial and final step in a boolean predicate definition, where clauses can be added.
 * <p>
 * Different types of clauses have different effects, see {@link BooleanPredicateOptionsCollector}.
 *
 * @param <SR> Scope root type.
 * @param <S> The "self" type (the actual exposed type of this step).
 */
public interface BooleanPredicateClausesStep<SR, S extends BooleanPredicateClausesStep<SR, ?>>
		extends GenericBooleanPredicateClausesStep<SR, S, BooleanPredicateOptionsCollector<SR, ?>> {

}
