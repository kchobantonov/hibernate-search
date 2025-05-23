/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.pojo.search.definition.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.search.engine.reporting.spi.EventContexts;
import org.hibernate.search.engine.search.projection.definition.spi.CompositeProjectionDefinition;
import org.hibernate.search.mapper.pojo.logging.impl.ProjectionLog;
import org.hibernate.search.mapper.pojo.mapping.building.impl.PojoMappingHelper;
import org.hibernate.search.mapper.pojo.mapping.building.spi.PojoSearchMappingConstructorNode;
import org.hibernate.search.mapper.pojo.mapping.building.spi.PojoTypeMetadataContributor;
import org.hibernate.search.mapper.pojo.model.spi.PojoConstructorModel;
import org.hibernate.search.mapper.pojo.model.spi.PojoRawTypeModel;
import org.hibernate.search.mapper.pojo.reporting.spi.PojoEventContexts;
import org.hibernate.search.mapper.pojo.search.definition.binding.impl.ProjectionConstructorBinder;
import org.hibernate.search.util.common.impl.Closer;

public final class PojoSearchQueryElementRegistryBuilder {

	private final PojoMappingHelper mappingHelper;
	private final Map<Class<?>, CompositeProjectionDefinition<?>> projectionDefinitions =
			new LinkedHashMap<>();

	public PojoSearchQueryElementRegistryBuilder(PojoMappingHelper mappingHelper) {
		this.mappingHelper = mappingHelper;
	}

	public void closeOnFailure() {
		try ( Closer<RuntimeException> closer = new Closer<>() ) {
			closer.pushAll( CompositeProjectionDefinition::close, projectionDefinitions.values() );
		}
	}

	public void process(PojoRawTypeModel<?> type) {
		try {
			for ( PojoTypeMetadataContributor contributor : mappingHelper.contributorProvider()
					// Constructor mapping is not inherited
					.getIgnoringInheritance( type ) ) {
				for ( PojoSearchMappingConstructorNode constructorMapping : contributor.constructors().values() ) {
					processProjectionConstructors( type, constructorMapping );
				}
			}
		}
		catch (RuntimeException e) {
			mappingHelper.failureCollector()
					.withContext( EventContexts.fromType( type ) )
					.add( e );
		}
	}

	private <T> void processProjectionConstructors(PojoRawTypeModel<T> type,
			PojoSearchMappingConstructorNode constructorMapping) {
		PojoConstructorModel<T> constructor = type.constructor( constructorMapping.parametersJavaTypes() );
		try {
			processProjectionConstructors( type, constructor, constructorMapping );
		}
		catch (RuntimeException e) {
			mappingHelper.failureCollector()
					.withContext( PojoEventContexts.fromType( type ) )
					.withContext( PojoEventContexts.fromConstructor( constructor ) )
					.add( e );
		}
	}

	private <T> void processProjectionConstructors(PojoRawTypeModel<T> type, PojoConstructorModel<T> constructor,
			PojoSearchMappingConstructorNode constructorMapping) {
		if ( constructorMapping.isProjectionConstructor() ) {
			Class<T> instantiatedJavaClass = type.typeIdentifier().javaClass();
			ProjectionConstructorBinder<T> binder = new ProjectionConstructorBinder<>( mappingHelper, constructor );
			PojoConstructorProjectionDefinition<T> definition = binder.bind();
			CompositeProjectionDefinition<?> existing =
					projectionDefinitions.putIfAbsent( instantiatedJavaClass, definition );
			ProjectionLog.INSTANCE.constructorProjection( type, definition );
			if ( existing != null ) {
				throw ProjectionLog.INSTANCE.multipleProjectionConstructorsForType( instantiatedJavaClass );
			}
		}
	}

	public PojoSearchQueryElementRegistry build() {
		return new PojoSearchQueryElementRegistry( projectionDefinitions );
	}
}
