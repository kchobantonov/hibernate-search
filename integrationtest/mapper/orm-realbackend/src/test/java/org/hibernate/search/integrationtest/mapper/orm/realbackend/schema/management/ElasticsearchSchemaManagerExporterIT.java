/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.integrationtest.mapper.orm.realbackend.schema.management;

import static org.hibernate.search.util.impl.integrationtest.backend.elasticsearch.dialect.ElasticsearchTestDialect.isActualVersion;
import static org.hibernate.search.util.impl.test.JsonHelper.assertJsonEquals;
import static org.hibernate.search.util.impl.test.JsonHelper.assertJsonEqualsIgnoringUnknownFields;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.search.backend.elasticsearch.cfg.ElasticsearchBackendSettings;
import org.hibernate.search.integrationtest.mapper.orm.realbackend.testsupport.BackendConfigurations;
import org.hibernate.search.integrationtest.mapper.orm.realbackend.util.Article;
import org.hibernate.search.integrationtest.mapper.orm.realbackend.util.Book;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.cfg.HibernateOrmMapperSettings;
import org.hibernate.search.util.impl.integrationtest.backend.elasticsearch.dialect.ElasticsearchTestDialect;
import org.hibernate.search.util.impl.integrationtest.mapper.orm.OrmSetupHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class ElasticsearchSchemaManagerExporterIT {

	@TempDir
	public Path temporaryFolder;
	@RegisterExtension
	public OrmSetupHelper setupHelper = OrmSetupHelper.withMultipleBackends(
			BackendConfigurations.simple(),
			Map.of( Article.BACKEND_NAME, BackendConfigurations.simple() )
	);

	private EntityManagerFactory entityManagerFactory;

	@Test
	void elasticsearch() throws IOException {
		assumeFalse(
				isActualVersion(
						esVersion -> esVersion.isLessThan( "7.0" ),
						osVersion -> false
				),
				"Older versions of Elasticsearch would not match the mappings"
		);
		String version = ElasticsearchTestDialect.getActualVersion().toString();
		entityManagerFactory = setupHelper.start()
				// so that we don't try to do anything with the schema and allow to run without ES being up:
				.withProperty( HibernateOrmMapperSettings.SCHEMA_MANAGEMENT_STRATEGY, "none" )
				.withBackendProperty( ElasticsearchBackendSettings.VERSION_CHECK_ENABLED, false )
				.withBackendProperty( ElasticsearchBackendSettings.VERSION, version )
				.withBackendProperty( Article.BACKEND_NAME, ElasticsearchBackendSettings.VERSION_CHECK_ENABLED, false )
				.withBackendProperty( Article.BACKEND_NAME, ElasticsearchBackendSettings.VERSION, version )
				.setup( Book.class, Article.class );

		Path directory = temporaryFolder;
		Search.mapping( entityManagerFactory ).scope( Object.class ).schemaManager().exportExpectedSchema( directory );

		assertJsonEqualsIgnoringUnknownFields(
				"{" +
						"  \"aliases\": {" +
						"    \"book-write\": {" +
						"      \"is_write_index\": true" +
						"    }," +
						"    \"book-read\": {" +
						"      \"is_write_index\": false" +
						"    }" +
						"  }," +
						"  \"mappings\": {" +
						"    \"properties\": {" +
						"      \"_entity_type\": {" +
						"        \"type\": \"keyword\"," +
						"        \"index\": false," +
						"        \"doc_values\": true" +
						"      }" +
						"    }," +
						"    \"dynamic\": \"strict\"" +
						"  }," +
						"  \"settings\": {}" +
						"}",
				readString(
						directory.resolve( "backend" ) // as we are using the default backend
								.resolve( "indexes" )
								.resolve( Book.NAME )
								.resolve( "create-index.json" ) )
		);

		assertJsonEquals(
				"{}",
				readString(
						directory.resolve( "backend" ) // as we are using the default backend
								.resolve( "indexes" )
								.resolve( Book.NAME )
								.resolve( "create-index-query-params.json" ) )
		);

		assertJsonEquals(
				"{" +
						"  \"aliases\": {" +
						"    \"article-write\": {" +
						"      \"is_write_index\": true" +
						"    }," +
						"    \"article-read\": {" +
						"      \"is_write_index\": false" +
						"    }" +
						"  }," +
						"  \"mappings\": {" +
						"    \"properties\": {" +
						"      \"_entity_type\": {" +
						"        \"type\": \"keyword\"," +
						"        \"index\": false," +
						"        \"doc_values\": true" +
						"      }," +
						"      \"title\": {" +
						"        \"type\": \"text\"," +
						"        \"index\": true," +
						"        \"norms\": true," +
						"        \"analyzer\": \"default\"," +
						"        \"term_vector\": \"no\"" +
						"      }" +
						"    }," +
						"    \"dynamic\": \"strict\"" +
						"  }," +
						"  \"settings\": {}" +
						"}",
				readString(
						directory.resolve( "backends" ) // as we are not using the default backend
								.resolve( Article.BACKEND_NAME ) // name of a backend
								.resolve( "indexes" )
								.resolve( Article.NAME )
								.resolve( "create-index.json" ) )
		);

		assertJsonEquals(
				"{}",
				readString(
						directory.resolve( "backends" ) // as we are not using the default backend
								.resolve( Article.BACKEND_NAME ) // name of a backend
								.resolve( "indexes" )
								.resolve( Article.NAME )
								.resolve( "create-index-query-params.json" ) )
		);
	}

	private String readString(Path path) throws IOException {
		try ( Stream<String> lines = Files.lines( path ) ) {
			return lines.collect( Collectors.joining( "\n" ) );
		}
	}
}
