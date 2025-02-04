/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.util.common.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.hibernate.search.util.common.logging.impl.CommonMiscLog;

/**
 * A {@link CompletableFuture} that, upon cancellation,
 * will not only change its state, but also try to cancel (abort) the corresponding operation.
 * @param <T> The return type of the future.
 */
class CancellableExecutionCompletableFuture<T> extends CompletableFuture<T> {

	private final Future<?> future;

	CancellableExecutionCompletableFuture(Runnable runnable, ExecutorService executor) {
		this.future = executor.submit( new CompletingRunnable<>( this, runnable ) );
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		/*
		 * Calling future.cancel() may trigger an exception in the operation that may
		 * end up setting 'this' as completed exceptionally because of the failure...
		 * Thus we mark 'this' as cancelled *first*, so that any exception in the operation
		 * from now on will be ignored.
		 */
		CommonMiscLog.INSTANCE.cancellingCompletableFuture( this, mayInterruptIfRunning );
		super.cancel( mayInterruptIfRunning );
		CommonMiscLog.INSTANCE.cancellingFuture( future, mayInterruptIfRunning );
		boolean cancelled = future.cancel( mayInterruptIfRunning );
		if ( !cancelled ) {
			CommonMiscLog.INSTANCE.cancellingFutureFailed( future, mayInterruptIfRunning );
		}
		return cancelled;
	}

	private static class CompletingRunnable<T> implements Runnable {
		private final CompletableFuture<T> future;
		private final Runnable delegate;

		private CompletingRunnable(CompletableFuture<T> future, Runnable delegate) {
			this.future = future;
			this.delegate = delegate;
		}

		@Override
		public String toString() {
			return "CompletingRunnable[" +
					"future=" + future +
					", delegate=" + delegate +
					']';
		}

		@Override
		public void run() {
			try {
				delegate.run();
				future.complete( null );
			}
			catch (Throwable t) {
				if ( future.isCancelled() ) {
					// The operation probably failed because of the cancellation,
					// but try to keep track of the failure anyway.
					Futures.getThrowableNow( future ).addSuppressed( t );
				}
				else {
					future.completeExceptionally( t );
				}
			}
		}
	}
}
