package com.pulseops.service.integration;

import com.pulseops.exception.ResourceNotFoundException;
import java.util.Arrays;
import java.util.List;

public enum IntegrationCatalog {
    AI_WEB_AUDITOR("ai-web-auditor", "AI Web Auditor", "Auditoria automatizada de sites e aplicações", "HTTP · Relatórios de qualidade"),
    ARENA_PREDICT("arena-predict", "Arena Predict", "Análise preditiva e inteligência esportiva", "HTTP"),
    PLAYSPACE("playspace", "PlaySpace", "Gestão de espaços e reservas esportivas", "HTTP"),
    LOGITRACK("logitrack", "LogiTrack", "Rastreamento e operações logísticas", "HTTP"),
    HELPDESK("helpdesk", "HelpDesk", "Suporte e gestão de atendimento", "HTTP"),
    HOSPITAL("hospital", "Gestão Hospitalar", "Operações e serviços de saúde", "HTTP");

    private final String slug;
    private final String title;
    private final String description;
    private final String type;

    IntegrationCatalog(String slug, String title, String description, String type) {
        this.slug = slug;
        this.title = title;
        this.description = description;
        this.type = type;
    }

    public String slug() { return slug; }
    public String title() { return title; }
    public String description() { return description; }
    public String type() { return type; }
    public static List<IntegrationCatalog> entries() { return List.of(values()); }
    public static IntegrationCatalog find(String slug) {
        return Arrays.stream(values()).filter(entry -> entry.slug.equals(slug)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Integração", slug));
    }
}
