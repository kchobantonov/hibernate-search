/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */

package org.hibernate.search.test.embedded.path.depth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.hibernate.Session;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.hibernate.search.test.embedded.path.AbstractDepthPathCaseEmbeddedTest;
import org.hibernate.search.util.common.SearchException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.lucene.search.Query;

/**
 * @author Davide D'Alto
 */
class PathRespectDepthCaseEmbeddedTest extends AbstractDepthPathCaseEmbeddedTest {

	private Session s = null;
	private EntityA entityA = null;

	@Override
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();
		EntityC indexed = new EntityC( "indexed" );
		EntityC skipped = new EntityC( "skipped" );

		EntityB indexedB = new EntityB( indexed, skipped );
		indexedB.insideThreshold = "insideThreshold";

		entityA = new EntityA( indexedB );
		s = openSession();
		persistEntity( s, indexed, skipped, indexedB, entityA );
	}

	@Override
	@AfterEach
	public void tearDown() throws Exception {
		s.clear();

		deleteAll( s, EntityA.class, EntityB.class, EntityC.class );
		s.close();
		super.tearDown();
	}

	@Test
	void testFieldIsIndexedIfInPath() {
		List<EntityA> result = search( s, "b.indexed.field", "indexed" );

		assertThat( result ).hasSize( 1 );
		assertThat( result.get( 0 ).id ).isEqualTo( entityA.id );
	}

	@Test
	void testFieldIsIndexedIfInsideDepthThreshold() {
		List<EntityA> result = search( s, "b.insideThreshold", "insideThreshold" );

		assertThat( result ).hasSize( 1 );
		assertThat( result.get( 0 ).id ).isEqualTo( entityA.id );
	}

	@Test
	void testEmbeddedNotIndexedIfNotInPath() {
		assertThatThrownBy( () -> search( s, "b.skipped.indexed", "indexed" ) )
				.isInstanceOf( SearchException.class );
	}

	@Test
	void testFieldNotIndexedIfNotInPath() {
		assertThatThrownBy( () -> search( s, "b.indexed.skipped", "skipped" ) )
				.isInstanceOf( SearchException.class );
	}

	private List<EntityA> search(Session s, String field, String value) {
		FullTextSession session = Search.getFullTextSession( s );
		QueryBuilder queryBuilder = session.getSearchFactory().buildQueryBuilder().forEntity( EntityA.class ).get();
		Query query = queryBuilder.keyword().onField( field ).matching( value ).createQuery();
		@SuppressWarnings("unchecked")
		List<EntityA> result = session.createFullTextQuery( query ).list();
		return result;
	}

	@Override
	public Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] { EntityA.class, EntityB.class, EntityC.class };
	}
}
