package org.point85.domain.collector;

import java.time.Duration;

import javax.persistence.AttributeOverride;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.point85.domain.plant.Reason;
import org.point85.domain.script.EventResolverType;
import org.point85.domain.script.ResolvedEvent;

@Entity
@Table(name = "AVAILABILITY")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@AttributeOverride(name = "primaryKey", column = @Column(name = "AVAIL_KEY"))

public class AvailabilityRecord extends BaseRecord {

	@OneToOne
	@JoinColumn(name = "REASON_KEY")
	private Reason reason;

	@Column(name = "DURATION")
	private Duration duration;

	public AvailabilityRecord() {
		super();
		setType(EventResolverType.AVAILABILITY);
	}

	public AvailabilityRecord(ResolvedEvent event) {
		super(event);
		setReason(event.getReason());
		setDuration(event.getDuration());
	}

	public Reason getReason() {
		return reason;
	}

	public void setReason(Reason reason) {
		this.reason = reason;
	}

	public Duration getDuration() {
		return duration;
	}

	public void setDuration(Duration duration) {
		this.duration = duration;
	}
}