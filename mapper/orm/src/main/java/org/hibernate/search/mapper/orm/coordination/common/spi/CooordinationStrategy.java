/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.orm.coordination.common.spi;

import java.util.concurrent.CompletableFuture;

import org.hibernate.search.mapper.orm.automaticindexing.spi.AutomaticIndexingConfigurationContext;

/**
 * The strategy for coordinating between threads of a single-node application,
 * or between nodes of a distributed application.
 * <p>
 * Advanced implementations may involve an external system to store and asynchronously consume indexing events,
 * ultimately routing them back to Hibernate Search's in-JVM indexing plans.
 *
 * @see org.hibernate.search.mapper.orm.cfg.HibernateOrmMapperSettings#COORDINATION_STRATEGY
 */
public interface CooordinationStrategy {

	/**
	 * Configures automatic indexing.
	 * <p>
	 * Called once during bootstrap,
	 * after backends and index managers were started.
	 *
	 * @param context The configuration context.
	 */
	void configureAutomaticIndexing(AutomaticIndexingConfigurationContext context);

	/**
	 * Configures this strategy and starts processing events in the background.
	 * <p>
	 * Called once during bootstrap, after {@link #configureAutomaticIndexing(AutomaticIndexingConfigurationContext)}.
	 *
	 * @param context The start context.
	 * @return A future that completes when the strategy is completely started.
	 */
	CompletableFuture<?> start(CoordinationStrategyStartContext context);

	/**
	 * Prepares for {@link #stop()},
	 * executing any operations that need to be executed before shutdown.
	 * <p>
	 * Called once on shutdown,
	 * before backends and index managers are stopped.
	 *
	 * @param context The pre-stop context.
	 * @return A future that completes when pre-stop operations complete.
	 */
	CompletableFuture<?> preStop(CoordinationStrategyPreStopContext context);

	/**
	 * Stops and releases all resources.
	 * <p>
	 * Called once on shutdown,
	 * after the future returned by {@link #preStop(CoordinationStrategyPreStopContext)} completed.
	 */
	void stop();
}