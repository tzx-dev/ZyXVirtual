# ZyX Virtual

ZyX Virtual é um fork experimental do [AEmulator](https://github.com/uxazu/AEmulator), destinado a um único guest Android full-system ARM64. O alvo final é **ZyX OS**, baseado em LineageOS 23.2 / Android 16, `virtio_arm64only`, sem Google Apps.

## Estado — `0.2.7-alpha.1` (prévia experimental)

- A tela inicial agora é uma biblioteca compacta: pacote ZyX OS instalado ou estado vazio com ação para importar. A landing page extensa foi removida.
- Cartões, botões e painel de logs/diagnóstico usam azul `#3D5AFE`, bordas arredondadas e interface localizada em Português Brasileiro.
- Corrigido o caminho longo do socket QMP: o processo usa um socket Unix curto sob `cacheDir`, não o caminho profundo sob o estado da VM. Mensagens de boots anteriores não são mais misturadas: cada inicialização recria `qemu.log`.
- Incluído o keymap VNC `en-us` correspondente ao QEMU 11.1.2 e passado o diretório de dados explicitamente via `-L`; o keymap é fixado por SHA-256 e faz parte dos assets verificados.
- O diálogo de diagnóstico pode exibir as últimas 24 KiB do `qemu.log`, copiar ou exportar via SAF para um destino selecionado pelo usuário (incluindo provedor Drive disponível no aparelho).
- O instalador SAF aceita `.zyxos` e verifica manifesto, caminhos, identidade e SHA-256. Hashes detectam corrupção, mas ainda não autenticam o publicador.
- O APK debug inclui runtime QEMU system-mode ARM64 11.1.2 baseado em pacotes Termux fixados. O runtime é extraído para área privada e conferido antes de iniciar. Isso não comprova compatibilidade do binário em todo Android físico.
- O launcher process-based usa TCG, QMP e VNC; armazenamento provisiona cópias graváveis persistentes dos discos e variáveis UEFI, sem modificar as bases.
- O launcher agora reproduz o `RNGDevice=true` do pacote UTM upstream com `virtio-rng-pci`, evitando bloqueios de keystore2/vold na criação das chaves do usuário 0.
- O cliente VNC envia toque e teclado por uma fila dedicada, sem bloquear a thread de UI em `flush()`; o ponteiro principal é preservado durante gestos multi-touch.
- O pipeline de ROM exige APEX compilados no `PRODUCT_OUT` antes de empacotar um `.zyxos`; APEX pertencem à imagem/estado do guest, não ao APK.
- **Limite importante:** esta versão foi compilada e testada no Sandbox; não foi instalada/executada no Galaxy A07 4G. O boot completo do guest, VNC/input em aparelho, SAF/Drive real, desempenho TCG e KVM/AVF seguem sem validação.
- O `.zyxos` disponível ainda é bootstrap upstream `lineage_virtio_arm64only`. O overlay `lineage_zyx_virtual` não foi compilado/aplicado e não se afirma inicialização completa do Android convidado.

A suíte local v0.2.3-alpha.1 passou em 34 testes Kotlin (standard/clone) e 4 testes Python. Os 71 assets do runtime, incluindo keymap, passaram a verificação de tamanho/SHA-256. A CI remota do commit f95f841 passou. O QEMU e a ROM ainda não foram testados no Galaxy A07 4G. Esta versão está na [prerelease v0.2.3-alpha.1](https://github.com/tzx-dev/ZyXVirtual/releases/tag/v0.2.3-alpha.1). APKs e hashes estão no [relatório de build](docs/build/build-report.md); detalhes no [estado da engine](docs/architecture/engine-refactor-status.md).

## Estrutura

```text
ZyXVirtual/
├── app/                         # app, instalador .zyxos, launcher QEMU e VNC
├── engine/                      # contratos e desenho do backend full-system
├── native/qemu-system/          # bridge experimental, não usada pelo launcher ativo
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

O empacotador verifica os arquivos `.deb` pelo lockfile e SHA-256; o APK debug fica em `app/build/outputs/apk/standard/debug/app-standard-debug.apk`. O GitHub Actions executa o empacotador antes de compilar os APKs.

## Build da ROM virtual

```bash
mkdir -p /caminho/para/lineage-23.2
scripts/check-zyx-build-host.sh /caminho/para/lineage-23.2
LINEAGE_DIR=/caminho/para/lineage-23.2 scripts/build-zyx-guest.sh
```

O preflight exige Linux x86-64, ao menos 64 GiB de RAM efetiva e 400 GiB livres. A árvore LineageOS completa e o produto `lineage_zyx_virtual` ainda não foram compilados; este Sandbox ARM64 não atende aos requisitos. Veja [estado da engine/ROM](docs/architecture/engine-refactor-status.md) e [bootstrap LineageOS 23.2](docs/roms/lineageos-23.2-virtual-bootstrap.md).

## GitHub e licenças

O código está no repositório público `tzx-dev/ZyXVirtual`. O runtime QEMU e dependências mantêm suas licenças próprias; proveniência, lockfile e avisos estão documentados em [`docs/architecture/android-qemu-runtime-research.md`](docs/architecture/android-qemu-runtime-research.md). O APK não contém a ROM `.zyxos`; ela é instalada separadamente via SAF.
