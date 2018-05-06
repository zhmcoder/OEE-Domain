package org.point85.domain.opc.ua;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.point85.domain.collector.CollectorDataSource;
import org.point85.domain.collector.DataSourceType;

@Entity
@DiscriminatorValue(DataSourceType.OPC_UA_VALUE)

public class OpcUaSource extends CollectorDataSource {

	private transient SecurityPolicy policy = SecurityPolicy.None;

	private transient String endpointUrl;
	
	private transient MessageSecurityMode messageSecurityMode = MessageSecurityMode.None;
	
	private transient KeyPair keyPair;
	
	//private transient X509Certificate certificate;
	
	@Column(name = "END_PATH")
	private String endpointPath;
	
	@Column(name = "SEC_POLICY")
	private String securityPolicy;
	
	@Column(name = "MSG_MODE")
	private String messageMode;
	
	@Column(name = "CERT_PATH")
	private String certificatePath;

	public OpcUaSource() {
		super();
		setDataSourceType(DataSourceType.OPC_UA);
	}

	public OpcUaSource(String name, String description) {
		super(name, description);
		setDataSourceType(DataSourceType.OPC_UA);
	}

	@Override
	public String getId() {
		return getEndpointUrl();
	}

	@Override
	public void setId(String id) {
		String[] tokens = id.split(":");
		if (tokens.length == 2) {
			setHost(tokens[0]);
			setPort(Integer.valueOf(tokens[1]));
		}
	}

	public String getEndpointPath() {
		return endpointPath;
	}

	public void setPath(String path) {
		this.endpointPath = path;
	}

	public String getEndpointUrl() {
		if (endpointUrl == null) {
			// only TCP is supported
			endpointUrl = String.format("opc.tcp://%s:%s", getHost(), getPort());

			if (getEndpointPath() != null && getEndpointPath().length() > 0) {
				endpointUrl += "/" + getEndpointPath();
			}
		}
		return endpointUrl;
	}

	public SecurityPolicy getSecurityPolicy() {
		if (securityPolicy != null) {
			policy = SecurityPolicy.valueOf(securityPolicy);
		}
		return policy;
	}

	public void setSecurityPolicy(SecurityPolicy policy) {
		this.securityPolicy = policy.name();
		this.policy = policy;
	}

	public MessageSecurityMode getMessageSecurityMode() {
		if (messageMode != null) {
			messageSecurityMode = MessageSecurityMode.valueOf(messageMode);
		}
		return messageSecurityMode;
	}

	public void setMessageSecurityMode(MessageSecurityMode messageSecurityMode) {
		this.messageMode = messageSecurityMode.name();
		this.messageSecurityMode = messageSecurityMode;
	}

	public KeyPair getKeyPair() {
		return keyPair;
	}

	public void setKeyPair(KeyPair keyPair) {
		this.keyPair = keyPair;
	}

	public String getCertificatePath() {
		return certificatePath;
	}

	public void setCertificatePath(String certificatePath) {
		this.certificatePath = certificatePath;
	}
	
}
