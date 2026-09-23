# Better Radio — PRD do MVP

**Status:** Fases 0–6 implementadas. Testes e builds passam; validação final em jogo será feita pelo usuário.
**Base:** `minecraft-music-hotkey-architecture.md`
**Plataformas alvo:** Minecraft 1.20.1 (Forge) e Minecraft 1.21.1 (NeoForge)

## 1. Resumo

Better Radio adiciona um atalho do lado do cliente para iniciar uma música ambiente aleatória ou avançar para outra enquanto a música atual está tocando. O áudio continua usando a infraestrutura de música do Minecraft, incluindo o controle de volume **Music**. O mod deve permanecer inativo até o jogador usar o atalho e não deve controlar sons de jukeboxes, blocos, mobs ou canais ambientes.

O MVP entrega o mesmo comportamento nas duas plataformas, com um núcleo pequeno de seleção/controle e adapters que isolam diferenças reais entre Forge 1.20.1 e NeoForge 1.21.1.

## 2. Problema e objetivos

O jogador não tem um comando direto e simples para escolher uma música ambiente aleatória ou avançar a faixa atual. O atalho oferece esse controle sem introduzir outro sistema de áudio ou exigir interação com menus durante o jogo.

### Objetivos

- Oferecer uma tecla configurável de Play/Next.
- Oferecer uma tecla configurável para pausar e retomar a faixa atual no ponto interrompido.
- Iniciar uma faixa elegível quando nenhuma música estiver tocando.
- Parar a faixa controlada e iniciar outra quando o atalho for usado durante uma música.
- Evitar repetir a faixa anterior sempre que houver outra opção elegível.
- Respeitar o volume de música vanilla.
- Manter Forge e NeoForge funcionalmente equivalentes.
- Encontrar música ambiente declarada por mods e datapacks nos biomas do mundo atual.
- Cobrir lógica compartilhada e adapters com testes automatizados adequados.

### Fora do MVP

- Biblioteca ou fila de reprodução escolhida pelo jogador.
- Histórico maior que a faixa anterior, playlists, favoritos ou busca.
- Novos canais, mixer ou biblioteca externa de áudio.
- Controle de jukebox, sons de blocos, mobs ou sons ambientes.
- Biblioteca comum publicada separadamente.
- HUD persistente ou interface de gerenciamento de faixas; o feedback é um toast temporário.

## 3. Usuários e fluxo principal

1. O jogador configura/usa o keybind Play/Next.
2. Se nenhuma música estiver tocando, Better Radio seleciona uma faixa elegível e a inicia.
3. Se uma música estiver tocando, Better Radio interrompe a música ambiente atual, escolhe uma faixa diferente quando possível e a inicia sem aguardar a próxima troca automática vanilla.
4. A tecla Pause/Resume congela e retoma a faixa atual na posição em que parou.
5. Se habilitado, um toast acima da barra de XP aparece por dois segundos quando a faixa ou o estado muda. Ele diz `Music playing`, `Paused` ou `Muted`; o jogo não fornece um título amigável consistente para as faixas de fundo.

## 4. Requisitos funcionais

| ID | Requisito | Prioridade |
|---|---|---|
| FR-1 | Registrar um keybind Play/Next no cliente, remapeável pelas opções de controles do Minecraft. | MVP |
| FR-2 | Processar uma ativação uma única vez e respeitar o foco/estado normal de entrada do jogo. | MVP |
| FR-3 | Distinguir música ambiente em reprodução de outros sons; não usar atividade geral de áudio como indicador. | MVP |
| FR-4 | Quando não houver música ambiente ativa, selecionar e iniciar uma faixa elegível. | MVP |
| FR-5 | Quando houver música ambiente ativa, interrompê-la e iniciar uma faixa elegível imediatamente. | MVP |
| FR-6 | Evitar que a seleção repita `lastTrack` se houver pelo menos duas faixas elegíveis. | MVP |
| FR-7 | Usar o MusicManager/engine vanilla para que o volume Music continue controlando o áudio. | MVP |
| FR-8 | Não interceptar nem parar áudio de jukeboxes, blocos, mobs ou sons ambientes. | MVP |
| FR-9 | Exibir um toast acima da barra de XP por dois segundos quando a faixa ou o estado de reprodução mudar. | MVP |
| FR-10 | Exibir `Music playing`, `Paused` ou `Muted`; não exibir identificadores técnicos como títulos. | MVP |
| FR-11 | Permitir habilitar/desabilitar o toast por configuração. | MVP |
| FR-12 | Apresentar o mesmo comportamento nas versões Forge e NeoForge suportadas. | MVP |
| FR-13 | Registrar tecla remapeável de Pause/Resume que retome a faixa atual do ponto pausado sem pausar outros sons. | MVP |
| FR-14 | Incluir música de biomas registrada por mods e datapacks entre as opções elegíveis. | MVP |

## 5. Requisitos não funcionais e qualidade

- Todo controle de áudio e entrada é client-side; o mod não exige servidor modificado.
- Não adicionar dependência de áudio externa.
- Priorizar API vanilla e dependências mínimas; MidnightLib permanece candidato, sujeito à validação de necessidade e compatibilidade.
- O núcleo não deve depender de classes de loader ou de cliente específicas de uma plataforma.
- Não criar abstrações para diferenças que não existam entre as APIs concretas.
- Tratar lista vazia, uma única faixa elegível e término natural da música sem crash ou estado inconsistente.
- A seleção aleatória deve permitir injeção de fonte de aleatoriedade em testes determinísticos.

## 6. Arquitetura proposta

### Núcleo compartilhado

- **`MusicController`**: coordena o fluxo Play/Next e mantém o estado mínimo necessário.
- **`MusicSelector`**: seleciona entre faixas elegíveis e evita repetição imediata; não controla áudio nem interface.
- **`MusicHistory`**: guarda somente `lastTrack` no MVP.
- **`MusicTrack`**: representação neutra baseada somente no identificador; o título de uma faixa ambiente não tem fonte localizada consistente.
- **`MusicPlatform`**: contrato mínimo para consultar música ambiente, parar a faixa controlada, obter faixas elegíveis, iniciar faixa e apresentar feedback quando aplicável.
- **`ModConfig`**: configuração do feedback e, se necessário, opções futuras; configuração da tecla permanece no sistema vanilla de key mappings.

### Adapters

- **Forge 1.20.1**: key mapping, ciclo de eventos do cliente e integração com MusicManager da versão.
- **NeoForge 1.21.1**: equivalentes NeoForge, mantendo o contrato compartilhado.

### Fluxo

```text
Play/Next pressionado
        ↓
MusicController.next()
        ↓
consulta de música ambiente ativa
   ├── não → selecionar faixa → iniciar
   └── sim → parar música → selecionar outra → iniciar
```

O toast usa rótulos genéricos e localizados. A descoberta de faixas parte dos valores `getBackgroundMusic()` de todos os biomas do registro dinâmico do mundo, o que inclui entradas de mods e datapacks que seguem o mecanismo normal de música ambiente.

## 7. Fases de implementação do MVP

As fases são sequenciais. O resultado de cada uma é uma condição de entrada para a seguinte; qualquer diferença entre as versões deve ser registrada antes de consolidar o contrato comum.

### Fase 0 — Preparar e validar o scaffold

**Trabalho**

- Confirmar build independente de `common`, Forge e NeoForge no estado atual.
- Confirmar inicialização client-side de cada adapter e funcionamento dos runs de desenvolvimento.
- Registrar requisitos de Java por target (Forge 1.20.1 / Java 17; NeoForge 1.21.1 / Java 21).
- Identificar configuração atual de testes e pontos de entrada específicos de cada loader.

**Aceite**

- Os três módulos compilam e os dois clientes de desenvolvimento iniciam com o mod.
- Falhas preexistentes do scaffold são separadas de falhas introduzidas pelas fases seguintes.

**Entrega:** baseline reproduzível e lista curta de limitações do scaffold.

**Resultado da Fase 0 (2026-09-22)**

- `:common:check` e `:forge:build` passaram com Temurin Java 17.0.19; `:neoforge:build` passou com GraalVM Java 21.0.12.
- `:forge:runClient` carregou Better Radio e registrou `better_radio common bootstrap initialized on Forge`; o engine de áudio inicializou.
- `:neoforge:runClient` listou Better Radio 0.1.0+1.21.1 e registrou `better_radio common bootstrap initialized on NeoForge`; o engine de áudio inicializou.
- Os dois clientes chegaram ao carregamento de recursos sem erro fatal relacionado ao mod. A validação confirma bootstrap do scaffold, não comportamento de reprodução, ainda fora desta fase.
- Não há testes configurados: `common:test`, `forge:test` e `neoforge:test` reportaram `NO-SOURCE`.
- Entrypoints: `forge/src/main/java/com/radaeli/betterradio/forge/BetterRadioForge.java` e `neoforge/src/main/java/com/radaeli/betterradio/neoforge/BetterRadioNeoForge.java`. Os alvos de execução client já estão definidos nos dois builds Gradle.

**Comandos reproduzíveis (PowerShell)**

Use um cache Gradle isolado no projeto quando o cache global não permitir escrita (`.gradle-better-radio/`):

```powershell
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-better-radio'
$env:JAVA_HOME = 'C:\Users\lucca\.jdks\temurin-17.0.19'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :common:check :forge:build --no-daemon
.\gradlew.bat :forge:runClient --no-daemon

$env:JAVA_HOME = 'C:\Users\lucca\.jdks\graalvm-jdk-21.0.12+7.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :neoforge:build --no-daemon
.\gradlew.bat :neoforge:runClient --no-daemon
```

O primeiro setup dos clientes baixa os assets do Minecraft. Os builds também mostraram avisos existentes das ferramentas Gradle/Minecraft (incluindo APIs Gradle depreciadas e sons vanilla sem arquivo); não impediram compilação nem bootstrap. O Forge fixado em 47.4.20 informou haver 47.4.23 disponível durante a verificação; a versão não foi alterada nesta fase.

### Fase 1 — Spike técnico de música (Forge e NeoForge)

**Trabalho**

- Inspecionar APIs/fontes da versão para identificar música ambiente ativa, faixa/identificador atual, operação de parada e caminho para iniciar uma faixa via engine vanilla.
- Determinar como obter o conjunto de faixas elegíveis; não presumir que MusicManager fornece uma lista pública.
- Fazer um protótipo mínimo em cada target para detectar, parar, selecionar e iniciar faixa.
- Verificar aplicação do volume Music e distinguir música ambiente de sons de jukebox, blocos, mobs e ambiente.
- Documentar diferenças entre versões e determinar os dados mínimos de `MusicTrack`.

**Aceite**

- O ciclo funciona em execução real nos dois targets sem biblioteca externa de áudio.
- A música iniciada é afetada pelo volume Music.
- O controle não interrompe as categorias de som fora do escopo.
- Há um caminho tecnicamente viável para selecionar faixas e iniciar uma nova imediatamente em ambas as versões.

**Gate:** se a API vanilla não permitir enumerar/reproduzir faixas diretamente, escolher e documentar a menor integração compatível com o requisito antes de implementar o núcleo. Não ampliar o escopo silenciosamente.

**Resultado da Fase 1 (2026-09-22)**

- `MusicManager` nos dois targets expõe `startPlaying(Music)`, `stopPlaying()` e `isPlayingMusic(Music)`, mas não expõe uma coleção de faixas nem um getter para a faixa atual. O estado só pode ser consultado fornecendo uma instância `Music` candidata.
- A enumeração viável usa o registro de biomas do nível atual e `Biome.getBackgroundMusic()`, deduplicando as instâncias `Music`. Isso inclui faixas configuradas por biomas/mods registrados; não enumera músicas de menu, créditos ou chefe sem que um bioma as use.
- O identificador rastreável em `Music` é o `SoundEvent` (via `Holder`), junto com `minDelay`, `maxDelay` e `replaceCurrentMusic`. O título/localização legível não está associado a `Music`; o evento de som pode ter seus próprios metadados/localização.
- `stopPlaying()` atua sobre a instância de música mantida pelo `MusicManager`, sem parar o restante do `SoundManager`. A reprodução via `MusicManager.startPlaying(Music)` usa a categoria vanilla `MUSIC`; jukeboxes usam a fonte separada `RECORDS`, e sons de blocos/entidades/ambiente não são alvo desta parada.
- Foi adicionado um probe temporário em F8 nos dois adapters. Dentro de um mundo, ele lista/deduplica as músicas de fundo dos biomas, identifica se uma candidata está ativa, para a música gerenciada, escolhe outra candidata quando existe e solicita sua reprodução imediata. O log registra número de candidatas, faixa selecionada, estado antes/depois e volume configurado de `MUSIC`.
- `:common:check`, `:forge:build` e `:neoforge:build` passaram. `:forge:runClient` e `:neoforge:runClient` iniciaram Better Radio, inicializaram o engine de áudio e chegaram ao carregamento de recursos sem erro relacionado ao probe. No NeoForge, os handlers são registrados por subscriber limitado a `Dist.CLIENT`; o entrypoint comum não referencia as classes do probe.
- Validação em jogo confirmada pelo usuário: F8 troca/inicia música no mundo aberto para LAN e a faixa respeita o volume `Music`.
- Foi observada uma diferença que bloqueia o fechamento do spike: em mundo single-player comum, um F8 interrompe a música e um segundo F8 inicia outra; ao abrir o mundo para LAN, um único F8 troca a música sem silêncio intermediário. O código do probe solicita parada e início no mesmo pressionamento, sem ramo intencional de “parar apenas”. Comparar os logs `Music spike ... active before probe`, `found ... tracks` e `requested immediate track ... nowActive` entre os dois contextos antes de concluir se o problema é detecção, faixa selecionada ou processamento da tecla.
- A não interferência percebida em jukeboxes e demais sons ainda não foi confirmada pelo usuário. Builds e inicialização não demonstram esse comportamento.

**Validação manual do probe**

1. Iniciar o cliente de desenvolvimento do target, entrar em um mundo e pressionar F8. Comparar os logs em single-player comum e com LAN aberta; verificar candidatas, detecção da faixa ativa, faixa solicitada e `nowActive`.
2. Confirmar que um único pressionamento inicia ou troca a faixa nos dois contextos; repetir F8 e verificar que, com alternativas disponíveis, a faixa ativa não é escolhida de novo.
3. Repetir com o volume `Music` em zero e em valor audível. Confirmar que zero silencia a faixa e que restaurar o volume permite ouvi-la.
4. Acionar jukebox e sons de bloco, mob e ambiente antes/depois de F8; confirmar que permanecem intactos.

O probe foi removido na Fase 3 e substituído pelo keybind Play/Next; F8 permanece como padrão inicial remapeável. O reteste em jogo da Fase 3 também deve verificar se a diferença SP/LAN observada no spike foi resolvida.

### Fase 2 — Contratos do core e seleção determinística

**Trabalho**

- Definir `MusicTrack`, `MusicPlatform`, `MusicSelector`, `MusicHistory` e `MusicController` com base nos achados do spike.
- Manter no core somente regras que sejam realmente iguais nos dois loaders.
- Implementar seleção aleatória e exclusão de `lastTrack` quando houver alternativa.
- Definir comportamento sem faixas elegíveis e com somente uma faixa.
- Permitir aleatoriedade injetável para testes reproduzíveis.

**Aceite**

- Core não importa classes Forge/NeoForge/Minecraft client.
- Seleção nunca acessa áudio, HUD ou estado global de cliente.
- Testes unitários cobrem lista vazia, faixa única, não repetição com múltiplas opções e atualização do histórico.

**Resultado da Fase 2 (2026-09-22)**

- Contratos loader-neutral implementados em `common`: `MusicTrack`, `MusicPlatform`, `MusicSelector`, `MusicHistory` e `MusicController`.
- `MusicController` seleciona e registra a faixa seguinte sem executar ações de áudio; a integração dos métodos de `MusicPlatform` fica para a Fase 3.
- A seleção usa `RandomGenerator` injetável, retorna vazio quando não há candidatas, permite repetir quando há somente uma faixa e exclui o último ID quando existem alternativas.
- Testes JUnit adicionados para lista vazia, faixa única, não repetição, histórico e seleção reproduzível.
- A discrepância de reprodução entre singleplayer e LAN permanece pendente da Fase 1 e deve ser considerada na implementação do adapter na Fase 3.

### Fase 3 — Integração de keybind e fluxo Play/Next

**Trabalho**

- Registrar o keybind Play/Next nos dois adapters usando os eventos próprios de cada loader.
- Acionar o controller apenas no cliente e uma vez por pressionamento.
- Ligar consulta de estado, parada e início aos métodos descobertos no spike.
- Tratar troca de mundo, desconexão e ausência de player/nível para não manter referências inválidas.

**Aceite**

- A tecla aparece nas opções de controles e pode ser remapeada.
- Um pressionamento inicia ou avança a música conforme o fluxo principal.
- Segurar a tecla não dispara trocas contínuas, salvo comportamento normal explicitamente validado do key mapping.
- Fluxo equivalente em Forge 1.20.1 e NeoForge 1.21.1.

**Resultado da Fase 3 (2026-09-22)**

- O probe foi substituído pelo keybind remapeável `Play/Next Music`, com F8 como padrão, registrado nos dois loaders e consumido uma vez por evento de tick do cliente.
- Os adapters implementam `MusicPlatform` usando as músicas de fundo distintas encontradas no registro de biomas. O controller consulta a faixa ativa, exclui essa faixa da próxima seleção, para a faixa gerenciada quando identificada e inicia a alternativa no mesmo pressionamento.
- O estado do adapter não retém referências a player, nível ou cliente entre ticks; fora de um mundo, o pressionamento é ignorado.
- Testes cobrem o fluxo do controller com adapter falso: uma chamada para uma faixa ativa resulta em stop e start de uma alternativa; lista vazia não chama áudio.
- O usuário reportou que a validação mais recente ainda reproduz o sintoma de tocar somente a cada dois pressionamentos. A correção foi incluída nesta integração, mas precisa de novo reteste ingame para confirmar; builds não validam o comportamento real do `MusicManager` em singleplayer/LAN.
- No diagnóstico posterior, `:forge:runClient` e `:neoforge:runClient` confirmaram no log o registro do keybind e a execução do listener de tick. O NeoForge agora registra explicitamente o listener no event bus de cliente. A recepção de um pressionamento real de F8 e a reprodução dentro de um mundo ainda aguardam confirmação ingame.
- Reteste ingame confirmado pelo usuário: F8 funciona corretamente.

### Fase 4 — Configuração e indicador de estado

**Trabalho**

- Implementar toast textual compacto ancorado acima da barra de XP, visível por dois segundos.
- Exibir `Music playing`, `Paused` ou `Muted`, sem título técnico de faixa.
- Mostrar o toast quando a faixa, o estado de pausa ou o estado de mute mudar; não renovar sua duração a cada frame.
- Implementar `showNowPlaying` com padrão habilitado e opção para ocultar o indicador.
- Usar rótulos localizados sem inferir título a partir do identificador do som.
- Avaliar MidnightLib contra configuração nativa do loader, considerando dependência, UX e paridade entre versões.

**Aceite**

- O jogador consegue desabilitar o indicador sem desativar o keybind.
- O indicador fica acima da barra de XP, apresenta os três estados definidos e acompanha mudanças sem ficar obsoleto.
- O indicador não altera o áudio.
- Mesmo comportamento e nomes equivalentes nos dois targets.
- A decisão de biblioteca/configuração fica registrada e a distribuição informa dependências obrigatórias, se existirem.

**Resultado da implementação (2026-09-23)**

- Forge e NeoForge mostram o toast acima da barra de XP por dois segundos ao detectar mudança de faixa, pausa/retomada ou mute.
- Foi confirmado pela inspeção da API que `Music` referencia o evento de som e seus atrasos, sem título de faixa localizado. O texto foi simplificado para `Music playing`, `Paused` e `Muted`.
- A opção `showNowPlaying` continua ligada por padrão em configuração client nativa dos dois loaders; sem dependência externa.
- Builds e bootstrap dos clientes passaram nas fases anteriores; inspeção visual em jogo segue pendente.

### Fase 5 — Testes automatizados, robustez e compatibilidade

**Trabalho**

- Completar testes automatizados do core e adicionar testes de adapter onde a infraestrutura permitir (mapeamento de estado, seleção e chamadas esperadas à plataforma).
- Implementar tecla Pause/Resume, preservando o ponto atual da faixa e sem pausar outros sons.
- Incluir música ambiente registrada em biomas por mods e datapacks e renovar o cache após reload/alterações dos registros.
- Cobrir a máquina de estados do toast: expiração em dois segundos, sem renovação por render, reexibição em mudanças relevantes.
- Exercitar lista vazia, somente uma faixa, término natural, troca repetida, transição de mundo e ausência temporária de contexto do cliente.
- Fazer revisão de carregamento dedicado para garantir que classes client-only não sejam referenciadas por bootstrap comum; o recurso é client-side, mas os metadados e carregamento precisam continuar válidos.
- Confirmar que a interação com sons não musicais permanece intacta em verificações controladas.

**Aceite**

- Testes automatizados do core passam de forma determinística.
- Pausar e retomar afeta somente o canal de música mantido pelo `MusicManager`.
- Toast expira no prazo e só reaparece quando a faixa ou um estado de reprodução muda.
- Faixas de namespaces externos permanecem elegíveis pelo seletor.
- Build e verificação dos dois adapters passam.
- Nenhum caminho de erro deixa a música ambiente parada sem tentativa válida de iniciar outra ou causa erro em log.
- Verificação client-side confirma volume Music e ausência de interferência nas fontes fora do escopo.

**Resultado da Fase 5 (2026-09-23)**

- F8 segue como Play/Next; F9 é o padrão remapeável de Pause/Resume. A pausa acessa o canal da instância atual do `MusicManager` para conservar sua posição, sem chamar pausa geral do motor de áudio.
- Toast de dois segundos implementado com estado puro testado no módulo `common`. Rótulos são `Music playing`, `Paused` e `Muted`; nenhuma tradução tenta apresentar o ID técnico como título.
- Os adapters coletam músicas de fundo de todos os biomas do registro dinâmico do nível e atualizam cache a cada cinco segundos, cobrindo músicas adicionadas por mods/datapacks através da música natural de bioma.
- 16 testes JUnit do `common` passaram: seleção/controller (10), ponte de pausa (2), toast (4). A ponte também verifica que um canal de som alheio não é pausado.
- `:common:check :forge:build` passou com Java 17; `:neoforge:build` passou com Java 21. JARs incluem `META-INF/mods.toml` e `META-INF/neoforge.mods.toml`.
- A revisão do entrypoint confirmou que ele referencia somente a configuração do loader; as classes client-only não são carregadas pelo bootstrap comum. A execução do servidor de desenvolvimento Forge parou na confirmação de EULA antes do bootstrap do mundo, por isso não foi tratada como teste de servidor bem-sucedido.
- Reprodução e retomada real, posição do toast e compatibilidade sonora com mods/datapacks dependem da validação final em jogo.

### Fase 6 — Polimento, documentação e entrega do MVP

**Trabalho**

- Revisar os nomes das teclas, mensagens e opções nos idiomas incluídos.
- Documentar instalação, versões/loaders suportados, uso do atalho, opção de feedback e limitações conhecidas em linguagem voltada ao jogador.
- Validar metadados e dependências dos artefatos Forge e NeoForge.
- Executar uma matriz final de verificação nos dois targets e preparar os artefatos de distribuição conforme o fluxo do projeto.

**Aceite**

- Forge 1.20.1 e NeoForge 1.21.1 produzem artefatos identificáveis e carregáveis.
- Instruções de uso permitem configurar Play/Next e Pause/Resume sem consultar documentação técnica.
- A versão entregue corresponde ao escopo e aos critérios de aceite deste documento.

**Resultado da Fase 6 (2026-09-23)**

- README em inglês documenta loaders/versões, teclas padrão/remapeáveis, volume Music, toast e suporte a música natural de mods/datapacks.
- JARs finais nomeados por loader e versão: `better_radio-forge-0.1.0+1.20.1.jar` e `better_radio-neoforge-0.1.0+1.21.1.jar`. Metadados foram inspecionados e contêm os IDs, versões e dependências esperados.
- `:common:check`, `:forge:build` e `:neoforge:build` passaram. Os clientes de desenvolvimento dos dois loaders inicializaram o mod, registraram o keybind e ativaram o handler de tick.
- A execução do servidor de desenvolvimento Forge foi bloqueada pela tela de EULA antes do bootstrap do mundo; nenhum aceite de EULA foi gravado. Os entrypoints foram inspecionados e não referenciam classes client-only.
- O usuário fará a confirmação de reprodução, pausa/retomada, posição do toast e sons não relacionados em jogo nos dois alvos.

## 8. Plano de testes e validação

| Camada | Verificações principais | Critério |
|---|---|---|
| Unitária (core) | Seleção sem repetição, lista vazia/única, histórico e fluxo do controller com plataforma simulada. | Reproduzível, sem Minecraft ou áudio real. |
| Integração (cada loader) | Registro de teclas, ponte para APIs de música e configuração. | Compila e exercita os pontos de integração específicos. |
| Cliente em jogo (cada versão) | Iniciar/avançar/pausar/retomar, mudança de contexto, volume Music, feedback e sons fora do escopo. | Comportamento observado nos dois targets. |
| Carregamento | Metadados, dependências, inicialização dos dois clientes e isolamento client-side no entrypoint comum. | JARs identificados e clientes iniciam sem erro relacionado ao mod. |

Builds e testes automatizados dão evidência estrutural; comportamento de áudio, volume e não interferência requer validação em cliente em execução para cada versão.

## 9. Riscos e decisões em aberto

1. **Pausa individual:** o MusicManager não oferece pausa pública para sua instância atual; a ponte usa os campos e o canal interno das versões alvo, portanto exige validação final em jogo por loader.
2. **Descoberta de música:** são elegíveis as músicas naturais referenciadas por biomas no registro do mundo. Música iniciada por outros sistemas que não usem as definições de bioma fica fora do fluxo natural e não é enumerada.
3. **Toast:** a posição acima da XP e as mudanças de estado precisam de confirmação visual em jogo nos dois targets.
4. **Configuração:** configuração nativa client de cada loader foi escolhida; MidnightLib não é dependência.
5. **Lista com uma faixa:** a não repetição é impossível; repetir a única opção elegível é o fallback esperado.

## 10. Definição de pronto

O MVP estará pronto quando todas as fases (0 a 6) estiverem concluídas; os critérios funcionais e automatizados forem atendidos; e a validação em cliente confirmar Play/Next, pausa/retomada, volume Music, toast configurável por dois segundos e ausência de interferência em sons fora do escopo em Forge 1.20.1 e NeoForge 1.21.1.
