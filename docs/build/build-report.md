# Relatório de build — ZyX Virtual 0.2.0-alpha.1

## Ambiente e comando

Validação em 17/10/2026 no Sandbox ARM64 (`aarch64`), OpenJDK 21, Android SDK 36, Build Tools 36.0.0 e Gradle Wrapper 8.14.2. O Android Gradle Plugin fornece AAPT2 x86-64; o wrapper do projeto executa essa ferramenta de build no Sandbox ARM64. Isso não virtualiza o Android convidado.

```bash
python3 scripts/prepare_qemu_runtime.py
scripts/check-engine-assets.sh
scripts/build-apk.sh testStandardDebugUnitTest assembleStandardDebug
```

## Runtime QEMU no APK

O empacotador processou 94 pacotes ARM64 do repositório oficial Termux, fixados em `scripts/qemu-termux-runtime.lock.json`, com download total de 58,2 MiB. Cada `.deb` foi conferido por tamanho, metadados e SHA-256. A saída inclui QEMU 11.1.2 (29,5 MiB), 69 bibliotecas ELF requeridas (36,2 MiB) e avisos de licença localizados. A proveniência e os links estáveis da receita estão em [`../architecture/android-qemu-runtime-research.md`](../architecture/android-qemu-runtime-research.md).

O APK foi inspecionado como ZIP: os 70 arquivos listados em `assets/zyx-qemu/runtime.json` existem e coincidem com os tamanhos e SHA-256 do manifesto; foram encontrados 92 avisos de licença. O runtime é extraído pelo app para armazenamento privado antes da execução. Isso valida o empacotamento, não a execução do binário em um Android físico.

## Resultado local

`BUILD SUCCESSFUL`. Os 14 testes Kotlin passaram sem falhas, erros ou skips: `ZyxOsPackageTest` (4), `ZyxOsVmStorageTest` (3), `ZyxSystemCommandTest` (4) e `DeviceOtaProbeTest` (3). Os testes Python do empacotador passaram; `py_compile`, `sh -n`, o preflight `scripts/check-engine-assets.sh` e `git diff --check` também passaram. O APK AAPT2 reportou o pacote `app.zyx.virtual`; o `BuildConfig` compilado contém `VERSION_CODE = 102` e `VERSION_NAME = 0.2.0-alpha.1`.

| Variante | Arquivo | Tamanho |
|---|---|---:|
| Standard debug, assinatura de desenvolvimento | `app/build/outputs/apk/standard/debug/app-standard-debug.apk` | 68.146.720 bytes (65,0 MiB) |

SHA-256 do APK standard debug: `63721bf332012025f0d954ba723a13452e195d815deed04a10e52ba94d5b902d`.

O APK foi compilado localmente; não foi instalado nem executado em telefone. Não há teste ADB do Galaxy A07. A CI atualizada para empacotar o runtime será acompanhada após a publicação do commit. A release pública `v0.1.1-alpha.1` continua sendo o APK anterior, sem esse runtime; `0.2.0-alpha.1` ainda não foi publicada como release. O APK debug não tem assinatura de produção.

## Guest e limites

O app agora contém um caminho de boot process-based com QEMU system-mode ARM64, QMP, VNC local, controle básico de vídeo/entrada e cópias graváveis persistentes dos discos/NVRAM. Isso ainda não confirma que o APK inicialize o guest: não houve teste em aparelho e TCG pode ser lento no Helio G99. A bridge JNI permanece experimental e fora desse caminho.

A ROM `lineage_zyx_virtual` ainda não foi compilada. O `.zyxos` disponível é apenas um bootstrap upstream da VM `lineage_virtio_arm64only`, com `customProductOverlayApplied: false`; a imagem interna continua upstream. No smoke test em QEMU de host com `-cpu max`, o guest chegou ao `init` e à descompressão de APEX, mas não confirmou zygote/framework após 300 segundos. A OTA `laurel_sprout` do Mi A3 é firmware de telefone e não foi usada. O build completo requer host Linux x86-64, 64 GiB de RAM efetiva e 400 GiB livres; este Sandbox ARM64 não atende aos requisitos.

O pacote bootstrap foi enviado ao Drive privado e o tamanho/MD5 remoto conferem com o arquivo local. O APK não inclui a ROM; ela continua sendo selecionada separadamente pelo SAF.
