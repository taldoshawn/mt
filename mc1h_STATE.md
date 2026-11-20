# mc1h — OpenFileLab state (2026-11-20)

## Pedido

Começar a desenvolver um aplicativo Android equivalente, por implementação independente, às ferramentas de arquivos/edição/análise observadas no MT Manager. Usuário enviou `MT-ANALISE.tar.gz` e `MT-SMALI.tar.gz`, que foram analisados anteriormente. Prioridade: funcionalidade real, não apenas plano, Android em celular e APK eventualmente testável.

## Referência observada

Relatório `MT-AUDITORIA.md` (fora deste projeto): metadados de `bin.mt.plus` 2.26.11, 5 DEX, 39 bibliotecas nativas em 4 ABIs, telas de arquivo/editor/Smali/APK/remoto identificadas por Manifest; Java do JADX parcialmente incompleto. APK original não fornecido (não há SHA-256 do original). Nenhum código proprietário ou material de assinatura foi incorporado.

## Entrega implementada nesta fase

Partida do OpenFileLab 0.1 extraído de `openfilelab-source.zip`, não dos arquivos proprietários. Alterações em `MainActivity.java`, novas classes `DexInspector.java`, `ZipExtractor.java`, `ApkInspector.java`, testes `FeatureTest.java` e runner de testes, workflow CI do Android, README e `versionCode=2` / `versionName=0.2.0`.

## Verificações realmente realizadas

- `javac --release 17` das classes Java puras + 2 arquivos de testes: PASSOU.
- `SmokeTest`: PASSOU.
- `FeatureTest`: PASSOU em 21 verificações (DEX íntegro/adulterado/truncado, seção fora dos limites, caminhos inválidos, extração e inventário APK sintético).
- Build de aplicativo Android: **NÃO EXECUTADO**, ambiente não possui Android SDK nem Gradle e rede do container não alcançou plataforma Android. APK de instalação não gerado.
- Execução no MT Manager: não é objetivo desta implementação; inspeção estática do APK descompilado foi realizada em etapa anterior.
- Execução do OpenFileLab em aparelho: **NÃO EXECUTADA**.

## Restrições e aceitação

Sem bypass de VIP/licenciamento nem cópia direta de código, assets ou chaves proprietárias. Usar Storage Access Framework e não alegar acesso global aos arquivos. Validar entradas antes de extrair. Não chamar a versão 0.2 de reconstrução completa.

## Próximo check executável

Compilar no GitHub Actions com `gradle :app:assembleDebug` e corrigir qualquer erro real de compilação antes de testar no Android. Depois testar seleção de pasta, navegação, extração de ZIP legítimo/malicioso, inspeção de APK e DEX. No próximo ciclo, implementar parser AXML de leitura e editor HEX com cópia segura.

## Atualização 0.2.1 — preparação GitHub (2026-11-20)

- Fonte base: **todos** os arquivos originais do OpenFileLab 0.2 foram preservados; nenhum binário ou código do MT Manager foi incorporado.
- `.github/workflows/build-android.yml` foi reforçado com execução de testes, setup JDK 17 / Android SDK 35 / Gradle 8.9, build `:app:assembleDebug`, validação do APK, SHA-256 e upload de artifact. Dispara em push/PR e manualmente.
- Adicionados `gradle.properties`, `.gitattributes`; ampliado `.gitignore` para evitar arquivos proprietários e chaves acidentais; README documenta publicação e download de artefatos.
- Incrementada versão para 0.2.1; sem alterações no comportamento funcional do app.
- Testes Java executados localmente. Compilação Android permanece não executada até um run real do GitHub Actions; não confundir com testes de Java puro.
