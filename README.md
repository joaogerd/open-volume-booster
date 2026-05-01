# Open Volume Booster

Open Volume Booster e um aplicativo Android simples, sem anuncios, voltado ao controle de volume, reforco perceptual de loudness e testes de booster em fone de ouvido ou Bluetooth.

O projeto agora possui dois modos:

1. **Booster externo**: tenta aplicar efeitos em sessoes de audio abertas por outros apps. Este modo depende do player, da ROM e do aparelho.
2. **Player interno**: toca um arquivo de audio dentro do proprio app e aplica o booster diretamente na `audioSessionId` controlada pelo app. Este e o modo mais confiavel para fone e Bluetooth.

## O que o app faz

- controla o volume do sistema para `STREAM_MUSIC`;
- oferece player interno com selecao de audio, play, pause e stop;
- aplica `LoudnessEnhancer` diretamente na sessao do player interno;
- aplica reforco perceptual com `Equalizer`, especialmente na regiao de presenca;
- aplica `BassBoost` leve para sensacao de corpo sem saturar demais;
- tenta aplicar `DynamicsProcessing` como limiter auxiliar no Android 9+;
- usa presets simples: normal, moderado e alto;
- calcula ganho em dB/mB por uma politica centralizada e testavel;
- mostra feedback visual de risco;
- persiste o ultimo nivel de boost configurado.

## Por que existe um player interno

No Android, efeitos como `LoudnessEnhancer`, `Equalizer` e `BassBoost` precisam estar ligados a uma sessao de audio. Outros apps nem sempre expoem essa sessao para efeitos externos. Por isso, um booster externo pode funcionar pouco ou nao funcionar dependendo do player.

No player interno, o app cria o proprio `MediaPlayer`, obtem sua `audioSessionId` e aplica os efeitos diretamente nessa sessao. Assim o audio enviado para fone ou Bluetooth ja sai processado pelo app.

## Volume do sistema versus processamento de audio

O volume do sistema apenas altera o nivel de saida permitido pelo Android para o fluxo de musica. Isso nao e o mesmo que processar o sinal.

O boost perceptual combina:

- `LoudnessEnhancer` para loudness geral;
- `Equalizer` para reforco de medios/agudos, onde a percepcao de volume e maior;
- `BassBoost` leve para sensacao de corpo;
- `DynamicsProcessing` como tentativa auxiliar de limiter.

## Estrategia para aumentar percepcao sem derrubar volume

Boost excessivo pode disparar compressao interna do Android ou do proprio dispositivo. Quando isso acontece, o modo alto pode soar mais baixo do que o normal. Por isso a politica atual evita ganho bruto extremo e prioriza presenca controlada.

A classe `BoostGainModel` aplica:

- curva perceptual estavel;
- teto de ganho dependente do volume atual do sistema;
- reducao quando boost e volume estao altos;
- reforco de presenca controlado;
- grave leve;
- classificacao de risco para a interface;
- conversoes testaveis entre dB e ganho linear.

## Limitacoes reais do Android

- O app nao consegue aumentar fisicamente a potencia maxima do alto-falante.
- O comportamento dos efeitos varia por fabricante, ROM, app de reproducao e dispositivo de saida.
- Nem todo player expoe ou comunica uma audio session compativel.
- O booster externo pode nao funcionar em YouTube, Spotify, navegadores ou jogos.
- O player interno e mais confiavel porque controla a propria sessao de audio.
- Sem processar amostras diretamente com um pipeline proprio de DSP, o app ainda nao tem medicao real de pico/sample.

## Permissoes

O manifesto usa:

- `android.permission.MODIFY_AUDIO_SETTINGS`: necessario para alterar configuracoes de audio e volume do sistema.
- `android.permission.FOREGROUND_SERVICE`: reservado para evolucao futura caso o booster externo passe a manter uma notificacao persistente.

A selecao de audio no player interno usa o seletor de documentos/conteudo do Android, sem exigir permissao ampla de leitura de armazenamento.

## Como compilar

```bash
./gradlew assembleDebug
```

## Como testar

```bash
./gradlew test
```

Teste recomendado no celular:

1. Conecte o fone ou Bluetooth.
2. Abra o app.
3. Use o bloco **Player interno**.
4. Escolha um arquivo de audio.
5. Aperte Play.
6. Compare Normal, Moderado e Alto.

## Cuidados auditivos

Volumes altos podem causar fadiga auditiva e dano permanente a audicao. Use o boost por pouco tempo, reduza imediatamente se ouvir distorcao e evite usar fones em volume alto por periodos prolongados.

## Estado do projeto

A versao atual possui um booster externo best-effort e um player interno mais confiavel. O proximo salto tecnico seria substituir `MediaPlayer` por um pipeline com `AudioTrack` ou ExoPlayer customizado para medir pico real, aplicar compressor/limiter sample-aware e controlar melhor a qualidade sonora.
