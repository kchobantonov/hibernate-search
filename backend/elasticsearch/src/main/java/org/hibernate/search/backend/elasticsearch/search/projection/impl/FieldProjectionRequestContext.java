/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.elasticsearch.search.projection.impl;

import java.util.Arrays;

import org.hibernate.search.backend.elasticsearch.logging.impl.QueryLog;
import org.hibernate.search.engine.backend.common.spi.FieldPaths;
import org.hibernate.search.engine.reporting.spi.EventContexts;
import org.hibernate.search.engine.search.common.NamedValues;
import org.hibernate.search.engine.search.common.spi.SearchQueryElementTypeKey;

public class FieldProjectionRequestContext implements ProjectionRequestContext {

	private final ProjectionRequestRootContext root;
	private final String absoluteCurrentFieldPath;
	private final String[] absoluteCurrentFieldPathComponents;
	private final String[] relativeCurrentFieldPathComponents;

	public FieldProjectionRequestContext(ProjectionRequestRootContext root,
			String absoluteCurrentFieldPath, String[] absoluteCurrentFieldPathComponents) {
		this( root, absoluteCurrentFieldPath, absoluteCurrentFieldPathComponents,
				absoluteCurrentFieldPathComponents
		);
	}

	private FieldProjectionRequestContext(ProjectionRequestRootContext root,
			String absoluteCurrentFieldPath, String[] absoluteCurrentFieldPathComponents,
			String[] relativeCurrentFieldPathComponents) {
		this.root = root;
		this.absoluteCurrentFieldPath = absoluteCurrentFieldPath;
		this.absoluteCurrentFieldPathComponents = absoluteCurrentFieldPathComponents;
		this.relativeCurrentFieldPathComponents = relativeCurrentFieldPathComponents;
	}

	@Override
	public void checkValidField(String absoluteFieldPath) {
		if ( !FieldPaths.isStrictPrefix( absoluteCurrentFieldPath, absoluteFieldPath ) ) {
			throw QueryLog.INSTANCE.invalidContextForProjectionOnField( absoluteFieldPath, absoluteCurrentFieldPath );
		}
	}

	@Override
	public void checkNotNested(SearchQueryElementTypeKey<?> projectionKey, String hint) {
		if ( absoluteCurrentFieldPath() != null ) {
			throw QueryLog.INSTANCE.cannotUseProjectionInNestedContext(
					projectionKey.toString(),
					hint,
					EventContexts.fromIndexFieldAbsolutePath( absoluteCurrentFieldPath() )
			);
		}
	}

	@Override
	public ProjectionRequestRootContext root() {
		return root;
	}

	@Override
	public ProjectionRequestContext forField(String absoluteFieldPath, String[] absoluteFieldPathComponents) {
		checkValidField( absoluteFieldPath );
		String[] relativeFieldPathComponents = Arrays.copyOfRange( absoluteFieldPathComponents,
				absoluteCurrentFieldPathComponents.length, absoluteFieldPathComponents.length );
		return new FieldProjectionRequestContext( root, absoluteFieldPath, absoluteFieldPathComponents,
				relativeFieldPathComponents );
	}

	@Override
	public String absoluteCurrentFieldPath() {
		return absoluteCurrentFieldPath;
	}

	@Override
	public String[] relativeCurrentFieldPathComponents() {
		return relativeCurrentFieldPathComponents;
	}

	@Override
	public NamedValues queryParameters() {
		return root().queryParameters();
	}

	@Override
	public boolean projectionCardinalityCorrectlyAddressed(String requiredContextAbsoluteFieldPath) {
		// requiredContextAbsoluteFieldPath generally speaking should be the path from root to the closest multi-valued field
		// (i.e. the very last multi-valued field in the current path)
		//
		// Hence if we have the context that includes that path or matches it exactly then we can assume that the cardinality was correctly handled:
		return requiredContextAbsoluteFieldPath == null
				|| requiredContextAbsoluteFieldPath.equals( absoluteCurrentFieldPath )
				|| ( absoluteCurrentFieldPath != null
						&& absoluteCurrentFieldPath.startsWith( requiredContextAbsoluteFieldPath + "." ) );
	}

}
