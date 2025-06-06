/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.pojo.work.impl;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.search.engine.backend.work.execution.DocumentCommitStrategy;
import org.hibernate.search.engine.backend.work.execution.DocumentRefreshStrategy;
import org.hibernate.search.engine.backend.work.execution.OperationSubmitter;
import org.hibernate.search.mapper.pojo.logging.impl.IndexingLog;
import org.hibernate.search.mapper.pojo.model.spi.PojoRawTypeIdentifier;
import org.hibernate.search.mapper.pojo.route.DocumentRoutesDescriptor;
import org.hibernate.search.mapper.pojo.work.spi.PojoIndexer;
import org.hibernate.search.mapper.pojo.work.spi.PojoWorkSessionContext;

public class PojoIndexerImpl implements PojoIndexer {

	private final PojoWorkTypeContextProvider typeContextProvider;
	private final PojoWorkSessionContext sessionContext;

	private final Map<PojoRawTypeIdentifier<?>, PojoTypeIndexer<?, ?>> delegates = new ConcurrentHashMap<>();

	public PojoIndexerImpl(PojoWorkTypeContextProvider typeContextProvider,
			PojoWorkSessionContext sessionContext) {
		this.typeContextProvider = typeContextProvider;
		this.sessionContext = sessionContext;
	}

	@Override
	public CompletableFuture<?> add(PojoRawTypeIdentifier<?> typeIdentifier, Object providedId,
			DocumentRoutesDescriptor providedRoutes,
			Object entity, DocumentCommitStrategy commitStrategy, DocumentRefreshStrategy refreshStrategy,
			OperationSubmitter operationSubmitter) {
		if ( entity == null ) {
			throw IndexingLog.INSTANCE.nullEntityForIndexerAddOrUpdate();
		}
		return getDelegate( typeIdentifier )
				.add( providedId, providedRoutes, entity, commitStrategy, refreshStrategy, operationSubmitter );
	}

	@Override
	public CompletableFuture<?> addOrUpdate(PojoRawTypeIdentifier<?> typeIdentifier, Object providedId,
			DocumentRoutesDescriptor providedRoutes, Object entity,
			DocumentCommitStrategy commitStrategy, DocumentRefreshStrategy refreshStrategy,
			OperationSubmitter operationSubmitter) {
		if ( entity == null ) {
			throw IndexingLog.INSTANCE.nullEntityForIndexerAddOrUpdate();
		}
		return getDelegate( typeIdentifier )
				.addOrUpdate( providedId, providedRoutes, entity, commitStrategy, refreshStrategy, operationSubmitter );
	}

	@Override
	public CompletableFuture<?> delete(PojoRawTypeIdentifier<?> typeIdentifier, Object providedId,
			DocumentRoutesDescriptor providedRoutes, Object entity,
			DocumentCommitStrategy commitStrategy, DocumentRefreshStrategy refreshStrategy,
			OperationSubmitter operationSubmitter) {
		return getDelegate( typeIdentifier )
				.delete( providedId, providedRoutes, entity, commitStrategy, refreshStrategy, operationSubmitter );
	}

	@Override
	public CompletableFuture<?> delete(PojoRawTypeIdentifier<?> typeIdentifier, Object providedId,
			DocumentRoutesDescriptor providedRoutes,
			DocumentCommitStrategy commitStrategy, DocumentRefreshStrategy refreshStrategy,
			OperationSubmitter operationSubmitter) {
		return getDelegate( typeIdentifier )
				.delete( providedId, providedRoutes, commitStrategy, refreshStrategy, operationSubmitter );
	}

	private PojoTypeIndexer<?, ?> getDelegate(PojoRawTypeIdentifier<?> typeIdentifier) {
		// Call get() before resorting to computeIfAbsent,
		// because it's faster and will be enough in the vast majority of cases.
		PojoTypeIndexer<?, ?> delegate = this.delegates.get( typeIdentifier );
		if ( delegate == null ) {
			delegate = this.delegates.computeIfAbsent( typeIdentifier, this::createTypeIndexer );
		}
		return delegate;
	}

	private PojoTypeIndexer<?, ?> createTypeIndexer(PojoRawTypeIdentifier<?> typeIdentifier) {
		PojoWorkIndexedTypeContext<?, ?> typeContext = typeContextProvider.indexedForExactType( typeIdentifier );
		return new PojoTypeIndexer<>( typeContext, sessionContext, typeContext.createIndexer( sessionContext ) );
	}
}
