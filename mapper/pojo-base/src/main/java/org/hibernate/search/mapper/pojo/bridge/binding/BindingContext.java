/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.pojo.bridge.binding;

import java.util.Optional;

import org.hibernate.search.engine.environment.bean.BeanResolver;
import org.hibernate.search.engine.search.common.NamedValues;
import org.hibernate.search.util.common.SearchException;

public interface BindingContext {

	/**
	 * @return A bean provider, allowing the retrieval of beans,
	 * including CDI/Spring DI beans when in the appropriate environment.
	 */
	BeanResolver beanResolver();

	/**
	 * @param name The name of the param
	 * @return Get a param defined for the binder by the given name
	 * @throws SearchException if it does not exist a param having such name
	 * @deprecated Use {@link #params()} instead.
	 */
	@Deprecated(since = "7.0")
	default Object param(String name) {
		return params().get( name, Object.class );
	}

	/**
	 * @param name The name of the param
	 * @param paramType The type of the parameter.
	 * @param <T> The type of the parameter.
	 * @return Get a param defined for the binder by the given name
	 * @throws SearchException if it does not exist a param having such name
	 * @deprecated Use {@link #params()} instead.
	 */
	@Deprecated(since = "7.2")
	default <T> T param(String name, Class<T> paramType) {
		return params().get( name, paramType );
	}

	/**
	 * @param name The name of the param
	 * @return Get an optional param defined for the binder by the given name,
	 * a param having such name may either exist or not.
	 * @deprecated Use {@link #params()} instead.
	 */
	@Deprecated(since = "7.0")
	default Optional<Object> paramOptional(String name) {
		return params().getOptional( name, Object.class );
	}

	/**
	 * @param name The name of the param
	 * @param paramType The type of the parameter.
	 * @param <T> The type of the parameter.
	 * @return Get an optional param defined for the binder by the given name,
	 * a param having such name may either exist or not.
	 * @deprecated Use {@link #params()} instead.
	 */
	@Deprecated(since = "7.2")
	default <T> Optional<T> paramOptional(String name, Class<T> paramType) {
		return params().getOptional( name, paramType );
	}

	/**
	 * @return Parameters defined for the binder.
	 *
	 * @see NamedValues#get(String, Class)
	 * @see NamedValues#getOptional(String, Class)
	 */
	NamedValues params();

}
