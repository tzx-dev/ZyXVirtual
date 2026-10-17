# ZyX QEMU system-mode JNI boundary

`zyx_qemu_api.h` define uma ABI pública versionada entre a aplicação e uma futura biblioteca QEMU AArch64 system-mode. `zyx_qemu_jni.c` carrega a biblioteca por `dlopen`, resolve `zyx_qemu_get_api`, serializa argumentos sem shell, executa a VM numa thread chamadora e expõe stop/reset/estado. O nome do símbolo é deliberadamente específico do ZyX: se uma biblioteca QEMU não implementar a ABI, a inicialização é recusada em vez de procurar assinaturas internas instáveis.

`app/src/main/java/app/aemu/core/ZyxQemuSystemBridge.kt` é o wrapper Kotlin. `scripts/build-zyx-qemu-jni.sh` compila somente `libzyx-qemu-jni.so` com Android NDK e instala o binário local em `app/src/main/jniLibs/arm64-v8a/`. Isso **não** compila QEMU, não fornece o adaptador que implementa `zyx_qemu_get_api` e não produz `libqemu-system-aarch64.so`.

## Estado

- Código de interface JNI e ABI versionada: presente.
- Compilação Android NDK: ainda não verificada nesta sessão (NDK Android não está instalado no sandbox ARM64).
- Implementação QEMU-side do ABI: ausente.
- Biblioteca QEMU `aarch64-softmmu` para Android: ausente.
- Display VNC/SDL e eventos touch/keyboard: ausentes.

Por isso `ZyxRuntime` verifica as duas bibliotecas no APK e mostra “não empacotado; boot desabilitado”. Não marque essas peças como funcionais até compilar e validar o ciclo de start/stop/reset em aparelho.

## Contrato de integração posterior

O adapter QEMU deve receber `argc/argv` e delegar a uma versão fixada do QEMU, expor shutdown/reset e terminar o loop antes da biblioteca ser descarregada. Os argumentos e armazenamento não podem vir de shell. QMP/VNC devem estar vinculados a loopback/socket Unix privado. O backend também deve criar um disco gravável separado da base verificada do pacote ZyXOS, registrar a licença e disponibilizar o código fonte correspondente.
