# Progresso — Layout Adaptativo

## Estado atual

- **Fase:** Planejamento concluído
- **Gate atual:** 5 — Go/No-Go
- **Status:** Concluído
- **Confiança:** 93%
- **Última atualização:** 2026-08-03

## Log de sessões

### Sessão 2026-08-03 — skill `spec`

**Gates:** 0 a 5

**Ações:**

- Contexto do repositório levantado direto do código, não de documentação. Achado
  decisivo: o app **já** trata rotação física mantendo o layout em retrato e girando
  os ícones por acelerômetro (`rollDegrees` → `snappedIconRotation`). O
  comportamento "controles na lateral" já existe; o que falta é o caso da janela
  larga com o aparelho em pé.
- Pesquisa externa com `WebSearch`/`WebFetch` (o `perplexity_research` não estava
  disponível). Três tópicos: escapatória do Android 16, `targetRotation` do CameraX
  com orientação destravada, detecção de janela larga em Compose.
- **Correção relevante:** descoberto que existe opt-out oficial —
  `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`. Uma conclusão anterior desta
  sessão afirmava que não havia; a conclusão vinha de um nome de propriedade
  inventado. Verificado em emulador: com a propriedade, a janela vira
  `Rect(680,0-1880,1600)` e a configuração reporta `w600dp h800dp port`.
- **Correção de escopo pelo usuário:** a pergunta de elicitação sobre "paridade
  total vs entrega parcial" superdimensionava o trabalho. O usuário apontou que
  bastaria detectar janela larga e inverter o layout existente. Verificação no
  código confirmou: os três blocos são âncoras de um `Box` e os overlays são
  posicionados por coordenada. A spec foi reescrita em torno disso (ADR-001).
- **Requisito novo do usuário:** legibilidade dos controles sobre imagem clara,
  observado na captura do tablet. Virou FR-5 + NFR-2, com medida objetiva de
  contraste (4,5:1, WCAG AA) em vez de "colocar um fundo".
- 7 FR e 5 NFR definidos; 3 ADRs; 6 tarefas em 3 ondas; rastreabilidade em três vias
  validada.

**Próximo:** implementação pela skill `dev`, começando pela Wave 0 (Task 1 e Task 5
em paralelo).

## Status das tarefas

| Tarefa | Onda | Status | Observação |
|---|---|---|---|
| 1. Predicado + helper de eixo | 0 | Pendente | Contrato; destrava a Wave 1 |
| 5. Rotação combinada da mídia | 0 | Pendente | Ortogonal; risco alto, defeito invisível na tela |
| 2. Barra superior | 1 | Pendente | |
| 3. Controles inferiores + proporção | 1 | Pendente | Caminho crítico |
| 4. Barras auxiliares + contraste | 1 | Pendente | |
| 6. Leitura, ponte, integração | 2 | Pendente | Remove o opt-out do manifesto |

## Pendências

- [ ] Iniciar a Wave 0

## Bloqueadores

Nenhum. Especificação aprovada.

## Notas para quem implementar

1. **Retrato é o caminho default.** Escreva os condicionais de forma que qualquer
   falha na detecção caia no layout de hoje. NFR-1 é Critical.
2. **A ponte fica até a Task 6.** Enquanto `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`
   estiver no manifesto, o tablet continua pillarboxed e você **não vê** o layout de
   paisagem. Para testar durante as Waves 0 e 1, remova a propriedade localmente e
   não comite.
3. **FR-6 não se verifica na tela.** O arquivo pode sair torto com a pré-visualização
   perfeita. Puxe o arquivo com `adb pull` e inspecione o metadado de rotação.
4. **A telemetria já ajuda.** `scripts/logcat.sh 'evt=bind'` mostra `aspect` e
   `elapsed_ms` — é como se verificam FR-3 e NFR-3 sem instrumentar nada novo.
