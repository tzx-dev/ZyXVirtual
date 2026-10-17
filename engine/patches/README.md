# Patches do engine

Os binários ELF herdados estão preservados para que o APK continue funcional. A auditoria local encontrou referências de build do QEMU 8.2.2 em `libqemu_gb.so` e `libqemu_kk.so`, além de código nativo próprio em `native/` para host JNI, áudio, GL, shims e stubs.

Nesta etapa não há uma árvore QEMU completa no repositório; portanto, nenhum binário foi apresentado como recompilado a partir de fonte que não existe aqui. Para a próxima etapa:

1. importar a revisão exata do QEMU usado pelo upstream;
2. separar patches de syscall, Binder e dispositivos em commits aplicáveis;
3. adicionar build arm64 determinístico;
4. substituir o caminho user-mode por um backend AVF/KVM somente quando o dispositivo permitir.

Use `scripts/check-engine-assets.sh` para verificar os ELF empacotados e o relatório em `docs/architecture/initial-analysis.md` para a matriz de risco.
