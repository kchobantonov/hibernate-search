/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.jakarta.batch.core.context.jpa.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.search.jakarta.batch.core.context.jpa.spi.EntityManagerFactoryRegistry;
import org.hibernate.search.jakarta.batch.core.logging.impl.JakartaBatchLog;

/**
 * A registry containing all the currently active (non-closed) session factories
 * that use the same classloader.
 * <p>
 * This implementation has the advantage of not relying on external frameworks
 * (like a dependency injection framework), but has two downsides:
 * <ul>
 * <li>Session factories cannot be instantiated on demand: they must
 * be created <strong>before</strong> being retrieved from the registry.
 * <li>Only session factories created from the same classloader are
 * visible in the registry.
 * </ul>
 *
 * @author Yoann Rodiere
 */
public class ActiveSessionFactoryRegistry implements EntityManagerFactoryRegistry {

	private static final ActiveSessionFactoryRegistry INSTANCE = new ActiveSessionFactoryRegistry();

	private static final String PERSISTENCE_UNIT_NAME_NAMESPACE = "persistence-unit-name";
	private static final String SESSION_FACTORY_NAME_NAMESPACE = "session-factory-name";

	public static ActiveSessionFactoryRegistry getInstance() {
		return INSTANCE;
	}

	private final Collection<SessionFactoryImplementor> sessionFactories = new HashSet<>();

	private final ConcurrentMap<String, SessionFactoryImplementor> sessionFactoriesByPUName = new ConcurrentHashMap<>();

	private final ConcurrentMap<String, SessionFactoryImplementor> sessionFactoriesByName = new ConcurrentHashMap<>();

	private ActiveSessionFactoryRegistry() {
		// Use getInstance()
	}

	public synchronized void register(SessionFactoryImplementor sessionFactory) {
		sessionFactories.add( sessionFactory );
		Object persistenceUnitName = sessionFactory.getProperties().get( AvailableSettings.PERSISTENCE_UNIT_NAME );
		if ( persistenceUnitName instanceof String ) {
			sessionFactoriesByPUName.put( (String) persistenceUnitName, sessionFactory );
		}
		String name = sessionFactory.getName();
		if ( name != null ) {
			sessionFactoriesByName.put( name, sessionFactory );
		}
	}

	public synchronized void unregister(SessionFactoryImplementor sessionFactory) {
		sessionFactories.remove( sessionFactory );
		/*
		 * Remove by value. This is inefficient, but we don't expect to have billions of session factories anyway,
		 * and it allows to easily handle the case where multiple session factories have been registered with the same name.
		 */
		sessionFactoriesByPUName.values().remove( sessionFactory );
		sessionFactoriesByName.values().remove( sessionFactory );
	}

	@Override
	public synchronized EntityManagerFactory useDefault() {
		if ( sessionFactories.isEmpty() ) {
			throw JakartaBatchLog.INSTANCE.noEntityManagerFactoryCreated();
		}
		else if ( sessionFactories.size() > 1 ) {
			throw JakartaBatchLog.INSTANCE.tooManyActiveEntityManagerFactories();
		}
		else {
			return sessionFactories.iterator().next();
		}
	}

	@Override
	public EntityManagerFactory get(String reference) {
		return get( PERSISTENCE_UNIT_NAME_NAMESPACE, reference );
	}

	@Override
	public EntityManagerFactory get(String namespace, String reference) {
		SessionFactory factory;

		switch ( namespace ) {
			case PERSISTENCE_UNIT_NAME_NAMESPACE:
				factory = sessionFactoriesByPUName.get( reference );
				if ( factory == null ) {
					throw JakartaBatchLog.INSTANCE.cannotFindEntityManagerFactoryByPUName( reference );
				}
				break;
			case SESSION_FACTORY_NAME_NAMESPACE:
				factory = sessionFactoriesByName.get( reference );
				if ( factory == null ) {
					throw JakartaBatchLog.INSTANCE.cannotFindEntityManagerFactoryByName( reference );
				}
				break;
			default:
				throw JakartaBatchLog.INSTANCE.unknownEntityManagerFactoryNamespace( namespace );
		}

		return factory;
	}

}
