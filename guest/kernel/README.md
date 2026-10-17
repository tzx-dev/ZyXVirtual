# Kernel guest ZyX OS

O kernel do guest ainda não é gerado nesta etapa. Um kernel Android moderno não pode ser inferido a partir do kernel do Galaxy A07 nem do QEMU user-mode: ele precisa ser compilado para o contrato de dispositivos do VMM escolhido.

Para a etapa AVF/KVM, esta pasta deve receber:

- fonte Android Common Kernel/GKI compatível com a versão AOSP escolhida;
- `arch/arm64/configs/zyx_virtual_defconfig`;
- drivers/transportes virtio exigidos pelo crosvm/VirtualizationService;
- Binder 64-bit, virtio-blk, virtio-console/vsock e framebuffer/display suportados;
- ramdisk, `vendor_boot.img`, `boot.img` e metadados AVB produzidos pelo build do guest.

O arquivo `guest/zyx-os/device/zyx/virtual/BoardConfig.mk` referencia esse defconfig apenas como contrato futuro. Nenhum `boot.img` é entregue como compilado.
