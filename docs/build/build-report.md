# Relatório de build — ZyX Virtual 0.2.1-alpha.1

## Ambiente e validação local

Validação em 17/10/2026 no Sandbox ARM64 (`aarch64`), OpenJDK 21, Android SDK 36, Build Tools 36.0.0 e Gradle Wrapper 8.14.2. O Android Gradle Plugin fornece AAPT2 x86-64; o wrapper do projeto executa essa ferramenta de build no Sandbox ARM64. Isso não virtualiza o Android convidado.

```bash
python3 app/l10n/gen.py
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
scripts/check-engine-assets.sh
scripts/build-apk.sh testStandardDebugUnitTest testCloneDebugUnitTest \
  assembleStandardDebug assembleCloneDebug assembleStandardRelease
```

Resultado: build Gradle `BUILD SUCCESSFUL`; 28 testes Kotlin no total entre standard e clone, sem falhas/erros/skips; testes Python do empacotador passaram. `py_compile`, validação sintática dos scripts Shell, `scripts/check-engine-assets.sh` e `git diff --check` também passaram. O preflight encontrou os 70 arquivos do runtime QEMU.

## Mudanças da alpha

- Nova home nativa Compose inspirada na hierarquia visual do site (hero em gradiente, cards de recursos, passos de uso, navegação inferior), com textos pt-BR e conteúdo ZyX atualizado; não copia marca, links nem alegações antigas do AEmulator.
- A tela do guest usa controles com cor de marca. Ao encerrar, o aviso pode ser tocado para ler até 24 KiB finais de stdout/stderr do `qemu.log`; o usuário pode copiar o trecho ou exportar o arquivo por SAF. A exportação depende do destino escolhido e pode usar um provedor Drive se disponível.
- O status de saída, inclusive código 1, não determina a causa. O app ainda não recebeu um `qemu.log` real do Galaxy A07, portanto nenhuma causa foi diagnosticada nesta versão.

## Runtime QEMU no APK

O empacotador processou 94 pacotes ARM64 do repositório oficial Termux, fixados em `scripts/qemu-termux-runtime.lock.json`, com download total de cerca de 58,2 MiB. Cada `.deb` foi conferido por tamanho, metadados e SHA-256. A saída inclui QEMU 11.1.2 e 69 bibliotecas ELF requeridas. Os 70 arquivos listados no `assets/zyx-qemu/runtime.json` foram empacotados; o runtime é extraído para armazenamento privado antes da execução. Esta inspeção valida o conteúdo do APK, não a execução do binário em aparelho físico.

## Artefatos locais

| Variante | Arquivo | Tamanho | SHA-256 |
|---|---|---:|---|
| Standard debug — candidato de distribuição | `app/build/outputs/apk/standard/debug/app-standard-debug.apk` | 68.531.909 bytes | `5754512b8f468077d16b38aaad03be1180f6b21f9fcc690019514bf732a61f70` |
| Clone debug | `app/build/outputs/apk/clone/debug/app-clone-debug.apk` | 68.531.908 bytes | `9777d57724e7da8547c142da8ab900a328f60ff54a74e9bcbc758594b48e3206` |
| Standard release unsigned | `app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk` | 60.786.645 bytes | `cafeeac8df2d8f8f9b7a88c506cd16a4184be57b4ff05cabd03a416d1b3bb35a` |

A variante standard usa `applicationId` `app.zyx.virtual`, `versionCode` 103 e `versionName` `0.2.1-alpha.1`. O APK debug é assinado com chave de desenvolvimento; o SHA-256 do certificado (`5ac363a525a32ebaaaf664e5445f039fd81fe3595c451d297c3196bfc3b964f5`) foi comparado com o APK da prerelease `v0.2.0-alpha.1` e coincide, permitindo atualização sobre essa instalação sem desinstalar. Não é certificado de produção.

## Limites e publicação

O build local não foi instalado ou executado em aparelho; `adb devices` não listou dispositivo. O comportamento do seletor SAF/Drive e a exportação do log ainda precisam de teste no Android físico. O APK não contém a ROM `.zyxos` e o bootstrap disponível ainda não recebeu o overlay ZyX. Não se afirma boot funcional no Galaxy A07.

A CI do commit `c63730d` passou em todas as etapas, incluindo geração dos assets e builds standard debug, clone debug e standard release unsigned: [execução 39027031367](https://github.com/tzx-dev/ZyXVirtual/actions/runs/39027031367). A prerelease pública [`v0.2.1-alpha.1`](https://github.com/tzx-dev/ZyXVirtual/releases/tag/v0.2.1-alpha.1) foi publicada com o APK standard debug indicado nesta tabela; o SHA-256 do asset remoto confere com o arquivo local.
