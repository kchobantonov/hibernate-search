/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.test.embedded;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import org.hibernate.annotations.TargetEmbeddable;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;

/**
 * @author Emmanuel Bernard
 */

@Entity
@Indexed
public class Address {

	@Id
	@GeneratedValue
	private Long id;

	@Field
	private String street;

	@IndexedEmbedded(depth = 1, prefix = "ownedBy_", targetElement = Owner.class)
	@TargetEmbeddable(Owner.class)
	@Embedded
	private Person ownedBy;

	@ElementCollection
	@IndexedEmbedded(prefix = "inhabitants.")
	private Set<Resident> residents = new HashSet<Resident>();

	@OneToMany(mappedBy = "address")
	private Set<Tower> towers = new HashSet<Tower>();

	@ManyToOne(cascade = CascadeType.ALL)
	@IndexedEmbedded
	@IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
	private Country country;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getStreet() {
		return street;
	}

	public void setStreet(String street) {
		this.street = street;
	}

	public Person getOwnedBy() {
		return ownedBy;
	}

	public void setOwnedBy(Person ownedBy) {
		this.ownedBy = ownedBy;
	}

	public Set<Resident> getResidents() {
		return residents;
	}

	public void setResidents(Set<Resident> residents) {
		this.residents = residents;
	}

	public Set<Tower> getTowers() {
		return towers;
	}

	public void setTowers(Set<Tower> towers) {
		this.towers = towers;
	}

	public Country getCountry() {
		return country;
	}

	public void setCountry(Country country) {
		this.country = country;
	}
}
