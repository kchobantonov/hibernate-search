/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.elasticsearch.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.hibernate.search.backend.elasticsearch.ElasticsearchBackend;
import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurer;
import org.hibernate.search.backend.elasticsearch.analysis.model.dsl.impl.ElasticsearchAnalysisConfigurationContextImpl;
import org.hibernate.search.backend.elasticsearch.analysis.model.impl.ElasticsearchAnalysisDefinitionRegistry;
import org.hibernate.search.backend.elasticsearch.cfg.ElasticsearchIndexSettings;
import org.hibernate.search.backend.elasticsearch.document.impl.DocumentMetadataContributor;
import org.hibernate.search.backend.elasticsearch.document.model.dsl.impl.ElasticsearchIndexRootBuilder;
import org.hibernate.search.backend.elasticsearch.index.DynamicMapping;
import org.hibernate.search.backend.elasticsearch.index.impl.ElasticsearchIndexManagerBuilder;
import org.hibernate.search.backend.elasticsearch.index.impl.IndexManagerBackendContext;
import org.hibernate.search.backend.elasticsearch.logging.impl.AnalysisLog;
import org.hibernate.search.backend.elasticsearch.logging.impl.ElasticsearchMiscLog;
import org.hibernate.search.backend.elasticsearch.logging.impl.MappingLog;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.RootTypeMapping;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.settings.impl.IndexSettings;
import org.hibernate.search.backend.elasticsearch.multitenancy.impl.MultiTenancyStrategy;
import org.hibernate.search.backend.elasticsearch.orchestration.impl.ElasticsearchSimpleWorkOrchestrator;
import org.hibernate.search.backend.elasticsearch.resources.impl.BackendThreads;
import org.hibernate.search.backend.elasticsearch.types.dsl.provider.impl.ElasticsearchIndexFieldTypeFactoryProvider;
import org.hibernate.search.backend.elasticsearch.validation.impl.ElasticsearchPropertyMappingValidatorProvider;
import org.hibernate.search.engine.backend.Backend;
import org.hibernate.search.engine.backend.index.spi.IndexManagerBuilder;
import org.hibernate.search.engine.backend.mapping.spi.BackendMapperContext;
import org.hibernate.search.engine.backend.spi.BackendBuildContext;
import org.hibernate.search.engine.backend.spi.BackendImplementor;
import org.hibernate.search.engine.backend.spi.BackendStartContext;
import org.hibernate.search.engine.cfg.ConfigurationPropertySource;
import org.hibernate.search.engine.cfg.spi.ConfigurationProperty;
import org.hibernate.search.engine.cfg.spi.OptionalConfigurationProperty;
import org.hibernate.search.engine.common.timing.spi.TimingSource;
import org.hibernate.search.engine.environment.bean.BeanHolder;
import org.hibernate.search.engine.environment.bean.BeanReference;
import org.hibernate.search.engine.environment.bean.BeanResolver;
import org.hibernate.search.engine.reporting.FailureHandler;
import org.hibernate.search.engine.reporting.spi.EventContexts;
import org.hibernate.search.util.common.impl.Closer;
import org.hibernate.search.util.common.reporting.EventContext;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

class ElasticsearchBackendImpl implements BackendImplementor, ElasticsearchBackend {

	private static final OptionalConfigurationProperty<
			List<BeanReference<? extends ElasticsearchAnalysisConfigurer>>> ANALYSIS_CONFIGURER =
					ConfigurationProperty.forKey( ElasticsearchIndexSettings.ANALYSIS_CONFIGURER )
							.asBeanReference( ElasticsearchAnalysisConfigurer.class )
							.multivalued()
							.build();

	private static final ConfigurationProperty<DynamicMapping> DYNAMIC_MAPPING =
			ConfigurationProperty.forKey( ElasticsearchIndexSettings.DYNAMIC_MAPPING )
					.as( DynamicMapping.class, DynamicMapping::of )
					.withDefault( ElasticsearchIndexSettings.Defaults.DYNAMIC_MAPPING )
					.build();

	private static final OptionalConfigurationProperty<String> SCHEMA_MANAGEMENT_SETTINGS_FILE =
			ConfigurationProperty.forKey( ElasticsearchIndexSettings.SCHEMA_MANAGEMENT_SETTINGS_FILE )
					.asString()
					.build();

	private static final OptionalConfigurationProperty<String> SCHEMA_MANAGEMENT_MAPPING_FILE =
			ConfigurationProperty.forKey( ElasticsearchIndexSettings.SCHEMA_MANAGEMENT_MAPPING_FILE )
					.asString()
					.build();

	private final Optional<String> backendName;
	private final EventContext eventContext;

	private final BackendThreads threads;
	private final ElasticsearchLinkImpl link;

	private final ElasticsearchSimpleWorkOrchestrator generalPurposeOrchestrator;

	private final ElasticsearchIndexFieldTypeFactoryProvider typeFactoryProvider;
	private final Gson userFacingGson;
	private final MultiTenancyStrategy multiTenancyStrategy;

	private final IndexManagerBackendContext indexManagerBackendContext;

	ElasticsearchBackendImpl(Optional<String> backendName,
			EventContext eventContext,
			BackendThreads threads,
			ElasticsearchLinkImpl link,
			ElasticsearchIndexFieldTypeFactoryProvider typeFactoryProvider,
			ElasticsearchPropertyMappingValidatorProvider propertyMappingValidatorProvider,
			Gson userFacingGson,
			MultiTenancyStrategy multiTenancyStrategy,
			FailureHandler failureHandler, TimingSource timingSource) {
		this.backendName = backendName;
		this.eventContext = eventContext;
		this.threads = threads;
		this.link = link;

		this.generalPurposeOrchestrator = new ElasticsearchSimpleWorkOrchestrator(
				"Elasticsearch general purpose orchestrator - " + eventContext.render(),
				link
		);
		this.multiTenancyStrategy = multiTenancyStrategy;
		this.typeFactoryProvider = typeFactoryProvider;
		this.userFacingGson = userFacingGson;

		this.indexManagerBackendContext = new IndexManagerBackendContext(
				this, eventContext, threads, link,
				userFacingGson,
				multiTenancyStrategy,
				failureHandler, timingSource,
				generalPurposeOrchestrator,
				propertyMappingValidatorProvider
		);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + eventContext.render() + "]";
	}

	@Override
	public void start(BackendStartContext context) {
		threads.onStart( context.configurationPropertySource(), context.beanResolver(), context.threadPoolProvider() );
		link.onStart( context.beanResolver(), multiTenancyStrategy, context.configurationPropertySource() );
		generalPurposeOrchestrator.start( context.configurationPropertySource() );
	}

	@Override
	public CompletableFuture<?> preStop() {
		return generalPurposeOrchestrator.preStop();
	}

	@Override
	public void stop() {
		try ( Closer<RuntimeException> closer = new Closer<>() ) {
			closer.push( ElasticsearchSimpleWorkOrchestrator::stop, generalPurposeOrchestrator );
			closer.push( ElasticsearchLinkImpl::onStop, link );
			closer.push( BackendThreads::onStop, threads );
		}
	}

	@Override
	@SuppressWarnings("unchecked") // Checked using reflection
	public <T> T unwrap(Class<T> clazz) {
		if ( clazz.isAssignableFrom( ElasticsearchBackend.class ) ) {
			return (T) this;
		}
		throw ElasticsearchMiscLog.INSTANCE.backendUnwrappingWithUnknownType( clazz, ElasticsearchBackend.class, eventContext );
	}

	@Override
	public Optional<String> name() {
		return backendName;
	}

	@Override
	public Backend toAPI() {
		return this;
	}

	@Override
	public <T> T client(Class<T> clientClass) {
		return link.getClient().unwrap( clientClass );
	}

	@Override
	public IndexManagerBuilder createIndexManagerBuilder(
			String hibernateSearchIndexName, String mappedTypeName, BackendBuildContext buildContext,
			BackendMapperContext backendMapperContext,
			ConfigurationPropertySource propertySource) {

		EventContext indexEventContext = EventContexts.fromIndexName( hibernateSearchIndexName );

		ElasticsearchAnalysisDefinitionRegistry analysisDefinitionRegistry =
				createAnalysisDefinitionRegistry( buildContext, indexEventContext, propertySource );

		return new ElasticsearchIndexManagerBuilder(
				indexManagerBackendContext,
				createIndexSchemaRootNodeBuilder( indexEventContext, backendMapperContext, hibernateSearchIndexName,
						mappedTypeName,
						analysisDefinitionRegistry,
						customIndexSettings( buildContext, propertySource, indexEventContext ),
						customIndexMappings( buildContext, propertySource, indexEventContext ),
						DYNAMIC_MAPPING.get( propertySource )
				),
				createDocumentMetadataContributors( mappedTypeName )
		);
	}

	private ElasticsearchIndexRootBuilder createIndexSchemaRootNodeBuilder(EventContext indexEventContext,
			BackendMapperContext backendMapperContext, String hibernateSearchIndexName, String mappedTypeName,
			ElasticsearchAnalysisDefinitionRegistry analysisDefinitionRegistry,
			IndexSettings customIndexSettings, RootTypeMapping customIndexMapping,
			DynamicMapping dynamicMapping) {

		ElasticsearchIndexRootBuilder builder = new ElasticsearchIndexRootBuilder(
				typeFactoryProvider,
				indexEventContext,
				backendMapperContext,
				hibernateSearchIndexName,
				mappedTypeName,
				analysisDefinitionRegistry,
				customIndexSettings, customIndexMapping,
				dynamicMapping
		);

		link.getTypeNameMapping().getIndexSchemaRootContributor()
				.ifPresent( builder::addSchemaRootContributor );
		link.getTypeNameMapping().getImplicitFieldContributor()
				.ifPresent( builder::addImplicitFieldContributor );

		multiTenancyStrategy.indexSchemaRootContributor()
				.ifPresent( builder::addSchemaRootContributor );

		return builder;
	}

	private ElasticsearchAnalysisDefinitionRegistry createAnalysisDefinitionRegistry(BackendBuildContext buildContext,
			EventContext indexEventContext, ConfigurationPropertySource propertySource) {
		try {
			BeanResolver beanResolver = buildContext.beanResolver();
			// Apply the user-provided analysis configurers if necessary
			return ANALYSIS_CONFIGURER.getAndMap( propertySource, beanResolver::resolve )
					.map( holder -> {
						try ( BeanHolder<List<ElasticsearchAnalysisConfigurer>> configurerHolder = holder ) {
							ElasticsearchAnalysisConfigurationContextImpl collector =
									new ElasticsearchAnalysisConfigurationContextImpl();
							for ( ElasticsearchAnalysisConfigurer configurer : configurerHolder.get() ) {
								configurer.configure( collector );
							}
							return new ElasticsearchAnalysisDefinitionRegistry( collector );
						}
					} )
					// Otherwise just use an empty registry
					.orElseGet( ElasticsearchAnalysisDefinitionRegistry::new );
		}
		catch (Exception e) {
			throw AnalysisLog.INSTANCE.unableToApplyAnalysisConfiguration( e.getMessage(), e, indexEventContext );
		}
	}

	private List<DocumentMetadataContributor> createDocumentMetadataContributors(String mappedTypeName) {
		List<DocumentMetadataContributor> contributors = new ArrayList<>();
		link.getTypeNameMapping().getDocumentMetadataContributor( mappedTypeName )
				.ifPresent( contributors::add );
		multiTenancyStrategy.documentMetadataContributor()
				.ifPresent( contributors::add );
		return contributors;
	}

	private IndexSettings customIndexSettings(BackendBuildContext buildContext,
			ConfigurationPropertySource propertySource, EventContext indexEventContext) {

		Optional<String> schemaManagementSettingsFile = SCHEMA_MANAGEMENT_SETTINGS_FILE.get( propertySource );
		if ( !schemaManagementSettingsFile.isPresent() ) {
			return null;
		}

		String filePath = schemaManagementSettingsFile.get();
		try ( InputStream inputStream = buildContext.resourceResolver().locateResourceStream( filePath ) ) {
			if ( inputStream == null ) {
				throw MappingLog.INSTANCE.customIndexSettingsFileNotFound( filePath, indexEventContext );
			}
			try ( Reader reader = new InputStreamReader( inputStream, StandardCharsets.UTF_8 ) ) {
				return userFacingGson.fromJson( reader, IndexSettings.class );
			}
		}
		catch (IOException e) {
			throw MappingLog.INSTANCE.customIndexSettingsErrorOnLoading( filePath, e.getMessage(), e, indexEventContext );
		}
		catch (JsonSyntaxException e) {
			throw MappingLog.INSTANCE.customIndexSettingsJsonSyntaxErrors( filePath, e.getMessage(), e, indexEventContext );
		}
	}

	private RootTypeMapping customIndexMappings(BackendBuildContext buildContext,
			ConfigurationPropertySource propertySource, EventContext indexEventContext) {

		Optional<String> schemaManagementMappingsFile = SCHEMA_MANAGEMENT_MAPPING_FILE.get( propertySource );
		if ( !schemaManagementMappingsFile.isPresent() ) {
			return null;
		}

		String filePath = schemaManagementMappingsFile.get();
		try ( InputStream inputStream = buildContext.resourceResolver().locateResourceStream( filePath ) ) {
			if ( inputStream == null ) {
				throw MappingLog.INSTANCE.customIndexMappingFileNotFound( filePath, indexEventContext );
			}
			try ( Reader reader = new InputStreamReader( inputStream, StandardCharsets.UTF_8 ) ) {
				return userFacingGson.fromJson( reader, RootTypeMapping.class );
			}
		}
		catch (IOException e) {
			throw MappingLog.INSTANCE.customIndexMappingErrorOnLoading( filePath, e.getMessage(), e, indexEventContext );
		}
		catch (JsonSyntaxException e) {
			throw MappingLog.INSTANCE.customIndexMappingJsonSyntaxErrors( filePath, e.getMessage(), e, indexEventContext );
		}
	}
}
