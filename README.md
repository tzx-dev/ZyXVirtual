# ZyX Virtual

ZyX Virtual é um fork experimental do [AEmulator](https://github.com/uxazu/AEmulator), destinado a um único guest: **ZyX OS**, que será baseado no produto virtual ARM64 `virtio_arm64only` da LineageOS 23.2 / Android 16, sem Google Apps.

## Estado real — código `0.2.0-alpha.1`

- O instalador SAF aceita pacotes `.zyxos`, incluindo arquivos escolhidos pelo provedor Google Drive do próprio Android. A ROM não é embutida no APK; o app não incorpora credenciais Google.
- O instalador mantém o pacote em armazenamento privado, verifica manifesto, caminhos, identidade e SHA-256 de cada arquivo. Hashes detectam corrupção, mas não autenticam o publicador: o manifesto ainda não tem assinatura criptográfica.
- O APK de desenvolvimento agora inclui um runtime QEMU system-mode AArch64 baseado nos pacotes oficiais Termux, versão QEMU 11.1.2. `scripts/prepare_qemu_runtime.py` baixa 94 pacotes com versões/SHA-256 fixados, extrai o executável e sua closure ELF (70 arquivos no manifesto: binário e bibliotecas) e inclui avisos de licença. O app copia esses assets para área executável privada e confere tamanho e hash antes de iniciar.
- O launcher atual é process-based (não JNI): `ZyxQemuSystemProcess` inicia QEMU com TCG, QMP e VNC local; `ZyxVmActivity` e `ZyxVncView` oferecem tela, toque e teclado; `ZyxOsVmStorage` cria cópias graváveis persistentes dos discos e das variáveis UEFI sem modificar a base do pacote. A bridge JNI/ABI antiga permanece experimental e não é usada por esse caminho.
- A linha de boot do app está integrada e o botão de prévia aparece quando o runtime está presente. **Ainda não houve inicialização do APK em aparelho Android**, portanto não afirmamos que o guest inicializa ou é utilizável no Galaxy A07. O TCG pode ser lento; desempenho e eventual acesso a KVM não foram validados.
- O `.zyxos` disponível é apenas um bootstrap upstream `lineage_virtio_arm64only`; o overlay `lineage_zyx_virtual` não foi compilado/aplicado. O smoke test em QEMU de host chegou ao `init` e à descompressão de APEX, mas não confirmou zygote/framework em 300 s. A OTA de telefone `laurel_sprout` do Mi A3 não foi usada como guest.
- A validação local atual passou em 14 testes Kotlin, nos testes Python do empacotador e na conferência de SHA-256 de todos os assets dentro do APK. O APK standard debug de `0.2.0-alpha.1` tem 68.146.720 bytes; veja [relatório de build](docs/build/build-report.md).

A prerelease pública atual é [`v0.2.0-alpha.1`](https://github.com/tzx-dev/ZyXVirtual/releases/tag/v0.2.0-alpha.1), com o APK standard debug e notas de teste/limitações. A release anterior [`v0.1.1-alpha.1`](https://github.com/tzx-dev/ZyXVirtual/releases/tag/v0.1.1-alpha.1) não inclui o runtime QEMU. O APK alpha usa a mesma assinatura debug da versão anterior para permitir atualização; não é uma chave de produção. O pacote bootstrap privado foi enviado ao Drive e tamanho/MD5 remoto conferem; ele não é uma ROM ZyX final.

## Estrutura

```text
ZyXVirtual/
├── app/                         # app, instalador .zyxos, launcher QEMU e VNC
├── engine/                      # contratos e desenho do backend full-system
├── native/                      # fontes nativas herdadas e ABI JNI experimental
├── guest/zyx-os/                # overlay LineageOS virtual lineage_zyx_virtual
├── scripts/                     # build APK/guest, pacote e verificações
├── docs/architecture/           # arquitetura, fontes e status do backend/guest
├── docs/build/                  # resultados de build/teste
├── docs/roms/                   # análise da OTA Mi A3 e do bootstrap virtual
└── .github/workflows/           # CI Android
```

## Build do app

```bash
export ANDROID_HOME=/caminho/para/android-sdk
python3 scripts/prepare_qemu_runtime.py
scripts/check-engine-assets.sh
scripts/build-apk.sh testStandardDebugUnitTest assembleStandardDebug
```

O empacotador de runtime verifica os arquivos `.deb` pelo lockfile e SHA-256; o download total atual é de cerca de 58,2 MiB. O APK debug fica em `app/build/outputs/apk/standard/debug/app-standard-debug.apk`. No Sandbox ARM64, o wrapper AAPT2 usa QEMU apenas para a ferramenta de build x86-64; isso não virtualiza o Android convidado. O GitHub Actions executa o empacotador antes de compilar os APKs.

## Build da ROM virtual

```bash
mkdir -p /caminho/para/lineage-23.2
scripts/check-zyx-build-host.sh /caminho/para/lineage-23.2
LINEAGE_DIR=/caminho/para/lineage-23.2 scripts/build-zyx-guest.sh
```

O preflight exige Linux x86-64, pelo menos 64 GiB de RAM efetiva (incluindo limites cgroup) e 400 GiB livres. A árvore LineageOS completa e o produto `lineage_zyx_virtual` ainda não foram compilados nesta sessão; o Sandbox ARM64 não atende aos requisitos. Veja [`docs/architecture/engine-refactor-status.md`](docs/architecture/engine-refactor-status.md) e [`docs/roms/lineageos-23.2-virtual-bootstrap.md`](docs/roms/lineageos-23.2-virtual-bootstrap.md).

## GitHub e licenças

O código está no repositório público `tzx-dev/ZyXVirtual`. O QEMU e suas dependências mantêm as licenças próprias; proveniência, lockfile, referências da receita Termux e avisos incluídos estão documentados em [`docs/architecture/android-qemu-runtime-research.md`](docs/architecture/android-qemu-runtime-research.md). O APK não contém a ROM `.zyxos`; ela é instalada separadamente via SAF.
