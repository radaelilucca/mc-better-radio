# Player pessoal — NeoForge 1.21.1

O player abre com **F6**, configurável nos controles. A tela usa RUI Lib, mantém o jogo rodando e tem quatro abas: Player, Histórico, Playlist e Configurações. F7, F8 e F9 continuam controlando anterior, próxima e pausa, inclusive com a tela aberta. Escape fecha primeiro um dropdown aberto, depois o catálogo sobreposto, depois o player.

Ao entrar no mundo, um toast de **5 segundos** informa que o Better Radio foi iniciado e mostra o modo ativo (Playlist ou Free flow), antes da primeira reprodução automática. Ele aparece uma vez por conexão, respeita a configuração de notificações e não se repete ao trocar de dimensão. Uma playlist sem faixas disponíveis é apresentada como Free flow.

## Player

Os controles ficam centralizados no topo. Play inicia uma música quando o player está vazio; durante a reprodução, alterna pausa e continuação do mesmo ponto. A faixa atual, o modo e o toggle de shuffle ficam acima da fila.

A fila mostra o avanço do histórico após usar Previous, seguido das escolhas manuais e das próximas faixas da playlist. Pular para uma faixa descarta as anteriores e mantém as seguintes. No Free flow, a indicação **Próxima: aleatória** aparece depois das faixas definidas; ela não sorteia uma música apenas para preencher a tela.

Na aba **Configurações**, os dropdowns **Intervalo do Free flow** e **Intervalo da Playlist** controlam, independentemente, o silêncio entre faixas no respectivo modo. Cada opção explica seu intervalo na tooltip:

| Opção | Intervalo aleatório |
| --- | --- |
| Frequente | 1–2 minutos |
| Equilibrado | 2–5 minutos |
| Ocasional | 5–10 minutos |
| Sem parar | 2–4 segundos |

Cada término de faixa sorteia um novo intervalo usando a preferência do modo ativo, inclusive para faixas enfileiradas e ao repetir a playlist. Free flow usa **Equilibrado** como padrão e Playlist usa **Sem parar**, preservando a pausa curta de **2–4 segundos**. Previous, Next, Tocar agora e os saltos da fila continuam imediatos. Ao entrar no mundo, o primeiro autoplay espera **10–15 segundos**, inclusive na playlist; uma ação manual pode iniciar a música antes. Alterar o intervalo do modo ativo durante o silêncio aplica um novo intervalo, sem alterar a espera inicial. Editar o intervalo do outro modo não reinicia a espera atual.

## Histórico

Registra cada reprodução iniciada, com as mais recentes primeiro. Pausar ou continuar não cria outra entrada. Escolher outra faixa depois de voltar no histórico muda o percurso de Previous/Next, mas mantém o registro completo do que foi ouvido.

- **Adicionar à fila:** acrescenta ao final da fila manual e não interrompe a faixa atual.
- **Tocar agora:** troca a faixa atual e preserva a fila manual e o modo ativo.
- **Salvar na playlist (↓):** salva a faixa do histórico na playlist pessoal. Fica desabilitado para faixas já salvas ou indisponíveis. O estado de faixas já salvas é armazenado em cache ao abrir a tela e atualizado somente após salvar pelo histórico; mudanças pela aba Playlist ou pelo catálogo aparecem nessa verificação na próxima abertura ou salvamento pelo histórico. Entradas repetidas da mesma faixa compartilham o estado pelo ID do áudio. Resize, troca de abas, ticks e renderização não refazem essa verificação; a montagem de uma linha apenas consulta o estado em cache em tempo constante.

## Playlist

Existe uma playlist pessoal. Adicionar música abre o catálogo em um modal; ele continua aberto após cada adição. A playlist não aceita duplicatas, mas é possível enfileirar a mesma música várias vezes.

**Usar playlist** começa imediatamente pela primeira faixa da ordem ativa, preservando a fila manual para depois. A playlist repete quando termina. Shuffle altera somente as próximas faixas da playlist, criando outra ordem a cada volta e evitando repetir a última faixa imediatamente quando há alternativas.

**Voltar ao Free flow** deixa a música atual terminar e mantém a fila manual. Remover a faixa atual da playlist também não interrompe sua reprodução. Cada modo usa seu próprio intervalo no avanço automático; o Free flow aplica a preferência pelo bioma configurada, com peso 3:1 como padrão.

Discos usam os nomes localizados de suas músicas; faixas ambientes aparecem como **Música ambiente**. Faixas ausentes do catálogo aparecem como indisponíveis, permanecem salvas e podem ser removidas; são ignoradas durante a reprodução. Uma playlist sem músicas disponíveis usa Free flow.

## Configurações

As configurações ficam em uma lista vertical com label à esquerda e input à direita. Cada linha ocupa toda a largura disponível. As labels explicam a configuração em tooltips; os dropdowns também explicam cada valor.

- **Intervalo do Free flow:** Frequente, Equilibrado (padrão), Ocasional ou Sem parar, conforme os intervalos da tabela acima.
- **Intervalo da Playlist:** as mesmas quatro opções, com Sem parar como padrão. A escolha independe do intervalo do Free flow e vale também ao repetir a playlist.
- **Preferência pelo bioma:** slider de 0–100%, em passos de 5%, ajustável por arraste, roda do mouse e teclado. 0% distribui o mesmo peso a todas as faixas elegíveis; 100% (padrão) mantém o peso 3:1 para faixas associadas ao bioma. O percentual representa a intensidade da preferência, não a probabilidade final de tocar uma categoria. As outras músicas continuam elegíveis.
- **Modo de inicialização:** Último modo usado (padrão), Free flow ou Playlist. A escolha se aplica na próxima entrada no mundo e mantém a espera inicial de 10–15 segundos. Playlist sem faixas disponíveis continua usando Free flow.

## Persistência e implementação

`config/better_radio-player.json` salva as referências da playlist, shuffle, modo ativo, intervalos do Free flow e da Playlist, bias percentual e modo de inicialização para toda a instalação do cliente. O áudio e a posição não são salvos. Histórico e fila reiniciam ao sair do mundo ou conectar a outra sessão; trocar de dimensão preserva o estado da sessão. Campos novos ausentes ou inválidos usam seus padrões e preservam as demais preferências. Arquivos antigos mantêm `musicFrequency` como intervalo do Free flow e recebem `playlistFrequency` com padrão `NON_STOP`.

O arquivo usa versão de formato 1 e gravação por arquivo temporário seguida de substituição. Se houver dados inválidos ou uma versão desconhecida, o player começa em Free flow; antes de um novo salvamento, guarda uma cópia do arquivo original. Erros de salvamento são registrados no log e informados na tela, mantendo as alterações em memória.

`PlayerSession` contém o estado sem depender de Minecraft. `MusicClientNeoForge` encaminha atalhos, botões e autoplay ao mesmo estado, e `PlayerScreen` usa Panel, Tabs, ScrollView, IconButton, Select, Slider e Modal da biblioteca. Os inputs de configurações permanecem montados durante atualizações da reprodução para preservar foco, arraste e dropdowns abertos. Apenas o layout das linhas, a divisão entre cabeçalho e lista e os textos limitados à largura são específicos do Better Radio.

## Validação

Os testes de `PlayerSession` cobrem fila, saltos, navegação, histórico completo, repetição, shuffle, pausa, faixas ausentes e reinício de sessão. Os testes de persistência verificam o arquivo salvo e a recuperação de dados inválidos.

No cliente, conferir F6, as quatro abas, o catálogo sobreposto, scroll, Escape, nomes longos, diferentes escalas de GUI e atualização durante a reprodução. Conferir também os dropdowns e suas tooltips, slider por arraste/roda/teclado, bias neutro/máximo, intervalos distintos por modo, repetição da playlist, faixas enfileiradas, atalhos imediatos, espera inicial de 10–15 segundos, troca de dimensão e persistência das configurações após reiniciar o cliente.
