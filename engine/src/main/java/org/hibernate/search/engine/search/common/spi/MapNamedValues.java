/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.engine.search.common.spi;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.hibernate.search.engine.logging.impl.QueryLog;
import org.hibernate.search.engine.search.common.NamedValues;
import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.common.annotation.Incubating;
import org.hibernate.search.util.common.impl.Contracts;

@Incubating
public class MapNamedValues implements NamedValues {

	/**
	 * Create a simple instance of {@link NamedValues} backed by a {@link Map map}.
	 *
	 * @param map The map with values.
	 */
	static NamedValues fromMap(Map<String, Object> map) {
		return new MapNamedValues( map );
	}

	/**
	 * Create a simple instance of {@link NamedValues} backed by a {@link Map map}.
	 *
	 * @param map The map with values.
	 * @param namedValueMissing A function that returns an exception for the name if the value is missing.
	 */
	public static NamedValues fromMap(Map<String, Object> map, Function<String, SearchException> namedValueMissing) {
		return new MapNamedValues( map, namedValueMissing::apply );
	}

	/**
	 * Create a simple instance of {@link NamedValues} backed by a {@link Map map}.
	 *
	 * @param map The map with values.
	 * @param namedValueMissing A function that returns an exception for the name if the value is missing.
	 * @param namedValueIncorrectType A function that returns an exception for the name if the value is missing.
	 */
	private static NamedValues fromMap(Map<String, Object> map,
			NamedValueMissing namedValueMissing,
			NamedValueIncorrectType namedValueIncorrectType) {
		return new MapNamedValues( map, namedValueMissing, namedValueIncorrectType );
	}

	@Incubating
	@FunctionalInterface
	protected interface NamedValueMissing {
		SearchException exception(String name);
	}

	@Incubating
	@FunctionalInterface
	protected interface NamedValueIncorrectType {
		SearchException exception(String name, Class<?> expectedType, Class<?> actualType);
	}

	protected final Map<String, Object> values;
	private final NamedValueMissing namedValueMissing;
	private final NamedValueIncorrectType namedValueIncorrectType;

	private MapNamedValues(Map<String, Object> values) {
		this( values, QueryLog.INSTANCE::namedValuesParameterNotDefined,
				QueryLog.INSTANCE::namedValuesParameterIncorrectType );
	}

	protected MapNamedValues(Map<String, Object> values, NamedValueMissing namedValueMissing) {
		this( values, namedValueMissing, QueryLog.INSTANCE::namedValuesParameterIncorrectType );
	}

	protected MapNamedValues(Map<String, Object> values,
			NamedValueMissing namedValueMissing,
			NamedValueIncorrectType namedValueIncorrectType) {
		this.values = values;
		this.namedValueMissing = namedValueMissing;
		this.namedValueIncorrectType = namedValueIncorrectType;
	}

	@Override
	public <T> T get(String name, Class<T> paramType) {
		Contracts.assertNotNull( name, "name" );
		Contracts.assertNotNull( paramType, "paramType" );

		if ( !values.containsKey( name ) ) {
			throw namedValueMissing.exception( name );
		}
		Object value = values.get( name );

		if ( value == null ) {
			return null;
		}

		if ( paramType.isAssignableFrom( value.getClass() ) ) {
			return paramType.cast( value );
		}
		throw namedValueIncorrectType.exception( name, paramType, value.getClass() );
	}

	@Override
	public <T> Optional<T> getOptional(String name, Class<T> paramType) {
		Contracts.assertNotNull( name, "name" );
		Contracts.assertNotNull( paramType, "paramType" );

		return Optional.ofNullable( values.get( name ) ).map( paramType::cast );
	}
}
