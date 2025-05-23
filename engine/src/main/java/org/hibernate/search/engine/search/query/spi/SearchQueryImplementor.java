/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.search.query.spi;

import java.util.concurrent.TimeUnit;

import org.hibernate.search.engine.search.query.SearchQuery;

/**
 * Defines the "service program contract" for {@link SearchQuery}.
 * <p>
 * Methods on {@link SearchQuery} are not supposed to change the internal state of the instance.
 * Methods here can do that.
 *
 * @param <H> The type of query hits.
 */
public interface SearchQueryImplementor<H> extends SearchQuery<H> {

	@Deprecated(since = "8.0")
	default void failAfter(long timeout, TimeUnit timeUnit) {
		failAfter( (Long) timeout, timeUnit );
	}

	void failAfter(Long timeout, TimeUnit timeUnit);
}
