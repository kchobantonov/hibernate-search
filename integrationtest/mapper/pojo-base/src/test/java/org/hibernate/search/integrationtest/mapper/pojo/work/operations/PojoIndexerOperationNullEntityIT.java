/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.mapper.pojo.work.operations;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.search.integrationtest.mapper.pojo.work.operations.PojoIndexingOperation.addWorkInfo;
import static org.hibernate.search.util.impl.test.FutureAssert.assertThatFuture;
import static org.junit.Assume.assumeTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.hibernate.search.mapper.javabean.session.SearchSession;
import org.hibernate.search.mapper.javabean.work.SearchIndexer;
import org.hibernate.search.mapper.pojo.route.DocumentRouteDescriptor;
import org.hibernate.search.mapper.pojo.route.DocumentRoutesDescriptor;
import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.impl.test.annotation.TestForIssue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests of individual operations in {@link org.hibernate.search.mapper.pojo.work.spi.PojoIndexer}
 * when the entity passed to the operation is null.
 */
@RunWith(Parameterized.class)
@TestForIssue(jiraKey = "HSEARCH-4153")
public class PojoIndexerOperationNullEntityIT extends AbstractPojoIndexingOperationIT {

	@Test
	public void simple() {
		try ( SearchSession session = createSession() ) {
			SearchIndexer indexer = session.indexer();

			if ( isDelete() ) {
				CompletableFuture<?> futureFromBackend = new CompletableFuture<>();
				expectOperation( futureFromBackend, 42, null, "1" );
				CompletionStage<?> returnedFuture = operation.execute( indexer, 42 );
				backendMock.verifyExpectationsMet();
				assertThatFuture( returnedFuture ).isPending();

				futureFromBackend.complete( null );
				assertThatFuture( returnedFuture ).isSuccessful();
			}
			else {
				assertThatThrownBy( () -> operation.execute( indexer, 42 ) )
						.isInstanceOf( SearchException.class )
						.hasMessageContaining( "Invalid indexing request",
								"the add and update operations require a non-null entity" );
			}
		}
	}

	@Test
	public void nullProvidedId() {
		assumeCanWorkWithNullEntity();

		try ( SearchSession session = createSession() ) {
			SearchIndexer indexer = session.indexer();

			assertThatThrownBy( () -> operation.execute( indexer, null, (DocumentRoutesDescriptor) null ) )
					.isInstanceOf( SearchException.class )
					.hasMessageContainingAll( "Invalid indexing request",
							"if the entity is null, the identifier must be provided explicitly" );
		}
	}

	@Test
	public void providedId_providedRoutes_currentAndNoPrevious() {
		assumeCanWorkWithNullEntity();

		CompletableFuture<?> futureFromBackend = new CompletableFuture<>();
		try ( SearchSession session = createSession() ) {
			SearchIndexer indexer = session.indexer();

			// Since we don't provide any previous routes, we don't expect additional deletes.
			expectOperation( futureFromBackend, 42, "UE-123", "1" );
			CompletionStage<?> returnedFuture = operation.execute( indexer, 42,
					DocumentRoutesDescriptor.of( DocumentRouteDescriptor.of( "UE-123" ), Collections.emptyList() ) );
			backendMock.verifyExpectationsMet();
			assertThatFuture( returnedFuture ).isPending();

			futureFromBackend.complete( null );
			assertThatFuture( returnedFuture ).isSuccessful();
		}
	}

	@Test
	public void providedId_providedRoutes_currentAndPrevious() {
		assumeCanWorkWithNullEntity();

		CompletableFuture<?> futureFromBackend = new CompletableFuture<>();
		try ( SearchSession session = createSession() ) {
			SearchIndexer indexer = session.indexer();

			// Since we don't provide any previous routes, we don't expect additional deletes.
			expectOperation( futureFromBackend,
					worksBefore -> {
						if ( !isAdd() ) {
							// For operations other than add, expect a delete for the previous routes (if different).
							worksBefore
									.delete( b -> addWorkInfo( b, tenantId, "42", "UE-121" ) )
									.delete( b -> addWorkInfo( b, tenantId, "42", "UE-122" ) );
						}
					},
					// And only then, expect the actual operation.
					42, "UE-123", "1" );
			CompletionStage<?> returnedFuture = operation.execute( indexer, 42,
					DocumentRoutesDescriptor.of( DocumentRouteDescriptor.of( "UE-123" ),
							Arrays.asList( DocumentRouteDescriptor.of( "UE-121" ),
									DocumentRouteDescriptor.of( "UE-122" ),
									DocumentRouteDescriptor.of( "UE-123" ) ) ) );
			backendMock.verifyExpectationsMet();
			assertThatFuture( returnedFuture ).isPending();

			futureFromBackend.complete( null );
			assertThatFuture( returnedFuture ).isSuccessful();
		}
	}

	@Test
	public void runtimeException() {
		assumeCanWorkWithNullEntity();

		CompletableFuture<?> futureFromBackend = new CompletableFuture<>();
		RuntimeException exception = new RuntimeException();
		try ( SearchSession session = createSession() ) {
			SearchIndexer indexer = session.indexer();

			expectOperation( futureFromBackend, 1, null, "1" );
			CompletionStage<?> returnedFuture = operation.execute( indexer, 1 );
			backendMock.verifyExpectationsMet();
			assertThatFuture( returnedFuture ).isPending();

			futureFromBackend.completeExceptionally( exception );
			assertThatFuture( returnedFuture ).isFailed( exception );
		}
	}

	@Test
	public void error() {
		assumeCanWorkWithNullEntity();

		CompletableFuture<?> futureFromBackend = new CompletableFuture<>();
		Error error = new Error();
		try ( SearchSession session = createSession() ) {
			SearchIndexer indexer = session.indexer();

			expectOperation( futureFromBackend, 1, null, "1" );
			CompletionStage<?> returnedFuture = operation.execute( indexer, 1 );
			backendMock.verifyExpectationsMet();
			assertThatFuture( returnedFuture ).isPending();

			futureFromBackend.completeExceptionally( error );
			assertThatFuture( returnedFuture ).isFailed( error );
		}
	}

	@Override
	protected boolean isImplicitRoutingEnabled() {
		// Entities are null and are not implicitly loaded, so implicit routing simply cannot work.
		return false;
	}

	protected final void assumeCanWorkWithNullEntity() {
		assumeTrue( "This test only makes sense for delete operations", isDelete() );
	}

}