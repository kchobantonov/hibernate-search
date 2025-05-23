/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.documentation.search.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.search.util.impl.integrationtest.mapper.orm.OrmUtils.with;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.search.documentation.testsupport.BackendConfigurations;
import org.hibernate.search.documentation.testsupport.DocumentationSetupHelper;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.search.common.ValueModel;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
import org.hibernate.search.util.impl.integrationtest.common.extension.BackendConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ProjectionConverterIT {
	@RegisterExtension
	public DocumentationSetupHelper setupHelper = DocumentationSetupHelper.withSingleBackend( BackendConfigurations.simple() );

	private EntityManagerFactory entityManagerFactory;

	@BeforeEach
	void setup() {
		entityManagerFactory = setupHelper.start().setup( Order.class );
		initData();
	}

	@Test
	void projectionConverterEnabled() {
		with( entityManagerFactory ).runInTransaction( entityManager -> {
			SearchSession searchSession = Search.session( entityManager );

			// tag::projection-converter-enabled[]
			List<OrderStatus> result = searchSession.search( Order.class )
					.select( f -> f.field( "status", OrderStatus.class ) )
					.where( f -> f.matchAll() )
					.fetchHits( 20 );
			// end::projection-converter-enabled[]

			assertThat( result )
					.containsExactlyInAnyOrder( OrderStatus.values() );
		} );
	}

	@Test
	void projectionConverterDisabled() {
		with( entityManagerFactory ).runInTransaction( entityManager -> {
			SearchSession searchSession = Search.session( entityManager );

			// tag::projection-converter-disabled[]
			List<String> result = searchSession.search( Order.class )
					.select( f -> f.field( "status", String.class, ValueModel.INDEX ) )
					.where( f -> f.matchAll() )
					.fetchHits( 20 );
			// end::projection-converter-disabled[]

			assertThat( result )
					.containsExactlyInAnyOrder(
							Stream.of( OrderStatus.values() ).map( Enum::name ).toArray( String[]::new )
					);
		} );
	}


	@Test
	void projectionConverterRaw() {
		with( entityManagerFactory ).runInTransaction( entityManager -> {
			SearchSession searchSession = Search.session( entityManager );

			// tag::projection-converter-raw[]
			Class<String> rawProjectionType = // ... // <1>
					// end::projection-converter-raw[]
					String.class;

			// tag::projection-converter-raw[]
			List<?> result = searchSession.search( Order.class )
					.select( f -> f.field( "status", rawProjectionType, ValueModel.RAW ) )
					.where( f -> f.matchAll() )
					.fetchHits( 20 );
			// end::projection-converter-raw[]
			if ( BackendConfiguration.isElasticsearch() ) {
				assertThat( result.stream().map( Object::toString ) )
						.containsExactlyInAnyOrder(
								Stream.of( OrderStatus.values() ).map( Enum::name )
										.map( s -> String.format( Locale.ROOT, "\"%s\"", s ) ).toArray( String[]::new )
						);

			}
			else {
				assertThat( result.stream().map( Object::toString ) )
						.containsExactlyInAnyOrder(
								Stream.of( OrderStatus.values() ).map( Enum::name ).toArray( String[]::new )
						);
			}
		} );
	}

	private void initData() {
		with( entityManagerFactory ).runInTransaction( entityManager -> {
			Order order1 = new Order( 1 );
			order1.setStatus( OrderStatus.ACKNOWLEDGED );
			Order order2 = new Order( 2 );
			order2.setStatus( OrderStatus.IN_PROGRESS );
			Order order3 = new Order( 3 );
			order3.setStatus( OrderStatus.DELIVERED );

			entityManager.persist( order1 );
			entityManager.persist( order2 );
			entityManager.persist( order3 );
		} );
	}

	@Entity(name = "Order")
	@Table(name = "orders")
	@Indexed
	public static class Order {
		@Id
		private Integer id;
		@Basic
		@Enumerated
		@KeywordField(projectable = Projectable.YES)
		private OrderStatus status;

		protected Order() {
			// For Hibernate ORM
		}

		public Order(int id) {
			this.id = id;
		}

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public OrderStatus getStatus() {
			return status;
		}

		public void setStatus(OrderStatus status) {
			this.status = status;
		}
	}

	enum OrderStatus {
		ACKNOWLEDGED,
		IN_PROGRESS,
		DELIVERED
	}

}
