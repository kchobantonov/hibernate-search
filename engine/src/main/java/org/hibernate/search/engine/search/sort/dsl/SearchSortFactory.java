/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.search.sort.dsl;

import java.util.function.Consumer;
import java.util.function.Function;

import org.hibernate.search.engine.search.common.NamedValues;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.engine.search.reference.sort.DistanceSortFieldReference;
import org.hibernate.search.engine.search.reference.sort.FieldSortFieldReference;
import org.hibernate.search.engine.spatial.GeoPoint;
import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.common.annotation.Incubating;

/**
 * A factory for search sorts.
 *
 * <h2 id="field-paths">Field paths</h2>
 *
 * By default, field paths passed to this DSL are interpreted as absolute,
 * i.e. relative to the index root.
 * <p>
 * However, a new, "relative" factory can be created with {@link #withRoot(String)}:
 * the new factory interprets paths as relative to the object field passed as argument to the method.
 * <p>
 * This can be useful when calling reusable methods that can apply the same sort
 * on different object fields that have same structure (same sub-fields).
 * <p>
 * Such a factory can also transform relative paths into absolute paths using {@link #toAbsolutePath(String)};
 * this can be useful for native sorts in particular.
 *
 *
 * <h2 id="field-references">Field references</h2>
 *
 * A {@link org.hibernate.search.engine.search.reference field reference} is always represented by the absolute field path and,
 * if applicable, i.e. when a field reference is typed, a combination of the {@link org.hibernate.search.engine.search.common.ValueModel} and the type.
 * <p>
 * Field references are usually accessed from the generated Hibernate Search's static metamodel classes that describe the index structure.
 * Such reference provides the information on which search capabilities the particular index field possesses, and allows switching between different
 * {@link org.hibernate.search.engine.search.common.ValueModel value model representations}.
 *
 * @param <SR> Scope root type.
 * @author Emmanuel Bernard emmanuel@hibernate.org
 */
public interface SearchSortFactory<SR> {

	/**
	 * Order elements by their relevance score.
	 * <p>
	 * The default order is <strong>descending</strong>, i.e. higher scores come first.
	 *
	 * @return A DSL step where the "score" sort can be defined in more details.
	 */
	ScoreSortOptionsStep<SR, ?> score();

	/**
	 * Order elements by their internal index order.
	 *
	 * @return A DSL step where the "index order" sort can be defined in more details.
	 */
	SortThenStep<SR> indexOrder();

	/**
	 * Order elements by the value of a specific field.
	 * <p>
	 * The default order is <strong>ascending</strong>.
	 *
	 * @param fieldPath The <a href="#field-paths">path</a> to the index field to sort by.
	 * @return A DSL step where the "field" sort can be defined in more details.
	 * @throws SearchException If the field doesn't exist or cannot be sorted on.
	 */
	FieldSortOptionsStep<SR, ?, ? extends SearchPredicateFactory<SR>> field(String fieldPath);

	/**
	 * Order elements by the value of a specific field.
	 * <p>
	 * The default order is <strong>ascending</strong>.
	 *
	 * @param fieldReference The reference representing the <a href="#field-paths">path</a> to the index field to sort by.
	 * @return A DSL step where the "field" sort can be defined in more details.
	 * @throws SearchException If the field doesn't exist or cannot be sorted on.
	 */
	@Incubating
	<T> FieldSortOptionsGenericStep<SR, T, ?, ?, ? extends SearchPredicateFactory<SR>> field(
			FieldSortFieldReference<? super SR, T> fieldReference);

	/**
	 * Order elements by the distance from the location stored in the specified field to the location specified.
	 * <p>
	 * The default order is <strong>ascending</strong>.
	 *
	 * @param fieldPath The <a href="#field-paths">path</a> to the index field
	 * containing the location to compute the distance from.
	 * @param location The location to which we want to compute the distance.
	 * @return A DSL step where the "distance" sort can be defined in more details.
	 * @throws SearchException If the field type does not constitute a valid location.
	 */
	DistanceSortOptionsStep<SR, ?, ? extends SearchPredicateFactory<SR>> distance(String fieldPath, GeoPoint location);

	/**
	 * Order elements by the distance from the location stored in the specified field to the location specified.
	 * <p>
	 * The default order is <strong>ascending</strong>.
	 *
	 * @param fieldReference The reference representing the <a href="#field-paths">path</a> to the index field
	 * containing the location to compute the distance from.
	 * @param location The location to which we want to compute the distance.
	 * @return A DSL step where the "distance" sort can be defined in more details.
	 * @throws SearchException If the field type does not constitute a valid location.
	 */
	@Incubating
	default DistanceSortOptionsStep<SR, ?, ? extends SearchPredicateFactory<SR>> distance(
			DistanceSortFieldReference<? super SR> fieldReference, GeoPoint location) {
		return distance( fieldReference.absolutePath(), location );
	}

	/**
	 * Order elements by the distance from the location stored in the specified field to the location specified.
	 * <p>
	 * The default order is <strong>ascending</strong>.
	 *
	 * @param fieldPath The <a href="#field-paths">path</a> to the index field
	 * containing the location to compute the distance from.
	 * @param latitude The latitude of the location to which we want to compute the distance.
	 * @param longitude The longitude of the location to which we want to compute the distance.
	 * @return A DSL step where the "distance" sort can be defined in more details.
	 * @throws SearchException If the field type does not constitute a valid location.
	 */
	default DistanceSortOptionsStep<SR, ?, ? extends SearchPredicateFactory<SR>> distance(String fieldPath, double latitude,
			double longitude) {
		return distance( fieldPath, GeoPoint.of( latitude, longitude ) );
	}

	/**
	 * Order elements by the distance from the location stored in the specified field to the location specified.
	 * <p>
	 * The default order is <strong>ascending</strong>.
	 *
	 * @param fieldReference The reference representing the <a href="#field-paths">path</a> to the index field
	 * containing the location to compute the distance from.
	 * @param latitude The latitude of the location to which we want to compute the distance.
	 * @param longitude The longitude of the location to which we want to compute the distance.
	 * @return A DSL step where the "distance" sort can be defined in more details.
	 * @throws SearchException If the field type does not constitute a valid location.
	 */
	@Incubating
	default DistanceSortOptionsStep<SR, ?, ? extends SearchPredicateFactory<SR>> distance(
			DistanceSortFieldReference<? super SR> fieldReference, double latitude,
			double longitude) {
		return distance( fieldReference, GeoPoint.of( latitude, longitude ) );
	}

	/**
	 * Order by a sort composed of several elements.
	 * <p>
	 * Note that, in general, calling this method is not necessary as you can chain sorts by calling
	 * {@link SortThenStep#then()}.
	 * This method is mainly useful to mix imperative and declarative style when building sorts.
	 * See {@link #composite(Consumer)}
	 *
	 * @return A DSL step where the "composite" sort can be defined in more details.
	 */
	CompositeSortComponentsStep<SR, ?> composite();

	/**
	 * Order by a sort composed of several elements,
	 * which will be defined by the given consumer.
	 * <p>
	 * Best used with lambda expressions.
	 * <p>
	 * This is mainly useful to mix imperative and declarative style when building sorts, e.g.:
	 * <pre>{@code
	 * f.composite( c -> {
	 *    c.add( f.field( "category" ) );
	 *    if ( someInput != null ) {
	 *        c.add( f.distance( "location", someInput.getLatitude(), someInput.getLongitude() );
	 *    }
	 *    c.add( f.indexOrder() );
	 * } )
	 * }</pre>
	 *
	 * @param elementContributor A consumer that will add clauses to the step passed in parameter.
	 * Should generally be a lambda expression.
	 * @return A DSL step where the "composite" sort can be defined in more details.
	 */
	SortThenStep<SR> composite(Consumer<? super CompositeSortComponentsStep<SR, ?>> elementContributor);

	/**
	 * Delegating sort that creates the actual sort at query create time and provides access to query parameters.
	 * <p>
	 * Which sort exactly to create is defined by a function passed to the arguments of this sort.
	 *
	 * @param sortCreator The function defining an actual sort to apply.
	 * @return A final DSL step in a parameterized sort definition.
	 */
	@Incubating
	SortThenStep<SR> withParameters(Function<? super NamedValues, ? extends SortFinalStep> sortCreator);

	/**
	 * Extend the current factory with the given extension,
	 * resulting in an extended factory offering different types of sorts.
	 *
	 * @param extension The extension to the sort DSL.
	 * @param <T> The type of factory provided by the extension.
	 * @return The extended factory.
	 * @throws SearchException If the extension cannot be applied (wrong underlying backend, ...).
	 */
	<T> T extension(SearchSortFactoryExtension<SR, T> extension);

	/**
	 * Create a DSL step allowing multiple attempts to apply extensions one after the other,
	 * failing only if <em>none</em> of the extensions is supported.
	 * <p>
	 * If you only need to apply a single extension and fail if it is not supported,
	 * use the simpler {@link #extension(SearchSortFactoryExtension)} method instead.
	 *
	 * @return A DSL step.
	 */
	SearchSortFactoryExtensionIfSupportedStep<SR> extension();

	/**
	 * Create a new sort factory whose root for all paths passed to the DSL
	 * will be the given object field.
	 * <p>
	 * This is used to call reusable methods that can apply the same sort
	 * on different object fields that have same structure (same sub-fields).
	 *
	 * @param objectFieldPath The path from the current root to an object field that will become the new root.
	 * @return A new sort factory using the given object field as root.
	 */
	@Incubating
	SearchSortFactory<SR> withRoot(String objectFieldPath);

	/**
	 * @param relativeFieldPath The path to a field, relative to the {@link #withRoot(String) root} of this factory.
	 * @return The absolute path of the field, for use in native sorts for example.
	 * Note the path is returned even if the field doesn't exist.
	 */
	@Incubating
	String toAbsolutePath(String relativeFieldPath);

}
