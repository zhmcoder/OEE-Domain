package org.point85.domain.plant;

import java.util.Objects;

import javax.persistence.AttributeOverride;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.point85.domain.uom.Quantity;
import org.point85.domain.uom.UnitOfMeasure;

@Entity
@Table(name = "EQUIPMENT_MATERIAL")
@AttributeOverride(name = "primaryKey", column = @Column(name = "EM_KEY"))
@NamedQueries({
		@NamedQuery(name = EquipmentMaterial.EQM_XREF, query = "SELECT eqm FROM EquipmentMaterial eqm WHERE runRateUOM = :uom OR rejectUOM = :uom"), })
public class EquipmentMaterial extends KeyedObject {
	// named queries
	public static final String EQM_XREF = "EQM.XRef";

	// OEE target
	@Column(name = "OEE_TARGET")
	private double oeeTarget;

	// ideal run rate
	@Column(name = "RUN_AMOUNT")
	private double runRateAmount;

	@OneToOne
	@JoinColumn(name = "RUN_UOM_KEY")
	private UnitOfMeasure runRateUOM;

	@OneToOne
	@JoinColumn(name = "REJECT_UOM_KEY")
	private UnitOfMeasure rejectUOM;

	@OneToOne
	@JoinColumn(name = "MAT_KEY")
	private Material material;

	@OneToOne
	@JoinColumn(name = "EQ_KEY")
	private Equipment equipment;

	public EquipmentMaterial() {
	}

	public EquipmentMaterial(Equipment equipment, Material material) {
		this.equipment = equipment;
		this.material = material;
	}

	public double getOeeTarget() {
		return oeeTarget;
	}

	public void setOeeTarget(double oeeTarget) {
		this.oeeTarget = oeeTarget;
	}

	public Quantity getRunRate() {
		return new Quantity(runRateAmount, runRateUOM);
	}

	public double getRunRateAmount() {
		return runRateAmount;
	}

	public UnitOfMeasure getRunRateUOM() {
		return runRateUOM;
	}

	public void setRunRate(Quantity runRate) {
		this.runRateAmount = runRate.getAmount();
		this.runRateUOM = runRate.getUOM();
	}

	public void setRunRateAmount(double amount) {
		this.runRateAmount = amount;
	}

	public void setRunRateUOM(UnitOfMeasure uom) {
		this.runRateUOM = uom;
	}

	public UnitOfMeasure getRejectUOM() {
		return rejectUOM;
	}

	public void setRejectUOM(UnitOfMeasure uom) {
		this.rejectUOM = uom;
	}

	public Material getMaterial() {
		return material;
	}

	public void setMaterial(Material material) {
		this.material = material;
	}

	public Equipment getEquipment() {
		return equipment;
	}

	public void setEquipment(Equipment equipment) {
		this.equipment = equipment;
	}

	@Override
	public int hashCode() {
		return Objects.hash(getEquipment(), getMaterial());
	}

	@Override
	public boolean equals(Object other) {

		if (other == null || !(other instanceof EquipmentMaterial)) {
			return false;
		}
		EquipmentMaterial otherEquipmentMaterial = (EquipmentMaterial) other;

		if (getMaterial() == null) {
			return true;
		}

		if (!getMaterial().equals(otherEquipmentMaterial.getMaterial())) {
			return false;
		}

		return true;
	}

	@Override
	public String toString() {
		String text = "Matl: " + this.getMaterial().getName() + ", OEE: " + getOeeTarget();
		return text;
	}
}