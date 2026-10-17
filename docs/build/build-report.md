# Relatório de build — ZyX Virtual 0.1.1-alpha.1

## Ambiente e execução

Validação em 17/10/2026 no Sandbox ARM64 (`aarch64`), OpenJDK 21, Android SDK 36, Build Tools 36.0.0 e Gradle Wrapper 8.14.2. O Android Gradle Plugin fornece AAPT2 x86-64; `scripts/build-apk.sh` usa o launcher ARM64/QEMU apenas para executar essa ferramenta de build. Isso não virtualiza o Android convidado.

## Resultado

Comando executado:

```bash
scripts/build-apk.sh \
  testStandardDebugUnitTest \
  assembleStandardDebug \
  assembleCloneDebug \
  assembleStandardRelease
```

Resultado: `BUILD SUCCESSFUL`. Os testes unitários tiveram **10 testes, 0 falhas, 0 erros e 0 ignorados**: `DeviceOtaProbeTest` (3), `ZyxOsPackageTest` (4) e `ZyxSystemCommandTest` (3). O gerador pt-BR foi executado e os recursos padrão e `values-pt-rBR` foram comparados: 197 strings sincronizadas; `app_name` permanece corretamente não traduzível. A bridge JNI C também passou anteriormente em checagem de sintaxe no host; scripts Shell, Python, manifesto JSON e XML passaram por validação estática.

O APK standard debug foi verificado com `apksigner`; a assinatura V2 de desenvolvimento é válida. O APK release é unsigned porque não há keystore de publicação. A CI do GitHub validou sincronização das traduções, sintaxe C e compilação Android com sucesso no commit `8c9951f` ([execução 39017408540](https://github.com/tzx-dev/ZyXVirtual/actions/runs/39017408540)).

## APKs gerados

| Variante | Arquivo | Tamanho |
|---|---|---:|
| Standard debug, alpha candidata, assinada para desenvolvimento | `app/build/outputs/apk/standard/debug/app-standard-debug.apk` | 31.280.402 bytes |
| Clone debug, assinada para desenvolvimento | `app/build/outputs/apk/clone/debug/app-clone-debug.apk` | 31.280.334 bytes |
| Standard release, sem assinatura | `app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk` | 24.700.433 bytes |

SHA-256 do APK standard debug: `fa3d9f77147f4062e43341ddb8f74b1cfe439c0d0b9dece7332457011d43f59e`.

A versão compilada é `0.1.1-alpha.1` (`versionCode` 101), com `applicationId` `app.zyx.virtual`, ABI `arm64-v8a` e `minSdk` 26. O APK é uma compilação de depuração; não é assinado para publicação comercial. A versão foi publicada como [pré-lançamento público v0.1.1-alpha.1](https://github.com/tzx-dev/ZyXVirtual/releases/tag/v0.1.1-alpha.1), contendo o APK standard debug. Consulte [as notas da release](../releases/v0.1.1-alpha.1.md).

Não instalei o APK nem testei em telefone físico; não há aparelho ADB conectado. O Galaxy A07 não foi validado.

## Motor e ROM

A interface e os recursos padrão estão em português brasileiro. A tela principal aceita somente o pacote ZyX OS `.zyxos`, selecionado pelo seletor SAF; o provedor Google Drive pode ser escolhido no celular quando o usuário estiver autenticado no aplicativo Drive. O instalador verifica identidade, versão, arquitetura, política sem Google Apps, caminhos do ZIP e SHA-256. O manifesto interno ainda não tem assinatura criptográfica do publicador: os hashes detectam corrupção/divergência, mas não autenticam a origem. O boot permanece desabilitado.

O construtor testável QEMU system-mode e a bridge JNI/ABI existem em código-fonte, mas a bridge não foi compilada com Android NDK. Ainda faltam o adaptador que implementa a ABI, `libqemu-system-aarch64.so`, renderer de vídeo/entrada, disco gravável e teste de inicialização pelo app.

Na data desta release, o produto virtual LineageOS 23.2 / Android 16 `virtio_arm64only` **não tinha sido construído**. A OTA de 1.169.332.586 bytes baixada para `laurel_sprout` é firmware de telefone Xiaomi Mi A3, não imagem de VM; permanece fora do Git e não foi usada como guest. A documentação AOSP pede host x86-64, 400 GB livres e 64 GB RAM para checkout/build; este Sandbox ARM64 não atende a esses requisitos.

## Atualização técnica — 17/10/2026

Depois da release, foi preparado localmente `build/zyxos/ZyXOS-lineage-23.2-20260917-bootstrap-aavmf.zyxos`, uma VM virtual upstream LineageOS 23.2 com firmware AAVMF. Os hashes dos oito arquivos foram verificados; SHA-256 do pacote: `9d5643729dff4bdf7ad489ec48bb7718702db49c4eab01659cae9ece9eae6247`. Ele continua sendo **bootstrap**, com `customProductOverlayApplied: false`; não é o produto final `lineage_zyx_virtual` e não foi instalado pelo app nem enviado ao Drive.

O smoke test em QEMU de host com CPU `max` avançou pelo kernel, Android `init` e descompressão de APEX; após 300 segundos não confirmou zygote/framework. Um segundo teste com a sintaxe exata do builder Kotlin (`-blockdev`, `bootindex`, dois discos, AAVMF e virtio) rodou por 60 s: o kernel reconheceu `vda`, `vdb`, GPU e teclado sem erro de inicialização. `cortex-a57` causou panic no módulo `virt_wifi.ko`, por isso o builder Kotlin escolhe `max`, dá prioridade de boot a `vda` e inclui o segundo disco e o par UEFI AAVMF. O boot do guest pelo APK segue desabilitado; faltam backend QEMU system-mode Android, adapter JNI, renderer/input, persistência e validação física no Galaxy A07. O relatório técnico completo está em [`docs/roms/lineageos-23.2-virtual-bootstrap.md`](../roms/lineageos-23.2-virtual-bootstrap.md).

Nesta revisão, `scripts/build-apk.sh testStandardDebugUnitTest assembleStandardDebug` concluiu com sucesso: 11 testes Kotlin passaram e o APK standard debug foi gerado (31 MB; SHA-256 `4a83986f29bbacb316978873e151e1d75d46c402fac6c51ae8a291592d4a1550`). Os testes Python do empacotador passaram 2/2. A validação de CI do commit com estas mudanças ainda será executada após o push.

## Avisos

O AGP emite aviso sobre `android:extractNativeLibs` e sobre o override experimental AAPT2. Há APIs Compose/Android depreciadas herdadas do upstream. Consulte `docs/architecture/engine-refactor-status.md` e `docs/architecture/qemu-system-and-build-constraints.md` para os bloqueios e referências.
