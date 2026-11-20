# OpenFileLab 0.2.1 · mc1h

Aplicativo Android independente para gerenciamento e inspeção de arquivos, desenvolvido em **Java 17**, com interface em português brasileiro, **sem VIP e sem paywall**.

> **Escopo:** este projeto contém o código OpenFileLab produzido para esta colaboração. O MT Manager serviu apenas como referência de categorias de funcionalidades. **Não inclui** código, assets, bibliotecas, chaves ou verificações de licença do MT Manager. Não é um clone completo nem uma versão modificada do MT Manager.

## Funcionalidades presentes

- Gerenciador de arquivos com **dois painéis** usando o Storage Access Framework (SAF), sem root.
- Navegação, filtro por nome, criar, renomear, copiar entre painéis e excluir com confirmação.
- Edição de texto UTF-8 (salva cópia `.editado`, limitada a 1 MiB).
- Visualização hexadecimal somente leitura (até 128 KiB).
- Listagem e extração de arquivos ZIP, JAR, APK e APKS, com limites para conteúdo não confiável e rejeição de caminhos perigosos.
- Inventário de APK (DEX, bibliotecas nativas, arquivos de recursos e assets).
- Inspeção de cabeçalho DEX, limites internos e integridade SHA-1 / Adler-32.

A extração tem limites de até 3.000 entradas, 128 MiB por arquivo, 256 MiB no total e 16 níveis. Ela cria uma pasta nova e tenta remover resultados parciais em caso de falha. Diferentes provedores SAF têm comportamentos próprios.

## GitHub Actions — como gerar APK no celular

O workflow `.github/workflows/build-android.yml` executa testes Java, instala Java 17 e Android SDK 35, utiliza Gradle 8.9 com Android Gradle Plugin 8.7.3, compila o aplicativo e publica o APK **debug** e seu SHA-256 como artefatos.

1. Publique todos os arquivos deste projeto **na raiz** de um repositório GitHub; `.github/` também deve ser incluída.
2. Abra **Actions → Android • testar e compilar APK → Run workflow** (ou faça push na branch `main`).
3. Após um build **bem-sucedido**, abra a execução e baixe o artefato `OpenFileLab-debug-<número>`.
4. Extraia o ZIP do artefato: ele contém `app-debug.apk` para instalação e `app-debug.apk.sha256` para conferência.

O APK é assinado com a **chave de debug do runner** (não é versão de distribuição). A cada runner, a identidade de assinatura pode mudar: desinstale uma versão debug anterior se houver conflito de assinatura. **Apenas enviar o código ao GitHub não garante que o build passe; confira o resultado do Actions.**

### Publicar em repositório GitHub vazio pelo Termux

No Termux, dentro da **pasta deste projeto já descompactado** (que contém `app/` e `.github/`):

```sh
pkg install git gh -y
gh auth login
git init
git add .
git commit -m "OpenFileLab 0.2.1: aplicativo Android e CI"
git branch -M main
git remote add origin https://github.com/taldoshawn/mt.git
git push -u origin main
```

O comando `gh auth login` é interativo; **não envie token, senha ou códigos de autenticação por mensagem**. Se o repositório já tiver commits, não faça push forçado: verifique o histórico antes de integrar o projeto. Se `git commit` pedir identidade, configure localmente `git config user.name` e `git config user.email` com os seus dados desejados.

## Compilar localmente

Requer **Java 17**, **Gradle 8.9** e **Android SDK 35**. Este repositório usa `gradle/actions/setup-gradle` no CI e não depende de `gradlew` / `gradle-wrapper.jar`:

```sh
sh tests/run.sh
gradle --no-daemon :app:assembleDebug
```

A compilação bem-sucedida cria `app/build/outputs/apk/debug/app-debug.apk`.

## Testes e limitações

`sh tests/run.sh` compila com `javac --release 17` e testa HEX, ZIP, extração segura, DEX íntegro/adulterado e inventário de APK sintético. Os testes de lógica Java **não equivalem** a um build Android ou teste de execução em um aparelho.

Ainda pendentes: parser AXML/ARSC, editor DEX/Smali, assinatura com chaves do próprio usuário, montagem de APK, editor HEX com escrita, busca recursiva, comparação de arquivos, SMB/SFTP, atualização de interface e testes Android com arquivos grandes. **Não afirmar compatibilidade completa com o MT Manager.**

## Estrutura

- `app/src/main/java/com/mc1h/openfilelab/` — implementação Java.
- `app/src/main/AndroidManifest.xml` e `app/src/main/res/` — configuração e recursos Android.
- `tests/` — testes Java executáveis sem Android SDK.
- `.github/workflows/build-android.yml` — CI com APK de depuração e hash.
- `mc1h_STATE.md` — decisões, verificações e limitações da fase anterior.
