/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.jakarta.batch.core.massindexing.util.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionBuilder;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.StatelessSessionBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.search.mapper.orm.loading.spi.HibernateOrmLoadingTypeContext;

/**
 * Internal utility class for persistence usage.
 *
 * @author Mincong Huang
 */
public final class PersistenceUtil {

	private PersistenceUtil() {
		// Private constructor, do not use it.
	}

	/**
	 * Open a session with specific tenant ID. If the tenant ID argument is {@literal null} or empty, then a normal
	 * session will be returned. The entity manager factory should be not null and opened when calling this method.
	 *
	 * @param entityManagerFactory entity manager factory
	 * @param tenantId tenant ID, can be {@literal null} or empty.
	 * @return a new session
	 */
	public static Session openSession(EntityManagerFactory entityManagerFactory, Object tenantId) {
		SessionFactory sessionFactory = entityManagerFactory.unwrap( SessionFactory.class );
		SessionBuilder builder = sessionFactory.withOptions();
		if ( tenantId != null ) {
			builder.tenantIdentifier( tenantId );
		}
		Session session = builder.openSession();
		// We don't need to write to the database
		session.setDefaultReadOnly( true );
		// ... thus flushes are not necessary.
		session.setHibernateFlushMode( FlushMode.MANUAL );
		return session;
	}

	/**
	 * Open a stateless session with specific tenant ID. If the tenant ID argument is {@literal null} or empty, then a
	 * normal stateless session will be returned. The entity manager factory should be not null and opened when calling
	 * this method.
	 *
	 * @param entityManagerFactory entity manager factory
	 * @param tenantId tenant ID, can be {@literal null} or empty.
	 * @return a new stateless session
	 */
	public static StatelessSession openStatelessSession(EntityManagerFactory entityManagerFactory, Object tenantId) {
		SessionFactory sessionFactory = entityManagerFactory.unwrap( SessionFactory.class );
		@SuppressWarnings("rawtypes")
		StatelessSessionBuilder builder = sessionFactory.withStatelessOptions();
		if ( tenantId != null ) {
			// TODO: needs a converter here too!
			builder.tenantIdentifier( tenantId );
		}
		return builder.openStatelessSession();
	}

	public static List<EntityTypeDescriptor<?, ?>> createDescriptors(SessionFactoryImplementor sessionFactory,
			Set<HibernateOrmLoadingTypeContext<?>> types) {
		List<EntityTypeDescriptor<?, ?>> result = new ArrayList<>( types.size() );
		for ( HibernateOrmLoadingTypeContext<?> type : types ) {
			result.add( EntityTypeDescriptor.create( sessionFactory, type ) );
		}
		return result;
	}

}
