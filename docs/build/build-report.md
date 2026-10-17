# Relatório de build — ZyX Virtual 0.2.2-alpha.1

## Ambiente e validação local

Validação em 17/10/2026 no Sandbox ARM64 (`aarch64`), OpenJDK 21, Android SDK 36, Build Tools 36.0.0 e Gradle Wrapper 8.14.2. A compilação usa o AAPT2 configurado pelo projeto para este ambiente ARM64.

Comandos executados:

```bash
python3 app/l10n/gen.py
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
scripts/build-apk.sh testStandardDebugUnitTest testCloneDebugUnitTest \
  assembleStandardDebug assembleCloneDebug assembleStandardRelease
```

Resultado: `BUILD SUCCESSFUL`; 30 testes Kotlin no total nas variantes standard e clone, sem falhas, erros ou skips; 2 testes Python passaram. Também passaram `py_compile`, validação sintática dos scripts Shell e `git diff --check`. Os 70 arquivos do runtime ARM64 foram lidos do APK debug e validados quanto a tamanho e SHA-256 conforme `runtime.json`.

## Mudanças nesta alpha

- A home foi reduzida à biblioteca compacta: cartão do pacote instalado e estado vazio/importação, sem a antiga landing page longa, blocos de recursos, guia e navegação inferior.
- A biblioteca, os botões, cartões e o painel de diagnóstico/log seguem a paleta azul `#3D5AFE`, superfícies arredondadas e textos pt-BR.
- O QMP usa um socket Unix curto em `cacheDir` (`qmp.sock`), evitando o limite de caminho de 108 bytes observado no diretório profundo do estado da VM. O teste de unidade verifica o caminho e limite construídos.
- O diálogo de logs pode apresentar as últimas 24 KiB do `qemu.log`; permite copiar ou exportar via SAF. Seleção de destino depende do usuário/provedor Android.

## Runtime QEMU incluído

O runtime ARM64 é QEMU system-mode 11.1.2 derivado de pacotes oficiais Termux fixados no lockfile. O manifesto no APK declara 70 arquivos (binário e bibliotecas); a inspeção local confirmou tamanho e SHA-256 de todos. Isso valida o empacotamento e integridade dos assets, não garante que o ELF execute em todo dispositivo Android.

## Artefatos locais

| Variante | Arquivo | Tamanho | SHA-256 |
|---|---|---:|---|
| Standard debug | `app/build/outputs/apk/standard/debug/app-standard-debug.apk` | 68.152.870 bytes | `037780c00be3f3a5b3cf258281dd0c66ee6eec4245a6eb0655a77d228217a00e` |
| Clone debug | `app/build/outputs/apk/clone/debug/app-clone-debug.apk` | 68.152.902 bytes | `3c87e305cbfecf75a500b84d0eed457461bf173c8a3996cf58d9bb3f50497b51` |
| Standard release unsigned | `app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk` | 60.751.845 bytes | `9dd83e0c3e86c83f949834eaad499ba1feacbe56e3b59ad2b73a86af424c4dea` |

A variante standard tem `applicationId` `app.zyx.virtual`, `versionCode` 104 e `versionName` `0.2.2-alpha.1`. APKs locais em `app/build/outputs/apk/`.

## Limites

O APK não foi instalado nem executado em Android físico nesta sessão. Portanto, QEMU no Android-linker, boot/zygote/framework, VNC, input, SAF/Drive, temperatura, desempenho TCG e acesso a KVM/AVF seguem sem validação no Galaxy A07 4G. O bootstrap permanece baseado em `lineage_virtio_arm64only`; não se afirma que o overlay `lineage_zyx_virtual` foi compilado/aplicado ou que a ROM ZyX inicializa completamente.
