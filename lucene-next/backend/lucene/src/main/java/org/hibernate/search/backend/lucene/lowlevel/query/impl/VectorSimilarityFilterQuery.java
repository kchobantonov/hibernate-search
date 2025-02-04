/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.lucene.lowlevel.query.impl;

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.FilterWeight;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnByteVectorQuery;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.Weight;

public class VectorSimilarityFilterQuery extends Query {

	private final Query query;
	private final float similarityAsScore;

	public static VectorSimilarityFilterQuery create(KnnByteVectorQuery query, float requiredMinimumScore) {
		return new VectorSimilarityFilterQuery( query, requiredMinimumScore );
	}

	public static VectorSimilarityFilterQuery create(KnnFloatVectorQuery query, float requiredMinimumScore) {
		return new VectorSimilarityFilterQuery( query, requiredMinimumScore );
	}

	private VectorSimilarityFilterQuery(Query query, float similarityAsScore) {
		this.query = query;
		this.similarityAsScore = similarityAsScore;
	}

	@Override
	public Query rewrite(IndexSearcher indexSearcher) throws IOException {
		Query rewritten = query.rewrite( indexSearcher );
		if ( rewritten == query ) {
			return this;
		}
		// Knn queries are rewritten and we need to use a rewritten one to get the weights and scores:
		return new VectorSimilarityFilterQuery( rewritten, this.similarityAsScore );
	}

	@Override
	public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
		// we've already converted distance/similarity to a score, but now if the underlying query is boosting the score,
		// we'd want to boost our converted one as well to get the expected matches:
		return new SimilarityWeight( query.createWeight( searcher, scoreMode, boost ), similarityAsScore * boost );
	}

	@Override
	public void visit(QueryVisitor visitor) {
		visitor.visitLeaf( this );
	}

	@Override
	public String toString(String field) {
		return getClass().getName() + "{" +
				"query=" + query +
				", similarityLimit=" + similarityAsScore +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if ( this == o ) {
			return true;
		}
		if ( o == null || getClass() != o.getClass() ) {
			return false;
		}
		VectorSimilarityFilterQuery that = (VectorSimilarityFilterQuery) o;
		return Float.compare( similarityAsScore, that.similarityAsScore ) == 0 && Objects.equals( query, that.query );
	}

	@Override
	public int hashCode() {
		return Objects.hash( query, similarityAsScore );
	}

	private static class SimilarityWeight extends FilterWeight {
		private final float similarityAsScore;

		protected SimilarityWeight(Weight weight, float similarityAsScore) {
			super( weight );
			this.similarityAsScore = similarityAsScore;
		}

		@Override
		public Explanation explain(LeafReaderContext context, int doc) throws IOException {
			Explanation explanation = super.explain( context, doc );
			if ( explanation.isMatch() && similarityAsScore > explanation.getValue().floatValue() ) {
				return Explanation.noMatch( "Similarity limit is greater than the vector similarity.", explanation );
			}
			return explanation;
		}

		@Override
		public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
			ScorerSupplier scorerSupplier = super.scorerSupplier( context );
			if ( scorerSupplier == null ) {
				return null;
			}
			return new MinScoreScorerSupplier( scorerSupplier, similarityAsScore );
		}
	}

	private static class MinScoreScorerSupplier extends ScorerSupplier {

		private final ScorerSupplier delegate;
		private final float similarityAsScore;

		private MinScoreScorerSupplier(ScorerSupplier delegate, float similarityAsScore) {
			this.delegate = delegate;
			this.similarityAsScore = similarityAsScore;
		}

		@Override
		public Scorer get(long leadCost) throws IOException {
			Scorer scorer = delegate.get( leadCost );
			if ( scorer == null ) {
				return null;
			}
			return new MinScoreScorer( scorer, similarityAsScore );
		}

		@Override
		public long cost() {
			return delegate.cost();
		}
	}

	// An adapted version of `org.opensearch.common.lucene.search.function.MinScoreScorer`:
	private static class MinScoreScorer extends Scorer {
		private final Scorer in;
		private final float minScore;
		private float curScore;

		MinScoreScorer(Scorer scorer, float minScore) {
			this.in = scorer;
			this.minScore = minScore;
		}

		@Override
		public int docID() {
			return in.docID();
		}

		@Override
		public float score() {
			return curScore;
		}

		@Override
		public int advanceShallow(int target) throws IOException {
			return in.advanceShallow( target );
		}

		@Override
		public float getMaxScore(int upTo) throws IOException {
			return in.getMaxScore( upTo );
		}

		@Override
		public DocIdSetIterator iterator() {
			return TwoPhaseIterator.asDocIdSetIterator( twoPhaseIterator() );
		}

		@Override
		public TwoPhaseIterator twoPhaseIterator() {
			final TwoPhaseIterator inTwoPhase = this.in.twoPhaseIterator();
			final DocIdSetIterator approximation = inTwoPhase == null ? in.iterator() : inTwoPhase.approximation();
			return new TwoPhaseIterator( approximation ) {

				@Override
				public boolean matches() throws IOException {
					// we need to check the two-phase iterator first
					// otherwise calling score() is illegal
					if ( inTwoPhase != null && !inTwoPhase.matches() ) {
						return false;
					}
					curScore = in.score();
					return curScore >= minScore;
				}

				@Override
				public float matchCost() {
					return 1000f // random constant for the score computation
							+ ( inTwoPhase == null ? 0 : inTwoPhase.matchCost() );
				}
			};
		}
	}
}
