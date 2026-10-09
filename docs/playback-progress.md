# Progresso de reprodução

O Player mostra `posição atual / duração total` e uma barra discreta abaixo do nome da faixa. O contador acompanha o canal real de áudio, inclusive pausa, retomada, suspensão por outras telas e mudanças de pitch. Ao trocar de faixa, o progresso começa novamente em zero.

## Custo e atualização

- A tela consulta o progresso no máximo quatro vezes por segundo, apenas na aba Player, sem modal aberto. A renderização usa valores já calculados e não reconstrói listas, inputs ou layout a cada atualização.
- As consultas OpenAL acontecem na thread de áudio. Quando o Player deixa de ficar visível, as consultas de posição param após uma janela de até 500 ms.
- Apenas os canais do Better Radio acompanham os buffers removidos do streaming. Uma consulta ao tamanho de cada buffer removido preserva o tempo acumulado mesmo quando a tela está fechada, permitindo reabri-la na posição correta. Outros sons não fazem essas consultas extras.
- A duração é solicitada somente ao mostrar o Player. A leitura ocorre na pool de I/O, examina os metadados Ogg/Vorbis e pula os dados comprimidos, sem decodificar PCM nem carregar a música inteira em memória.
- O cache armazena até 64 durações, incluindo resultados indisponíveis, e é limpo ao recarregar os recursos. A duração de um recurso substituído por um resource pack é recalculada.
- O texto do contador é refeito apenas quando muda o segundo exibido ou a duração. Enquanto a duração carrega ou não pode ser determinada, ambos os tempos aparecem como `--:-- / --:--`.

Há um pequeno custo de acompanhamento e I/O em segundo plano; impacto de desempenho em jogo ainda precisa de medição. Compilação não comprova aparência, injeção dos Mixins ou comportamento em execução.

## Estados durante o silêncio

- `Starting soon` / `Iniciando em breve`: espera inicial de 10–15 segundos ao entrar no mundo.
- `Interval between tracks` / `Intervalo entre músicas`: intervalo automático entre faixas, conforme a configuração do modo ativo, incluindo a pausa curta de Non-stop.
- `Nothing playing` / `Nenhuma música tocando`: nenhuma faixa ativa e nenhuma dessas esperas programadas; inclui a tentativa de encontrar uma faixa quando não há músicas disponíveis.

## Referências

- [OpenAL 1.1](https://www.openal.org/documentation/openal-1.1-specification.pdf): offsets do canal e buffers de streaming.
- [Ogg framing](https://www.xiph.org/ogg/doc/framing.html) e [Vorbis I](https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html): identificação do stream, sample rate e granule position para duração.
