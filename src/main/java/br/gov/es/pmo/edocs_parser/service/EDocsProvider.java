package br.gov.es.pmo.edocs_parser.service;

import br.gov.es.pmo.administrative_process_core.model.AdministrativeProcessDto;
import br.gov.es.pmo.administrative_process_core.model.AdministrativeProcessHistoryDto;
import br.gov.es.pmo.administrative_process_core.model.IAdministrativeProcessProvider;
import br.gov.es.pmo.edocs_parser.client.EDocsClient;
import br.gov.es.pmo.edocs_parser.model.EDocsHistoryEntry;
import br.gov.es.pmo.edocs_parser.properties.EDocsProperties;
import br.gov.es.pmo.organization_parser.pmo_base.model.IOrganizationParser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class EDocsProvider implements IAdministrativeProcessProvider {

    private static final String CLOSED_STATUS = "Encerrado";

    private final EDocsClient client;
    private final EDocsProperties properties;
    private final ObjectProvider<IOrganizationParser<String>> organizationParser;

    public EDocsProvider(
        final EDocsClient client,
        final EDocsProperties properties,
        final ObjectProvider<IOrganizationParser<String>> organizationParser
    ) {
        this.client = client;
        this.properties = properties;
        this.organizationParser = organizationParser;
    }

    @Override
    public AdministrativeProcessDto getProcess(final String protocol) {
        clearOrganizationCache();
        final List<AdministrativeProcessDto> processes = loadProcesses(
            Collections.singletonList(protocol),
            properties.getGrantType()
        );
        if (processes.isEmpty()) {
            throw new IllegalStateException("Processo não encontrado para o protocolo: " + protocol);
        }
        return processes.get(0);
    }

    @Override
    public List<AdministrativeProcessDto> getProcesses(final List<String> protocols) {
        if (protocols == null || protocols.isEmpty()) {
            return Collections.emptyList();
        }
        clearOrganizationCache();
        return loadProcesses(protocols, "client_credentials");
    }

    private List<AdministrativeProcessDto> loadProcesses(
        final List<String> protocols,
        final String grantType
    ) {
        final String token = client.fetchToken(grantType);
        final JSONArray results = client.searchProcesses(protocols, token);
        final List<AdministrativeProcessDto> processes = new ArrayList<>();
        for (int index = 0; index < results.length(); index++) {
            processes.add(mapProcess(results.getJSONObject(index), token));
        }
        return processes;
    }

    private AdministrativeProcessDto mapProcess(final JSONObject json, final String token) {
        final String processId = json.getString("id");
        final List<EDocsHistoryEntry> entries = mapHistory(
            client.getProcessHistory(processId, token),
            token
        );
        final List<AdministrativeProcessHistoryDto> timeline = createTimeline(entries);
        final EDocsHistoryEntry current = entries.stream()
            .max(Comparator.comparing(EDocsHistoryEntry::getDate))
            .orElse(null);
        final String currentSector = current == null ? null : current.getSector();
        final String currentOrganization = current == null ? null : current.getOrganization();

        LocalDateTime sectorDate = LocalDateTime.now();
        LocalDateTime organizationDate = LocalDateTime.now();
        final List<EDocsHistoryEntry> descendingEntries = entries.stream()
            .sorted(Comparator.comparing(EDocsHistoryEntry::getDate).reversed())
            .collect(Collectors.toList());
        for (final EDocsHistoryEntry entry : descendingEntries) {
            if (!Objects.equals(entry.getSector(), currentSector)) {
                break;
            }
            sectorDate = entry.getDate();
        }
        for (final EDocsHistoryEntry entry : descendingEntries) {
            if (!Objects.equals(entry.getOrganization(), currentOrganization)) {
                break;
            }
            organizationDate = entry.getDate();
        }

        LocalDateTime until = LocalDateTime.now();
        if (CLOSED_STATUS.equals(json.optString("situacao"))) {
            final Optional<AdministrativeProcessHistoryDto> closing = timeline.stream()
                .filter(item -> "Encerramento".equals(item.getDescriptionType()))
                .max(Comparator.comparing(AdministrativeProcessHistoryDto::getUpdateDate));
            until = closing.map(AdministrativeProcessHistoryDto::getUpdateDate)
                .orElse(LocalDateTime.now());
            closing.ifPresent(item -> item.setDaysDuration(null));
        }

        final Optional<AdministrativeProcessHistoryDto> opening = timeline.stream()
            .filter(item -> "Autuacao".equalsIgnoreCase(item.getDescriptionType()))
            .min(Comparator.comparing(AdministrativeProcessHistoryDto::getUpdateDate));
        final Optional<AdministrativeProcessHistoryDto> lastDispatch = timeline.stream()
            .filter(item -> "Despacho".equalsIgnoreCase(item.getDescriptionType()))
            .max(Comparator.comparing(AdministrativeProcessHistoryDto::getUpdateDate));

        final AdministrativeProcessDto process = new AdministrativeProcessDto();
        process.setProcessNumber(json.getString("protocolo"));
        process.setSubject(json.getString("resumo"));
        process.setStatus(json.getString("situacao"));
        process.setPriority(client.isProcessPriority(processId, token));
        process.setCurrentOrganization(currentOrganization);
        process.setLengthOfStayOn(Duration.between(organizationDate, until).abs().toDays());
        process.setLengthOfStayOnSector(Duration.between(sectorDate, until).abs().toDays());
        process.setActingOrganization(opening.map(AdministrativeProcessHistoryDto::getOrganizationName).orElse(null));
        process.setActingSector(opening.map(AdministrativeProcessHistoryDto::getSector).orElse(null));
        process.setActingDate(opening.map(AdministrativeProcessHistoryDto::getUpdateDate).orElse(null));
        process.setLastDispatchDate(lastDispatch.map(AdministrativeProcessHistoryDto::getUpdateDate).orElse(null));
        process.setHistory(timeline);
        return process;
    }

    private List<EDocsHistoryEntry> mapHistory(final JSONArray history, final String token) {
        final List<EDocsHistoryEntry> entries = new ArrayList<>();
        String lastSector = null;
        String lastOrganization = null;
        for (int index = 0; index < history.length(); index++) {
            final JSONObject item = history.getJSONObject(index);
            final String descriptionType = item.optString("descricaoTipo", null);
            if (hasLocation(descriptionType)) {
                lastSector = resolveSector(descriptionType, item).orElse(null);
                lastOrganization = resolveOrganization(descriptionType, item, token).orElse(null);
            }
            final LocalDateTime date = LocalDateTime.parse(
                item.getString("dataHora"),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME
            );
            entries.add(new EDocsHistoryEntry(
                date,
                lastSector,
                lastOrganization,
                descriptionType
            ));
        }
        return entries;
    }

    private List<AdministrativeProcessHistoryDto> createTimeline(final List<EDocsHistoryEntry> entries) {
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        final List<EDocsHistoryEntry> ascending = entries.stream()
            .sorted(Comparator.comparing(EDocsHistoryEntry::getDate))
            .collect(Collectors.toList());
        final List<AdministrativeProcessHistoryDto> timeline = new ArrayList<>();
        for (int index = 0; index < ascending.size(); index++) {
            final EDocsHistoryEntry current = ascending.get(index);
            final LocalDateTime end = index + 1 < ascending.size()
                ? ascending.get(index + 1).getDate()
                : LocalDateTime.now();
            final AdministrativeProcessHistoryDto item = new AdministrativeProcessHistoryDto();
            item.setDaysDuration(Duration.between(current.getDate(), end).toDays());
            item.setUpdateDate(current.getDate());
            item.setOrganizationName(current.getOrganization());
            item.setSector(current.getSector());
            item.setDescriptionType(current.getDescriptionType());
            timeline.add(item);
        }
        timeline.sort(Comparator.comparing(AdministrativeProcessHistoryDto::getUpdateDate).reversed());
        return timeline;
    }

    private Optional<String> resolveSector(final String type, final JSONObject json) {
        if (isTypeA(type)) {
            final Optional<String> location = nestedString(json, "localizacao", "nome");
            if (location.isPresent()) {
                return location;
            }
            return nestedString(json, "papel", "setor", "nome");
        }
        if (isTypeB(type)) {
            return nestedString(json, "papel", "setor", "nome");
        }
        final JSONObject destination = json.optJSONObject("destino");
        if (destination == null) {
            return Optional.empty();
        }
        return firstPresent(
            nestedString(destination, "cidadao", "nome"),
            nestedString(destination, "papel", "setor", "nome"),
            nestedString(destination, "grupo", "nome"),
            nestedString(destination, "setor", "nome"),
            optionalText(destination.optString("nome", null))
        );
    }

    private Optional<String> resolveOrganization(
        final String type,
        final JSONObject json,
        final String token
    ) {
        if (isTypeA(type)) {
            final Optional<String> unitId = nestedString(json, "localizacao", "id");
            if (unitId.isPresent()) {
                final Optional<String> abbreviation = organizationByUnit(unitId.get(), token);
                if (abbreviation.isPresent()) {
                    return abbreviation;
                }
            }
            return nestedString(json, "papel", "setor", "organizacao", "sigla");
        }
        if (isTypeB(type)) {
            return nestedString(json, "papel", "setor", "organizacao", "sigla");
        }
        final JSONObject destination = json.optJSONObject("destino");
        if (destination == null) {
            return Optional.empty();
        }
        final List<Optional<String>> unitIds = new ArrayList<>();
        unitIds.add(nestedString(destination, "papel", "setor", "id"));
        unitIds.add(nestedString(destination, "grupo", "localizacao", "id"));
        unitIds.add(nestedString(destination, "setor", "id"));
        for (final Optional<String> unitId : unitIds) {
            if (unitId.isPresent()) {
                final Optional<String> abbreviation = organizationByUnit(unitId.get(), token);
                if (abbreviation.isPresent()) {
                    return abbreviation;
                }
            }
        }
        return nestedString(destination, "organizacao", "sigla");
    }

    private Optional<String> organizationByUnit(final String unitId, final String token) {
        final IOrganizationParser<String> parser = organizationParser.getIfAvailable();
        return parser == null
            ? Optional.empty()
            : parser.findAbbreviationByUnit(unitId, token);
    }

    private void clearOrganizationCache() {
        final IOrganizationParser<String> parser = organizationParser.getIfAvailable();
        if (parser != null) {
            parser.clearCache();
        }
    }

    private boolean hasLocation(final String type) {
        return "Autuacao".equals(type) ||
            "Encerramento".equals(type) ||
            "AjusteCustodia".equals(type) ||
            "Despacho".equals(type) ||
            "Reabertura".equals(type) ||
            "Avocamento".equals(type);
    }

    private boolean isTypeA(final String type) {
        return "Autuacao".equals(type) || "Encerramento".equals(type) || "Edicao".equals(type);
    }

    private boolean isTypeB(final String type) {
        return "Entranhamento".equals(type) || "Desentranhamento".equals(type) || "AjusteCustodia".equals(type);
    }

    private boolean isTypeC(final String type) {
        return "Despacho".equals(type) || "Reabertura".equals(type) || "Avocamento".equals(type);
    }

    private static Optional<String> nestedString(final JSONObject root, final String... path) {
        JSONObject current = root;
        for (int index = 0; index < path.length - 1; index++) {
            current = current.optJSONObject(path[index]);
            if (current == null) {
                return Optional.empty();
            }
        }
        return optionalText(current.optString(path[path.length - 1], null));
    }

    private static Optional<String> optionalText(final String value) {
        return value == null || value.trim().isEmpty()
            ? Optional.empty()
            : Optional.of(value);
    }

    @SafeVarargs
    private static Optional<String> firstPresent(final Optional<String>... values) {
        for (final Optional<String> value : values) {
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
}
