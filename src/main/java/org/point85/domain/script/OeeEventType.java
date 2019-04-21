package org.point85.domain.script;

import java.util.ArrayList;
import java.util.List;

import org.point85.domain.i18n.DomainLocalizer;

public enum OeeEventType {
	AVAILABILITY, PROD_GOOD, PROD_REJECT, PROD_STARTUP, MATL_CHANGE, JOB_CHANGE, CUSTOM;

	// database values
	public static final String AVAILABILITY_VALUE = "AVAIL";
	public static final String GOOD_PROD_VALUE = "GOOD";
	public static final String REJECT_PROD_VALUE = "REJECT";
	public static final String STARTUP_PROD_VALUE = "STARTUP";
	public static final String MATERIAL_VALUE = "MATL";
	public static final String JOB_VALUE = "JOB";
	public static final String CUSTOM_VALUE = "CUSTOM";

	public boolean isAvailability() {
		return this.equals(AVAILABILITY) ? true : false;
	}

	public boolean isProduction() {
		return (this.equals(PROD_GOOD) || this.equals(PROD_REJECT) || this.equals(PROD_STARTUP)) ? true : false;
	}

	public boolean isMaterial() {
		return this.equals(MATL_CHANGE) ? true : false;
	}

	public boolean isJob() {
		return this.equals(JOB_CHANGE) ? true : false;
	}

	public static List<OeeEventType> getProductionTypes() {
		List<OeeEventType> types = new ArrayList<>();
		types.add(OeeEventType.PROD_GOOD);
		types.add(OeeEventType.PROD_REJECT);
		types.add(OeeEventType.PROD_STARTUP);
		return types;
	}

	@Override
	public String toString() {
		String key = null;

		switch (this) {
		case AVAILABILITY:
			key = "availability.type";
			break;
		case CUSTOM:
			key = "custom.type";
			break;
		case JOB_CHANGE:
			key = "job.type";
			break;
		case MATL_CHANGE:
			key = "setup.type";
			break;
		case PROD_GOOD:
			key = "good.type";
			break;
		case PROD_REJECT:
			key = "reject.type";
			break;
		case PROD_STARTUP:
			key = "startup.type";
			break;
		default:
			break;
		}
		return DomainLocalizer.instance().getLangString(key);
	}
}
