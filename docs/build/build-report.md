# Relatório de build — ZyX Virtual 0.1.0-dev

## Ambiente e execução

Validação em 17/10/2026 no sandbox ARM64 (`aarch64`), OpenJDK 21, Android SDK 36, Build Tools 36.0.0 e Gradle Wrapper 8.14.2. O Android Gradle Plugin fornece AAPT2 x86_64, então `scripts/build-apk.sh` usa o launcher AAPT2 ARM64/QEMU apenas para a ferramenta de build. Isso não virtualiza o guest Android.

## Resultado

Comando executado:

```bash
scripts/build-apk.sh \
  testStandardDebugUnitTest \
  assembleStandardDebug \
  assembleCloneDebug \
  assembleStandardRelease
```

Resultado: `BUILD SUCCESSFUL`. Os testes unitários tiveram **10 testes, 0 falhas, 0 erros e 0 ignorados**: `DeviceOtaProbeTest` (3), `ZyxOsPackageTest` (4) e `ZyxSystemCommandTest` (3). A bridge JNI C também passou em checagem de sintaxe do host com `gcc -std=c11 -Wall -Wextra -Werror -fsyntax-only`; scripts Shell, Python, manifesto JSON e XML de strings passaram por validação estática.

O APK debug foi verificado com `apksigner` usando assinatura de desenvolvimento V2. A variante release é unsigned porque não há keystore de publicação. O workflow GitHub valida adicionalmente a sintaxe C da bridge antes de compilar APKs.

## APKs gerados

| Variante | Arquivo | Tamanho |
|---|---|---:|
| Standard debug, assinado para desenvolvimento | `app/build/outputs/apk/standard/debug/app-standard-debug.apk` | 31.982.276 bytes |
| Clone debug, assinado para desenvolvimento | `app/build/outputs/apk/clone/debug/app-clone-debug.apk` | 31.981.952 bytes |
| Standard release, sem assinatura | `app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk` | 24.696.985 bytes |

Não instalei o APK nem testei num Galaxy A07; não há aparelho ADB conectado.

## Motor e ROM

O app agora oferece somente o pacote ZyX OS `.zyxos`, importado pelo seletor SAF (incluindo Drive no celular quando o provedor está autenticado). A instalação valida identidade, versão, arquitetura, política sem GApps, paths do ZIP e SHA-256 de cada payload. O manifesto interno **não tem assinatura criptográfica do publicador**, portanto os hashes detectam corrupção mas ainda não autenticam a origem. O boot fica desabilitado.

O construtor QEMU system-mode é testado, e a bridge JNI/ABI ZyX existe em fonte, mas a bridge não foi compilada com Android NDK; o adaptador QEMU, `libqemu-system-aarch64.so`, renderer VNC/Surface, entrada, disco overlay e boot continuam ausentes. `scripts/check-engine-assets.sh` informa `PENDING` para esse runtime.

O produto LineageOS 23.2/Android 16 virtual `virtio_arm64only` ainda não foi construído: não há árvore LineageOS completa nem pacote `.zyxos`. A OTA de 1.169.332.586 bytes baixada para `laurel_sprout` é firmware de telefone Mi A3, não guest; permanece fora do Git pelo `.gitignore` e não foi enviada ao Drive. O build completo AOSP requer segundo documentação host x86-64, 400 GB livres e 64 GB RAM; o sandbox ARM64 tem aproximadamente 35 GB livres e 11 GiB RAM.

## Avisos

O AGP emite aviso sobre `android:extractNativeLibs` usado para extrair os executáveis herdados e sobre o override experimental AAPT2. Há APIs Compose/Android depreciadas herdadas do upstream. Consulte `docs/architecture/engine-refactor-status.md` e `docs/architecture/qemu-system-and-build-constraints.md` para os bloqueios e referências.
