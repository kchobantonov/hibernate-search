/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.orm.coordination.databasepolling.event.impl;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Collections;

import org.hibernate.boot.jaxb.Origin;
import org.hibernate.boot.jaxb.SourceType;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmHibernateMapping;
import org.hibernate.boot.jaxb.internal.MappingBinder;
import org.hibernate.boot.jaxb.spi.Binding;
import org.hibernate.boot.model.source.internal.hbm.MappingDocument;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.search.mapper.orm.coordination.databasepolling.cluster.impl.DatabasePollingAgentAdditionalJaxbMappingProducer;
import org.hibernate.search.mapper.orm.coordination.databasepolling.logging.impl.Log;
import org.hibernate.search.util.common.annotation.impl.SuppressForbiddenApis;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;

import org.jboss.jandex.IndexView;

@SuppressWarnings("deprecation")
public final class DatabasePollingOutboxEventAdditionalJaxbMappingProducer
		implements org.hibernate.boot.spi.AdditionalJaxbMappingProducer {

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

	private static final String HSEARCH_TABLE_NAME_PREFIX = DatabasePollingAgentAdditionalJaxbMappingProducer.HSEARCH_TABLE_NAME_PREFIX;

	// Must not be longer than 20 characters, so that the generator does not exceed the 30 characters for Oracle11g
	private static final String TABLE_NAME = HSEARCH_TABLE_NAME_PREFIX + "OUTBOX_EVENT";

	private static final String ENTITY_DEFINITION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<hibernate-mapping>\n" +
			"    <class name=\"" + OutboxEvent.class.getName() + "\" table=\"" + TABLE_NAME + "\">\n" +
			"        <id name=\"id\" type=\"long\">\n" +
			"            <generator class=\"org.hibernate.id.enhanced.SequenceStyleGenerator\">\n" +
			"                <param name=\"sequence_name\">" + TABLE_NAME + "_GENERATOR</param>\n" +
			"                <param name=\"table_name\">" + TABLE_NAME + "_GENERATOR</param>\n" +
			"                <param name=\"initial_value\">1</param>\n" +
			"                <param name=\"increment_size\">1</param>\n" +
			"            </generator>\n" +
			"        </id>\n" +
			"        <property name=\"entityName\" type=\"string\" length=\"256\" />\n" +
			"        <property name=\"entityId\" type=\"string\" length=\"256\" />\n" +
			"        <property name=\"entityIdHash\" type=\"integer\" index=\"entityIdHash\" />\n" +
			"        <property name=\"payload\" type=\"materialized_blob\" />\n" +
			"        <property name=\"retries\" type=\"integer\" />\n" +
			"        <property name=\"processAfter\" type=\"Instant\" index=\"processAfter\" />\n" +
			"    </class>\n" +
			"</hibernate-mapping>\n";

	@Override
	@SuppressForbiddenApis(reason = "Strangely, this SPI involves the internal MappingBinder class,"
			+ " and there's nothing we can do about it")
	public Collection<MappingDocument> produceAdditionalMappings(final MetadataImplementor metadata,
			IndexView jandexIndex, final MappingBinder mappingBinder, final MetadataBuildingContext buildingContext) {
		log.outboxEventGeneratedEntityMapping( ENTITY_DEFINITION );
		Origin origin = new Origin( SourceType.OTHER, "search" );

		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream( ENTITY_DEFINITION.getBytes() );
		BufferedInputStream bufferedInputStream = new BufferedInputStream( byteArrayInputStream );
		Binding<?> binding = mappingBinder.bind( bufferedInputStream, origin );

		JaxbHbmHibernateMapping root = (JaxbHbmHibernateMapping) binding.getRoot();

		MappingDocument mappingDocument = new MappingDocument( root, origin, buildingContext );
		return Collections.singletonList( mappingDocument );
	}
}