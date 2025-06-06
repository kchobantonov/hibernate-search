/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.integrationtest.mapper.pojo.mapping.annotation.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandles;
import java.util.Collections;

import org.hibernate.search.engine.search.projection.dsl.SearchProjectionFactory;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.DocumentId;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ProjectionConstructor;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.MappingAnnotatedMethodParameter;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.MethodParameterMapping;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.MethodParameterMappingAnnotationProcessor;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.MethodParameterMappingAnnotationProcessorContext;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.MethodParameterMappingAnnotationProcessorRef;
import org.hibernate.search.mapper.pojo.mapping.definition.programmatic.MethodParameterMappingStep;
import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;
import org.hibernate.search.mapper.pojo.standalone.session.SearchSession;
import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.common.reporting.EventContext;
import org.hibernate.search.util.impl.integrationtest.common.extension.BackendMock;
import org.hibernate.search.util.impl.integrationtest.common.extension.StubSearchWorkBehavior;
import org.hibernate.search.util.impl.integrationtest.common.reporting.FailureReportUtils;
import org.hibernate.search.util.impl.integrationtest.mapper.pojo.standalone.StandalonePojoMappingSetupHelper;
import org.hibernate.search.util.impl.test.annotation.TestForIssue;
import org.hibernate.search.util.impl.test.extension.StaticCounters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Test common use cases of (custom) method parameter mapping annotations.
 */
@SuppressWarnings("unused")
@TestForIssue(jiraKey = "HSEARCH-4574")
class CustomMethodParameterMappingAnnotationBaseIT {

	private static final String INDEX_NAME = "IndexName";

	@RegisterExtension
	public BackendMock backendMock = BackendMock.create();

	@RegisterExtension
	public StandalonePojoMappingSetupHelper setupHelper =
			StandalonePojoMappingSetupHelper.withBackendMock( MethodHandles.lookup(), backendMock );

	@RegisterExtension
	public StaticCounters counters = StaticCounters.create();

	/**
	 * Basic test checking that a simple constructor mapping will be applied as expected.
	 */
	@Test
	void simple() {
		@Indexed(index = INDEX_NAME)
		class IndexedEntity {
			@DocumentId
			Long id;
			@GenericField(name = "myText")
			String text;
		}

		backendMock.expectAnySchema( INDEX_NAME );

		SearchMapping mapping = setupHelper.start()
				.expectCustomBeans()
				.withAnnotatedTypes( SimpleMyProjection.class )
				.setup( IndexedEntity.class );
		backendMock.verifyExpectationsMet();

		try ( SearchSession session = mapping.createSession() ) {
			backendMock.expectSearchProjection(
					INDEX_NAME,
					b -> {
						SearchProjectionFactory<?, ?, ?> f = mapping.scope( IndexedEntity.class ).projection();
						b.projection( f.composite()
								.from(
										f.field( "myText", String.class )
								)
								.asList() );
					},
					StubSearchWorkBehavior.of(
							2,
							Collections.singletonList( "hit1Text" ),
							Collections.singletonList( "hit2Text" )
					)
			);

			assertThat( session.search( IndexedEntity.class )
					.select( SimpleMyProjection.class )
					.where( f -> f.matchAll() )
					.fetchAllHits() )
					.usingRecursiveFieldByFieldElementComparator()
					.containsExactly(
							new SimpleMyProjection( "hit1Text" ),
							new SimpleMyProjection( "hit2Text" )
					);
		}
		backendMock.verifyExpectationsMet();
	}

	static class SimpleMyProjection {
		public final String text;

		@ProjectionConstructor
		public SimpleMyProjection(@WorkingAnnotation String text) {
			this.text = text;
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	@MethodParameterMapping(processor = @MethodParameterMappingAnnotationProcessorRef(type = WorkingAnnotation.Processor.class))
	private @interface WorkingAnnotation {
		class Processor implements MethodParameterMappingAnnotationProcessor<WorkingAnnotation> {
			@Override
			public void process(MethodParameterMappingStep mapping, WorkingAnnotation annotation,
					MethodParameterMappingAnnotationProcessorContext context) {
				mapping.projection( bindingContext -> {
					bindingContext.definition( String.class,
							(factory, definitionContext) -> factory.field( "myText", String.class ).toProjection() );
				} );
			}
		}
	}

	@Test
	void missingProcessorReference() {
		@Indexed
		class IndexedEntity {
			@DocumentId
			Long id;

			public IndexedEntity(@AnnotationWithEmptyProcessorRef Long id) {
				this.id = id;
			}
		}
		assertThatThrownBy(
				() -> setupHelper.start().setup( IndexedEntity.class )
		)
				.isInstanceOf( SearchException.class )
				.satisfies( FailureReportUtils.hasFailureReport()
						.annotationTypeContext( AnnotationWithEmptyProcessorRef.class )
						.failure( "Empty annotation processor reference in meta-annotation '"
								+ MethodParameterMapping.class.getName() + "'" ) );
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	@MethodParameterMapping(processor = @MethodParameterMappingAnnotationProcessorRef())
	private @interface AnnotationWithEmptyProcessorRef {
	}

	@Test
	void invalidAnnotationType() {
		@Indexed
		class IndexedEntity {
			@DocumentId
			Long id;

			public IndexedEntity(@AnnotationWithProcessorWithDifferentAnnotationType Long id) {
				this.id = id;
			}
		}
		assertThatThrownBy( () -> setupHelper.start().expectCustomBeans().setup( IndexedEntity.class ) )
				.isInstanceOf( SearchException.class )
				.satisfies( FailureReportUtils.hasFailureReport()
						.annotationTypeContext( AnnotationWithProcessorWithDifferentAnnotationType.class )
						.failure( "Invalid annotation processor: '" + DifferentAnnotationType.Processor.TO_STRING + "'",
								"This processor expects annotations of a different type: '"
										+ DifferentAnnotationType.class.getName() + "'" ) );
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	@MethodParameterMapping(
			processor = @MethodParameterMappingAnnotationProcessorRef(type = DifferentAnnotationType.Processor.class))
	private @interface AnnotationWithProcessorWithDifferentAnnotationType {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	private @interface DifferentAnnotationType {
		class Processor
				implements MethodParameterMappingAnnotationProcessor<
						CustomMethodParameterMappingAnnotationBaseIT.DifferentAnnotationType> {
			public static final String TO_STRING = "DifferentAnnotationType.Processor";

			@Override
			public void process(MethodParameterMappingStep mapping,
					CustomMethodParameterMappingAnnotationBaseIT.DifferentAnnotationType annotation,
					MethodParameterMappingAnnotationProcessorContext context) {
				throw new UnsupportedOperationException( "This should not be called" );
			}

			@Override
			public String toString() {
				return TO_STRING;
			}
		}
	}

	@Test
	void annotatedElement() {
		@Indexed(index = INDEX_NAME)
		class IndexedEntity {
			@DocumentId
			Integer id;
		}

		backendMock.expectAnySchema( INDEX_NAME );

		SearchMapping mapping = setupHelper.start().expectCustomBeans()
				.withAnnotatedTypes( AnnotatedElementMyProjection.class )
				.setup( IndexedEntity.class );
		backendMock.verifyExpectationsMet();

		assertThat( counters.get( AnnotatedElementAwareAnnotation.CONSTRUCTOR_PARAMETER_WITH_OTHER_ANNOTATION ) )
				.isEqualTo( 1 );
		assertThat( counters
				.get( AnnotatedElementAwareAnnotation.CONSTRUCTOR_PARAMETER_WITH_EXPLICIT_REPEATABLE_OTHER_ANNOTATION ) )
				.isEqualTo( 1 );
		assertThat( counters
				.get( AnnotatedElementAwareAnnotation.CONSTRUCTOR_PARAMETER_WITH_IMPLICIT_REPEATABLE_OTHER_ANNOTATION ) )
				.isEqualTo( 1 );
	}

	static class AnnotatedElementMyProjection {
		@ProjectionConstructor
		public AnnotatedElementMyProjection(
				@AnnotatedElementAwareAnnotation @OtherAnnotationForAnnotatedElementAwareAnnotation(
						name = "nonRepeatable") String paramWithOtherAnnotation,
				@AnnotatedElementAwareAnnotation @RepeatableOtherAnnotationForAnnotatedElementAwareAnnotation.List({
						@RepeatableOtherAnnotationForAnnotatedElementAwareAnnotation(name = "explicitRepeatable1"),
						@RepeatableOtherAnnotationForAnnotatedElementAwareAnnotation(name = "explicitRepeatable2")
				}) String paramWithExplicitRepeatableOtherAnnotation,
				@AnnotatedElementAwareAnnotation @RepeatableOtherAnnotationForAnnotatedElementAwareAnnotation(
						name = "implicitRepeatable1") @RepeatableOtherAnnotationForAnnotatedElementAwareAnnotation(
								name = "implicitRepeatable2") String paramWithImplicitRepeatableOtherAnnotation) {
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	@MethodParameterMapping(
			processor = @MethodParameterMappingAnnotationProcessorRef(type = AnnotatedElementAwareAnnotation.Processor.class))
	private @interface AnnotatedElementAwareAnnotation {
		StaticCounters.Key CONSTRUCTOR_PARAMETER_WITH_OTHER_ANNOTATION = StaticCounters.createKey();
		StaticCounters.Key CONSTRUCTOR_PARAMETER_WITH_EXPLICIT_REPEATABLE_OTHER_ANNOTATION = StaticCounters.createKey();
		StaticCounters.Key CONSTRUCTOR_PARAMETER_WITH_IMPLICIT_REPEATABLE_OTHER_ANNOTATION = StaticCounters.createKey();

		class Processor
				implements MethodParameterMappingAnnotationProcessor<
						CustomMethodParameterMappingAnnotationBaseIT.AnnotatedElementAwareAnnotation> {
			@Override
			public void process(MethodParameterMappingStep mapping,
					CustomMethodParameterMappingAnnotationBaseIT.AnnotatedElementAwareAnnotation annotation,
					MethodParameterMappingAnnotationProcessorContext context) {
				MappingAnnotatedMethodParameter annotatedElement = context.annotatedElement();
				if ( annotatedElement.name().get().equals( "paramWithOtherAnnotation" ) ) {
					assertThat( annotatedElement.allAnnotations()
							.filter( a -> OtherAnnotationForAnnotatedElementAwareAnnotation.class.equals( a.annotationType() ) )
							.map( a -> ( (OtherAnnotationForAnnotatedElementAwareAnnotation) a ).name() )
							.toArray() )
							.containsExactlyInAnyOrder( "nonRepeatable" );
					StaticCounters.get().increment( CONSTRUCTOR_PARAMETER_WITH_OTHER_ANNOTATION );
				}
				else if ( annotatedElement.name().get().equals( "paramWithExplicitRepeatableOtherAnnotation" ) ) {
					assertThat( annotatedElement.allAnnotations()
							.filter( a -> RepeatableOtherAnnotationForAnnotatedElementAwareAnnotation.class
									.equals( a.annotationType() ) )
							.map( a -> ( (RepeatableOtherAnnotationForAnnotatedElementAwareAnnotation) a ).name() )
							.toArray() )
							.containsExactlyInAnyOrder( "explicitRepeatable1", "explicitRepeatable2" );
					StaticCounters.get().increment( CONSTRUCTOR_PARAMETER_WITH_EXPLICIT_REPEATABLE_OTHER_ANNOTATION );
				}
				else if ( annotatedElement.name().get().equals( "paramWithImplicitRepeatableOtherAnnotation" ) ) {
					assertThat( annotatedElement.allAnnotations()
							.filter( a -> RepeatableOtherAnnotationForAnnotatedElementAwareAnnotation.class
									.equals( a.annotationType() ) )
							.map( a -> ( (RepeatableOtherAnnotationForAnnotatedElementAwareAnnotation) a ).name() )
							.toArray() )
							.containsExactlyInAnyOrder( "implicitRepeatable1", "implicitRepeatable2" );
					StaticCounters.get().increment( CONSTRUCTOR_PARAMETER_WITH_IMPLICIT_REPEATABLE_OTHER_ANNOTATION );
				}
			}
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	private @interface OtherAnnotationForAnnotatedElementAwareAnnotation {

		String name();

	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	@Repeatable(RepeatableOtherAnnotationForAnnotatedElementAwareAnnotation.List.class)
	// Must be public in order for Hibernate Search to be able to access List#value
	public @interface RepeatableOtherAnnotationForAnnotatedElementAwareAnnotation {

		String name();

		@Retention(RetentionPolicy.RUNTIME)
		@Target(ElementType.PARAMETER)
		@interface List {
			RepeatableOtherAnnotationForAnnotatedElementAwareAnnotation[] value();
		}

	}

	@Test
	void eventContext() {
		assumeTrue(
				Runtime.version().feature() < 25,
				"With JDK 25+ nonstatic (inner class) projections are not supported."
		);
		@Indexed(index = INDEX_NAME)
		class IndexedEntityType {
			@DocumentId
			Integer id;

			@ProjectionConstructor
			IndexedEntityType(@EventContextAwareAnnotation String text) {
			}
		}

		backendMock.expectAnySchema( INDEX_NAME );

		SearchMapping mapping = setupHelper.start().expectCustomBeans().setup( IndexedEntityType.class );
		backendMock.verifyExpectationsMet();

		assertThat( EventContextAwareAnnotation.Processor.lastProcessedContext ).isNotNull();
		// Ideally we would not need a regexp here,
		// but the annotation can be rendered differently depending on the JDK in use...
		// See https://bugs.openjdk.java.net/browse/JDK-8282230
		assertThat( EventContextAwareAnnotation.Processor.lastProcessedContext.render() )
				.matches( "\\Qtype '" + IndexedEntityType.class.getName() + "', constructor with parameter types ["
						+ CustomMethodParameterMappingAnnotationBaseIT.class.getName() // Implicit parameter because we're declaring a nested class
						+ ", " + String.class.getName() + "]"
						+ ", parameter at index 1 (text)"
						+ ", annotation '@\\E.*"
						+ EventContextAwareAnnotation.class.getSimpleName() + "\\Q()\\E'" );
	}

	@Test
	void eventContextStaticClass() {
		backendMock.expectAnySchema( INDEX_NAME );

		SearchMapping mapping = setupHelper.start().expectCustomBeans().setup( EventContextIndexedEntityType.class );
		backendMock.verifyExpectationsMet();

		assertThat( EventContextAwareAnnotation.Processor.lastProcessedContext ).isNotNull();
		// Ideally we would not need a regexp here,
		// but the annotation can be rendered differently depending on the JDK in use...
		// See https://bugs.openjdk.java.net/browse/JDK-8282230
		assertThat( EventContextAwareAnnotation.Processor.lastProcessedContext.render() )
				.matches( "\\Qtype '" + EventContextIndexedEntityType.class.getName() + "', constructor with parameter types ["
						+ String.class.getName() + "]"
						+ ", parameter at index 0 (text)"
						+ ", annotation '@\\E.*"
						+ EventContextAwareAnnotation.class.getSimpleName() + "\\Q()\\E'" );
	}

	@Indexed(index = INDEX_NAME)
	static class EventContextIndexedEntityType {
		@DocumentId
		Integer id;

		@ProjectionConstructor
		EventContextIndexedEntityType(@EventContextAwareAnnotation String text) {
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	@MethodParameterMapping(
			processor = @MethodParameterMappingAnnotationProcessorRef(type = EventContextAwareAnnotation.Processor.class))
	private @interface EventContextAwareAnnotation {

		class Processor
				implements MethodParameterMappingAnnotationProcessor<
						CustomMethodParameterMappingAnnotationBaseIT.EventContextAwareAnnotation> {
			static EventContext lastProcessedContext = null;

			@Override
			public void process(MethodParameterMappingStep mapping,
					CustomMethodParameterMappingAnnotationBaseIT.EventContextAwareAnnotation annotation,
					MethodParameterMappingAnnotationProcessorContext context) {
				lastProcessedContext = context.eventContext();
			}
		}
	}
}
