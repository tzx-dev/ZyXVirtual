# ZyX OS — camada de produto

Esta árvore é o **overlay de produto** do ZyX OS. Ela não duplica os muitos gigabytes do AOSP/LineageOS e não contém, por si só, um sistema compilado. O build reproduzível deve receber uma árvore LineageOS/AOSP compatível através de `LINEAGE_DIR`.

## Alvo inicial

- dispositivo lógico: `zyx_virtual`;
- guest planejado: arm64, quando existir um backend de VM arm64 real;
- host inicial: Samsung Galaxy A07 4G, conforme a especificação do projeto;
- interface gráfica e comunicação: virtio/AVF no backend futuro, não o QEMU user-mode legado;
- boot: kernel ZyX + ramdisk + `system.img`/`vendor.img` próprios, com AVB conforme a versão AOSP escolhida.

Os arquivos em `device/zyx/virtual/` são um ponto de partida revisável. Eles não são declarados como uma configuração de placa Samsung, porque o app comum não pode trocar o bootloader/kernel do telefone e a compatibilidade AVF do modelo precisa ser verificada no aparelho.

## Build

```bash
LINEAGE_DIR=/caminho/para/lineage scripts/build-zyx-guest.sh
```

Sem `LINEAGE_DIR` ou sem a configuração de build correspondente, o script falha com uma mensagem clara. Ele nunca cria uma imagem vazia e nunca chama isso de ZyX OS compilado.
