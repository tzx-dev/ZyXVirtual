# Estado da refatoração do motor ZyX

## Alvo e estado da ROM

O objetivo é executar Android full-system ARM64, não simular uma tela. O AEmulator original usa QEMU user-mode para guests ARM32. O produto-alvo é `lineage_zyx_virtual`, baseado em LineageOS 23.2 / Android 16 e no alvo virtual `virtio_arm64only`, sem Google Apps. O overlay existe, mas ainda não foi compilado/aplicado dentro da imagem. O pacote `.zyxos` local é um bootstrap de uma VM upstream `lineage_virtio_arm64only`; o nome ZyX no manifesto não altera a identidade do Android interno.

A OTA física `laurel_sprout` do Mi A3 não é uma imagem de VM e não foi usada. No QEMU de host 8.2.2 com TCG, o bootstrap avançou pelo kernel, Android `init` e descompressão dos APEX; após 300 segundos não houve confirmação de zygote/framework. Isso não prova boot completo.

## Integração implementada no app `0.2.1-alpha.1`

- `scripts/prepare_qemu_runtime.py` baixa e verifica 94 pacotes Termux ARM64 do lockfile por SHA-256, extrai somente o executável QEMU 11.1.2 e sua closure ELF, e preserva avisos de licença. O runtime gerado contém 70 arquivos no manifesto: executável e 69 bibliotecas.
- `ZyxQemuAssetRuntime` lê o manifesto do APK, copia o runtime para armazenamento privado executável e verifica tamanho e SHA-256 de cada asset antes de iniciar. Essa conferência do pacote não prova que o ELF execute em Android físico.
- `ZyxQemuSystemProcess` inicia QEMU como processo ARM64 com `ProcessBuilder`, configura `LD_LIBRARY_PATH` para as bibliotecas privadas, monitora o processo e usa QMP para stop/reset. `ZyxSystemCommand` usa `virt`, TCG multi-thread, CPU `max`, discos virtio, firmware AAVMF, rede e VNC loopback.
- `ZyxOsVmStorage` resolve os caminhos do manifesto, valida os hashes das bases e provisiona cópias graváveis persistentes de `vda`, `vdb` e das variáveis UEFI. As imagens do pacote permanecem intactas.
- `ZyxVmActivity` e `ZyxVncView` apresentam o guest via VNC, com vídeo raw, toque e teclas. Ao terminar o processo, o banner agora abre as últimas 24 KiB de stdout/stderr em `qemu.log`; oferece cópia e exportação SAF. Código de saída 1 não é tratado como explicação da falha. Serial do guest, QMP e exceções pré-processo são canais separados.
- A home foi redesenhada em Compose com hierarquia inspirada no site (hero, cards, recursos, guia e navegação inferior), reaproveitando a paleta e as formas do tema Material 3 Expressive. O conteúdo antigo de AEmulator foi substituído por instruções ZyX em pt-BR.
- O caminho ativo é process-based, não JNI. A ABI/bridge C e o wrapper Kotlin mantidos em `native/qemu-system/` são experimentais e não são usados pelo launcher atual.

## Validação local

A alpha `0.2.1-alpha.1` (`versionCode` 103) compilou em Sandbox ARM64. A suíte executada para standard e clone totalizou 28 testes Kotlin, sem falhas, erros ou skips. Testes Python do empacotador, `py_compile`, verificações `sh -n`, preflight dos assets e `git diff --check` também passaram. O preflight encontrou os 70 arquivos do runtime.

O APK standard debug tem 68.531.909 bytes e SHA-256 `5754512b8f468077d16b38aaad03be1180f6b21f9fcc690019514bf732a61f70`. O certificado de desenvolvimento SHA-256 coincide com o APK público `v0.2.0-alpha.1`; não é assinatura de produção. Detalhes e hashes das variantes estão em [`../build/build-report.md`](../build/build-report.md).

## O que ainda falta validar

1. Instalar e executar o APK atualizado em Android físico, em especial no Galaxy A07 4G: boot, execução do QEMU Android-linkado, VNC, toque/teclado, QMP e persistência. Não havia dispositivo listado em `adb devices` nesta sessão.
2. Se o QEMU encerrar, exportar pelo banner o `qemu.log` real do aparelho. A causa do código 1 não pode ser determinada pelo screenshot ou pelo número sozinho; ainda não recebemos esse log.
3. Testar no aparelho o seletor SAF/Drive e a exportação do diagnóstico; o build local não valida esses caminhos interativos.
4. Avaliar memória, temperatura e desempenho TCG no Helio G99. A configuração atual força TCG; `/dev/kvm` não foi verificado no aparelho.
5. Atingir o framework Android com o bootstrap. O smoke test de host chegou ao `init`/APEX, mas não confirmou zygote.
6. Aplicar e compilar o overlay ZyX dentro de uma árvore LineageOS completa. Este Sandbox ARM64 não satisfaz os requisitos de build (host x86-64, 64 GiB RAM efetiva e 400 GiB livres).
7. Preparar assinatura de produção e autenticação criptográfica do manifesto `.zyxos`; os hashes atuais verificam integridade, não a identidade do publicador.

A alpha `0.2.1-alpha.1` é uma prévia experimental e não foi testada em aparelho. Consulte também [`../build/build-report.md`](../build/build-report.md), [`android-qemu-runtime-research.md`](android-qemu-runtime-research.md) e [`../roms/lineageos-23.2-virtual-bootstrap.md`](../roms/lineageos-23.2-virtual-bootstrap.md).
