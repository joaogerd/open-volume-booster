# Open Volume Booster

Open Volume Booster e um aplicativo Android simples, sem anuncios, voltado ao controle de volume do fluxo de musica e ao reforco perceptual de loudness com protecoes contra distorcao.

O objetivo do projeto nao e burlar limites fisicos do aparelho nem prometer ganho sem perda. O foco e aplicar reforco de sinal usando recursos nativos de audio do Android, com curva progressiva de ganho, limitacao dinamica quando disponivel, fallback seguro e avisos claros ao usuario.

## O que o app faz

- controla o volume do sistema para `STREAM_MUSIC`;
- tenta aplicar `DynamicsProcessing` no Android 9+ com ganho de entrada e limiter;
- usa `LoudnessEnhancer` como fallback quando `DynamicsProcessing` nao esta disponivel ou falha no aparelho;
- usa presets simples: normal, moderado e alto;
- calcula ganho em dB/mB por uma politica centralizada e testavel;
- reduz o ganho maximo quando o volume do sistema ja esta proximo do limite;
- mostra feedback visual de risco: seguro, moderado ou proximo da saturacao;
- persiste o ultimo nivel de boost configurado.

## Volume do sistema versus processamento de audio

O volume do sistema apenas altera o nivel de saida permitido pelo Android para o fluxo de musica. Isso nao e o mesmo que processar o sinal.

O boost deste app usa dois caminhos:

1. `DynamicsProcessing`, quando disponivel, com ganho de entrada e limiter. Esse caminho permite aplicar mais ganho percebido com uma protecao melhor contra saturacao.
2. `LoudnessEnhancer`, como fallback, usando ganho alvo em millibels. A documentacao Android define 100 mB como 1 dB e 0 mB como ausencia de amplificacao.

## Estrategia para aumentar mais sem estourar

A classe `BoostGainModel` aplica:

- curva suave de ganho (`smoothstep`);
- teto de ganho dependente do volume atual do sistema;
- reducao extra quando boost e volume estao muito altos;
- limiter threshold negativo no caminho com `DynamicsProcessing`;
- fallback com ganho menor em `LoudnessEnhancer`;
- classificacao de risco para a interface;
- conversoes testaveis entre dB e ganho linear.

A politica atual permite ganho mais forte do que a primeira versao protegida: aproximadamente ate 16 dB em volume de sistema mais baixo, reduzindo progressivamente para cerca de 10,5 dB quando o volume do sistema esta no maximo. Em aparelhos com `DynamicsProcessing` funcional, o limiter trabalha com threshold negativo para tentar reduzir clipping audivel. No fallback com `LoudnessEnhancer`, o ganho e reduzido em relacao ao ganho principal para evitar distorcao excessiva.

## Limitacoes reais do Android

- O app nao consegue aumentar fisicamente a potencia maxima do alto-falante.
- O comportamento dos efeitos varia por fabricante, ROM, app de reproducao e dispositivo de saida.
- Nem todo player expoe ou comunica uma audio session compativel.
- O uso de efeitos na sessao global de audio tem limitacoes e pode ser inconsistente entre versoes do Android.
- Sem processar diretamente um `AudioTrack` proprio, o app nao tem medicao real de pico/sample para detectar clipping com precisao absoluta.

## Permissoes

O manifesto usa:

- `android.permission.MODIFY_AUDIO_SETTINGS`: necessario para alterar configuracoes de audio e volume do sistema.
- `android.permission.FOREGROUND_SERVICE`: reservado para evolucao futura caso o booster passe a manter uma notificacao persistente. A implementacao atual usa um servico simples iniciado pelo app.

## Como compilar

```bash
./gradlew assembleDebug
```

## Como testar

```bash
./gradlew test
```

Testes incluidos:

- boost zero nao aplica ganho;
- limites maximos sao respeitados;
- volume alto reduz ganho maximo;
- conversao dB <-> ganho linear permanece consistente;
- preset moderado cai em classificacao moderada;
- boost alto usa headroom/threshold de limiter.

## Cuidados auditivos

Volumes altos podem causar fadiga auditiva e dano permanente a audicao. Use o boost por pouco tempo, reduza imediatamente se ouvir distorcao e evite usar fones em volume alto por periodos prolongados.

## Estado do projeto

Esta versao ja usa uma cadeia mais forte: `DynamicsProcessing` com limiter quando possivel e `LoudnessEnhancer` como fallback. O proximo salto tecnico seria controlar diretamente a reproducao com um pipeline proprio baseado em `AudioTrack` ou player interno, permitindo medicao real de pico e limitacao sample-aware.
