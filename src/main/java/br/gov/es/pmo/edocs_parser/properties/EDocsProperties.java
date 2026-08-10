package br.gov.es.pmo.edocs_parser.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "edocs")
public class EDocsProperties {

    private String tokenUrl;
    private String apiUrl;
    private String clientId;
    private String clientSecret;
    private String grantType = "client_credentials";
    private String scope;
    private String priorityExternalIdentifier;
    private int maxInMemorySize = 16777216;

    public String getTokenUrl() { return tokenUrl; }
    public void setTokenUrl(final String tokenUrl) { this.tokenUrl = tokenUrl; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(final String apiUrl) { this.apiUrl = apiUrl; }
    public String getClientId() { return clientId; }
    public void setClientId(final String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(final String clientSecret) { this.clientSecret = clientSecret; }
    public String getGrantType() { return grantType; }
    public void setGrantType(final String grantType) { this.grantType = grantType; }
    public String getScope() { return scope; }
    public void setScope(final String scope) { this.scope = scope; }
    public String getPriorityExternalIdentifier() { return priorityExternalIdentifier; }
    public void setPriorityExternalIdentifier(final String priorityExternalIdentifier) { this.priorityExternalIdentifier = priorityExternalIdentifier; }
    public int getMaxInMemorySize() { return maxInMemorySize; }
    public void setMaxInMemorySize(final int maxInMemorySize) { this.maxInMemorySize = maxInMemorySize; }
}
