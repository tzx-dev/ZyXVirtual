package app.zyx.virtual.core

/**
 * The renderer of guest programs on the phone's GPU (native/glbridge/host.c): the guest's GLES driver connects to
 * the socket, one connection per drawing thread, and its commands are played in real GLES 3 contexts here.
 */
object GlHost {
    private var nativeOk: Boolean = try { System.loadLibrary("aemugl"); true } catch (_: Throwable) { false }

    /** "ok <renderer>, <version>" or why not */
    fun start(socket: String): String = if (nativeOk) runCatching { nativeStart(socket) }.getOrElse {
        nativeOk = false
        "GPU bridge JNI unavailable; software fallback"
    } else "GPU bridge unavailable; software fallback"
    fun stop() { if (nativeOk) runCatching { nativeStop() } }

    /** the view finished frames are drawn into by the GPU (null: none, frames are dropped) */
    fun surface(s: android.view.Surface?) { if (nativeOk) runCatching { nativeSurface(s) } }
    /** how many frames went to the screen that way since start() */
    fun presented(): Long = if (nativeOk) runCatching { nativePresented() }.getOrDefault(0L) else 0L

    @JvmStatic private external fun nativeStart(socket: String): String
    @JvmStatic private external fun nativeStop()
    @JvmStatic private external fun nativeSurface(s: android.view.Surface?)
    @JvmStatic private external fun nativePresented(): Long
}
