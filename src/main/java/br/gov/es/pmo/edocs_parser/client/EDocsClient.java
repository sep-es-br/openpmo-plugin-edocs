package br.gov.es.pmo.edocs_parser.client;

import br.gov.es.pmo.edocs_parser.properties.EDocsProperties;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
public class EDocsClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(EDocsClient.class);

    private final EDocsProperties properties;
    private final WebClient edocsClient;
    private final WebClient tokenClient;

    public EDocsClient(final EDocsProperties properties) {
        this.properties = properties;
        this.edocsClient = createClient(properties.getApiUrl());
        this.tokenClient = createClient(properties.getTokenUrl());
    }

    public String fetchToken(final String grantType) {
        validateCredentials();
        LOGGER.info("Requesting EDocs access token");
        final String body = tokenClient.post()
            .headers(headers -> headers.setBasicAuth(
                properties.getClientId(),
                properties.getClientSecret()
            ))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData("grant_type", grantType)
                .with("scope", properties.getScope()))
            .retrieve()
            .bodyToMono(String.class)
            .block();
        return new JSONObject(requiredBody(body)).getString("access_token");
    }

    public JSONArray searchProcesses(final List<String> protocols, final String token) {
        final JSONObject request = new JSONObject();
        request.put("protocolos", new JSONArray(protocols));
        request.put("tamanhoPagina", protocols.size() + 1);
        LOGGER.info("Searching {} process protocol(s) in EDocs", protocols.size());
        final String body = edocsClient.post()
            .uri("/v2/processos/paginated-search")
            .headers(headers -> headers.setBearerAuth(token))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request.toString())
            .retrieve()
            .bodyToMono(String.class)
            .block();
        return new JSONObject(requiredBody(body)).getJSONArray("result");
    }

    public JSONArray getProcessHistory(final String processId, final String token) {
        final String body = edocsClient.get()
            .uri("/v2/processos/{id}/atos", processId)
            .headers(headers -> headers.setBearerAuth(token))
            .retrieve()
            .bodyToMono(String.class)
            .block();
        return new JSONArray(requiredBody(body));
    }

    public boolean isProcessPriority(final String processId, final String token) {
        final ClientResponse response = edocsClient.get()
            .uri("/v2/processos/{id}/sinalizacao", processId)
            .headers(headers -> headers.setBearerAuth(token))
            .exchange()
            .block();
        if (response == null || !response.statusCode().is2xxSuccessful()) {
            return false;
        }
        final String body = response.bodyToMono(String.class).block();
        final JSONArray signals = new JSONArray(requiredBody(body));
        for (int index = 0; index < signals.length(); index++) {
            if (properties.getPriorityExternalIdentifier().equals(
                signals.getJSONObject(index).optString("identificadorExterno")
            )) {
                return true;
            }
        }
        return false;
    }

    private WebClient createClient(final String baseUrl) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .codecs(configurer -> configurer.defaultCodecs()
                .maxInMemorySize(properties.getMaxInMemorySize()))
            .build();
    }

    private void validateCredentials() {
        if (!StringUtils.hasText(properties.getClientId()) ||
            !StringUtils.hasText(properties.getClientSecret())) {
            throw new IllegalStateException("Credenciais do EDocs não configuradas");
        }
    }

    private static String requiredBody(final String body) {
        if (body == null) {
            throw new IllegalStateException("EDocs retornou uma resposta vazia");
        }
        return body;
    }
}
