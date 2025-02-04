/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.pojo.bridge.binding.impl;

import org.hibernate.search.engine.backend.types.dsl.IndexFieldTypeOptionsStep;
import org.hibernate.search.engine.backend.types.dsl.ScaledNumberIndexFieldTypeOptionsStep;
import org.hibernate.search.engine.backend.types.dsl.SearchableProjectableIndexFieldTypeOptionsStep;
import org.hibernate.search.engine.backend.types.dsl.StandardIndexFieldTypeOptionsStep;
import org.hibernate.search.engine.backend.types.dsl.StringIndexFieldTypeOptionsStep;
import org.hibernate.search.engine.backend.types.dsl.VectorFieldTypeOptionsStep;
import org.hibernate.search.mapper.pojo.bridge.ValueBridge;
import org.hibernate.search.mapper.pojo.bridge.binding.spi.FieldModelContributorContext;
import org.hibernate.search.mapper.pojo.logging.impl.MappingLog;

final class FieldModelContributorContextImpl<F> implements FieldModelContributorContext {

	private final ValueBridge<?, F> bridge;
	private final IndexFieldTypeOptionsStep<?, ? super F> fieldTypeOptionsStep;

	FieldModelContributorContextImpl(ValueBridge<?, F> bridge, IndexFieldTypeOptionsStep<?, ? super F> fieldTypeOptionsStep) {
		this.bridge = bridge;
		this.fieldTypeOptionsStep = fieldTypeOptionsStep;
	}

	@Override
	public void indexNullAs(String value) {
		searchableProjectableIndexFieldTypeOptionsStep().indexNullAs( bridge.parse( value ) );
	}

	/*
	 * If fieldTypeOptionsStep is an instance of IndexFieldTypeOptionsStep<?, ? super F>
	 * and StandardIndexFieldTypeOptionsStep,
	 * it's an instance of StandardIndexFieldTypeOptionsStep<?, ? super F>
	 */
	@SuppressWarnings("unchecked")
	@Override
	public StandardIndexFieldTypeOptionsStep<?, ? super F> standardTypeOptionsStep() {
		if ( fieldTypeOptionsStep instanceof StandardIndexFieldTypeOptionsStep ) {
			return (StandardIndexFieldTypeOptionsStep<?, ? super F>) fieldTypeOptionsStep;
		}
		else {
			throw MappingLog.INSTANCE.invalidFieldEncodingForStandardFieldMapping(
					fieldTypeOptionsStep, StandardIndexFieldTypeOptionsStep.class
			);
		}
	}

	@Override
	public StringIndexFieldTypeOptionsStep<?> stringTypeOptionsStep() {
		if ( fieldTypeOptionsStep instanceof StringIndexFieldTypeOptionsStep ) {
			return (StringIndexFieldTypeOptionsStep<?>) fieldTypeOptionsStep;
		}
		else {
			throw MappingLog.INSTANCE.invalidFieldEncodingForStringFieldMapping(
					fieldTypeOptionsStep, StringIndexFieldTypeOptionsStep.class
			);
		}
	}

	@Override
	public ScaledNumberIndexFieldTypeOptionsStep<?, ?> scaledNumberTypeOptionsStep() {
		if ( fieldTypeOptionsStep instanceof ScaledNumberIndexFieldTypeOptionsStep ) {
			return (ScaledNumberIndexFieldTypeOptionsStep<?, ?>) fieldTypeOptionsStep;
		}
		else {
			throw MappingLog.INSTANCE.invalidFieldEncodingForScaledNumberFieldMapping(
					fieldTypeOptionsStep, ScaledNumberIndexFieldTypeOptionsStep.class
			);
		}
	}

	@Override
	public VectorFieldTypeOptionsStep<?, ?> vectorTypeOptionsStep() {
		if ( fieldTypeOptionsStep instanceof VectorFieldTypeOptionsStep ) {
			return (VectorFieldTypeOptionsStep<?, ?>) fieldTypeOptionsStep;
		}
		else {
			throw MappingLog.INSTANCE.invalidFieldEncodingForVectorFieldMapping(
					fieldTypeOptionsStep, VectorFieldTypeOptionsStep.class
			);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public SearchableProjectableIndexFieldTypeOptionsStep<?, ? super F> searchableProjectableIndexFieldTypeOptionsStep() {
		if ( fieldTypeOptionsStep instanceof VectorFieldTypeOptionsStep ) {
			return (VectorFieldTypeOptionsStep<?, F>) fieldTypeOptionsStep;
		}
		return standardTypeOptionsStep();
	}

	@Override
	public void checkNonStandardTypeOptionsStep() {
		if ( fieldTypeOptionsStep instanceof StandardIndexFieldTypeOptionsStep ) {
			throw MappingLog.INSTANCE.invalidFieldEncodingForNonStandardFieldMapping(
					fieldTypeOptionsStep, StandardIndexFieldTypeOptionsStep.class
			);
		}
	}
}
