/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.integrationtest.mapper.orm.automaticindexing;

import static org.hibernate.search.util.impl.integrationtest.mapper.orm.OrmUtils.with;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.function.Consumer;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.work.SearchIndexingPlan;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.util.impl.integrationtest.common.extension.BackendMock;
import org.hibernate.search.util.impl.integrationtest.common.stub.backend.document.StubDocumentNode;
import org.hibernate.search.util.impl.integrationtest.mapper.orm.OrmSetupHelper;
import org.hibernate.search.util.impl.test.annotation.TestForIssue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Test automatic indexing based on Hibernate ORM entity events for basic fields.
 *
 * This test only checks basic, direct updates to the entity state.
 * Other tests in the same package check more complex updates involving associations.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutomaticIndexingBasicIT {

	@RegisterExtension
	public static BackendMock backendMock = BackendMock.create();

	@RegisterExtension
	public static OrmSetupHelper ormSetupHelper = OrmSetupHelper.withBackendMock( backendMock );
	private SessionFactory sessionFactory;

	@BeforeAll
	void setup() {
		backendMock.expectSchema( IndexedEntity.INDEX, b -> b
				.field( "indexedField", String.class )
				.field( "shallowReindexOnUpdateField", String.class )
				.field( "noReindexOnUpdateField", String.class )
		);

		sessionFactory = ormSetupHelper.start().withAnnotatedTypes( IndexedEntity.class ).setup();
	}

	@Test
	void directPersistUpdateDelete() {
		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = new IndexedEntity();
			entity1.setId( 1 );
			entity1.setIndexedField( "initialValue" );

			session.persist( entity1 );

			backendMock.expectWorks( IndexedEntity.INDEX )
					.add( "1", b -> b
							.field( "indexedField", entity1.getIndexedField() )
							.field( "shallowReindexOnUpdateField", null )
							.field( "noReindexOnUpdateField", null )
					);
		} );
		backendMock.verifyExpectationsMet();

		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = session.find( IndexedEntity.class, 1 );
			entity1.setIndexedField( "updatedValue" );

			backendMock.expectWorks( IndexedEntity.INDEX )
					.addOrUpdate( "1", b -> b
							.field( "indexedField", entity1.getIndexedField() )
							.field( "shallowReindexOnUpdateField", null )
							.field( "noReindexOnUpdateField", null )
					);
		} );
		backendMock.verifyExpectationsMet();

		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = session.find( IndexedEntity.class, 1 );

			session.remove( entity1 );

			backendMock.expectWorks( IndexedEntity.INDEX )
					.delete( "1" );
		} );
		backendMock.verifyExpectationsMet();
	}

	@Test
	void rollback_discardPreparedWorks() {
		assumeTrue( ormSetupHelper.areEntitiesProcessedInSession(),
				"This test only makes sense if entities are processed in-session" );

		with( sessionFactory ).runNoTransaction( session -> {
			Transaction trx = session.beginTransaction();
			IndexedEntity entity1 = new IndexedEntity();
			entity1.setId( 1 );
			entity1.setIndexedField( "initialValue" );

			session.persist( entity1 );

			backendMock.expectWorks( IndexedEntity.INDEX )
					.createFollowingWorks()
					.add( "1", b -> b
							.field( "indexedField", entity1.getIndexedField() )
							.field( "shallowReindexOnUpdateField", null )
							.field( "noReindexOnUpdateField", null )
					);

			session.flush();
			// Entities should be processed and works created on flush
			backendMock.verifyExpectationsMet();

			backendMock.expectWorks( IndexedEntity.INDEX )
					.discardFollowingWorks()
					.add( "1", b -> b
							.field( "indexedField", entity1.getIndexedField() )
							.field( "shallowReindexOnUpdateField", null )
							.field( "noReindexOnUpdateField", null )
					);

			trx.rollback();
			backendMock.verifyExpectationsMet();
		} );
	}

	/**
	 * Test that updating a non-indexed basic property
	 * does not trigger reindexing of the indexed entity owning the property.
	 */
	@Test
	@TestForIssue(jiraKey = "HSEARCH-3199")
	void directValueUpdate_nonIndexedField() {
		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = new IndexedEntity();
			entity1.setId( 1 );
			entity1.setNonIndexedField( "initialValue" );

			session.persist( entity1 );

			backendMock.expectWorks( IndexedEntity.INDEX )
					.add( "1", b -> b
							.field( "indexedField", entity1.getIndexedField() )
							.field( "shallowReindexOnUpdateField", null )
							.field( "noReindexOnUpdateField", null )
					);
		} );
		backendMock.verifyExpectationsMet();

		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = session.find( IndexedEntity.class, 1 );
			entity1.setNonIndexedField( "updatedValue" );

			// Do not expect any work
		} );
		backendMock.verifyExpectationsMet();

		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = session.find( IndexedEntity.class, 1 );
			entity1.setNonIndexedField( null );

			// Do not expect any work
		} );
		backendMock.verifyExpectationsMet();
	}

	/**
	 * Test that updating a indexed basic property configured with reindexOnUpdate = SHALLOW
	 * does trigger reindexing of the indexed entity owning the property.
	 * <p>
	 * SHALLOW isn't really useful in this case, since there's no "depth" to speak of,
	 * but we're testing this anyway, for the sake of completeness.
	 */
	@Test
	@TestForIssue(jiraKey = "HSEARCH-4001")
	void directValueUpdate_shallowReindexOnUpdateField() {
		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = new IndexedEntity();
			entity1.setId( 1 );
			entity1.setShallowReindexOnUpdateField( "initialValue" );

			session.persist( entity1 );

			backendMock.expectWorks( IndexedEntity.INDEX )
					.add( "1", b -> b
							.field( "indexedField", null )
							.field( "shallowReindexOnUpdateField", entity1.getShallowReindexOnUpdateField() )
							.field( "noReindexOnUpdateField", null )
					);
		} );
		backendMock.verifyExpectationsMet();

		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = session.find( IndexedEntity.class, 1 );
			entity1.setShallowReindexOnUpdateField( "updatedValue" );

			backendMock.expectWorks( IndexedEntity.INDEX )
					.addOrUpdate( "1", b -> b
							.field( "indexedField", null )
							.field( "shallowReindexOnUpdateField", entity1.getShallowReindexOnUpdateField() )
							.field( "noReindexOnUpdateField", null )
					);
		} );
		backendMock.verifyExpectationsMet();

		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = session.find( IndexedEntity.class, 1 );
			entity1.setShallowReindexOnUpdateField( null );

			backendMock.expectWorks( IndexedEntity.INDEX )
					.addOrUpdate( "1", b -> b
							.field( "indexedField", null )
							.field( "shallowReindexOnUpdateField", null )
							.field( "noReindexOnUpdateField", null )
					);
		} );
		backendMock.verifyExpectationsMet();
	}

	/**
	 * Test that updating a indexed basic property configured with reindexOnUpdate = NO
	 * does not trigger reindexing of the indexed entity owning the property.
	 */
	@Test
	@TestForIssue(jiraKey = "HSEARCH-3206")
	void directValueUpdate_noReindexOnUpdateField() {
		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = new IndexedEntity();
			entity1.setId( 1 );
			entity1.setNoReindexOnUpdateField( "initialValue" );

			session.persist( entity1 );

			backendMock.expectWorks( IndexedEntity.INDEX )
					.add( "1", b -> b
							.field( "indexedField", null )
							.field( "shallowReindexOnUpdateField", null )
							.field( "noReindexOnUpdateField", entity1.getNoReindexOnUpdateField() )
					);
		} );
		backendMock.verifyExpectationsMet();

		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = session.find( IndexedEntity.class, 1 );
			entity1.setNoReindexOnUpdateField( "updatedValue" );

			// Do not expect any work
		} );
		backendMock.verifyExpectationsMet();

		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = session.find( IndexedEntity.class, 1 );
			entity1.setNoReindexOnUpdateField( null );

			// Do not expect any work
		} );
		backendMock.verifyExpectationsMet();
	}

	@Test
	void sessionClear() {
		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = new IndexedEntity( 1, "number1" );
			IndexedEntity entity2 = new IndexedEntity( 2, "number2" );

			session.persist( entity1 );
			session.persist( entity2 );

			backendMock.expectWorks( IndexedEntity.INDEX )
					.createFollowingWorks()
					.add( "1", expectedValue( "number1" ) )
					.add( "2", expectedValue( "number2" ) );

			session.flush();
			if ( ormSetupHelper.areEntitiesProcessedInSession() ) {
				// Entities should be processed and works created on flush
				backendMock.verifyExpectationsMet();
			}

			IndexedEntity entity3 = new IndexedEntity( 3, "number3" );
			IndexedEntity entity4 = new IndexedEntity( 4, "number4" );

			session.persist( entity3 );
			session.persist( entity4 );

			// without clear the session
			backendMock.expectWorks( IndexedEntity.INDEX )
					.createFollowingWorks()
					.add( "3", expectedValue( "number3" ) )
					.add( "4", expectedValue( "number4" ) );

			backendMock.expectWorks( IndexedEntity.INDEX )
					.executeFollowingWorks()
					.add( "1", expectedValue( "number1" ) )
					.add( "2", expectedValue( "number2" ) )
					.add( "3", expectedValue( "number3" ) )
					.add( "4", expectedValue( "number4" ) );
		} );
		// Works should be executed on transaction commit
		backendMock.verifyExpectationsMet();

		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity5 = new IndexedEntity( 5, "number5" );
			IndexedEntity entity6 = new IndexedEntity( 6, "number6" );

			session.persist( entity5 );
			session.persist( entity6 );

			// flush triggers the prepare of the current indexing plan
			backendMock.expectWorks( IndexedEntity.INDEX )
					.createFollowingWorks()
					.add( "5", expectedValue( "number5" ) )
					.add( "6", expectedValue( "number6" ) );

			session.flush();
			if ( ormSetupHelper.areEntitiesProcessedInSession() ) {
				// Entities should be processed and works created on flush
				backendMock.verifyExpectationsMet();
			}

			IndexedEntity entity7 = new IndexedEntity( 7, "number7" );
			IndexedEntity entity8 = new IndexedEntity( 8, "number8" );
			IndexedEntity entity9 = new IndexedEntity( 9, "number9" );
			IndexedEntity entity10 = new IndexedEntity( 10, "number10" );

			session.persist( entity7 );
			session.persist( entity8 );

			SearchIndexingPlan indexingPlan = Search.session( session ).indexingPlan();
			indexingPlan.addOrUpdate( entity9 );
			indexingPlan.addOrUpdate( entity10 );

			// the clear will revert the changes that haven't been flushed yet,
			// including the ones that have been inserted directly in the indexing plan (bypassing the ORM session)
			session.clear();

			backendMock.expectWorks( IndexedEntity.INDEX )
					.executeFollowingWorks()
					.add( "5", expectedValue( "number5" ) )
					.add( "6", expectedValue( "number6" ) );
		} );
		// Works should be executed on transaction commit
		backendMock.verifyExpectationsMet();
	}

	/**
	 * Test that merging an entity using ~update()~ merge() to change an indexed field
	 * triggers reindexing of the indexed entity owning the property.
	 */
	@Test
	void sessionUpdate_directValueUpdate_indexedField() {
		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = new IndexedEntity();
			entity1.setId( 1 );
			entity1.setIndexedField( "initialValue" );

			session.persist( entity1 );

			backendMock.expectWorks( IndexedEntity.INDEX )
					.add( "1", b -> b
							.field( "indexedField", entity1.getIndexedField() )
							.field( "shallowReindexOnUpdateField", null )
							.field( "noReindexOnUpdateField", null )
					);
		} );
		backendMock.verifyExpectationsMet();

		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = new IndexedEntity();
			entity1.setId( 1 );
			entity1.setIndexedField( "updatedValue" );

			session.merge( entity1 );

			// Hibernate ORM does not track dirtiness on calls to update(): we assume everything is dirty.
			backendMock.expectWorks( IndexedEntity.INDEX )
					.addOrUpdate( "1", b -> b
							.field( "indexedField", entity1.getIndexedField() )
							.field( "shallowReindexOnUpdateField", null )
							.field( "noReindexOnUpdateField", null )
					);
		} );
		backendMock.verifyExpectationsMet();
	}

	/**
	 * Test that merging an entity using merge() to change an indexed field
	 * triggers reindexing of the indexed entity owning the property.
	 */
	@Test
	void sessionMerge_directValueUpdate_indexedField() {
		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = new IndexedEntity();
			entity1.setId( 1 );
			entity1.setIndexedField( "initialValue" );

			session.persist( entity1 );

			backendMock.expectWorks( IndexedEntity.INDEX )
					.add( "1", b -> b
							.field( "indexedField", entity1.getIndexedField() )
							.field( "shallowReindexOnUpdateField", null )
							.field( "noReindexOnUpdateField", null )
					);
		} );
		backendMock.verifyExpectationsMet();

		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = new IndexedEntity();
			entity1.setId( 1 );
			entity1.setIndexedField( "updatedValue" );

			session.merge( entity1 );

			backendMock.expectWorks( IndexedEntity.INDEX )
					.addOrUpdate( "1", b -> b
							.field( "indexedField", entity1.getIndexedField() )
							.field( "shallowReindexOnUpdateField", null )
							.field( "noReindexOnUpdateField", null )
					);
		} );
		backendMock.verifyExpectationsMet();
	}

	/**
	 * Test that merging an entity using merge() to change a non-indexed field
	 * does not trigger reindexing of the indexed entity owning the property.
	 */
	@Test
	@TestForIssue(jiraKey = "HSEARCH-3199")
	void sessionMerge_directValueUpdate_nonIndexedField() {
		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = new IndexedEntity();
			entity1.setId( 1 );
			entity1.setNonIndexedField( "initialValue" );

			session.persist( entity1 );

			backendMock.expectWorks( IndexedEntity.INDEX )
					.add( "1", b -> b
							.field( "indexedField", entity1.getIndexedField() )
							.field( "shallowReindexOnUpdateField", null )
							.field( "noReindexOnUpdateField", null )
					);
		} );
		backendMock.verifyExpectationsMet();

		with( sessionFactory ).runInTransaction( session -> {
			IndexedEntity entity1 = new IndexedEntity();
			entity1.setId( 1 );
			entity1.setNonIndexedField( "updatedValue" );

			session.merge( entity1 );
		} );
		backendMock.verifyExpectationsMet();
	}

	public Consumer<StubDocumentNode.Builder> expectedValue(String indexedFieldExpectedValue) {
		return b -> b.field( "indexedField", indexedFieldExpectedValue )
				.field( "shallowReindexOnUpdateField", null )
				.field( "noReindexOnUpdateField", null );
	}

	@Entity(name = "indexed")
	@Indexed(index = IndexedEntity.INDEX)
	public static class IndexedEntity {

		static final String INDEX = "IndexedEntity";

		@Id
		private Integer id;

		@Basic
		@GenericField
		private String indexedField;

		@Basic
		private String nonIndexedField;

		@Basic
		@GenericField
		@IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
		private String shallowReindexOnUpdateField;

		@Basic
		@GenericField
		@IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
		private String noReindexOnUpdateField;

		public IndexedEntity() {
		}

		public IndexedEntity(Integer id, String indexedField) {
			this.id = id;
			this.indexedField = indexedField;
		}

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getIndexedField() {
			return indexedField;
		}

		public void setIndexedField(String indexedField) {
			this.indexedField = indexedField;
		}

		public String getNonIndexedField() {
			return nonIndexedField;
		}

		public void setNonIndexedField(String nonIndexedField) {
			this.nonIndexedField = nonIndexedField;
		}

		public String getShallowReindexOnUpdateField() {
			return shallowReindexOnUpdateField;
		}

		public void setShallowReindexOnUpdateField(String shallowReindexOnUpdateField) {
			this.shallowReindexOnUpdateField = shallowReindexOnUpdateField;
		}

		public String getNoReindexOnUpdateField() {
			return noReindexOnUpdateField;
		}

		public void setNoReindexOnUpdateField(String noReindexOnUpdateField) {
			this.noReindexOnUpdateField = noReindexOnUpdateField;
		}

	}

}
