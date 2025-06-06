/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.pojo.search.definition.binding.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.hibernate.search.engine.environment.bean.BeanHolder;
import org.hibernate.search.engine.environment.bean.BeanReference;
import org.hibernate.search.engine.search.projection.definition.ProjectionDefinition;
import org.hibernate.search.engine.search.projection.definition.spi.ConstantProjectionDefinition;
import org.hibernate.search.mapper.pojo.logging.impl.ProjectionLog;
import org.hibernate.search.mapper.pojo.mapping.building.impl.PojoMappingHelper;
import org.hibernate.search.mapper.pojo.mapping.building.spi.PojoSearchMappingConstructorNode;
import org.hibernate.search.mapper.pojo.mapping.building.spi.PojoSearchMappingMethodParameterNode;
import org.hibernate.search.mapper.pojo.mapping.building.spi.PojoTypeMetadataContributor;
import org.hibernate.search.mapper.pojo.model.impl.PojoModelConstructorParameterRootElement;
import org.hibernate.search.mapper.pojo.model.spi.PojoConstructorModel;
import org.hibernate.search.mapper.pojo.model.spi.PojoMethodParameterModel;
import org.hibernate.search.mapper.pojo.model.spi.PojoRawTypeModel;
import org.hibernate.search.mapper.pojo.reporting.spi.PojoEventContexts;
import org.hibernate.search.mapper.pojo.search.definition.binding.ProjectionBinder;
import org.hibernate.search.util.common.impl.SuppressingCloser;
import org.hibernate.search.util.common.reporting.EventContext;
import org.hibernate.search.util.common.reporting.spi.EventContextProvider;

class ProjectionConstructorParameterBinder<P> implements EventContextProvider {

	final PojoMappingHelper mappingHelper;
	final ProjectionConstructorBinder<?> parent;
	final PojoMethodParameterModel<P> parameter;
	final PojoModelConstructorParameterRootElement<P> parameterRootElement;

	ProjectionConstructorParameterBinder(PojoMappingHelper mappingHelper, ProjectionConstructorBinder<?> parent,
			PojoMethodParameterModel<P> parameter) {
		this.mappingHelper = mappingHelper;
		this.parent = parent;
		this.parameter = parameter;
		this.parameterRootElement = new PojoModelConstructorParameterRootElement<>(
				mappingHelper.introspector(),
				parameter
		);
	}

	@Override
	public EventContext eventContext() {
		return parent.eventContext().append( PojoEventContexts.fromMethodParameter( parameter ) );
	}

	BeanHolder<? extends ProjectionDefinition<?>> bind() {
		if ( parameter.isEnclosingInstance() ) {
			// Let's ignore this parameter, because we are not able to provide a surrounding instance,
			// and it's often useful to be able to declare a method-local type for projections
			// (those types have a "surrounding instance" parameter in their constructor
			// even if they don't use it).

			// NOTE: with JDK 25+ this is no longer the case,
			// and will fail further at runtime when such projection is created.
			// Hence, let's fails faster instead;
			if ( parameter.enclosingInstanceCanBeNull() ) {
				return ConstantProjectionDefinition.nullValue();
			}
			else {
				throw ProjectionLog.INSTANCE.nullEnclosingParameterInProjectionConstructorNotAllowed( eventContext() );
			}
		}

		BeanHolder<? extends ProjectionDefinition<?>> result = null;

		for ( PojoTypeMetadataContributor contributor : mappingHelper.contributorProvider()
				// Constructor mapping is not inherited
				.getIgnoringInheritance( parent.constructor.typeModel() ) ) {
			PojoSearchMappingConstructorNode constructorMapping = contributor.constructors()
					.get( Arrays.asList( parent.constructor.parametersJavaTypes() ) );
			if ( constructorMapping == null ) {
				continue;
			}
			Optional<PojoSearchMappingMethodParameterNode> parameterMapping = constructorMapping.parameterNode(
					parameter.index() );
			if ( !parameterMapping.isPresent() ) {
				continue;
			}
			for ( PojoSearchMappingMethodParameterNode.ProjectionBindingData projectionDefinition : parameterMapping.get()
					.projectionBindings() ) {
				if ( result != null ) {
					throw ProjectionLog.INSTANCE.multipleProjectionMappingsForParameter();
				}
				ProjectionBindingContextImpl<?> bindingContext =
						new ProjectionBindingContextImpl<>( this, projectionDefinition.params );
				result = applyBinder( bindingContext, projectionDefinition.reference );
			}
		}

		if ( result != null ) {
			return result;
		}
		else {
			ProjectionBindingContextImpl<?> bindingContext =
					new ProjectionBindingContextImpl<>( this, Collections.emptyMap() );
			return bindingContext.applyDefaultProjection();
		}
	}

	private BeanHolder<? extends ProjectionDefinition<?>> applyBinder(ProjectionBindingContextImpl<?> context,
			BeanReference<? extends ProjectionBinder> binderReference) {
		BeanHolder<? extends ProjectionDefinition<?>> definitionHolder = null;
		try ( BeanHolder<? extends ProjectionBinder> binderHolder = mappingHelper.beanResolver()
				.resolve( binderReference ) ) {
			definitionHolder = context.applyBinder( binderHolder.get() );
			return definitionHolder;
		}
		catch (RuntimeException e) {
			new SuppressingCloser( e ).push( definitionHolder );
			throw e;
		}
	}

	<T> PojoConstructorModel<T> findProjectionConstructorOrNull(PojoRawTypeModel<T> projectedType) {
		PojoSearchMappingConstructorNode result = null;
		for ( PojoTypeMetadataContributor contributor : mappingHelper.contributorProvider()
				// Constructor mapping is not inherited
				.getIgnoringInheritance( projectedType ) ) {
			for ( PojoSearchMappingConstructorNode constructorMapping : contributor.constructors().values() ) {
				if ( constructorMapping.isProjectionConstructor() ) {
					if ( result != null ) {
						throw ProjectionLog.INSTANCE.multipleProjectionConstructorsForType(
								projectedType.typeIdentifier().javaClass() );
					}
					result = constructorMapping;
				}
			}
		}
		return result == null ? null : projectedType.constructor( result.parametersJavaTypes() );
	}

}
