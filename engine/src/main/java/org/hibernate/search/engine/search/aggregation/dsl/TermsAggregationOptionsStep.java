/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.search.aggregation.dsl;

import java.util.function.Function;

import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;

/**
 * The final step in a "terms" aggregation definition, where optional parameters can be set.
 *
 * @param <S> The "self" type (the actual exposed type of this step).
 * @param <PDF> The type of factory used to create predicates in {@link #filter(Function)}.
 * @param <F> The type of the targeted field.
 * @param <A> The type of result for this aggregation.
 */
public interface TermsAggregationOptionsStep<
		SR,
		S extends TermsAggregationOptionsStep<SR, ?, PDF, F, A>,
		PDF extends SearchPredicateFactory<SR>,
		F,
		A>
		extends AggregationFinalStep<A>, AggregationFilterStep<SR, S, PDF> {

	/**
	 * Order buckets by descending document count in the aggregation result.
	 * <p>
	 * This is the default behavior.
	 *
	 * @return {@code this}, for method chaining.
	 */
	S orderByCountDescending();

	/**
	 * Order buckets by ascending document count in the aggregation result.
	 *
	 * @return {@code this}, for method chaining.
	 */
	S orderByCountAscending();

	/**
	 * Order buckets by ascending term value in the aggregation result.
	 *
	 * @return {@code this}, for method chaining.
	 */
	S orderByTermAscending();

	/**
	 * Order buckets by descending term value in the aggregation result.
	 *
	 * @return {@code this}, for method chaining.
	 */
	S orderByTermDescending();

	/**
	 * Eliminates buckets with less than {@code minDocumentCount} matching documents
	 * from the aggregation result.
	 * <p>
	 * If set to {@code 0}, terms that are present in the index,
	 * but are not referenced in any document matched by the search query
	 * will yield a bucket with a document count of zero.
	 * <p>
	 * Defaults to {@code 1}.
	 *
	 * @param minDocumentCount The minimum document count for each aggregation value.
	 * @return {@code this}, for method chaining.
	 */
	S minDocumentCount(int minDocumentCount);

	/**
	 * Requires to only create buckets for the top {@code maxTermCount} most frequent terms.
	 * <p>
	 * Defaults to {@code 100}.
	 *
	 * @param maxTermCount The maximum number of reported terms.
	 * @return {@code this}, for method chaining.
	 */
	S maxTermCount(int maxTermCount);

}
