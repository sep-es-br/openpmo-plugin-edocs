package br.gov.es.pmo.edocs_parser.model;

import java.time.LocalDateTime;

public class EDocsHistoryEntry {

    private final LocalDateTime date;
    private final String sector;
    private final String organization;
    private final String descriptionType;

    public EDocsHistoryEntry(
        final LocalDateTime date,
        final String sector,
        final String organization,
        final String descriptionType
    ) {
        this.date = date;
        this.sector = sector;
        this.organization = organization;
        this.descriptionType = descriptionType;
    }

    public LocalDateTime getDate() { return date; }
    public String getSector() { return sector; }
    public String getOrganization() { return organization; }
    public String getDescriptionType() { return descriptionType; }
}
