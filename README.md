# Open Volume Booster

Open Volume Booster e um aplicativo Android simples, sem anuncios, voltado ao controle de volume do fluxo de musica e ao reforco perceptual de loudness com protecoes basicas contra distorcao.

O objetivo do projeto nao e burlar limites fisicos do aparelho nem prometer ganho sem perda. O foco e aplicar um reforco conservador usando os recursos nativos de audio do Android, com curva progressiva de ganho, reducao automatica quando o volume do sistema esta alto e avisos claros ao usuario.

## O que o app faz

- controla o volume do sistema para `STREAM_MUSIC`;
- aplica reforco de loudness via `LoudnessEnhancer` quando uma sessao de audio compativel esta disponivel;
- usa presets simples: normal, moderado e alto;
- calcula ganho em dB/mB por uma politica centralizada e testavel;
- reduz o ganho maximo quando o volume do sistema ja esta proximo do limite;
- mostra feedback visual de risco: seguro, moderado ou proximo da saturacao;
- persiste o ultimo nivel de boost configurado.

## Volume do sistema versus processamento de audio

O volume do sistema apenas altera o nivel de saida permitido pelo Android para o fluxo de musica. Isso nao e o mesmo que processar o sinal.

O boost deste app usa `LoudnessEnhancer`, que recebe um ganho alvo em millibels. A documentacao Android define 100 mB como 1 dB e 0 mB como ausencia de amplificacao. O proprio efeito pode comprimir sinais que ultrapassem a faixa suportada pela plataforma, mas isso nao elimina totalmente distorcao em todos os aparelhos, fones ou apps.

## Estrategia para reduzir clipping

O app evita mapear 100% de boost diretamente para um ganho extremo. A classe `BoostGainModel` aplica:

- curva suave de ganho (`smoothstep`);
- teto de ganho dependente do volume atual do sistema;
- reducao extra quando boost e volume estao muito altos;
- classificacao de risco para a interface;
- conversoes testaveis entre dB e ganho linear.

A politica atual limita o ganho alvo aproximadamente entre 6,5 dB e 9,5 dB, dependendo do volume do sistema. Em volumes muito altos, o teto e reduzido para preservar headroom.

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
- preset moderado cai em classificacao moderada.

## Cuidados auditivos

Volumes altos podem causar fadiga auditiva e dano permanente a audicao. Use o boost por pouco tempo, reduza imediatamente se ouvir distorcao e evite usar fones em volume alto por periodos prolongados.

## Estado do projeto

Esta e uma base inicial, simples e mantida propositalmente pequena. O proximo passo tecnico, caso o objetivo seja controle real de pico e limitacao dinamica completa, seria implementar uma cadeia propria baseada em `AudioTrack`/player interno ou integrar `DynamicsProcessing` quando o app controlar diretamente a sessao de audio.
