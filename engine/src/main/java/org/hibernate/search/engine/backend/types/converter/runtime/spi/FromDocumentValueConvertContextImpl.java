/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.backend.types.converter.runtime.spi;

import org.hibernate.search.engine.backend.session.spi.BackendSessionContext;
import org.hibernate.search.engine.backend.types.converter.runtime.FromDocumentValueConvertContext;
import org.hibernate.search.engine.backend.types.converter.runtime.FromDocumentValueConvertContextExtension;
import org.hibernate.search.engine.common.dsl.spi.DslExtensionState;

public class FromDocumentValueConvertContextImpl implements FromDocumentValueConvertContext {
	private final BackendSessionContext sessionContext;

	public FromDocumentValueConvertContextImpl(BackendSessionContext sessionContext) {
		this.sessionContext = sessionContext;
	}

	@Override
	@Deprecated(since = "6.1")
	public <T> T extension(
			org.hibernate.search.engine.backend.types.converter.runtime.FromDocumentFieldValueConvertContextExtension<
					T> extension) {
		return DslExtensionState.returnIfSupported( extension, extension.extendOptional( this, sessionContext ) );
	}

	@Override
	public <T> T extension(FromDocumentValueConvertContextExtension<T> extension) {
		return DslExtensionState.returnIfSupported( extension, extension.extendOptional( this, sessionContext ) );
	}
}
