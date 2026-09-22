# Better Radio — Minecraft Music Hotkey

## 1. Objetivo

Permitir que o jogador controle rapidamente a música ambiente do Minecraft por keybind.

Quando nenhuma música estiver tocando, a tecla escolhe e toca uma faixa aleatória. Quando já existir música, a tecla para a faixa atual, escolhe outra e toca imediatamente.

## 2. Plataformas alvo

- Minecraft 1.20.1 — Forge
- Minecraft 1.21.1 — NeoForge

O projeto deve manter um core pequeno e adapters específicos por plataforma, sem abstrair diferenças de loader que não tragam benefício real.

## 3. Arquitetura

Estrutura conceitual:

~~~
better-radio/
│
├── common/
│   ├── MusicController
│   ├── MusicSelector
│   ├── MusicHistory
│   ├── ModConfig
│   └── platform/
│       └── MusicPlatform
│
├── forge-1.20.1/
│   └── ForgeMusicPlatform
│
└── neoforge-1.21.1/
    └── NeoForgeMusicPlatform
~~~

### MusicController

Fluxo principal:

~~~
hotkey pressed
      ↓
MusicController.next()
      ↓
is music playing?
   ├── no  → choose random → play
   └── yes → stop → choose random → play
~~~

### MusicSelector

Responsável por descobrir faixas elegíveis, escolher uma aleatoriamente e evitar repetição imediata. Não deve tocar ou parar áudio nem renderizar HUD.

### MusicHistory

No MVP, mantém apenas lastTrack, impedindo sequências como:

~~~
Sweden → hotkey → Sweden
~~~

Um histórico maior pode existir no futuro, mas não faz parte do escopo atual.

### MusicPlatform

Abstrai as APIs específicas de cada versão:

~~~java
interface MusicPlatform {

    boolean isMusicPlaying();

    void stopCurrent();

    List<MusicTrack> getAvailableTracks();

    void play(MusicTrack track);
}
~~~

A implementação deve utilizar a infraestrutura vanilla sempre que possível.

## 4. Áudio

Não usar biblioteca externa. Utilizar o Minecraft MusicManager para respeitar o volume de música, integrar com a engine vanilla, não interferir em outros canais de som e manter a complexidade baixa.

## 5. Escopo do MVP

- keybind Play/Next;
- tocar música quando nada estiver tocando;
- trocar a música atual;
- randomização;
- evitar repetição imediata;
- respeitar o volume de Music;
- não interferir em jukeboxes, blocos, mobs ou sons ambientes;
- feedback textual opcional.

O projeto deve ser invisível quando não estiver sendo usado e não alterar o comportamento dos demais canais de áudio.

## 6. Feedback

O feedback pode ser:

~~~
Now Playing
Sweden
~~~

ou apenas:

~~~
Sweden
~~~

Configuração prevista:

~~~
showNowPlaying = true|false
~~~

O formato ainda não está fechado: actionbar, toast ou pequeno overlay são opções válidas.

## 7. Configuração e dependências

MidnightLib é o candidato atual para configuração por ser leve e suportar Forge 1.20.1 e NeoForge 1.21.1, mas não é uma decisão irreversível.

Priorizar APIs vanilla, código client-side, poucas dependências e comportamento previsível. Não criar imediatamente uma biblioteca comum publicada para os dois projetos; qualquer código realmente reutilizável pode ficar duplicado ou em módulo interno compartilhado quando isso fizer sentido.

## 8. Spike técnico

Antes da implementação completa:

1. listar as músicas disponíveis;
2. detectar a música atual;
3. parar a faixa atual;
4. selecionar outra faixa, evitando repetição imediata;
5. tocar imediatamente;
6. confirmar que o volume de Music é respeitado e que jukeboxes, blocos, mobs e sons ambientes não são afetados.

## 9. Próximo passo

Validar primeiro o acesso às faixas e o ciclo detectar → parar → selecionar → tocar em Forge 1.20.1 e NeoForge 1.21.1. Depois do spike, fechar as APIs concretas e decidir o formato do feedback Now Playing.

