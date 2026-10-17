# ZyX Virtual

ZyX Virtual é um fork experimental do [AEmulator](https://github.com/uxazu/AEmulator), destinado a um único guest: **ZyX OS**, que será baseado no produto virtual ARM64 `virtio_arm64only` da LineageOS 23.2 / Android 16, sem Google Apps.

## Estado real — build `0.2.1-alpha.1` (prévia)

- A tela principal foi redesenhada em Compose a partir da linguagem visual do site: hero em gradiente, cards tonais, recursos, passos de uso e navegação inferior por seções. O conteúdo antigo de AEmulator/Android clássico não foi copiado; a interface e os novos textos permanecem em Português Brasileiro.
- A tela do guest agora usa cor de marca nos controles. Se o processo QEMU terminar, o aviso pode ser tocado para ver as últimas 24 KiB de `qemu.log`, copiar o texto ou exportar o arquivo por SAF para um destino escolhido pelo usuário (inclusive um provedor como Google Drive, se disponível). Código 1 é apenas status de saída; a causa ainda depende do log real do aparelho.
- O instalador SAF aceita pacotes `.zyxos`, incluindo arquivos escolhidos pelo provedor Google Drive do próprio Android. A ROM não é embutida no APK; o app não incorpora credenciais Google.
- O instalador mantém o pacote em armazenamento privado, verifica manifesto, caminhos, identidade e SHA-256 de cada arquivo. Hashes detectam corrupção, mas não autenticam o publicador: o manifesto ainda não tem assinatura criptográfica.
- O APK de desenvolvimento inclui um runtime QEMU system-mode AArch64 baseado nos pacotes oficiais Termux, versão QEMU 11.1.2. `scripts/prepare_qemu_runtime.py` baixa 94 pacotes com versões/SHA-256 fixados, extrai o executável e sua closure ELF (70 arquivos no manifesto: binário e bibliotecas) e inclui avisos de licença. O app copia esses assets para área executável privada e confere tamanho e hash antes de iniciar.
- O launcher atual é process-based (não JNI): `ZyxQemuSystemProcess` inicia QEMU com TCG, QMP e VNC local; `ZyxVmActivity` e `ZyxVncView` oferecem tela, toque e teclado; `ZyxOsVmStorage` cria cópias graváveis persistentes dos discos e das variáveis UEFI sem modificar a base do pacote. A bridge JNI/ABI antiga permanece experimental e não é usada por esse caminho.
- **Ainda não houve inicialização do APK atualizado em aparelho Android.** A nova tela e a exportação de logs foram compiladas/testadas no Sandbox, mas o guest não foi validado no Galaxy A07. Não alegamos que o Android convidado inicia; o TCG pode ser lento e KVM não foi confirmado no telefone.
- O `.zyxos` disponível é apenas um bootstrap upstream `lineage_virtio_arm64only`; o overlay `lineage_zyx_virtual` não foi compilado/aplicado. O smoke test em QEMU de host chegou ao `init` e à descompressão de APEX, mas não confirmou zygote/framework em 300 s. A OTA de telefone `laurel_sprout` do Mi A3 não foi usada como guest.
- O build candidato passou em 28 testes Kotlin nas variantes standard/clone, nos testes Python do empacotador e na conferência local dos assets. O APK standard debug final tem 68.531.909 bytes; SHA-256 `5754512b8f468077d16b38aaad03be1180f6b21f9fcc690019514bf732a61f70`. Veja o [relatório de build](docs/build/build-report.md).

A prerelease pública anterior é [`v0.2.0-alpha.1`](https://github.com/tzx-dev/ZyXVirtual/releases/tag/v0.2.0-alpha.1). O APK `0.2.1-alpha.1` usa a mesma assinatura de desenvolvimento, para permitir atualização sobre a versão anterior; não é uma chave de produção. O pacote bootstrap privado foi enviado ao Drive e tamanho/MD5 remoto conferem; ele não é uma ROM ZyX final.

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
