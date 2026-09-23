# Better Radio — PRD do MVP

**Status:** Implementação até a Fase 4 concluída; indicador precisa de validação visual em jogo nos dois targets
**Base:** `minecraft-music-hotkey-architecture.md`
**Plataformas alvo:** Minecraft 1.20.1 (Forge) e Minecraft 1.21.1 (NeoForge)

## 1. Resumo

Better Radio adiciona um atalho do lado do cliente para iniciar uma música ambiente aleatória ou avançar para outra enquanto a música atual está tocando. O áudio continua usando a infraestrutura de música do Minecraft, incluindo o controle de volume **Music**. O mod deve permanecer inativo até o jogador usar o atalho e não deve controlar sons de jukeboxes, blocos, mobs ou canais ambientes.

O MVP entrega o mesmo comportamento nas duas plataformas, com um núcleo pequeno de seleção/controle e adapters que isolam diferenças reais entre Forge 1.20.1 e NeoForge 1.21.1.

## 2. Problema e objetivos

O jogador não tem um comando direto e simples para escolher uma música ambiente aleatória ou avançar a faixa atual. O atalho oferece esse controle sem introduzir outro sistema de áudio ou exigir interação com menus durante o jogo.

### Objetivos

- Oferecer uma tecla configurável de Play/Next.
- Iniciar uma faixa elegível quando nenhuma música estiver tocando.
- Parar a faixa controlada e iniciar outra quando o atalho for usado durante uma música.
- Evitar repetir a faixa anterior sempre que houver outra opção elegível.
- Respeitar o volume de música vanilla.
- Manter Forge e NeoForge funcionalmente equivalentes.
- Cobrir lógica compartilhada e adapters com testes automatizados adequados.

### Fora do MVP

- Biblioteca ou fila de reprodução escolhida pelo jogador.
- Histórico maior que a faixa anterior, playlists, favoritos ou busca.
- Novos canais, mixer ou biblioteca externa de áudio.
- Controle de jukebox, sons de blocos, mobs ou sons ambientes.
- Biblioteca comum publicada separadamente.
- HUD persistente ou interface de gerenciamento de faixas.

## 3. Usuários e fluxo principal

1. O jogador configura/usa o keybind Play/Next.
2. Se nenhuma música estiver tocando, Better Radio seleciona uma faixa elegível e a inicia.
3. Se uma música estiver tocando, Better Radio interrompe a música ambiente atual, escolhe uma faixa diferente quando possível e a inicia sem aguardar a próxima troca automática vanilla.
4. Se habilitado, o mod atualiza um indicador acima da barra de XP com o estado atual: `Now playing: <faixa>`, `Paused` ou `Muted`.

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
| FR-9 | Exibir um indicador compacto acima da barra de XP com o estado atual da música e, quando aplicável, o título da faixa. | MVP |
| FR-10 | Mostrar `Now playing: <faixa>` durante reprodução, `Paused` quando a reprodução estiver pausada e `Muted` quando o volume Music estiver em zero. | MVP |
| FR-11 | Permitir habilitar/desabilitar o indicador por configuração. | MVP |
| FR-12 | Apresentar o mesmo comportamento nas versões Forge e NeoForge suportadas. | MVP |

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
- **`MusicTrack`**: representação neutra de faixa. Seu identificador e os dados necessários ao feedback serão fechados no spike.
- **`MusicPlatform`**: contrato mínimo para consultar música ambiente, parar a faixa controlada, obter faixas elegíveis, iniciar faixa e apresentar feedback quando aplicável.
- **`ModConfig`**: configuração do feedback e, se necessário, opções futuras; configuração da tecla permanece no sistema vanilla de key mappings.

### Adapters

- **Forge 1.20.1**: key mapping, ciclo de eventos do cliente e integração com MusicManager da versão.
- **NeoForge 1.21.1**: equivalentes NeoForge, mantendo o contrato compartilhado.

### Fluxo

```text
keybind pressionado
        ↓
MusicController.next()
        ↓
consulta de música ambiente ativa
   ├── não → selecionar faixa → iniciar
   └── sim → parar música → selecionar outra → iniciar
```

O nome apresentado no feedback deve vir de uma fonte estável e localizada quando disponível; detalhes técnicos da fonte de faixa serão definidos pelo spike, evitando assumir que a API vanilla oferece uma lista enumerável diretamente.

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

**Resultado da implementação (2026-09-23)**

- Adicionado indicador renderizado acima da barra de experiência nos dois loaders; consulta a faixa de música ambiente reconhecida pelo `MusicManager`.
- Estados localizados: `Now playing: <faixa>`, `Paused` e `Muted`. Faixas sem nome localizado usam o fallback legível derivado do identificador do evento de som.
- Adicionada opção client `showNowPlaying`, habilitada por padrão, nos arquivos de configuração nativos de Forge e NeoForge. Não foi adicionada dependência de configuração externa.
- `:common:check`, `:forge:build` e `:neoforge:build` passaram. A inspeção visual e as transições de estado durante jogo permanecem pendentes nos dois targets.

### Fase 4 — Configuração e indicador de estado

**Trabalho**

- Implementar um indicador textual compacto ancorado acima da barra de XP, sem toast temporário que desapareça enquanto o estado ainda estiver ativo.
- Exibir `Now playing: <faixa>` durante reprodução; `Paused` quando a faixa estiver pausada; e `Muted` quando o volume Music estiver em zero.
- Atualizar o indicador quando reprodução, pausa ou volume Music mudar; não deixar texto desatualizado após a mudança de estado.
- Implementar `showNowPlaying` com padrão habilitado e opção para ocultar o indicador.
- Mostrar título legível quando disponível; definir fallback quando houver apenas identificador técnico.
- Avaliar MidnightLib contra configuração nativa do loader, considerando dependência, UX e paridade entre versões.

**Aceite**

- O jogador consegue desabilitar o indicador sem desativar o keybind.
- O indicador fica acima da barra de XP, apresenta os três estados definidos e acompanha mudanças sem ficar obsoleto.
- O indicador não altera o áudio.
- Mesmo comportamento e nomes equivalentes nos dois targets.
- A decisão de biblioteca/configuração fica registrada e a distribuição informa dependências obrigatórias, se existirem.

### Fase 5 — Testes automatizados, robustez e compatibilidade

**Trabalho**

- Completar testes automatizados do core e adicionar testes de adapter onde a infraestrutura permitir (mapeamento de estado, seleção e chamadas esperadas à plataforma).
- Exercitar lista vazia, somente uma faixa, término natural, troca repetida, transição de mundo e ausência temporária de contexto do cliente.
- Fazer revisão de carregamento dedicado para garantir que classes client-only não sejam referenciadas por bootstrap comum; o recurso é client-side, mas os metadados e carregamento precisam continuar válidos.
- Confirmar que a interação com sons não musicais permanece intacta em verificações controladas.

**Aceite**

- Testes automatizados do core passam de forma determinística.
- Build e verificação dos dois adapters passam.
- Nenhum caminho de erro deixa a música ambiente parada sem tentativa válida de iniciar outra ou causa erro em log.
- Verificação client-side confirma volume Music e ausência de interferência nas fontes fora do escopo.

### Fase 6 — Polimento, documentação e entrega do MVP

**Trabalho**

- Revisar nomes, keybind padrão, mensagens e opções nos idiomas incluídos.
- Documentar instalação, versões/loaders suportados, uso do atalho, opção de feedback e limitações conhecidas em linguagem voltada ao jogador.
- Validar metadados e dependências dos artefatos Forge e NeoForge.
- Executar uma matriz final de verificação nos dois targets e preparar os artefatos de distribuição conforme o fluxo do projeto.

**Aceite**

- Forge 1.20.1 e NeoForge 1.21.1 produzem artefatos identificáveis e carregáveis.
- Instruções de uso permitem configurar e acionar Play/Next sem consultar documentação técnica.
- A versão entregue corresponde ao escopo e aos critérios de aceite deste documento.

## 8. Plano de testes e validação

| Camada | Verificações principais | Critério |
|---|---|---|
| Unitária (core) | Seleção sem repetição, lista vazia/única, histórico e fluxo do controller com plataforma simulada. | Reproduzível, sem Minecraft ou áudio real. |
| Integração (cada loader) | Registro do keybind, ponte para APIs de música, tradução de faixa e configuração. | Compila e exercita os pontos de integração específicos. |
| Cliente em jogo (cada versão) | Iniciar/avançar, término natural, mudança de contexto, volume Music, feedback e sons fora do escopo. | Comportamento observado nos dois targets. |
| Carregamento | Metadados, dependências e isolamento client-side. | Ambos carregam sem erros relacionados ao mod. |

Builds e testes automatizados dão evidência estrutural; comportamento de áudio, volume e não interferência requer validação em cliente em execução para cada versão.

## 9. Riscos e decisões em aberto

1. **Descoberta e início de faixas:** confirmar APIs concretas por versão no spike. O MusicManager pode não expor uma coleção pública adequada.
2. **Parada de música ambiente:** validar que o mecanismo escolhido não afete fontes de som que compartilhem implementação/categoria.
3. **Indicador e estados:** validar APIs para posicionar o texto acima da barra de XP nos dois loaders e observar pausa e volume Music sem polling excessivo ou interferência no áudio.
4. **Título da faixa:** definir fallback quando o título não estiver disponível.
5. **Configuração:** decidir entre MidnightLib e configuração nativa; evitar obrigar dependência sem necessidade comprovada.
6. **Lista com uma faixa:** a não repetição é impossível; repetir a única opção elegível é o fallback esperado.
7. **Escopo de faixas elegíveis:** definir se faixas elegíveis são somente as músicas ambiente vanilla ou também faixas registradas por outros mods, conforme o acesso descoberto no spike.

## 10. Definição de pronto

O MVP está pronto quando todas as fases (0 a 6) forem concluídas; os critérios funcionais e automatizados forem atendidos; e a validação em cliente confirmar o keybind, início/avanço imediato, volume Music, indicador configurável acima da barra de XP com os estados Now playing/Paused/Muted e ausência de interferência em sons fora do escopo em Forge 1.20.1 e NeoForge 1.21.1.
