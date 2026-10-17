# Estado da refatoração do motor ZyX

## Alvo e estado da ROM

O objetivo é executar Android full-system ARM64, não simular uma tela. O AEmulator original usa QEMU user-mode para guests ARM32. O produto-alvo é `lineage_zyx_virtual`, baseado em LineageOS 23.2 / Android 16 e no alvo virtual `virtio_arm64only`, sem Google Apps. O overlay existe, mas ainda não foi compilado/aplicado dentro da imagem. O pacote `.zyxos` local é um bootstrap de uma VM upstream `lineage_virtio_arm64only`; o nome ZyX no manifesto não altera a identidade do Android interno.

A OTA física `laurel_sprout` do Mi A3 não é uma imagem de VM e não foi usada. No QEMU de host 8.2.2 com TCG, o bootstrap avançou pelo kernel, Android `init` e descompressão dos APEX; após 300 segundos não houve confirmação de zygote/framework. Isso não prova boot completo.

## Integração implementada no app `0.2.0-alpha.1`

- `scripts/prepare_qemu_runtime.py` baixa e verifica 94 pacotes Termux ARM64 do lockfile por SHA-256, extrai somente o executável QEMU 11.1.2 e sua closure ELF, e preserva avisos de licença. O runtime gerado contém 70 arquivos no manifesto: executável (29,5 MiB) e 69 bibliotecas (36,2 MiB).
- `ZyxQemuAssetRuntime` lê o manifesto do APK, copia o runtime para armazenamento privado executável e verifica tamanho e SHA-256 de cada asset antes de iniciar. O APK debug foi aberto como ZIP e todos os hashes foram recalculados com sucesso.
- `ZyxQemuSystemProcess` inicia QEMU como processo ARM64 com `ProcessBuilder`, configura `LD_LIBRARY_PATH` para as bibliotecas privadas, monitora o processo e usa QMP para stop/reset. A linha de comando `ZyxSystemCommand` usa `virt`, TCG multi-thread, CPU `max`, discos virtio, firmware AAVMF, rede e VNC loopback.
- `ZyxOsVmStorage` resolve os caminhos do manifesto, valida os hashes das bases e provisiona cópias graváveis persistentes de `vda`, `vdb` e das variáveis UEFI. As imagens do pacote permanecem intactas.
- `ZyxVmActivity` é a tela dedicada do guest; `ZyxVncView` implementa o protocolo RFB básico de VNC, vídeo raw, toque e teclas. A tela principal só oferece a prévia se o manifesto do runtime estiver no APK.
- O caminho ativo é process-based, não JNI. A ABI/bridge C e o wrapper Kotlin mantidos em `native/qemu-system/` são experimentais e não são usados pelo launcher atual.

## Validação local

O APK standard debug foi compilado como `0.2.0-alpha.1` (`versionCode` 102), com 68.146.720 bytes e SHA-256 `63721bf332012025f0d954ba723a13452e195d815deed04a10e52ba94d5b902d`. A suíte local passou em 14 testes Kotlin (4 de pacote, 3 de armazenamento, 4 de comando e 3 de detecção OTA), sem falhas. Os testes Python do empacotador, `py_compile`, `sh -n`, preflight dos assets e `git diff --check` também passaram. A verificação do APK confirmou todos os hashes de runtime e 92 avisos de licença empacotados.

## O que ainda falta validar

1. Instalar e executar o APK em Android físico, em especial no Galaxy A07 4G: primeiro boot, execução do QEMU Android-linkado, conexão VNC, toque/teclado, QMP e persistência após reinício.
2. Avaliar memória, temperatura e desempenho TCG no Helio G99. A configuração atual força TCG; a presença de `/dev/kvm` no aparelho não foi verificada e não se deve anunciar aceleração KVM.
3. Atingir o framework Android com o bootstrap. O smoke test de host chegou ao `init`/APEX, mas não confirmou zygote.
4. Aplicar e compilar o overlay ZyX dentro de uma árvore LineageOS completa. Este sandbox ARM64 não satisfaz os requisitos de build (host x86-64, 64 GiB RAM efetiva e 400 GiB livres).
5. Preparar assinatura de produção e autenticação criptográfica do manifesto `.zyxos`; os hashes atuais verificam integridade, não a identidade do publicador.

O build mais recente é uma alpha experimental. A release pública anterior `v0.1.1-alpha.1` não contém o novo runtime; `0.2.0-alpha.1` ainda não foi publicada como release. Consulte também [`../build/build-report.md`](../build/build-report.md), [`android-qemu-runtime-research.md`](android-qemu-runtime-research.md) e [`../roms/lineageos-23.2-virtual-bootstrap.md`](../roms/lineageos-23.2-virtual-bootstrap.md).
