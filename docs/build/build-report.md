# Relatório de build — ZyX Virtual 0.2.3-alpha.1

## Ambiente e validação local

Validação em 17/10/2026 no Sandbox ARM64 (`aarch64`), OpenJDK 21, Android SDK 36, Build Tools 36.0.0 e Gradle Wrapper 8.14.2.

Comandos executados:

```bash
python3 scripts/prepare_qemu_runtime.py
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
scripts/build-apk.sh testStandardDebugUnitTest testCloneDebugUnitTest \
  assembleStandardDebug assembleCloneDebug assembleStandardRelease
scripts/check-engine-assets.sh
```

Resultado: `BUILD SUCCESSFUL`; 34 testes Kotlin nas variantes standard/clone, sem falhas, erros ou skips; 4 testes Python passaram. Também passaram `py_compile`, validação sintática dos scripts Shell, verificação de todos os 71 assets QEMU dentro do APK quanto a tamanho e SHA-256, e `git diff --check`.

Um QEMU system-mode instalado no Sandbox foi iniciado com servidor VNC e `-L` apontando para os novos dados até ser encerrado pelo timeout de 3 segundos; não houve erro de keymap. Esse smoke test exercita QEMU do host, não o binário Termux Android nem o APK no Galaxy A07.

A CI do commit `f95f841429327c270426b3e1542021bdc4d93c3f` passou em [GitHub Actions](https://github.com/tzx-dev/ZyXVirtual/actions/runs/39053613140). O SHA-256 do certificado de desenvolvimento deste APK é `5ac363a525a32ebaaaf664e5445f039fd81fe3595c451d297c3196bfc3b964f5`, igual ao APK `v0.2.2-alpha.1`, permitindo atualização assinada pelo mesmo debug key.

## Correções desta alpha

- O processo QEMU substitui o caminho profundo antigo por `cacheDir/zyx-qmp-<pid>.sock` e confirma limite inferior a 108 bytes. Há teste para a função que cria exatamente esse socket.
- O QEMU recebe `-L <runtime>/share/qemu`; o runtime agora inclui `keymaps/en-us`, ausente no conjunto anterior de assets. A keymap foi extraída do source oficial `qemu-11.1.2.tar.xz`, cujo hash é verificado pelo lockfile; o próprio arquivo `en-us` também tem SHA-256 fixado.
- Cada boot inicia um `qemu.log` limpo em vez de acrescentar a saída nova ao fim do log anterior. Isso impede que mensagens de boots passados sejam apresentadas como pertencentes à execução atual.
- Mantida a biblioteca compacta e o estilo azul `#3D5AFE` com formas arredondadas e interface pt-BR.

## Runtime QEMU incluído

QEMU system-mode ARM64 11.1.2 é preparado a partir de pacotes Termux fixados no lockfile. O manifesto `runtime.json` lista 71 arquivos: binário, bibliotecas necessárias e keymap. O APK foi aberto e todos os tamanhos/hashes do manifesto foram revalidados localmente.

## Artefatos locais

| Variante | Arquivo | Tamanho | SHA-256 |
|---|---|---:|---|
| Standard debug | `app/build/outputs/apk/standard/debug/app-standard-debug.apk` | 68.159.436 bytes | `7bf354180b1ad39a1a8e93bc996397d4f73372e25a57a6b1cbd74cea88dc865c` |
| Clone debug | `app/build/outputs/apk/clone/debug/app-clone-debug.apk` | 68.159.448 bytes | `fdaefb26296576cfd81a8ba76a46ab7efed2198e073fbf00c0536caf3845a8db` |
| Standard release unsigned | `app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk` | 60.757.711 bytes | `8c667ee09fbc0025565be4db6674e7367cf94f187f77936b7637513b30a3b3d0` |

A variante standard usa `applicationId` `app.zyx.virtual`, `versionCode` 105 e `versionName` `0.2.3-alpha.1`. O APK debug usa o certificado de desenvolvimento usado na prerelease anterior; não é assinatura de produção.

## Limites

O build não foi instalado nem executado em telefone. Testes locais validam a geração dos argumentos QEMU, o limite do caminho, os arquivos e os hashes; eles não demonstram execução de QEMU/VNC Android-linkado ou boot completo. Galaxy A07 4G, VNC e input em hardware, SAF/Drive, TCG, KVM/AVF e temperatura continuam por validar. O `.zyxos` ainda é bootstrap upstream e não contém o overlay `lineage_zyx_virtual` aplicado.
