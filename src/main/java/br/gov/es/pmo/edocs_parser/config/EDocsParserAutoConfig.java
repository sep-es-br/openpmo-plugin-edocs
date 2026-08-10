package br.gov.es.pmo.edocs_parser.config;

import br.gov.es.pmo.edocs_parser.properties.EDocsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@ComponentScan("br.gov.es.pmo.edocs_parser")
@EnableConfigurationProperties(EDocsProperties.class)
@PropertySource("classpath:edocs-plugin.properties")
public class EDocsParserAutoConfig {
}
