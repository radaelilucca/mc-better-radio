# Better Radio — PRD do MVP

**Status:** Implementação das fases 0–8 concluída nos dois loaders. Testes comuns, builds/JARs Forge e NeoForge e smoke de inicialização dos clientes passaram. A validação final de áudio e HUD em jogo permanece para o usuário.
**Plataformas:** Minecraft 1.20.1 (Forge) e Minecraft 1.21.1 (NeoForge).
**Arquitetura:** [minecraft-music-hotkey-architecture.md](minecraft-music-hotkey-architecture.md)

## 1. Resumo

O MVP transforma Better Radio em um player local de música de gameplay. O mod descobre os arquivos de áudio concretos referenciados pelas definições de background de biomas e outras situações naturais de gameplay de vanilla/mods/datapacks, além das músicas de todos os discos registrados. Ele mantém uma fila de sessão e reproduz o arquivo escolhido diretamente, para que Previous, Next e Pause/Resume controlem a faixa que o jogador realmente ouve.

O player usa o canal vanilla `MUSIC` e respeita o controle de volume correspondente. A música de menu, créditos e demais contextos não relacionados a gameplay permanece vanilla. Better Radio não controla jukeboxes físicos: seus sons espaciais continuam no canal `RECORDS` e sobrepõem o background em `MUSIC` como no jogo vanilla.

## 2. Escopo e decisões aprovadas

- **Somente gameplay:** assumir controle do background musical durante gameplay. Menu, créditos e outros contextos não-gameplay ficam fora do escopo.
- **Pool por fontes declaradas:** incluir arquivos alcançáveis pelas definições de `Music` de background de biomas e outras situações naturais de gameplay, além de todas as definições registradas de músicas de disco. Não varrer todos os arquivos `.ogg` do jogo.
- **Identidade por arquivo:** entradas são caminhos concretos de áudio, não IDs de `SoundEvent`. Uma entrada pode manter várias origens (biomas, situações e discos) sem duplicar sua identidade de playback.
- **Discos locais:** os arquivos de disco podem ser tocados pelo player local. Não substituir, interceptar, parar nem mudar comportamento físico de jukebox/redstone/mundo.
- **Sobreposição vanilla:** o background próprio toca em `MUSIC`; jukebox continua tocando em `RECORDS` com posicionamento espacial. Não pausar o background quando começa uma jukebox.
- **Randomização contextual:** em novos sorteios, é permitido dar preferência suave aos arquivos associados ao bioma/situação atual. A preferência não elimina arquivos de outras origens do pool e deve cair no pool geral quando não houver correspondência.
- **Fila da sessão:** Previous volta ao arquivo anterior; Next avança pelo futuro já visitado antes de criar uma nova entrada. Um novo sorteio depois de Previous substitui o ramo futuro abandonado. A fila reinicia em troca/saída de mundo e não persiste entre sessões.
- **Pausa precisa:** Pause/Resume suspende e retoma o arquivo atual na posição em que parou, sem pausar outros sons.
- **Feedback:** manter o toast curto acima da barra de XP; exibir `Previous`, `Next`, `Paused`, `Playing` ou `Muted`, sem caminho/ID técnico como título. Desaparece com fade após dois segundos e reaparece apenas em nova ação ou mudança de estado.
- **Diagnóstico:** logs devem mostrar ação, arquivo solicitado e efetivamente iniciado, origem/afinidade, posição/tamanho da fila e resultado da operação, permitindo comparar estado com o que foi ouvido.
- **Equivalência:** oferecer o mesmo comportamento em Forge 1.20.1 e NeoForge 1.21.1, isolando diferenças nos adapters.

## 3. Objetivos e fora de escopo

### Objetivos

1. Construir o catálogo apenas a partir das fontes musicais aprovadas, incluindo música de biomas e outras situações naturais de gameplay de mods/datapacks.
2. Resolver eventos de som em seus arquivos concretos, incluindo alternativas ponderadas e referências aninhadas válidas.
3. Tocar um arquivo exato com a engine vanilla e categoria `MUSIC`.
4. Gerenciar Play/Next, Previous e Pause/Resume com identidade exata e fila testável.
5. Manter jukebox físico independente e preservar o overlay `RECORDS` sobre `MUSIC`.
6. Oferecer feedback e logs adequados ao comportamento real do player.

### Fora do MVP

- Controle ou substituição de jukeboxes físicos, redstone e seus sons no mundo.
- Música de menu, créditos e outros fluxos não-gameplay.
- Busca, favoritos, playlists criadas pelo usuário, GUI de biblioteca ou mixer.
- Inclusão indiscriminada de recursos de áudio que não sejam referenciados pelas fontes elegíveis.
- Biblioteca de áudio externa ou novo canal de volume.
- Garantia de um título amigável para cada arquivo de background; usar rótulos de ação/estado.

## 4. Requisitos funcionais

| ID | Requisito | Prioridade |
|---|---|---|
| FR-1 | Descobrir definições naturais de background `Music` aplicáveis ao gameplay, incluindo contribuições carregadas de mods/datapacks. | MVP |
| FR-2 | Descobrir todas as músicas de disco registradas em cada loader e incluí-las como fontes locais. | MVP |
| FR-3 | Resolver cada fonte em arquivos concretos, cobrindo alternativas ponderadas e referências válidas, e deduplicar por resource location. | MVP |
| FR-4 | Preservar as várias origens associadas a um arquivo, inclusive bioma/situação e disco. | MVP |
| FR-5 | Tocar o arquivo selecionado diretamente, sem nova randomização pelo grupo `SoundEvent`. | MVP |
| FR-6 | Usar `MUSIC` e respeitar volume/mute configurados pelo jogador. | MVP |
| FR-7 | Controlar o background gameplay vanilla para evitar reprodução concorrente enquanto o player Better Radio está ativo; deixar menu/não-gameplay intacto. | MVP |
| FR-8 | Deixar sons de jukebox física intocados em `RECORDS`, sobrepostos ao background `MUSIC` como vanilla. | MVP |
| FR-9 | Oferecer keybinds remapeáveis Play/Next, Previous e Pause/Resume. | MVP |
| FR-10 | Manter fila de sessão por arquivo concreto; Previous, avanço no futuro, novo sorteio e ramificação obedecem às decisões acima. | MVP |
| FR-11 | Pausar e retomar o canal/faixa próprios do mod a partir do cursor de playback, sem pausar a engine inteira. | MVP |
| FR-12 | Em novos sorteios, permitir bias suave por afiliação com bioma/situação sem remover o fallback global. | MVP |
| FR-13 | Recriar/atualizar catálogo após reload de recursos e tratar referências ausentes sem crash. | MVP |
| FR-14 | Exibir toast localizado de ação/estado por dois segundos, com fade, apenas em eventos relevantes. | MVP |
| FR-15 | Registrar dados suficientes para distinguir arquivo pedido, arquivo resolvido, fila e resultado de áudio. | MVP |
| FR-16 | Manter paridade comportamental nos dois loaders suportados. | MVP |

## 5. Modelo de dados e comportamento

### Entrada de catálogo

Uma entrada é identificada pelo resource location canônico do arquivo de áudio. Ela não é o `SoundEvent` nem o nome do bioma. Metadados de origem mantêm as relações com as fontes registradas que levam ao arquivo. Se duas definições apontam ao mesmo `.ogg`, há uma entrada de playback com ambas as origens.

O catálogo parte de fontes elegíveis; ele não percorre o filesystem/asset namespace em busca de todos os sons. Eventos sem arquivos válidos são ignorados com diagnóstico. Atualizações de resource/datapack devem invalidar objetos derivados antigos e reconstruir o conjunto.

### Operações

- **Play/Next:** sem faixa Better Radio atual, iniciar uma elegível; com faixa ativa, navegar à próxima entrada futura da fila ou gerar e registrar uma nova escolha.
- **Previous:** mover o cursor para a entrada anterior e iniciar exatamente esse arquivo. No início da fila, não iniciar uma faixa aleatória.
- **Novo sorteio após Previous:** descartar o futuro e iniciar um novo ramo.
- **Pause/Resume:** preservar entrada e cursor; pausar/retomar apenas a instância própria.
- **Fim natural:** registrar estado parado sem iniciar uma faixa espontaneamente; a próxima ação explícita do jogador retoma o fluxo da fila conforme o contrato definido nos testes.
- **Pool vazia:** não chamar o motor de áudio; registrar o motivo e manter estado seguro.
- **Uma entrada:** repetição é válida, pois não há alternativa.
- **Mudança de mundo/desconexão:** parar o som próprio se necessário e limpar fila, cursor e referências ao contexto anterior.
- **Reload de recursos:** reconstruir catálogo. A faixa já tocando pode terminar usando a instância atual; futuras seleções usam apenas entradas atuais. Se o arquivo ativo deixou de existir, parar com segurança e limpar a entrada inválida.

## 6. Arquitetura

- **Common:** tipos loader-neutral para arquivo, origem, catálogo/snapshot, fila, política de seleção e estado do toast; interfaces pequenas para operações de plataforma.
- **Forge 1.20.1:** coleta background Music de biomas/fontes gameplay e músicas de `RecordItem`; resolução de sound definitions e resource reload conforme API 1.20.1; fixed-file playback e pausa de uma instância em `MUSIC`.
- **NeoForge 1.21.1:** coleta background Music e registry de `JukeboxSong`/itens tocáveis; resolução e reload com APIs 1.21.1; fixed-file playback e pausa de uma instância em `MUSIC`.
- **Owned gameplay playback:** integração deve impedir que o seletor vanilla de background comece sons concorrentes durante gameplay sem capturar telas/menu ou outros sons.
- **Jukebox vanilla:** manter caminho de reprodução, posicionamento e categoria originais, sem patch de controle da jukebox.

Não assumir equivalência de API entre versões sem verificar. Qualquer hook usado para capturar a seleção vanilla, enumerar referências de som, tocar um caminho fixo ou pausar o canal precisa de teste nos dois alvos.

## 7. Fases de implementação

As fases abaixo descrevem o pivot inteiro. A existência anterior de keybind, toast, pause bridge ou histórico por `SoundEvent` não significa que estas fases do novo design estejam concluídas. Cada fase termina com testes apropriados e commit isolado; comportamento audível/visual em jogo continua exigindo validação manual.

### Fase 0 — Baseline e contrato do pivot

**Trabalho:** registrar estado atual de branch/build/testes; mapear código antigo que continuará útil (keybinds, toast, histórico) e as partes baseadas em `MusicManager`/`SoundEvent` que serão substituídas; fechar interfaces de `AudioFile`, `MusicSource`, catálogo, fila e player.

**Testes/validação:** checks existentes; testes de contrato puro para igualdade/hash por caminho de áudio concreto e associação de múltiplas origens. Estado: `:common:test`, `:forge:compileJava` e `:neoforge:compileJava` passaram; verificação de refmap e runtime continua nas fases seguintes.

**Aceite:** baseline reproduzível nos dois alvos, arquitetura usa identidade por arquivo e escopo/semântica da fila estão documentados. Nenhum resultado de fase de pivot é declarado como implementado antes de seu código existir.

### Fase 1 — Inventário de fontes por loader

**Estado:** Implementado em ambos adapters: fontes de bioma, situações naturais de gameplay, situações modded observadas e músicas de discos registrados; compilação e testes do contrato comum passaram. Inspeção com conteúdo carregado no cliente fica para a validação final.

**Trabalho:** enumerar as fontes de background de biomas e outras situações naturais de gameplay, além dos discos registrados em Forge 1.20.1 e NeoForge 1.21.1; incluir fontes dinâmicas de mods/datapacks; identificar lifecycle de reload e como limitar fontes a gameplay. Confirmar quais registros de disco são completos em cada versão.

**Testes/validação:** fixtures ou testes de adapter verificam fontes vanilla conhecidas, fontes externas/registro dinâmico e discos. Revisão dos registros e logs num cliente de desenvolvimento.

**Aceite:** ambos adapters fornecem snapshot de fontes com IDs/afiliações sem tocar na reprodução; menus e sons alheios não entram.

### Fase 2 — Flatten de sound definitions para arquivos concretos

**Estado:** Implementado em ambos loaders: alternativas, referências de evento aninhadas, deduplicação, ciclos e definições ausentes são resolvidos em arquivos concretos carregados. `:common:test`, `:forge:compileJava` e `:neoforge:compileJava` passaram; o smoke test NeoForge confirmou a aplicação do accessor no ambiente dev.

**Trabalho:** resolver SoundEvent definitions em arquivos tocáveis, incluindo weighted alternatives, event references aninhadas e deduplicação; tratar ciclos, entradas inválidas, som ausente e streaming; preservar afiliações de fonte. Validar comportamento com packs de recursos/datapacks.

**Testes/validação:** testes com eventos de um arquivo, grupos ponderados, grupo compartilhado, referência aninhada, ciclo, caminho faltante e nomespaced modded; confirmar em cada loader que o resolvedor retorna o mesmo conjunto de candidatos declarado nos recursos carregados.

**Aceite:** catálogo só contém arquivos alcançáveis das fontes aprovadas; uma fonte pode apontar a vários arquivos e um arquivo a várias fontes; reload substitui snapshots sem referências obsoletas.

### Fase 3 — Spike de fixed-file playback e controle vanilla

**Estado:** Implementado nos dois loaders: instância própria `MUSIC` resolve para o arquivo concreto, pausa/retomada atua no canal dessa instância, e o background vanilla é suprimido apenas durante gameplay. Telas que declaram música própria suspendem a faixa Better Radio; sons `RECORDS` de jukebox não são interceptados. `:forge:jar`, `:forge:runClient`, `:neoforge:compileJava` e `:neoforge:runClient` passaram; os dois clientes inicializaram e aplicaram seus hooks no ambiente dev. Ainda falta validação manual de áudio/telas/jukebox.

**Trabalho:** provar reprodução de um resource location exato como sound instance `MUSIC`, volume Music, pausa/retomada do cursor, observação do path efetivamente resolvido, e forma de impedir a música vanilla gameplay concorrente sem afetar menu. Verificar que a jukebox vanilla continua no canal `RECORDS`.

**Testes/validação:** adapter tests para categoria, path, volume/estado e stop/pause da instância própria; clientes de desenvolvimento nos dois loaders. Validação manual: A/B de arquivos conhecidos, slider Music, tela de menu, gameplay, pause/resume, jukebox tocando, sons de bloco e entidades.

**Aceite:** existe caminho estável nos dois targets para tocar, inspecionar e pausar um arquivo exato; playback não é re-randomizado por SoundEvent; controle de gameplay não vaza para menu ou áudio de jukebox/outros sons.

### Fase 4 — Catálogo comum e fila por arquivo

**Estado:** O catálogo deduplica pelo caminho concreto e mantém origens; a fila mantém ordem/cursor, Previous/Next atravessam vários arquivos exatos, e arquivos distintos de um mesmo SoundEvent continuam separados. Testes comuns cobrem navegação repetida e identidade por arquivo.

**Trabalho:** implementar tipos comuns para arquivo/origens e fila da sessão; cursor Previous/Next, truncamento do ramo futuro, limpeza em troca de mundo e sem persistência. Conectar snapshots dos adapters sem trazer classes Minecraft para o core.

**Testes/validação:** unit tests para deduplicação e merge de origens, ordem/cursor, início/fim, branching, pool vazia/única, reset, mudança do catálogo e identidade distinta para arquivos de um mesmo SoundEvent.

**Aceite:** fila determinística e inspecionável trabalha apenas com identidade de arquivo; testes não dependem de Minecraft ou áudio real.

### Fase 5 — Player gameplay próprio e keybinds

**Estado:** F7/F8/F9 chamam o player próprio nos dois loaders; ciclo integrado Next→Pause→Resume→Previous→Next mantém os paths exatos e não reinicia a faixa ao pausar. Troca/saída de mundo limpa a instância própria e a fila. Teste de controller com plataforma fake e smoke/builds por loader passaram.

**Trabalho:** integrar Play/Next, Previous e Pause/Resume à fila e à reprodução fixed-file; assumir o controle do background gameplay, mas somente nessa tela/contexto; lifecycle de entrar/sair do mundo; preservar menu/non-gameplay vanilla e jukebox físico. Definir estado após fim natural.

**Testes/validação:** controller tests com fake platform verificam comandos e sequência exata de resource locations, inclusive ida Previous→Next; testes adapter verificam que não se chama stop/pause para fontes alheias; builds e inicialização nos dois loaders. Reteste manual das três teclas em ambos.

**Aceite:** a música ouvida corresponde ao arquivo registrado; Previous e Next navegam arquivos concretos; pausa volta ao mesmo instante; transições de contexto não deixam som órfão nem competem com vanilla.

### Fase 6 — Bias contextual, reload e robustez

**Estado:** Implementado: novos sorteios dão peso 3:1 a faixas associadas ao bioma atual, sem retirar o pool global; evitam repetir a faixa ativa quando há alternativa; Next/Previous ignoram entradas removidas após reload. Os dois adapters invalidam snapshots em resource reload e descartam com segurança a instância ativa se o canal já não a reconhece. Testes determinísticos cobrem bias, fallback, não repetição e fila com entradas removidas; builds atuais dos dois loaders passaram.

**Trabalho:** adicionar preferência suave por afiliação do bioma/situação atual em novos sorteios, mantendo fallback no pool todo; definir comportamento de entradas removidas e novas após resource reload; tratar pools vazias e assets inválidos.

**Testes/validação:** seleção determinística com seed confirma preferência em amostra controlada, fallback sem correspondência, nenhuma exclusão global e ausência de repetição imediata quando há alternativa; testes de reload e evento faltante; validação manual com vanilla e conteúdo de mod/datapack.

**Aceite:** viés nunca torna fontes sem afinidade inalcançáveis; reload atualiza próximas seleções sem quebrar playback ativo válido.

### Fase 7 — Toast e diagnósticos de uso real

**Estado:** Implementado: labels do toast compartilham chaves localizadas, mudanças reais de estado durante uma ação atualizam o texto/temporizador, e o fade continua nos últimos 500 ms do período de dois segundos. Logs formatam de modo estável o arquivo, fontes e biomas; testes verificam traduções e formatação. O smoke de inicialização dos dois clientes passou; inspeção visual do HUD segue na validação manual.

**Trabalho:** reaproveitar/ajustar toast existente e logs. Toast `Previous`, `Next`, `Paused`, `Playing`, `Muted`; fade e expiração em dois segundos, só reaparece para nova ação ou mudança de estado. Logs distinguem arquivo pedido, arquivo resolvido/ativo, afiliação, fila e resultado.

**Testes/validação:** unit tests da máquina temporal do toast, prioridade do mute e não reexibição após expiração; testes de formatação e evento de playback logging; inspeção de log em cada loader. Validação visual no HUD.

**Aceite:** feedback nunca apresenta SoundEvent/path como nome amigável, não renova todo frame e logs permitem comparar inequivocamente arquivo ouvido com estado reportado.

### Fase 8 — Compatibilidade e entrega do MVP

**Estado:** Revisão de compatibilidade, documentação pública, metadados e conteúdo de JAR concluída; `:common:test`, `:forge:build`, `:neoforge:build` e smoke de inicialização dos dois clientes passaram. O aceite manual de som e HUD em jogo continua pendente.

**Trabalho:** revisar isolamento client-side, resource reload, configurações, keybinds, idioma, metadados, instruções públicas e documentação técnica; remover/desativar caminhos antigos de `MusicManager` que conflitem com o player.

**Testes/validação:** common tests e build/check de Forge (Java 17) e NeoForge (Java 21); teste de inicialização client; inspeção de conteúdo dos JARs. Matriz manual nos dois jogos para: background vanilla, faixa de mod/datapack, disco local, jukebox espacial simultânea, volume/mute, Next/Previous/branch, Pause/Resume, reload, menu, disconnect, toast e logs.

**Aceite:** comportamento e conteúdo têm paridade nos targets; nenhuma concorrência vanilla em gameplay; menu e jukebox mantêm o fluxo vanilla; critérios funcionais acima passam. Builds não substituem validação auditiva/visual em jogo.

## 8. Critérios de pronto

O MVP estará pronto quando todas as fases 0–8 forem implementadas e verificadas, com testes automatizados para regras comuns, checks/build por loader e validação manual por target. Em especial, o arquivo que os logs dizem tocar deve corresponder ao que é ouvido; Previous/Next/Pause devem atuar nesse arquivo exato; volume Music deve ser respeitado; jukebox física deve continuar independente; e não-gameplay deve permanecer vanilla.

## 9. Estado atual e migração

O projeto começou com keybinds, histórico, pausa, toast e integração pelo `MusicManager`. O pivot substitui a seleção de gameplay por catálogo de arquivos concretos, fila por sessão e playback próprio, preservando menu e jukebox física. Todas as fases de implementação 0–8 estão concluídas e os checks automatizados/builds passaram; o usuário ainda precisa validar áudio, jukebox e apresentação do HUD durante jogo real.
