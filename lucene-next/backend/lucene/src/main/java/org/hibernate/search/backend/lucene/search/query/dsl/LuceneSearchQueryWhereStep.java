/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.lucene.search.query.dsl;

import org.hibernate.search.backend.lucene.search.predicate.dsl.LuceneSearchPredicateFactory;
import org.hibernate.search.engine.search.query.dsl.SearchQueryWhereStep;

public interface LuceneSearchQueryWhereStep<SR, H, LOS>
		extends SearchQueryWhereStep<SR, LuceneSearchQueryOptionsStep<SR, H, LOS>, H, LOS, LuceneSearchPredicateFactory<SR>> {

}
