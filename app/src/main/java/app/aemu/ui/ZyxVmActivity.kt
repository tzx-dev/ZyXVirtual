package app.aemu.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import app.aemu.R
import app.aemu.core.ZyxOsPackageStore
import app.aemu.core.ZyxOsVmStorage
import app.aemu.core.ZyxQemuSystemProcess
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Tela isolada do ZyXOS; mantém QEMU fora do processo principal da biblioteca. */
class ZyxVmActivity : ComponentActivity() {
    private lateinit var vnc: ZyxVncView
    private lateinit var status: TextView
    @Volatile private var session: ZyxQemuSystemProcess? = null
    @Volatile private var guestProcess: Process? = null
    private val closing = AtomicBoolean(false)
    @Volatile private var qemuLogFile: File? = null
    @Volatile private var pendingExportLog: File? = null
    private val exportLogLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val source = pendingExportLog
        pendingExportLog = null
        if (uri != null) {
            if (source == null || !source.isFile) {
                Toast.makeText(this, getString(R.string.log_export_failed), Toast.LENGTH_LONG).show()
            } else {
                thread(name = "zyx-qemu-log-export") {
                    try {
                        val copied = contentResolver.openOutputStream(uri)?.use { output ->
                            source.inputStream().use { input -> input.copyTo(output) }
                            true
                        } ?: false
                        runOnUiThread {
                            val message = if (copied) getString(R.string.log_exported) else getString(R.string.log_export_failed)
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.log_export_failed) + ": " + (e.localizedMessage ?: e.toString()), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(app.aemu.AppPrefs.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildContent()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = stopAndFinish()
        })
        startGuest()
    }

    private fun buildContent() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        vnc = ZyxVncView(this)
        root.addView(vnc, FrameLayout.LayoutParams(-1, -1))

        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = rounded(Color.rgb(30, 31, 38), dp(20).toFloat(), Color.rgb(76, 78, 94))
            elevation = dp(5).toFloat()
            text = getString(R.string.zyx_vm_preparing)
            isClickable = false
        }
        val statusLp = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        statusLp.setMargins(dp(16), dp(24), dp(16), 0)
        root.addView(status, statusLp)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(Color.rgb(24, 25, 31), dp(22).toFloat(), Color.rgb(54, 55, 66))
        }
        val reset = Button(this).apply {
            text = getString(R.string.zyx_vm_reset)
            background = rounded(Color.rgb(61, 90, 254), dp(16).toFloat())
            setTextColor(Color.WHITE)
            textSize = 13f
            isAllCaps = false
            elevation = dp(2).toFloat()
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                if (session?.requestReset() != true) showStatus("Não foi possível enviar o comando de reinício.")
            }
        }
        val stop = Button(this).apply {
            text = getString(R.string.zyx_vm_stop)
            background = rounded(Color.rgb(52, 53, 62), dp(16).toFloat(), Color.rgb(91, 93, 109))
            setTextColor(Color.WHITE)
            textSize = 13f
            isAllCaps = false
            elevation = dp(2).toFloat()
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { stopAndFinish() }
        }
        controls.addView(reset, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) })
        controls.addView(stop, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) })
        val controlsLp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        root.addView(controls, controlsLp)
        setContentView(root)

        vnc.onConnectionState = { connected, error ->
            runOnUiThread {
                if (connected) {
                    status.text = getString(R.string.zyx_vm_running)
                    status.visibility = View.GONE
                    status.isClickable = false
                    status.setOnClickListener(null)
                } else if (!closing.get()) {
                    status.text = getString(R.string.zyx_vm_vnc_error, error ?: "conexão encerrada")
                    status.visibility = View.VISIBLE
                    status.isClickable = qemuLogFile?.isFile == true
                    status.setOnClickListener { showQemuLogDialog() }
                }
            }
        }
    }

    private fun startGuest() {
        if (!ZyxQemuSystemProcess.isAvailable(this)) {
            showStatus("O executável QEMU system-mode ARM64 não está incluído neste APK.")
            return
        }
        thread(name = "zyx-vm-start") {
            try {
                val installed = ZyxOsPackageStore(applicationContext).installed()
                    ?: throw IllegalStateException("Instale um pacote ZyX OS válido antes de iniciar a prévia.")
                val config = ZyxOsVmStorage.prepare(installed)
                // O Android guest escreve kernel/init/framework em serial.log. O stdout/stderr
                // do processo QEMU vai para qemu.log e frequentemente fica vazio, então o
                // diagnóstico principal precisa apontar para o serial real do guest.
                qemuLogFile = File(config.serialLogPath)
                val runner = ZyxQemuSystemProcess(applicationContext, config)
                session = runner
                val process = runner.start()
                guestProcess = process
                runOnUiThread {
                    status.text = getString(R.string.zyx_vm_connecting)
                    status.visibility = View.VISIBLE
                    vnc.connect(5900 + config.vncDisplay)
                }
                thread(name = "zyx-vm-monitor") {
                    val exitCode = runCatching { process.waitFor() }.getOrDefault(-1)
                    if (!closing.get()) {
                        runOnUiThread {
                            status.text = getString(R.string.zyx_vm_exit_status, exitCode)
                            status.visibility = View.VISIBLE
                            status.isClickable = qemuLogFile?.isFile == true
                            status.setOnClickListener { showQemuLogDialog() }
                        }
                    }
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    status.text = getString(R.string.zyx_vm_vnc_error, t.localizedMessage ?: t.toString())
                    status.visibility = View.VISIBLE
                    status.isClickable = qemuLogFile?.isFile == true
                    status.setOnClickListener { showQemuLogDialog() }
                }
            }
        }
    }

    private fun showStatus(message: String) {
        if (!::status.isInitialized) return
        runOnUiThread {
            status.text = message
            status.visibility = View.VISIBLE
            status.isClickable = false
            status.setOnClickListener(null)
        }
    }

    private fun showQemuLogDialog() {
        val source = qemuLogFile
        thread(name = "zyx-qemu-log-read") {
            val raw = source?.let { file ->
                runCatching { readQemuLogTail(file) }.getOrElse { "Falha ao ler serial.log: ${it.localizedMessage ?: it}" }
            }.orEmpty()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val shownText = raw.ifBlank { getString(R.string.zyx_vm_log_empty) }
                val textView = TextView(this).apply {
                    text = shownText
                    textSize = 12f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.rgb(221, 224, 236))
                    setTextIsSelectable(true)
                    val inset = dp(16)
                    setPadding(inset, inset, inset, inset)
                }
                val scrollView = ScrollView(this).apply {
                    background = rounded(Color.rgb(17, 18, 23), dp(18).toFloat(), Color.rgb(65, 67, 82))
                    isFillViewport = true
                    addView(textView)
                }
                val dialog = Dialog(this)
                val content = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(18), dp(18), dp(18), dp(16))
                    background = rounded(Color.rgb(31, 32, 39), dp(26).toFloat(), Color.rgb(76, 78, 94))
                }
                val titleRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val title = TextView(this).apply {
                    text = getString(R.string.zyx_vm_log_title)
                    textSize = 19f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(Color.WHITE)
                }
                titleRow.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
                titleRow.addView(actionButton(getString(R.string.close), false) { dialog.dismiss() }, LinearLayout.LayoutParams(-2, dp(42)))
                content.addView(titleRow)
                content.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                    topMargin = dp(14)
                    bottomMargin = dp(14)
                })
                val actions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                }
                if (source?.isFile == true) {
                    actions.addView(actionButton(getString(R.string.log_export), false) {
                        pendingExportLog = source
                        exportLogLauncher.launch("zyx-qemu-diagnostic.txt")
                    }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
                }
                actions.addView(actionButton(getString(R.string.log_copy), true) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.zyx_vm_log_title), shownText))
                    Toast.makeText(this, getString(R.string.log_copied), Toast.LENGTH_SHORT).show()
                }, LinearLayout.LayoutParams(0, dp(48), 1f))
                content.addView(actions)
                dialog.setContentView(content)
                dialog.show()
                dialog.window?.apply {
                    setBackgroundDrawableResource(android.R.color.transparent)
                    setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), (resources.displayMetrics.heightPixels * 0.72f).toInt())
                }
            }
        }
    }

    private fun actionButton(label: String, emphasized: Boolean, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(
            if (emphasized) Color.rgb(61, 90, 254) else Color.rgb(53, 54, 64),
            dp(15).toFloat(),
            if (emphasized) null else Color.rgb(91, 93, 109),
        )
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { onClick() }
    }

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun readQemuLogTail(file: File): String {
        if (!file.isFile) return ""
        RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            val count = minOf(length, 24L * 1024).toInt()
            if (count == 0) return ""
            input.seek(length - count)
            val bytes = ByteArray(count)
            input.readFully(bytes)
            val tail = String(bytes, Charsets.UTF_8)
            return if (length > count) "[Exibindo os últimos 24 KiB de serial.log]\n\n$tail" else tail
        }
    }

    private fun stopAndFinish() {
        if (!closing.compareAndSet(false, true)) return
        status.text = getString(R.string.st_stopping)
        status.visibility = View.VISIBLE
        vnc.disconnect()
        thread(name = "zyx-vm-stop") {
            runCatching { session?.stop() }
            guestProcess?.let { if (it.isAlive) runCatching { it.destroy() } }
            runOnUiThread { finish() }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (::vnc.isInitialized && vnc.onKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (::vnc.isInitialized && vnc.onKeyUp(keyCode, event)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        closing.set(true)
        if (::vnc.isInitialized) vnc.disconnect()
        if (session?.isRunning == true) thread(name = "zyx-vm-cleanup") { runCatching { session?.stop() } }
        super.onDestroy()
    }
}
