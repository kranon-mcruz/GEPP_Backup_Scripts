package ScriptsExport.config;

public class OrgCredential {
    private String host;
    private String clientId;
    private String clientSecret;


    // Constructor por defecto (para Jackson/JSON)
    public OrgCredential() {}

    // AGREGAR ESTE CONSTRUCTOR para tu Service
    public OrgCredential(String host, String clientId, String clientSecret) {
        this.host = host;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    // Getters y Setters...
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
}