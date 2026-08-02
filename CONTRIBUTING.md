# Guia de Contribuição

## Como Começar

1. Faça um fork do repositório
2. Clone seu fork: `git clone https://github.com/seu-usuario/space-camera.git`
3. Configure upstream: `git remote add upstream https://github.com/maicon-projetos/space-camera.git`
4. Execute setup: `./setup.sh`

## Branches

- `main`: Código estável e em produção
- `develop`: Branch de desenvolvimento
- `feature/*`: Novas features
- `bugfix/*`: Correções de bugs
- `hotfix/*`: Correções urgentes para produção

## Workflow de Contribuição

### 1. Criar uma branch
```bash
git checkout -b feature/minha-feature
```

### 2. Fazer commits com boas mensagens
```bash
git commit -m "feat: descrição clara da mudança"
```

**Convenções de commit**:
- `feat:` Nova feature
- `fix:` Correção de bug
- `docs:` Documentação
- `style:` Formatação (sem mudança de lógica)
- `refactor:` Refatoração de código
- `test:` Adicionar testes
- `chore:` Atualizar dependências

### 3. Push para seu fork
```bash
git push origin feature/minha-feature
```

### 4. Criar Pull Request
- Descreva claramente o que foi mudado
- Referencie issues relacionadas: `Closes #123`
- Aguarde code review

## Padrões de Código

### Kotlin Style Guide
- Seguir [Kotlin official style guide](https://kotlinlang.org/docs/coding-conventions.html)
- Usar `camelCase` para variáveis e funções
- Usar `PascalCase` para classes
- Máximo 120 caracteres por linha

### Composables
```kotlin
@Composable
fun MyComposable(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    // Implementation
}
```

### ViewModels
```kotlin
class MyViewModel : ViewModel() {
    private val _state = MutableStateFlow<MyState>(MyState.Initial)
    val state: StateFlow<MyState> = _state.asStateFlow()
    
    fun performAction() {
        viewModelScope.launch {
            // Implementation
        }
    }
}
```

## Testes

### Cobertura Mínima
- Todas as funções públicas devem ter testes
- Mínimo 70% de cobertura de código

### Tipos de Testes
- **Unit Tests**: Testar lógica isolada (JUnit)
- **Integration Tests**: Testar interação entre componentes
- **UI Tests**: Testar telas Compose (Espresso/Compose Test)

### Executar Testes
```bash
yarn test
# ou
./gradlew test
```

## Pull Request Checklist

- [ ] Testes adicionados/atualizados
- [ ] Documentação atualizada
- [ ] Sem conflitos com `develop`
- [ ] Mensagens de commit claras
- [ ] Sem código comentado ou debug
- [ ] Segue padrões do projeto

## Documentação

### README
Atualize se:
- Novas features principais
- Mudança em instruções de instalação
- Novos comandos de build

### Code Comments
```kotlin
/**
 * Descrição clara da função.
 * 
 * @param param1 Descrição do primeiro parâmetro
 * @return Descrição do retorno
 * @throws ExceptionType Quando esta exception é lançada
 */
fun myFunction(param1: String): String {
    // Implementation
}
```

## Reportar Bugs

Use a seção "Issues" do GitHub:

1. **Título claro**: "Camera crashes on 4K recording"
2. **Descrição**: Passos para reproduzir
3. **Esperado vs Atual**: O que deveria acontecer vs o que acontece
4. **Ambiente**: Android version, device model, app version
5. **Logs**: Cole logcat output se relevante

### Template
```markdown
## Descrição do Bug
[Descrição clara do problema]

## Como Reproduzir
1. Passo 1
2. Passo 2
3. Passo 3

## Comportamento Esperado
[O que deveria acontecer]

## Comportamento Atual
[O que acontece]

## Ambiente
- Android Version: [ex: 13]
- Device: [ex: Pixel 6]
- App Version: [ex: 1.0.0]

## Logs
[Colocar logcat ou stack trace]
```

## Sugerir Features

1. Abra uma Issue com tag `enhancement`
2. Descreva:
   - O que deseja
   - Por quê é útil
   - Possível implementação (opcional)

## Comunidade

- Respeito mútuo
- Sem spam ou conteúdo ofensivo
- Inglês ou Português
- Estamos aqui para aprender juntos!

## Contato

Para dúvidas sobre contribuição:
- Comente em issues
- Abra discussions
- Envie email: maicon@example.com

---

**Obrigado por contribuir! 🎉**
