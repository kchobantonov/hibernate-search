/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.orm.outbox.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hibernate.Session;
import org.hibernate.search.engine.backend.common.spi.EntityReferenceFactory;
import org.hibernate.search.engine.backend.common.spi.MultiEntityOperationExecutionReport;
import org.hibernate.search.engine.reporting.EntityIndexingFailureContext;
import org.hibernate.search.engine.reporting.FailureHandler;
import org.hibernate.search.mapper.orm.automaticindexing.spi.AutomaticIndexingMappingContext;
import org.hibernate.search.mapper.orm.automaticindexing.spi.AutomaticIndexingQueueEventProcessingPlan;
import org.hibernate.search.mapper.orm.common.EntityReference;
import org.hibernate.search.mapper.pojo.route.DocumentRoutesDescriptor;
import org.hibernate.search.util.common.impl.Futures;
import org.hibernate.search.util.common.serialization.spi.SerializationUtils;

public class OutboxEventProcessingPlan {

	private final AutomaticIndexingQueueEventProcessingPlan processingPlan;
	private final FailureHandler failureHandler;
	private final EntityReferenceFactory<EntityReference> entityReferenceFactory;
	private final List<OutboxEvent> events;
	private final Map<OutboxEventReference, List<OutboxEvent>> failedEvents = new HashMap<>();
	private final List<Integer> eventsIds;

	public OutboxEventProcessingPlan(AutomaticIndexingMappingContext mapping, Session session,
			List<OutboxEvent> events) {
		this.processingPlan = mapping.createIndexingQueueEventProcessingPlan( session );
		this.failureHandler = mapping.failureHandler();
		this.entityReferenceFactory = mapping.entityReferenceFactory();
		this.events = events;
		this.eventsIds = new ArrayList<>( events.size() );
	}

	List<Integer> processEvents() {
		try {
			addEventsToThePlan();
			reportBackendResult( Futures.unwrappedExceptionGet( processingPlan.executeAndReport() ) );
		}
		catch (Throwable throwable) {
			if ( throwable instanceof InterruptedException ) {
				Thread.currentThread().interrupt();
			}
			reportMapperFailure( throwable );
		}

		return eventsIds;
	}

	Map<OutboxEventReference, List<OutboxEvent>> getFailedEvents() {
		return failedEvents;
	}

	EntityReference entityReference(String entityName, String entityId, Throwable throwable) {
		try {
			Object identifier = processingPlan.toIdentifier( entityName, entityId );
			return EntityReferenceFactory.safeCreateEntityReference(
					entityReferenceFactory, entityName, identifier, throwable::addSuppressed );
		}
		catch (RuntimeException e) {
			// We failed to extract a reference.
			// Let's just give up and suppress the exception.
			throwable.addSuppressed( e );
			return null;
		}
	}

	private void addEventsToThePlan() {
		for ( OutboxEvent event : events ) {
			// Do this first, so that we're sure to delete the event after it's been processed.
			// In case of failure, a new "retry" event is created.
			eventsIds.add( event.getId() );
			DocumentRoutesDescriptor routes = getRoutes( event );

			switch ( event.getType() ) {
				case ADD:
					processingPlan.add( event.getEntityName(), event.getEntityId(), routes );
					break;
				case ADD_OR_UPDATE:
					processingPlan.addOrUpdate( event.getEntityName(), event.getEntityId(), routes );
					break;
				case DELETE:
					processingPlan.delete( event.getEntityName(), event.getEntityId(), routes );
					break;
			}
		}
	}

	private DocumentRoutesDescriptor getRoutes(OutboxEvent event) {
		return SerializationUtils.deserialize( DocumentRoutesDescriptor.class, event.getDocumentRoutes() );
	}

	private void reportMapperFailure(Throwable throwable) {
		try {
			// Something failed, but we don't know what.
			// Assume all events failed.
			reportAllEventsFailure( throwable, getEventsByReferences() );
		}
		catch (Throwable t) {
			throwable.addSuppressed( t );
		}
	}

	private void reportBackendResult(MultiEntityOperationExecutionReport<EntityReference> report) {
		Optional<Throwable> throwable = report.throwable();
		if ( !throwable.isPresent() ) {
			return;
		}

		Map<OutboxEventReference, List<OutboxEvent>> eventsMap = getEventsByReferences();
		EntityIndexingFailureContext.Builder builder = EntityIndexingFailureContext.builder();
		builder.throwable( throwable.get() );
		builder.failingOperation( "Processing an outbox event." );

		for ( EntityReference entityReference : report.failingEntityReferences() ) {
			OutboxEventReference outboxEventReference = new OutboxEventReference(
					entityReference.name(),
					extractReferenceOrSuppress( entityReference, throwable.get() )
			);

			builder.entityReference( entityReference );
			failedEvents.put( outboxEventReference, eventsMap.get( outboxEventReference ) );
		}
		failureHandler.handle( builder.build() );
	}

	private String extractReferenceOrSuppress(EntityReference entityReference, Throwable throwable) {
		try {
			return processingPlan.toSerializedId( entityReference.name(), entityReference.id() );
		}
		catch (RuntimeException e) {
			throwable.addSuppressed( e );
			return null;
		}
	}

	private void reportAllEventsFailure(Throwable throwable, Map<OutboxEventReference, List<OutboxEvent>> eventsMap) {
		failedEvents.putAll( eventsMap );
		EntityIndexingFailureContext.Builder builder = EntityIndexingFailureContext.builder();
		builder.throwable( throwable );
		builder.failingOperation( "Processing an outbox event." );

		for ( List<OutboxEvent> events : eventsMap.values() ) {
			for ( OutboxEvent event : events ) {
				builder.entityReference( entityReference( event.getEntityName(), event.getEntityId(), throwable ) );
			}
		}
		failureHandler.handle( builder.build() );
	}

	private Map<OutboxEventReference, List<OutboxEvent>> getEventsByReferences() {
		Map<OutboxEventReference, List<OutboxEvent>> eventsMap = new HashMap<>();
		for ( OutboxEvent event : events ) {
			eventsMap.computeIfAbsent( event.getReference(), key -> new ArrayList<>() );
			eventsMap.get( event.getReference() ).add( event );
		}
		return eventsMap;
	}
}