# Estado da refatoração do motor ZyX

## Alvo e estado da ROM

O objetivo é executar Android full-system ARM64, não simular uma tela. O produto-alvo é `lineage_zyx_virtual`, baseado em LineageOS 23.2 / Android 16 e no alvo virtual `virtio_arm64only`, sem Google Apps. O pacote `.zyxos` disponível continua sendo um bootstrap upstream `lineage_virtio_arm64only`; o overlay ZyX ainda não foi compilado/aplicado. O nome ZyX no manifesto não altera a identidade do Android interno.

A OTA física `laurel_sprout` do Mi A3 não é imagem de VM e não foi usada. Em smoke test de host QEMU/TCG, o bootstrap avançou pelo kernel, `init` e descompressão de APEX; não houve confirmação de zygote/framework. Isso não prova boot completo.

## Integração no app `0.2.2-alpha.1`

- `scripts/prepare_qemu_runtime.py` prepara QEMU 11.1.2 system-mode ARM64 e sua closure ELF a partir de pacotes Termux fixados. O runtime incluído tem 70 arquivos declarados no `runtime.json`; o app extrai para área privada e valida tamanho/SHA-256 antes de executar.
- `ZyxQemuSystemProcess` inicia QEMU como processo ARM64, configura bibliotecas privadas, monitora processo e controla ciclo de vida por QMP. `ZyxSystemCommand` configura `virt`, TCG, CPU, discos virtio, firmware AAVMF, rede e VNC loopback.
- O socket QMP agora fica em `cacheDir` em caminho curto (`qmp.sock`), reduzindo o risco de exceder o limite Unix de 108 bytes observado no antigo diretório profundo da VM. O teste Kotlin cobre o caminho produzido e o limite. Ainda não foi medido em dispositivo físico.
- `ZyxOsVmStorage` valida arquivos-base do manifesto e provisiona cópias graváveis persistentes de `vda`, `vdb` e variáveis UEFI; as imagens do pacote permanecem imutáveis.
- `ZyxVmActivity` e `ZyxVncView` fornecem vídeo VNC, toque e teclas. O banner de encerramento abre até 24 KiB finais de stdout/stderr em `qemu.log`, com copiar/exportar SAF. O código de saída não determina sozinho a causa.
- A tela principal foi compactada em uma biblioteca, removendo a antiga landing page extensa. Cartões, botões e diálogo de logs usam azul `#3D5AFE`, formas arredondadas e conteúdo localizado em pt-BR.
- O caminho ativo é baseado em processo, não JNI. A bridge JNI/ABI experimental não é usada pelo launcher atual.

## Validação local em 17/10/2026

A v0.2.2-alpha.1 compilou no Sandbox ARM64. `testStandardDebugUnitTest` e `testCloneDebugUnitTest` somaram 30 testes Kotlin, sem falhas/erros/skips; os dois testes Python do empacotador passaram. `py_compile`, `sh -n` e `git diff --check` também passaram. Os 70 assets do runtime foram verificados no APK debug conforme tamanho e SHA-256.

Artefatos, hashes e limitações estão em [`../build/build-report.md`](../build/build-report.md). Build bem-sucedido e integridade dos assets não substituem teste de inicialização em telefone.

## O que ainda falta validar

1. Instalar/executar no Galaxy A07 4G: processo QEMU Android-linkado, boot, VNC, toque/teclado, QMP e persistência. Sem dispositivo físico conectado nesta sessão.
2. Capturar e analisar `qemu.log` real do telefone se QEMU encerrar.
3. Testar em aparelho os fluxos SAF/Drive e exportação de logs.
4. Avaliar memória, temperatura e desempenho TCG no Helio G99; `/dev/kvm`/AVF não foram verificados.
5. Avançar o bootstrap até framework Android; o smoke test de host não confirmou zygote.
6. Aplicar/compilar `lineage_zyx_virtual` numa árvore LineageOS completa. Sandbox ARM64 não satisfaz os requisitos usuais de host x86-64, memória e espaço para esse build.
7. Preparar assinatura de produção e autenticação criptográfica do manifesto `.zyxos`; SHA-256 verifica integridade, não identidade do publicador.

A versão permanece prévia experimental, sem validação no aparelho Samsung indicado.
