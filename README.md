# openpmo-plugin-edocs

Plugin de integração entre o OpenPMO e o EDocs do Governo do Estado do Espírito Santo.

## Objetivo

Este projeto implementa o contrato genérico de processo administrativo definido por [`openpmo-plugin-administrative-process-interface`](https://github.com/sep-es-br/openpmo-plugin-administrative-process-interface).

O plugin isola do OpenPMO todos os detalhes específicos do EDocs: autenticação, endpoints HTTP, interpretação do histórico, identificação de prioridade e conversão das respostas para os DTOs neutros.

## Funcionalidades

- autenticação OAuth 2.0;
- consulta de processo por protocolo;
- consulta em lote de processos;
- leitura do histórico de atos;
- identificação de processo prioritário;
- cálculo do tempo no órgão e no setor atuais;
- identificação da autuação e do último despacho;
- resolução opcional da sigla do órgão por meio de `IOrganizationParser`;
- auto-configuração pelo Spring Boot.

## Fluxo da integração

1. `EDocsProvider` solicita um token ao Acesso Cidadão por meio de `EDocsClient`.
2. O cliente pesquisa os protocolos no EDocs.
3. Para cada processo, o plugin consulta o histórico e a sinalização de prioridade.
4. Quando disponível, `IOrganizationParser` converte os identificadores de unidade em siglas de órgãos.
5. O resultado é convertido em `AdministrativeProcessDto` e `AdministrativeProcessHistoryDto`.

## Requisitos

- Java 11 ou superior;
- Spring Boot 2.2.x;
- credenciais com acesso à API do EDocs;
- repositório JitPack configurado no projeto consumidor.

## Instalação

Adicione o JitPack aos repositórios do Gradle:

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

Para utilizar diretamente o contrato e o plugin:

```groovy
dependencies {
    implementation 'com.github.sep-es-br:openpmo-plugin-administrative-process-interface:1.0.0'
    implementation 'com.github.sep-es-br:openpmo-plugin-edocs:1.0.0'
}
```

O plugin também depende de `pmo-core-organization-parser:1.1.2`. A implementação concreta do parser de Organograma é opcional e deve ser adicionada pelo projeto consumidor quando a resolução de unidades for necessária.

## Uso no OpenPMO API

O contrato permanece como dependência fixa da API:

```groovy
implementation 'com.github.sep-es-br:openpmo-plugin-administrative-process-interface:1.0.0'
```

O plugin EDocs pode ser habilitado por coordenada no `application.properties`:

```properties
app.edocs.plugin.repository=com.github.sep-es-br:openpmo-plugin-edocs:1.0.0
```

Quando essa propriedade não está configurada, o OpenPMO inicia sem a implementação EDocs. As rotinas opcionais podem verificar a disponibilidade de `IAdministrativeProcessProvider` antes de consultar dados ou executar atualizações.

## Configuração

Recomenda-se fornecer credenciais por variáveis de ambiente.

| Propriedade Spring | Variável de ambiente | Padrão | Descrição |
| --- | --- | --- | --- |
| `edocs.token-url` | `EDOCS_TOKEN_URL` | `https://acessocidadao.es.gov.br/is/connect/token` | Endpoint de emissão do token. |
| `edocs.api-url` | `EDOCS_API_URL` | `https://api.e-docs.es.gov.br` | URL base da API do EDocs. |
| `edocs.client-id` | `EDOCS_CLIENT_ID` | sem padrão | Identificador OAuth do cliente. Obrigatório. |
| `edocs.client-secret` | `EDOCS_CLIENT_SECRET` | sem padrão | Segredo OAuth do cliente. Obrigatório. |
| `edocs.grant-type` | `EDOCS_GRANT_TYPE` | `client_credentials` | Fluxo usado na consulta individual. |
| `edocs.scope` | `EDOCS_SCOPE` | `api-sigades-consultar ApiOrganograma` | Escopos solicitados ao servidor de identidade. |
| `edocs.priority-external-identifier` | `EDOCS_PRIORITY_EXTERNAL_IDENTIFIER` | vazio | Identificador externo que caracteriza um processo prioritário. |
| `edocs.max-in-memory-size` | `EDOCS_MAX_IN_MEMORY_SIZE` | `16777216` | Limite, em bytes, para respostas carregadas pelo `WebClient`. |

Exemplo com variáveis de ambiente:

```powershell
$env:EDOCS_CLIENT_ID='client-id'
$env:EDOCS_CLIENT_SECRET='client-secret'
$env:EDOCS_PRIORITY_EXTERNAL_IDENTIFIER='identificador-prioridade'
```

Não grave credenciais reais no repositório.

### Compatibilidade com propriedades antigas

O plugin mantém fallback para propriedades usadas anteriormente pelo OpenPMO:

- `api.e-docs.uri.token`;
- `api.e-docs.uri.webapi`;
- `api.e-docs.client-id`;
- `api.e-docs.client-secret`;
- `api.e-docs.grant_type`;
- `api.e-docs.scope`;
- `api.e-docs.identificador-externo.processo-prioritario`;
- `api.acessocidadao.client-id`;
- `api.acessocidadao.client-secret`.

As propriedades `edocs.*` e as variáveis `EDOCS_*` devem ser preferidas em novas instalações.

## Auto-configuração Spring Boot

O arquivo `META-INF/spring.factories` registra `EDocsParserAutoConfig`. Ao adicionar o plugin ao classpath, o Spring Boot:

- carrega `edocs-plugin.properties`;
- registra `EDocsProperties`;
- encontra `EDocsClient` e `EDocsProvider` por component scan;
- disponibiliza `EDocsProvider` como bean de `IAdministrativeProcessProvider`.

Não é necessário adicionar `@Import` manualmente.

## Uso pelo contrato

O consumidor deve depender de `IAdministrativeProcessProvider`, sem acoplamento direto a `EDocsProvider`:

```java
@Service
public class ProcessIntegrationService {

    private final IAdministrativeProcessProvider provider;

    public ProcessIntegrationService(final IAdministrativeProcessProvider provider) {
        this.provider = provider;
    }

    public AdministrativeProcessDto find(final String protocol) {
        return provider.getProcess(protocol);
    }

    public List<AdministrativeProcessDto> findAll(final List<String> protocols) {
        return provider.getProcesses(protocols);
    }
}
```

## Comportamento das consultas

### Consulta individual

`getProcess(protocol)`:

- limpa o cache do parser de Organograma, quando presente;
- usa o `grant-type` configurado;
- consulta o protocolo informado;
- lança `IllegalStateException` quando o processo não é encontrado.

### Consulta em lote

`getProcesses(protocols)`:

- retorna uma lista vazia quando a entrada é nula ou vazia;
- limpa o cache do parser de Organograma, quando presente;
- utiliza `client_credentials`;
- envia os protocolos em uma única pesquisa paginada.

## Integração com Organograma

O plugin procura opcionalmente um bean de `IOrganizationParser<String>`.

Quando o parser está disponível, o plugin usa os identificadores de unidade retornados pelo EDocs para obter a sigla do órgão. O cache do parser é limpo no início de cada consulta individual ou em lote.

Sem uma implementação de `IOrganizationParser`, o plugin continua funcionando e tenta usar as siglas presentes na resposta do próprio EDocs. Dependendo do tipo de evento, alguns campos de órgão podem ficar nulos.

## Endpoints consumidos

| Operação | Método e caminho |
| --- | --- |
| Obter token | `POST` na URL configurada em `edocs.token-url` |
| Pesquisar processos | `POST /v2/processos/paginated-search` |
| Consultar histórico | `GET /v2/processos/{id}/atos` |
| Consultar prioridade | `GET /v2/processos/{id}/sinalizacao` |

## Tratamento de erros

- credenciais ausentes geram `IllegalStateException` antes da autenticação;
- respostas vazias do EDocs geram `IllegalStateException`;
- erros HTTP de autenticação, pesquisa ou histórico são propagados pelo `WebClient`;
- falha ou resposta não bem-sucedida na consulta de sinalização resulta em `priority=false`.

## Build local

Para compilar e publicar no Maven local no Windows:

```powershell
.\gradlew.bat clean build publishToMavenLocal
```

Em Linux ou macOS:

```bash
./gradlew clean build publishToMavenLocal
```

Para o build local do plugin encontrar uma versão ainda não publicada da interface, publique primeiro `openpmo-plugin-administrative-process-interface` no Maven local.
