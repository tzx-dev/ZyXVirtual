package app.aemu.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

/** Cliente RFB mínimo para o servidor VNC local do QEMU (sem autenticação, vinculado a loopback). */
class ZyxVncView(context: Context) : View(context) {
    @Volatile private var active = false
    @Volatile private var connected = false
    @Volatile private var socket: Socket? = null
    @Volatile private var output: DataOutputStream? = null
    @Volatile var onConnectionState: ((Boolean, String?) -> Unit)? = null
    private val bitmapLock = Any()
    private val outputLock = Any()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private var bitmap: Bitmap? = null
    private var fbWidth = 0
    private var fbHeight = 0
    private var pressed = false
    @Volatile private var primaryPointerId = MotionEvent.INVALID_POINTER_ID
    private val inputExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "zyx-vnc-input").apply { isDaemon = true }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(0xff000000.toInt())
    }

    fun connect(port: Int) {
        require(port in 1..65535)
        if (active) return
        active = true
        Thread({ runClient(port) }, "zyx-vnc").apply { isDaemon = true; start() }
    }

    fun disconnect() {
        active = false
        connected = false
        pressed = false
        primaryPointerId = MotionEvent.INVALID_POINTER_ID
        runCatching { socket?.close() }
        socket = null
        output = null
        postInvalidate()
    }

    override fun onDetachedFromWindow() {
        disconnect()
        inputExecutor.shutdownNow()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xff000000.toInt())
        synchronized(bitmapLock) {
            val frame = bitmap ?: return
            dst.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(frame, null, dst, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        requestFocus()
        // Consume o gesto mesmo enquanto o VNC conecta; devolver false aqui permite que
        // outro pai roube o gesto e faz o primeiro toque parecer perdido.
        if (!connected || width <= 0 || height <= 0 || fbWidth <= 0 || fbHeight <= 0) return true
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                primaryPointerId = event.getPointerId(event.actionIndex)
                pressed = true
                sendPointerAt(event, event.actionIndex, 1)
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(primaryPointerId).takeIf { it >= 0 } ?: 0
                pressed = true
                sendPointerAt(event, index, 1)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val liftedIndex = event.actionIndex
                if (event.getPointerId(liftedIndex) == primaryPointerId) {
                    val replacement = (0 until event.pointerCount).firstOrNull { it != liftedIndex }
                    if (replacement != null) {
                        primaryPointerId = event.getPointerId(replacement)
                        sendPointerAt(event, replacement, 1)
                    } else {
                        pressed = false
                        sendPointerAt(event, liftedIndex, 0)
                        primaryPointerId = MotionEvent.INVALID_POINTER_ID
                    }
                } else {
                    val index = event.findPointerIndex(primaryPointerId).takeIf { it >= 0 } ?: 0
                    sendPointerAt(event, index, 1)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val index = event.findPointerIndex(primaryPointerId).takeIf { it >= 0 } ?: 0
                pressed = false
                sendPointerAt(event, index, 0)
                primaryPointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
    }

    private fun sendPointerAt(event: MotionEvent, index: Int, mask: Int) {
        if (index !in 0 until event.pointerCount) return
        val x = (event.getX(index) * fbWidth / width).toInt().coerceIn(0, fbWidth - 1)
        val y = (event.getY(index) * fbHeight / height).toInt().coerceIn(0, fbHeight - 1)
        sendPointer(mask, x, y)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val symbol = keysym(keyCode, event) ?: return super.onKeyDown(keyCode, event)
        sendKey(true, symbol)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val symbol = keysym(keyCode, event) ?: return super.onKeyUp(keyCode, event)
        sendKey(false, symbol)
        return true
    }

    private fun runClient(port: Int) {
        var failure: String? = null
        try {
            var connectedSocket: Socket? = null
            var attempt = 0
            while (active && connectedSocket == null && attempt < CONNECT_ATTEMPTS) {
                val candidate = Socket()
                try {
                    candidate.tcpNoDelay = true
                    candidate.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS)
                    connectedSocket = candidate
                } catch (_: Exception) {
                    runCatching { candidate.close() }
                    attempt++
                    Thread.sleep(CONNECT_RETRY_MS)
                }
            }
            if (!active) return
            val s = connectedSocket ?: throw IOException("O servidor VNC do QEMU não respondeu.")
            socket = s
            val input = DataInputStream(BufferedInputStream(s.getInputStream(), IO_BUFFER_BYTES))
            val out = DataOutputStream(BufferedOutputStream(s.getOutputStream(), IO_BUFFER_BYTES))
            output = out
            negotiate(input, out)
            connected = true
            onConnectionState?.invoke(true, null)
            while (active) {
                when (input.readUnsignedByte()) {
                    SERVER_FRAMEBUFFER_UPDATE -> readFramebufferUpdate(input, out)
                    SERVER_BELL -> Unit
                    SERVER_CUT_TEXT -> readCutText(input)
                    else -> throw IOException("Mensagem VNC desconhecida do servidor.")
                }
            }
        } catch (e: Exception) {
            if (active) failure = e.localizedMessage ?: "Falha na conexão VNC."
        } finally {
            active = false
            connected = false
            runCatching { socket?.close() }
            socket = null
            output = null
            if (failure != null) onConnectionState?.invoke(false, failure)
            postInvalidate()
        }
    }

    private fun negotiate(input: DataInputStream, out: DataOutputStream) {
        val serverVersion = ByteArray(12)
        input.readFully(serverVersion)
        val serverText = String(serverVersion, Charsets.US_ASCII)
        if (!serverText.startsWith("RFB 003.")) throw IOException("Versão RFB incompatível: $serverText")
        out.write("RFB 003.008\n".toByteArray(Charsets.US_ASCII))
        out.flush()

        val securityCount = input.readUnsignedByte()
        if (securityCount == 0) {
            val reasonSize = input.readInt().coerceIn(0, MAX_REASON_BYTES)
            val reason = ByteArray(reasonSize)
            input.readFully(reason)
            throw IOException(String(reason, Charsets.UTF_8).ifBlank { "O servidor VNC recusou a conexão." })
        }
        val securityTypes = ByteArray(securityCount)
        input.readFully(securityTypes)
        if (securityTypes.none { (it.toInt() and 0xff) == SECURITY_NONE }) {
            throw IOException("O servidor VNC exige autenticação não configurada no app.")
        }
        out.writeByte(SECURITY_NONE)
        out.flush()
        if (input.readInt() != 0) throw IOException("Falha na negociação de segurança VNC.")

        out.writeByte(1) // shared session
        out.flush()
        fbWidth = input.readUnsignedShort()
        fbHeight = input.readUnsignedShort()
        if (fbWidth <= 0 || fbHeight <= 0 || fbWidth.toLong() * fbHeight > MAX_PIXELS) {
            throw IOException("Resolução VNC inválida ou grande demais.")
        }
        val serverPixelFormat = ByteArray(16)
        input.readFully(serverPixelFormat)
        val nameLength = input.readInt().coerceIn(0, MAX_REASON_BYTES)
        if (nameLength > 0) input.skipFully(nameLength)
        synchronized(bitmapLock) { bitmap = Bitmap.createBitmap(fbWidth, fbHeight, Bitmap.Config.ARGB_8888) }

        // Negocia pixels little-endian 32 bpp, RGB888; o cliente converte para Android ARGB_8888.
        out.writeByte(0); out.writeByte(0); out.writeByte(0); out.writeByte(0)
        out.writeByte(32); out.writeByte(24); out.writeByte(0); out.writeByte(1)
        out.writeShort(255); out.writeShort(255); out.writeShort(255)
        out.writeByte(16); out.writeByte(8); out.writeByte(0)
        out.writeByte(0); out.writeByte(0); out.writeByte(0)

        // Raw pixels e DesktopSize; evita depender de codecs de terceiros.
        out.writeByte(2); out.writeByte(0); out.writeShort(2)
        out.writeInt(ENCODING_RAW); out.writeInt(ENCODING_DESKTOP_SIZE)
        requestUpdate(out, incremental = false)
        out.flush()
        if (serverPixelFormat.isEmpty()) throw IOException("Formato de pixels VNC inválido.")
    }

    private fun readFramebufferUpdate(input: DataInputStream, out: DataOutputStream) {
        input.readUnsignedByte() // padding
        val rectCount = input.readUnsignedShort()
        repeat(rectCount) {
            val x = input.readUnsignedShort()
            val y = input.readUnsignedShort()
            val w = input.readUnsignedShort()
            val h = input.readUnsignedShort()
            val encoding = input.readInt()
            when (encoding) {
                ENCODING_RAW -> readRawRectangle(input, x, y, w, h)
                ENCODING_DESKTOP_SIZE -> resizeFramebuffer(w, h)
                else -> throw IOException("Codificação VNC não suportada: $encoding")
            }
        }
        postInvalidate()
        synchronized(outputLock) {
            requestUpdate(out, incremental = true)
            out.flush()
        }
    }

    private fun readRawRectangle(input: DataInputStream, x: Int, y: Int, w: Int, h: Int) {
        if (w == 0 || h == 0) return
        if (x + w > fbWidth || y + h > fbHeight || w.toLong() * h > MAX_PIXELS) {
            throw IOException("Retângulo VNC fora dos limites do quadro.")
        }
        val bytes = ByteArray(w * h * 4)
        input.readFully(bytes)
        val pixels = IntArray(w * h)
        var src = 0
        for (i in pixels.indices) {
            val blue = bytes[src++].toInt() and 0xff
            val green = bytes[src++].toInt() and 0xff
            val red = bytes[src++].toInt() and 0xff
            src++ // padding
            pixels[i] = (0xff shl 24) or (red shl 16) or (green shl 8) or blue
        }
        synchronized(bitmapLock) {
            bitmap?.setPixels(pixels, 0, w, x, y, w, h)
        }
    }

    private fun resizeFramebuffer(w: Int, h: Int) {
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_PIXELS) throw IOException("Nova resolução VNC inválida.")
        synchronized(bitmapLock) {
            fbWidth = w
            fbHeight = h
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
    }

    private fun readCutText(input: DataInputStream) {
        input.skipFully(3)
        val size = input.readInt()
        if (size < 0 || size > MAX_REASON_BYTES) throw IOException("Mensagem de texto VNC grande demais.")
        input.skipFully(size)
    }

    private fun requestUpdate(out: DataOutputStream, incremental: Boolean) {
        out.writeByte(3)
        out.writeByte(if (incremental) 1 else 0)
        out.writeShort(0); out.writeShort(0)
        out.writeShort(fbWidth); out.writeShort(fbHeight)
    }

    private fun sendPointer(mask: Int, x: Int, y: Int) {
        enqueueInput { out ->
            out.writeByte(5); out.writeByte(mask); out.writeShort(x); out.writeShort(y); out.flush()
        }
    }

    private fun sendKey(down: Boolean, symbol: Int) {
        enqueueInput { out ->
            out.writeByte(4); out.writeByte(if (down) 1 else 0); out.writeShort(0); out.writeInt(symbol); out.flush()
        }
    }

    /** Nunca bloqueia a thread de UI em escrita/flush de socket VNC. */
    private fun enqueueInput(write: (DataOutputStream) -> Unit) {
        runCatching {
            inputExecutor.execute {
                val out = output ?: return@execute
                val succeeded = synchronized(outputLock) {
                    runCatching { write(out) }.isSuccess
                }
                if (!succeeded && active) post { if (active) disconnect() }
            }
        }
    }

    private fun keysym(code: Int, event: KeyEvent): Int? = when (code) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 'a'.code + code - KeyEvent.KEYCODE_A
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> '0'.code + code - KeyEvent.KEYCODE_0
        KeyEvent.KEYCODE_SPACE -> 0x20
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> 0xff0d
        KeyEvent.KEYCODE_DEL -> 0xff08
        KeyEvent.KEYCODE_FORWARD_DEL -> 0xffff
        KeyEvent.KEYCODE_TAB -> 0xff09
        KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> 0xff1b
        KeyEvent.KEYCODE_DPAD_LEFT -> 0xff51
        KeyEvent.KEYCODE_DPAD_UP -> 0xff52
        KeyEvent.KEYCODE_DPAD_RIGHT -> 0xff53
        KeyEvent.KEYCODE_DPAD_DOWN -> 0xff54
        KeyEvent.KEYCODE_HOME -> 0xff50
        KeyEvent.KEYCODE_PAGE_UP -> 0xff55
        KeyEvent.KEYCODE_PAGE_DOWN -> 0xff56
        KeyEvent.KEYCODE_VOLUME_UP -> 0x1008ff13
        KeyEvent.KEYCODE_VOLUME_DOWN -> 0x1008ff11
        else -> event.unicodeChar.takeIf { it > 0 }
    }

    private fun DataInputStream.skipFully(count: Int) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skipBytes(remaining)
            if (skipped <= 0) {
                if (read() < 0) throw IOException("Conexão VNC encerrada durante leitura.")
                remaining--
            } else remaining -= skipped
        }
    }

    companion object {
        private const val SECURITY_NONE = 1
        private const val SERVER_FRAMEBUFFER_UPDATE = 0
        private const val SERVER_BELL = 2
        private const val SERVER_CUT_TEXT = 3
        private const val ENCODING_RAW = 0
        private const val ENCODING_DESKTOP_SIZE = -223
        private const val CONNECT_ATTEMPTS = 60
        private const val CONNECT_TIMEOUT_MS = 500
        private const val CONNECT_RETRY_MS = 500L
        private const val IO_BUFFER_BYTES = 64 * 1024
        private const val MAX_REASON_BYTES = 1024 * 1024
        private const val MAX_PIXELS = 4L * 1024 * 1024
    }
}
