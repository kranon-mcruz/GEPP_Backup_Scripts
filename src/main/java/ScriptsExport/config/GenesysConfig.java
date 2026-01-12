package ScriptsExport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "genesys")
public class GenesysConfig {

    private String path; // Nuevo campo para la ruta
    private Map<String, Credential> credentials;

    public String getPath() {
        return path;
    }

    public void setPath(String rutaDescarga) {
        this.path = rutaDescarga;
    }
    public Map<String, Credential> getCredentials() {
        return credentials;
    }

    public void setCredentials(Map<String, Credential> credentials) {
        this.credentials = credentials;
    }

    public static class Credential {
        private String host;
        private String clientId;
        private String clientSecret;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }
}
