/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.integrationtest.mapper.pojo.mapping.definition.noparameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import org.hibernate.search.integrationtest.mapper.pojo.mapping.definition.AbstractProjectionConstructorIT;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.DocumentId;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FieldProjection;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectProjection;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ProjectionConstructor;
import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;
import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.impl.integrationtest.common.reporting.FailureReportUtils;
import org.hibernate.search.util.impl.integrationtest.mapper.pojo.standalone.StandalonePojoMappingSetupHelper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;


class ProjectionConstructorClassNoParametersCompilerFlagIT extends AbstractProjectionConstructorIT {

	@RegisterExtension
	public StandalonePojoMappingSetupHelper setupHelper =
			StandalonePojoMappingSetupHelper.withBackendMock( MethodHandles.lookup(), backendMock );

	@BeforeEach
	void sourcesCompiledWithoutParametersFlag() {
		assertThat( ConstructorWithParameters.class.getDeclaredConstructors()[0].getParameters() )
				.withFailMessage( "This test only works if compiled *without* the '-parameters' compiler flag." )
				.extracting( Parameter::isNamePresent )
				.containsOnly( Boolean.FALSE );
	}

	@Test
	void implicitInnerMapping() {
		assertThatThrownBy( () -> setupHelper.start()
				.withAnnotatedTypes( ImplicitInnerMappingMyProjection.class,
						ImplicitInnerMappingMyProjection.InnerProjection.class )
				.setup( IndexedEntity.class ) )
				.isInstanceOf( SearchException.class )
				.satisfies( FailureReportUtils.hasFailureReport()
						.typeContext( ImplicitInnerMappingMyProjection.class.getName() )
						.constructorContext( String.class,
								Integer.class,
								ImplicitInnerMappingMyProjection.InnerProjection.class )
						.methodParameterContext( 0 )
						.failure( "Missing parameter names in Java metadata for projection constructor",
								"When inferring inner projections from constructor parameters,"
										+ " constructor parameter names must be known",
								"Either make sure this class was compiled with the '-parameters' compiler flag",
								"or set the path explicitly with '@FieldProjection(path = ...)'" )
						.methodParameterContext( 1 )
						.failure( "Missing parameter names in Java metadata for projection constructor",
								"When inferring inner projections from constructor parameters,"
										+ " constructor parameter names must be known",
								"Either make sure this class was compiled with the '-parameters' compiler flag",
								"or set the path explicitly with '@FieldProjection(path = ...)' or '@ObjectProjection(path = ...)'" )
						.methodParameterContext( 2 )
						.failure( "Missing parameter names in Java metadata for projection constructor",
								"When inferring inner projections from constructor parameters,"
										+ " constructor parameter names must be known",
								"Either make sure this class was compiled with the '-parameters' compiler flag",
								"or set the path explicitly with '@FieldProjection(path = ...)' or '@ObjectProjection(path = ...)'" ) );
	}

	@ProjectionConstructor
	static class ImplicitInnerMappingMyProjection {
		private final String someText;
		private final Integer someInteger;
		private final InnerProjection someContained;

		ImplicitInnerMappingMyProjection(String someText, Integer someInteger, InnerProjection someContained) {
			this.someText = someText;
			this.someInteger = someInteger;
			this.someContained = someContained;
		}

		@ProjectionConstructor
		static class InnerProjection {
			private final String someText2;
			private final Integer someInteger2;

			InnerProjection(String someText2, Integer someInteger2) {
				this.someText2 = someText2;
				this.someInteger2 = someInteger2;
			}
		}
	}

	@Test
	void explicitInnerMapping_implicitPath() {
		assertThatThrownBy( () -> setupHelper.start()
				.withAnnotatedTypes( ExplicitInnerMapping_implicitPathMyProjection.class,
						ExplicitInnerMapping_implicitPathMyProjection.InnerProjection.class )
				.setup( IndexedEntity.class ) )
				.isInstanceOf( SearchException.class )
				.satisfies( FailureReportUtils.hasFailureReport()
						.typeContext( ExplicitInnerMapping_implicitPathMyProjection.class.getName() )
						.constructorContext( String.class,
								Integer.class,
								ExplicitInnerMapping_implicitPathMyProjection.InnerProjection.class )
						.methodParameterContext( 0 )
						.failure( "Missing parameter names in Java metadata for projection constructor",
								"When mapping a projection constructor parameter to a field projection without providing a field path,"
										+ " constructor parameter names must be known",
								"Either make sure this class was compiled with the '-parameters' compiler flag",
								"or set the path explicitly with '@FieldProjection(path = ...)'" )
						.methodParameterContext( 1 )
						.failure( "Missing parameter names in Java metadata for projection constructor",
								"When mapping a projection constructor parameter to a field projection without providing a field path,"
										+ " constructor parameter names must be known",
								"Either make sure this class was compiled with the '-parameters' compiler flag",
								"or set the path explicitly with '@FieldProjection(path = ...)'" )
						.methodParameterContext( 2 )
						.failure( "Missing parameter names in Java metadata for projection constructor",
								"When mapping a projection constructor parameter to an object projection without providing a field path,"
										+ " constructor parameter names must be known",
								"Either make sure this class was compiled with the '-parameters' compiler flag",
								"or set the path explicitly with '@ObjectProjection(path = ...)'" ) );
	}

	@ProjectionConstructor
	static class ExplicitInnerMapping_implicitPathMyProjection {
		private final String someText;
		private final Integer someInteger;
		private final InnerProjection someContained;

		ExplicitInnerMapping_implicitPathMyProjection(@FieldProjection String someText,
				@FieldProjection Integer someInteger,
				@ObjectProjection InnerProjection someContained) {
			this.someText = someText;
			this.someInteger = someInteger;
			this.someContained = someContained;
		}

		@ProjectionConstructor
		static class InnerProjection {
			private final String someText2;
			private final Integer someInteger2;

			InnerProjection(@FieldProjection String someText2,
					@FieldProjection Integer someInteger2) {
				this.someText2 = someText2;
				this.someInteger2 = someInteger2;
			}
		}
	}

	@Test
	void explicitInnerMapping_explicitPath() {
		backendMock.expectAnySchema( INDEX_NAME );
		SearchMapping mapping = setupHelper.start()
				.withAnnotatedTypes( ExplicitInnerMapping_explicitPathMyProjection.class,
						ExplicitInnerMapping_explicitPathMyProjection.InnerProjection.class )
				.setup( IndexedEntity.class, ContainedEntity.class );
		testSuccessfulRootProjection(
				mapping, IndexedEntity.class, ExplicitInnerMapping_explicitPathMyProjection.class,
				Arrays.asList(
						Arrays.asList( "result1", 1, Arrays.asList( "result1_1", 11 ) ),
						Arrays.asList( "result2", 2, Arrays.asList( "result2_1", 21 ) ),
						Arrays.asList( "result3", 3, Arrays.asList( "result3_1", 31 ) )
				),
				f -> f.composite()
						.from(
								f.field( "text", String.class ),
								f.field( "integer", Integer.class ),
								f.object( "contained" )
										.from(
												f.field( "contained.text2", String.class ),
												f.field( "contained.integer2", Integer.class )
										)
										.asList()
						)
						.asList(),
				Arrays.asList(
						new ExplicitInnerMapping_explicitPathMyProjection( "result1", 1,
								new ExplicitInnerMapping_explicitPathMyProjection.InnerProjection( "result1_1", 11 ) ),
						new ExplicitInnerMapping_explicitPathMyProjection( "result2", 2,
								new ExplicitInnerMapping_explicitPathMyProjection.InnerProjection( "result2_1", 21 ) ),
						new ExplicitInnerMapping_explicitPathMyProjection( "result3", 3,
								new ExplicitInnerMapping_explicitPathMyProjection.InnerProjection( "result3_1", 31 ) )
				)
		);
	}

	@ProjectionConstructor
	static class ExplicitInnerMapping_explicitPathMyProjection {
		private final String someText;
		private final Integer someInteger;
		private final InnerProjection someContained;

		ExplicitInnerMapping_explicitPathMyProjection(@FieldProjection(path = "text") String someText,
				@FieldProjection(path = "integer") Integer someInteger,
				@ObjectProjection(path = "contained") InnerProjection someContained) {
			this.someText = someText;
			this.someInteger = someInteger;
			this.someContained = someContained;
		}

		@ProjectionConstructor
		static class InnerProjection {
			private final String someText2;
			private final Integer someInteger2;

			InnerProjection(@FieldProjection(path = "text2") String someText2,
					@FieldProjection(path = "integer2") Integer someInteger2) {
				this.someText2 = someText2;
				this.someInteger2 = someInteger2;
			}
		}
	}

	@Indexed(index = INDEX_NAME)
	static class IndexedEntity {
		@DocumentId
		public Integer id;
		@FullTextField
		public String text;
		@GenericField
		public Integer integer;
		@IndexedEmbedded
		@IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
		public ContainedEntity contained;
	}

	static class ContainedEntity {
		@FullTextField
		public String text2;
		@GenericField
		public Integer integer2;
	}

	static class ConstructorWithParameters {
		ConstructorWithParameters(int paramInt, String paramString) {
		}
	}
}
