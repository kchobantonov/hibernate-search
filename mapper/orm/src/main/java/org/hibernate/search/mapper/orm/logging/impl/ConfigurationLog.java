/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.orm.logging.impl;

import static org.hibernate.search.mapper.orm.logging.impl.OrmLog.ID_OFFSET;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Set;

import org.hibernate.resource.beans.container.spi.BeanContainer;
import org.hibernate.search.engine.environment.bean.spi.BeanNotFoundException;
import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.common.logging.CategorizedLogger;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;
import org.hibernate.search.util.common.logging.impl.MessageConstants;

import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

@CategorizedLogger(
		category = ConfigurationLog.CATEGORY_NAME,
		description = """
				Logs related to Hibernate ORM mapper-specific configuration.
				"""
)
@MessageLogger(projectCode = MessageConstants.PROJECT_CODE)
public interface ConfigurationLog {
	String CATEGORY_NAME = "org.hibernate.search.configuration.mapper.orm";

	ConfigurationLog INSTANCE = LoggerFactory.make( ConfigurationLog.class, CATEGORY_NAME, MethodHandles.lookup() );

	@Message(id = ID_OFFSET + 1,
			value = "Hibernate Search was not initialized.")
	SearchException hibernateSearchNotInitialized();

	@Message(id = ID_OFFSET + 3,
			value = "Invalid automatic indexing strategy name: '%1$s'. Valid names are: %2$s.")
	SearchException invalidAutomaticIndexingStrategyName(String invalidRepresentation,
			List<String> validRepresentations);

	@Message(id = ID_OFFSET + 18,
			value = "Invalid entity loading cache lookup strategy name: '%1$s'. Valid names are: %2$s.")
	SearchException invalidEntityLoadingCacheLookupStrategyName(String invalidRepresentation,
			List<String> validRepresentations);

	@Message(id = ID_OFFSET + 32, value = "Invalid schema management strategy name: '%1$s'."
			+ " Valid names are: %2$s.")
	SearchException invalidSchemaManagementStrategyName(String invalidRepresentation,
			List<String> validRepresentations);

	@Message(id = ID_OFFSET + 41, value = "No such bean in bean container '%1$s'.")
	BeanNotFoundException beanNotFoundInBeanContainer(BeanContainer beanContainer);

	@Message(id = ID_OFFSET + 42, value = "Cannot customize the indexing plan synchronization strategy: "
			+ " the selected coordination strategy always processes events asynchronously, through a queue.")
	SearchException cannotConfigureSynchronizationStrategyWithIndexingEventQueue();

	@Message(id = ID_OFFSET + 54, value = "Cannot determine the set of all possible tenant identifiers."
			+ " You must provide this information by setting configuration property '%1$s'"
			+ " to a comma-separated string containing all possible tenant identifiers.")
	SearchException missingTenantIdConfiguration(String tenantIdsConfigurationPropertyKey);

	@Message(id = ID_OFFSET + 55, value = "Cannot target tenant '%1$s' because this tenant identifier"
			+ " was not listed in the configuration provided on startup."
			+ " To target this tenant, you must provide the tenant identifier through configuration property '%3$s',"
			+ " which should be set to a comma-separated string containing all possible tenant identifiers."
			+ " Currently configured tenant identifiers: %2$s.")
	SearchException invalidTenantId(String tenantId, Set<String> allTenantIds, String tenantIdsConfigurationPropertyKey);

	@Message(id = ID_OFFSET + 124,
			value = "Unable to apply the given filter at the session level with the outbox polling coordination strategy. " +
					"With this coordination strategy, applying a session-level indexing plan filter is only allowed if it excludes all types.")
	SearchException cannotApplySessionFilterWhenAsyncProcessingIsUsed();

	@LogMessage(level = Logger.Level.DEBUG)
	@Message(id = ID_OFFSET + 138, value = "Error resolving bean of type [%s] - using fallback")
	void errorResolvingBean(Class<?> typeReference, @Cause Exception e);

	@LogMessage(level = Logger.Level.DEBUG)
	@Message(id = ID_OFFSET + 139, value = "Hibernate Search is disabled through configuration properties.")
	void hibernateSearchDisabled();

	@LogMessage(level = Logger.Level.DEBUG)
	@Message(id = ID_OFFSET + 140, value = "Hibernate Search dirty checks: %s")
	void dirtyChecksEnabled(boolean dirtyCheckingEnabled);

	@LogMessage(level = Logger.Level.DEBUG)
	@Message(id = ID_OFFSET + 141, value = "Hibernate Search event listeners activated")
	void hibernateSearchListenerEnabled();

	@LogMessage(level = Logger.Level.DEBUG)
	@Message(id = ID_OFFSET + 142, value = "Hibernate Search event listeners deactivated")
	void hibernateSearchListenerDisabled();
}
