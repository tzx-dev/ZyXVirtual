package app.aemu.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import app.aemu.R
import app.aemu.core.ZyxOsPackageStore
import app.aemu.core.ZyxOsVmStorage
import app.aemu.core.ZyxQemuSystemProcess
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Tela isolada do ZyXOS; mantém QEMU fora do processo principal da biblioteca. */
class ZyxVmActivity : ComponentActivity() {
    private lateinit var vnc: ZyxVncView
    private lateinit var status: TextView
    @Volatile private var session: ZyxQemuSystemProcess? = null
    @Volatile private var guestProcess: Process? = null
    private val closing = AtomicBoolean(false)

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
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(16, 10, 16, 10)
            background = ColorDrawable(0xcc151515.toInt())
            text = getString(R.string.zyx_vm_preparing)
        }
        val statusLp = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        statusLp.setMargins(16, 24, 16, 0)
        root.addView(status, statusLp)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            background = ColorDrawable(0xcc151515.toInt())
        }
        val reset = Button(this).apply {
            text = getString(R.string.zyx_vm_reset)
            setOnClickListener {
                if (session?.requestReset() != true) showStatus("Não foi possível enviar o comando de reinício.")
            }
        }
        val stop = Button(this).apply {
            text = getString(R.string.zyx_vm_stop)
            setOnClickListener { stopAndFinish() }
        }
        controls.addView(reset, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(stop, LinearLayout.LayoutParams(0, -2, 1f))
        val controlsLp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        root.addView(controls, controlsLp)
        setContentView(root)

        vnc.onConnectionState = { connected, error ->
            runOnUiThread {
                if (connected) {
                    status.text = getString(R.string.zyx_vm_running)
                    status.visibility = View.GONE
                } else if (!closing.get()) {
                    status.text = getString(R.string.zyx_vm_vnc_error, error ?: "conexão encerrada")
                    status.visibility = View.VISIBLE
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
                            status.text = "O processo QEMU foi encerrado (código $exitCode). Consulte qemu.log no armazenamento privado do app."
                            status.visibility = View.VISIBLE
                        }
                    }
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    status.text = getString(R.string.zyx_vm_vnc_error, t.localizedMessage ?: t.toString())
                    status.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showStatus(message: String) {
        if (!::status.isInitialized) return
        runOnUiThread {
            status.text = message
            status.visibility = View.VISIBLE
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
