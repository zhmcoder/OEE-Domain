package org.point85.domain.plant;

import javax.persistence.AttributeOverride;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "MATERIAL")
@AttributeOverride(name = "primaryKey", column = @Column(name = "MAT_KEY"))

public class Material extends NamedObject {
	// the one and only root material in the hierarchy
	public static final String ROOT_MATERIAL_NAME = "All Materials";

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

	public String getDisplayString() {
		String text = getName();

		if (getDescription() != null) {
			text += " (" + getDescription() + ")";
		}
		return text;
	}
}
