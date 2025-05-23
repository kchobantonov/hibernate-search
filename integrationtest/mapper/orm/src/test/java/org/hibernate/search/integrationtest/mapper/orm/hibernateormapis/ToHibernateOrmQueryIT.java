/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.integrationtest.mapper.orm.hibernateormapis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.search.util.impl.integrationtest.common.stub.backend.StubBackendUtils.reference;
import static org.hibernate.search.util.impl.integrationtest.mapper.orm.ManagedAssert.assertThatManaged;
import static org.hibernate.search.util.impl.integrationtest.mapper.orm.OrmUtils.with;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.QueryTimeoutException;

import org.hibernate.SessionFactory;
import org.hibernate.graph.GraphSemantic;
import org.hibernate.query.Query;
import org.hibernate.search.engine.search.query.SearchQuery;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;
import org.hibernate.search.util.common.SearchTimeoutException;
import org.hibernate.search.util.impl.integrationtest.common.extension.BackendMock;
import org.hibernate.search.util.impl.integrationtest.common.extension.StubSearchWorkBehavior;
import org.hibernate.search.util.impl.integrationtest.mapper.orm.OrmSetupHelper;
import org.hibernate.search.util.impl.test.annotation.TestForIssue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Test the compatibility layer between our APIs and Hibernate ORM APIs
 * for the {@link Query} class.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ToHibernateOrmQueryIT {

	@RegisterExtension
	public static BackendMock backendMock = BackendMock.create();

	@RegisterExtension
	public static OrmSetupHelper ormSetupHelper = OrmSetupHelper.withBackendMock( backendMock );
	private SessionFactory sessionFactory;

	@BeforeAll
	void setup() {
		backendMock.expectAnySchema( IndexedEntity.NAME );
		sessionFactory = ormSetupHelper.start().withAnnotatedTypes(
				IndexedEntity.class, ContainedEntity.class )
				.setup();
	}

	@BeforeEach
	void initData() {
		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity indexed1 = new IndexedEntity();
			indexed1.setId( 1 );
			indexed1.setText( "this is text (1)" );
			ContainedEntity contained1_1 = new ContainedEntity();
			contained1_1.setId( 11 );
			contained1_1.setText( "this is text (1_1)" );
			indexed1.setContainedEager( contained1_1 );
			contained1_1.setContainingEager( indexed1 );
			ContainedEntity contained1_2 = new ContainedEntity();
			contained1_2.setId( 12 );
			contained1_2.setText( "this is text (1_2)" );
			indexed1.getContainedLazy().add( contained1_2 );
			contained1_2.setContainingLazy( indexed1 );

			IndexedEntity indexed2 = new IndexedEntity();
			indexed2.setId( 2 );
			indexed2.setText( "some more text (2)" );
			ContainedEntity contained2_1 = new ContainedEntity();
			contained2_1.setId( 21 );
			contained2_1.setText( "this is text (2_1)" );
			indexed2.setContainedEager( contained2_1 );
			contained2_1.setContainingEager( indexed2 );
			ContainedEntity contained2_2 = new ContainedEntity();
			contained2_2.setId( 22 );
			contained2_2.setText( "this is text (2_2)" );
			indexed2.getContainedLazy().add( contained2_2 );
			contained2_2.setContainingLazy( indexed2 );

			session.persist( contained1_1 );
			session.persist( contained1_2 );
			session.persist( indexed1 );
			session.persist( contained2_1 );
			session.persist( contained2_2 );
			session.persist( indexed2 );

			backendMock.expectWorks( IndexedEntity.NAME )
					.add( "1", b -> b
							.field( "text", indexed1.getText() )
							.objectField( "containedEager", b2 -> b2
									.field( "text", contained1_1.getText() )
							)
							.objectField( "containedLazy", b2 -> b2
									.field( "text", contained1_2.getText() )
							)
					)
					.add( "2", b -> b
							.field( "text", indexed2.getText() )
							.objectField( "containedEager", b2 -> b2
									.field( "text", contained2_1.getText() )
							)
							.objectField( "containedLazy", b2 -> b2
									.field( "text", contained2_2.getText() )
							)
					);
		} );
		backendMock.verifyExpectationsMet();
	}

	@Test
	void toHibernateOrmQuery() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery( createSimpleQuery( searchSession ) );
			assertThat( query ).isNotNull();
		} );
	}

	@Test
	void list() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery( createSimpleQuery( searchSession ) );

			backendMock.expectSearchObjects(
					Arrays.asList( IndexedEntity.NAME ),
					b -> {},
					StubSearchWorkBehavior.of(
							6L,
							reference( IndexedEntity.NAME, "1" ),
							reference( IndexedEntity.NAME, "2" )
					)
			);
			List<IndexedEntity> result = query.list();
			backendMock.verifyExpectationsMet();
			assertThat( result )
					.containsExactly(
							session.getReference( IndexedEntity.class, 1 ),
							session.getReference( IndexedEntity.class, 2 )
					);
		} );
	}

	@Test
	void uniqueResult() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery( createSimpleQuery( searchSession ) );

			backendMock.expectSearchObjects(
					Arrays.asList( IndexedEntity.NAME ),
					b -> {},
					StubSearchWorkBehavior.of(
							1L,
							reference( IndexedEntity.NAME, "1" )
					)
			);
			IndexedEntity result = query.uniqueResult();
			backendMock.verifyExpectationsMet();
			assertThat( result )
					.isEqualTo( session.getReference( IndexedEntity.class, 1 ) );

			backendMock.expectSearchObjects(
					Arrays.asList( IndexedEntity.NAME ),
					b -> {},
					StubSearchWorkBehavior.empty()
			);
			result = query.uniqueResult();
			backendMock.verifyExpectationsMet();
			assertThat( result ).isNull();

			backendMock.expectSearchObjects(
					Arrays.asList( IndexedEntity.NAME ),
					b -> {},
					StubSearchWorkBehavior.of(
							2L,
							reference( IndexedEntity.NAME, "1" ),
							reference( IndexedEntity.NAME, "2" )
					)
			);
			assertThatThrownBy( () -> query.uniqueResult() )
					.isInstanceOf( org.hibernate.NonUniqueResultException.class );
			backendMock.verifyExpectationsMet();

			backendMock.expectSearchObjects(
					Arrays.asList( IndexedEntity.NAME ),
					b -> {},
					StubSearchWorkBehavior.of(
							2L,
							reference( IndexedEntity.NAME, "1" ),
							reference( IndexedEntity.NAME, "1" )
					)
			);
			result = query.uniqueResult();
			backendMock.verifyExpectationsMet();
			assertThat( result )
					.isEqualTo( session.getReference( IndexedEntity.class, 1 ) );
		} );
	}

	@Test
	void pagination() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery( createSimpleQuery( searchSession ) );

			assertThat( query.getFirstResult() ).isEqualTo( 0 );
			assertThat( query.getMaxResults() ).isEqualTo( Integer.MAX_VALUE );

			query.setFirstResult( 3 );
			query.setMaxResults( 2 );

			assertThat( query.getFirstResult() ).isEqualTo( 3 );
			assertThat( query.getMaxResults() ).isEqualTo( 2 );

			backendMock.expectSearchObjects(
					Arrays.asList( IndexedEntity.NAME ),
					b -> b
							.offset( 3 )
							.limit( 2 ),
					StubSearchWorkBehavior.empty()
			);
			query.list();
			backendMock.verifyExpectationsMet();
		} );
	}

	@Test
	void timeout_dsl() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery(
					searchSession.search( IndexedEntity.class )
							.where( f -> f.matchAll() )
							.failAfter( 2, TimeUnit.SECONDS )
							.toQuery()
			);

			SearchTimeoutException timeoutException = new SearchTimeoutException( "Timed out" );

			backendMock.expectSearchObjects(
					Arrays.asList( IndexedEntity.NAME ),
					b -> b.failAfter( 2, TimeUnit.SECONDS ),
					StubSearchWorkBehavior.failing( () -> timeoutException )
			);

			// Just check that the exception is propagated
			assertThatThrownBy( () -> query.list() )
					.isInstanceOf( QueryTimeoutException.class )
					.hasCause( timeoutException );
		} );
	}

	@Test
	void timeout_jpaHint() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery( createSimpleQuery( searchSession ) );

			query.setHint( "jakarta.persistence.query.timeout", 200 );

			SearchTimeoutException timeoutException = new SearchTimeoutException( "Timed out" );

			backendMock.expectSearchObjects(
					Arrays.asList( IndexedEntity.NAME ),
					b -> b.failAfter( 200, TimeUnit.MILLISECONDS ),
					StubSearchWorkBehavior.failing( () -> timeoutException )
			);

			// Just check that the exception is propagated
			assertThatThrownBy( () -> query.list() )
					.isInstanceOf( QueryTimeoutException.class )
					.hasCause( timeoutException );
		} );
	}

	@Test
	void timeout_ormHint() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery( createSimpleQuery( searchSession ) );

			query.setHint( "org.hibernate.timeout", 4 );

			SearchTimeoutException timeoutException = new SearchTimeoutException( "Timed out" );

			backendMock.expectSearchObjects(
					Arrays.asList( IndexedEntity.NAME ),
					b -> b.failAfter( 4, TimeUnit.SECONDS ),
					StubSearchWorkBehavior.failing( () -> timeoutException )
			);

			// Just check that the exception is propagated
			assertThatThrownBy( () -> query.list() )
					.isInstanceOf( QueryTimeoutException.class )
					.hasCause( timeoutException );
		} );
	}

	@Test
	void timeout_setter() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery( createSimpleQuery( searchSession ) );

			query.setTimeout( 3 );

			SearchTimeoutException timeoutException = new SearchTimeoutException( "Timed out" );

			backendMock.expectSearchObjects(
					Arrays.asList( IndexedEntity.NAME ),
					b -> b.failAfter( 3, TimeUnit.SECONDS ),
					StubSearchWorkBehavior.failing( () -> timeoutException )
			);

			// Just check that the exception is propagated
			assertThatThrownBy( () -> query.list() )
					.isInstanceOf( QueryTimeoutException.class )
					.hasCause( timeoutException );
		} );
	}

	@Test
	void timeout_override_ormHint() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery(
					searchSession.search( IndexedEntity.class )
							.where( f -> f.matchAll() )
							.failAfter( 2, TimeUnit.SECONDS )
							.toQuery()
			);

			query.setHint( "org.hibernate.timeout", 4 );

			SearchTimeoutException timeoutException = new SearchTimeoutException( "Timed out" );

			backendMock.expectSearchObjects(
					Arrays.asList( IndexedEntity.NAME ),
					b -> b.failAfter( 4, TimeUnit.SECONDS ),
					StubSearchWorkBehavior.failing( () -> timeoutException )
			);

			// Just check that the exception is propagated
			assertThatThrownBy( () -> query.list() )
					.isInstanceOf( QueryTimeoutException.class )
					.hasCause( timeoutException );
		} );
	}

	@Test
	void timeout_override_setter() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery(
					searchSession.search( IndexedEntity.class )
							.where( f -> f.matchAll() )
							.failAfter( 2, TimeUnit.SECONDS )
							.toQuery()
			);

			query.setTimeout( 3 );

			SearchTimeoutException timeoutException = new SearchTimeoutException( "Timed out" );

			backendMock.expectSearchObjects(
					Arrays.asList( IndexedEntity.NAME ),
					b -> b.failAfter( 3, TimeUnit.SECONDS ),
					StubSearchWorkBehavior.failing( () -> timeoutException )
			);

			// Just check that the exception is propagated
			assertThatThrownBy( () -> query.list() )
					.isInstanceOf( QueryTimeoutException.class )
					.hasCause( timeoutException );
		} );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-3628")
	void graph_jpaHint_fetch() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery( createSimpleQuery( searchSession ) );

			query.setHint( "jakarta.persistence.fetchgraph", session.getEntityGraph( IndexedEntity.GRAPH_EAGER ) );

			backendMock.expectSearchObjects(
					IndexedEntity.NAME,
					StubSearchWorkBehavior.of( 1, reference( IndexedEntity.NAME, "1" ) )
			);

			IndexedEntity loaded = query.uniqueResult();
			assertThatManaged( loaded.getContainedEager() ).isInitialized();
			assertThatManaged( loaded.getContainedLazy() ).isInitialized();
		} );

		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery( createSimpleQuery( searchSession ) );

			query.setHint( "jakarta.persistence.fetchgraph", session.getEntityGraph( IndexedEntity.GRAPH_LAZY ) );

			backendMock.expectSearchObjects(
					IndexedEntity.NAME,
					StubSearchWorkBehavior.of( 1, reference( IndexedEntity.NAME, "1" ) )
			);

			IndexedEntity loaded = query.uniqueResult();
			// FETCH graph => associations can be forced to lazy even if eager in the mapping
			assertThatManaged( loaded.getContainedEager() ).isNotInitialized();
			assertThatManaged( loaded.getContainedLazy() ).isNotInitialized();
		} );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-3628")
	void graph_jpaHint_load() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery( createSimpleQuery( searchSession ) );

			query.setHint( "jakarta.persistence.loadgraph", session.getEntityGraph( IndexedEntity.GRAPH_EAGER ) );

			backendMock.expectSearchObjects(
					Arrays.asList( IndexedEntity.NAME ), b -> {},
					StubSearchWorkBehavior.of( 1, reference( IndexedEntity.NAME, "1" ) )
			);

			IndexedEntity loaded = query.uniqueResult();
			assertThatManaged( loaded.getContainedEager() ).isInitialized();
			assertThatManaged( loaded.getContainedLazy() ).isInitialized();
		} );

		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery( createSimpleQuery( searchSession ) );

			query.setHint( "jakarta.persistence.loadgraph", session.getEntityGraph( IndexedEntity.GRAPH_LAZY ) );

			backendMock.expectSearchObjects(
					IndexedEntity.NAME,
					StubSearchWorkBehavior.of( 1, reference( IndexedEntity.NAME, "1" ) )
			);

			IndexedEntity loaded = query.uniqueResult();
			// LOAD graph => associations cannot be forced to lazy if eager in the mapping
			assertThatManaged( loaded.getContainedEager() ).isInitialized();
			assertThatManaged( loaded.getContainedLazy() ).isNotInitialized();
		} );
	}

	@SuppressWarnings("unchecked")
	@Test
	@TestForIssue(jiraKey = "HSEARCH-3628")
	void graph_setter_fetch() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery( createSimpleQuery( searchSession ) );

			query.setEntityGraph(
					(EntityGraph<IndexedEntity>) session.getSessionFactory().getNamedEntityGraphs( IndexedEntity.class )
							.get( IndexedEntity.GRAPH_EAGER ),
					GraphSemantic.FETCH
			);

			backendMock.expectSearchObjects(
					IndexedEntity.NAME,
					StubSearchWorkBehavior.of( 1, reference( IndexedEntity.NAME, "1" ) )
			);

			IndexedEntity loaded = query.uniqueResult();
			assertThatManaged( loaded.getContainedEager() ).isInitialized();
			assertThatManaged( loaded.getContainedLazy() ).isInitialized();
		} );

		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery( createSimpleQuery( searchSession ) );
			// TODO: HSEARCH-5318 use different API for the entity graphs:
			query.setEntityGraph(
					(EntityGraph<IndexedEntity>) session.getSessionFactory().getNamedEntityGraphs( IndexedEntity.class )
							.get( IndexedEntity.GRAPH_LAZY ),
					GraphSemantic.FETCH
			);

			backendMock.expectSearchObjects(
					IndexedEntity.NAME,
					StubSearchWorkBehavior.of( 1, reference( IndexedEntity.NAME, "1" ) )
			);

			IndexedEntity loaded = query.uniqueResult();
			// FETCH graph => associations can be forced to lazy even if eager in the mapping
			assertThatManaged( loaded.getContainedEager() ).isNotInitialized();
			assertThatManaged( loaded.getContainedLazy() ).isNotInitialized();
		} );
	}

	@SuppressWarnings("unchecked")
	@Test
	@TestForIssue(jiraKey = "HSEARCH-3628")
	void graph_setter_load() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery( createSimpleQuery( searchSession ) );

			// TODO: HSEARCH-5318 use different API for the entity graphs:
			query.setEntityGraph(
					(EntityGraph<IndexedEntity>) session.getSessionFactory().getNamedEntityGraphs( IndexedEntity.class )
							.get( IndexedEntity.GRAPH_EAGER ),
					GraphSemantic.LOAD
			);

			backendMock.expectSearchObjects(
					IndexedEntity.NAME,
					StubSearchWorkBehavior.of( 1, reference( IndexedEntity.NAME, "1" ) )
			);

			IndexedEntity loaded = query.uniqueResult();
			assertThatManaged( loaded.getContainedEager() ).isInitialized();
			assertThatManaged( loaded.getContainedLazy() ).isInitialized();
		} );

		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery( createSimpleQuery( searchSession ) );

			// TODO: HSEARCH-5318 use different API for the entity graphs:
			query.setEntityGraph(
					(EntityGraph<IndexedEntity>) session.getSessionFactory().getNamedEntityGraphs( IndexedEntity.class )
							.get( IndexedEntity.GRAPH_LAZY ),
					GraphSemantic.LOAD
			);

			backendMock.expectSearchObjects(
					IndexedEntity.NAME,
					StubSearchWorkBehavior.of( 1, reference( IndexedEntity.NAME, "1" ) )
			);

			IndexedEntity loaded = query.uniqueResult();
			// LOAD graph => associations cannot be forced to lazy if eager in the mapping
			assertThatManaged( loaded.getContainedEager() ).isInitialized();
			assertThatManaged( loaded.getContainedLazy() ).isNotInitialized();
		} );
	}

	@Test
	void graph_override_jpaHint() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery(
					searchSession.search( IndexedEntity.class )
							.where( f -> f.matchAll() )
							.loading( o -> o.graph( IndexedEntity.GRAPH_EAGER, GraphSemantic.LOAD ) )
							.toQuery()
			);

			query.setHint( "jakarta.persistence.fetchgraph", session.getEntityGraph( IndexedEntity.GRAPH_LAZY ) );

			backendMock.expectSearchObjects(
					IndexedEntity.NAME,
					StubSearchWorkBehavior.of( 1, reference( IndexedEntity.NAME, "1" ) )
			);

			IndexedEntity loaded = query.uniqueResult();
			// FETCH graph => associations can be forced to lazy even if eager in the mapping
			assertThatManaged( loaded.getContainedEager() ).isNotInitialized();
			assertThatManaged( loaded.getContainedLazy() ).isNotInitialized();
		} );
	}

	@SuppressWarnings("unchecked")
	@Test
	@TestForIssue(jiraKey = "HSEARCH-3628")
	void graph_override_setter() {
		with( sessionFactory ).runNoTransaction( session -> {
			SearchSession searchSession = Search.session( session );
			Query<IndexedEntity> query = Search.toOrmQuery(
					searchSession.search( IndexedEntity.class )
							.where( f -> f.matchAll() )
							.loading( o -> o.graph( IndexedEntity.GRAPH_EAGER, GraphSemantic.LOAD ) )
							.toQuery()
			);

			// TODO: HSEARCH-5318 use different API for the entity graphs:
			query.setEntityGraph(
					(EntityGraph<IndexedEntity>) session.getSessionFactory().getNamedEntityGraphs( IndexedEntity.class )
							.get( IndexedEntity.GRAPH_LAZY ),
					GraphSemantic.FETCH
			);

			backendMock.expectSearchObjects(
					IndexedEntity.NAME,
					StubSearchWorkBehavior.of( 1, reference( IndexedEntity.NAME, "1" ) )
			);

			IndexedEntity loaded = query.uniqueResult();
			// FETCH graph => associations can be forced to lazy even if eager in the mapping
			assertThatManaged( loaded.getContainedEager() ).isNotInitialized();
			assertThatManaged( loaded.getContainedLazy() ).isNotInitialized();
		} );
	}

	private SearchQuery<IndexedEntity> createSimpleQuery(SearchSession searchSession) {
		return searchSession.search( IndexedEntity.class )
				.selectEntity()
				.where( f -> f.matchAll() )
				.toQuery();
	}

	@Entity(name = IndexedEntity.NAME)
	@Indexed(index = IndexedEntity.NAME)
	@NamedEntityGraph(
			name = IndexedEntity.GRAPH_EAGER,
			includeAllAttributes = true
	)
	@NamedEntityGraph(
			name = IndexedEntity.GRAPH_LAZY
	)
	public static class IndexedEntity {

		public static final String NAME = "indexed";

		public static final String GRAPH_EAGER = "graph-eager";
		public static final String GRAPH_LAZY = "graph-lazy";

		@Id
		private Integer id;

		@FullTextField
		private String text;

		@OneToOne(fetch = FetchType.EAGER)
		@IndexedEmbedded
		private ContainedEntity containedEager;

		@OneToMany(mappedBy = "containingLazy", fetch = FetchType.LAZY)
		@IndexedEmbedded
		private List<ContainedEntity> containedLazy = new ArrayList<>();

		@Override
		public String toString() {
			return "IndexedEntity[id=" + id + "]";
		}

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

		public ContainedEntity getContainedEager() {
			return containedEager;
		}

		public void setContainedEager(ContainedEntity containedEager) {
			this.containedEager = containedEager;
		}

		public List<ContainedEntity> getContainedLazy() {
			return containedLazy;
		}
	}

	@Entity(name = ContainedEntity.NAME)
	public static class ContainedEntity {

		public static final String NAME = "contained";

		@Id
		private Integer id;

		@FullTextField
		private String text;

		@OneToOne(mappedBy = "containedEager")
		private IndexedEntity containingEager;

		@ManyToOne
		private IndexedEntity containingLazy;

		@Override
		public String toString() {
			return "ContainedEntity[id=" + id + "]";
		}

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

		public IndexedEntity getContainingEager() {
			return containingEager;
		}

		public void setContainingEager(IndexedEntity containingEager) {
			this.containingEager = containingEager;
		}

		public IndexedEntity getContainingLazy() {
			return containingLazy;
		}

		public void setContainingLazy(IndexedEntity containingLazy) {
			this.containingLazy = containingLazy;
		}
	}

}
