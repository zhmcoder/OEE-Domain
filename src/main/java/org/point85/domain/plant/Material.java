package org.point85.domain.plant;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

@Entity
@Table(name = "MATERIAL")
@AttributeOverrides({ @AttributeOverride(name = "primaryKey", column = @Column(name = "MAT_KEY")),
		@AttributeOverride(name = "name", column = @Column(name = "ID")) })

@NamedQueries({
		@NamedQuery(name = Material.MATL_BY_NAME, query = "SELECT matl FROM Material matl WHERE matl.name = :name"),
		@NamedQuery(name = Material.MATLS_BY_CATEGORY, query = "SELECT matl FROM Material matl WHERE matl.category = :category"),
		@NamedQuery(name = Material.MATL_CATEGORIES, query = "SELECT DISTINCT matl.category FROM Material matl WHERE matl.category IS NOT NULL"), 
		@NamedQuery(name = Material.MATL_ALL, query = "SELECT matl FROM Material matl"),})

public class Material extends NamedObject {
	// the one and only root material in the hierarchy
	public static final String ROOT_MATERIAL_NAME = "All Materials";

	// query names
	public static final String MATL_BY_NAME = "MATL.ByName";
	public static final String MATLS_BY_CATEGORY = "MATL.ByCategory";
	public static final String MATL_CATEGORIES = "MATL.Categories";
	public static final String MATL_ALL = "MATL.All";

	@Column(name = "CATEGORY")
	private String category;

	public Material() {
		super();
	}

	public Material(String id, String description) {
		super(id, description);
	}

	/**
	 * Get the category
	 * 
	 * @return Category
	 */
	public String getCategory() {
		return category;
	}

	/**
	 * Set the category
	 * 
	 * @param category
	 *            Category
	 */
	public void setCategory(String category) {
		this.category = category;
	}

	@Override
	public String toString() {
		return super.toString() + ", Category: " + getCategory();
	}

	/*
	@Override
	public String getFetchQueryName() {
		return MATL_BY_NAME;
	}
	*/
}
