package com.example.androidbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice

import androidx.documentfile.provider.DocumentFile
import com.example.androidbox.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pageReady = false

    private val PREFS = "usb_prefs"
    private val KEY_TREE_URI = "tree_uri"
    // ▼ GASスナップショット（localStorageミラー）
    private val SNAP_PREFS = "gas_snapshot_prefs"
    private val KEY_SNAPSHOT = "gas_snapshot_json"

    // ▼ Bluetooth切断検知（名前などには触れない → 実行時パーミッション不要）
    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF) {
                        Toast.makeText(this@MainActivity, "BluetoothがOFFになりました（スナップショット保存）", Toast.LENGTH_SHORT).show()
                        // if (pageReady) requestSnapshotFromWebView(binding.webView) { }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    // HID系スキャナの切断で飛んでくることが多い
                    Toast.makeText(this@MainActivity, "スキャナ切断を検知（スナップショット保存）", Toast.LENGTH_SHORT).show()
                    // if (pageReady) requestSnapshotFromWebView(binding.webView) { }
                }
            }
        }
    }
    // ▼ここから追加：USBフォルダ選択（SAF） StartActivityForResult 版
    private val folderPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val uri = data.data ?: return@registerForActivityResult

            // 付与された一時権限のみを AND して永続化
            val takeFlags = (data.flags) and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            prefs.getString(KEY_TREE_URI, null)?.let { old ->
                runCatching {
                    contentResolver.releasePersistableUriPermission(Uri.parse(old), takeFlags)
                }
            }
            try {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: SecurityException) {
                Toast.makeText(this, "フォルダ許可の取得に失敗しました。もう一度選択してください。", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply()
            Toast.makeText(this, "保存先を設定しました", Toast.LENGTH_SHORT).show()
        }
    }

    // ピッカー起動関数
    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
        folderPicker.launch(intent)
    }
// ▲ここまで追加

// koko2
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

    }
    // ▼ WebView 内のJSを呼ぶユーティリティ
    private fun runJS(script: String) {
        if (!pageReady) {
            Toast.makeText(this, "画面の読み込み待機中です…", Toast.LENGTH_SHORT).show()
            return
        }
        binding.webView.evaluateJavascript(script, null)
    }
    override fun onStart() {
        super.onStart()
        // BT切断などのブロードキャスト受信を登録
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(btReceiver, filter)
    }

    override fun onStop() {
        // 画面離脱時にGASの状態を取り出して端末へ退避（保険）
        super.onStop()
        if (pageReady) requestSnapshotFromWebView(binding.webView)  }
        // koko
        private fun setupButtons() = with(binding) {
            btnSetFolder.setOnClickListener { openFolderPicker() }
            btnChangeFolder.setOnClickListener { openFolderPicker() }

        // koko
        // ▼ 端末側バー（GASは無改変）
        // btnSeq.setOnClickListener {
        //     if (!pageReady) { Toast.makeText(this@MainActivity, "読み込み中…", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
        //     binding.webView.evaluateJavascript("scanAndFill('seq')", null)
        // }
        // btnOne.setOnClickListener {
        //     if (!pageReady) { Toast.makeText(this@MainActivity, "読み込み中…", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
        //     binding.webView.evaluateJavascript("scanAndFill('one')", null)
        // }
        // btnGuide.setOnClickListener {
        //     if (!pageReady) { Toast.makeText(this@MainActivity, "読み込み中…", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
        //     binding.webView.evaluateJavascript("toggleGuide()", null)
        // }
        // btnUndo.setOnClickListener {
        //     if (!pageReady) { Toast.makeText(this@MainActivity, "読み込み中…", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
        //     binding.webView.evaluateJavascript("undo()", null)
        // }
        // btnClear.setOnClickListener {
        //     if (!pageReady) { Toast.makeText(this@MainActivity, "読み込み中…", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
        //     binding.webView.evaluateJavascript("clearAll()", null)
        // }
        // ▼ USBに保存（WebView→PDF→SAF）
        binding.btnSaveUsb.setOnClickListener {
            if (!pageReady) {
                Toast.makeText(this@MainActivity, "読み込み中…", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // ファイル名のベースをWeb側から拾う（見つからなければ <title> → "label"）
            /* fetchLabelName(binding.webView) { base ->
                val safeBase = if (base.isBlank()) "label" else base
                val name = "${safeBase}_${timestamp()}.pdf"
                // A4を180dpiでレンダ（画質と容量のバランス）
                val bytes = generatePdfFromWebViewSimple(binding.webView, dpi = 180)
                if (bytes != null) {
                    saveBytesToUsb(name, bytes)
                } else {
                    Toast.makeText(this@MainActivity, "PDF化に失敗しました", Toast.LENGTH_LONG).show()
                }
            } */
        }

        // 保存先は既存のSAFを利用
 //       btnSetFolder.setOnClickListener { pickFolder.launch(null) }

        //btnSaveDummyPdf.setOnClickListener {
 //           val bytes: ByteArray? = createDummyPdfBytes()
  //          if (bytes != null) {
  //              saveBytesToUsb("test_${timestamp()}.pdf", bytes)
  //          } else {
 //               Toast.makeText(this@MainActivity, "PDF作成に失敗", Toast.LENGTH_SHORT).show()
 //           }


    }

    private fun setupWebView(webView: WebView) {
        val s: WebSettings = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.loadsImagesAutomatically = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        webView.webViewClient = object : WebViewClient() {
            // docs/drive/accounts は外部ブラウザで開く（任意：必要なければ削除可）
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url ?: return false
                val host = (u.host ?: return false).lowercase()
                if (host.startsWith("docs.") || host.startsWith("drive.") || host.startsWith("accounts.")) {
                    startActivity(Intent(Intent.ACTION_VIEW, u))
                    return true
                }
                return false
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady = true
                Toast.makeText(this@MainActivity, "GAS画面を読み込みました", Toast.LENGTH_SHORT).show()
                // ★ 端末側にスナップショットがあり、かつ localStorage が空なら差し戻して復元
                pushSnapshotToWebIfMissing(binding.webView)

            }
        }

        // ★ あなたのGAS WebアプリURLに差し替え（/exec）
        webView.loadUrl("https://script.google.com/macros/s/AKfycbzaifuFk2Dyn2RXg4qe3Blfq-DbNfJ7NT1RNDb8C9duCDq6DTbAEEoIkuIdffDI-dI/exec")
    }

    // WebViewのDOMからラベル名を拾う（見つからなければ <title>、それも無ければ "label"）
    private fun fetchLabelName(webView: WebView, callback: (String) -> Unit) {
        val js = """
            (function(){
              function pick(){
                var el = document.querySelector(
                  '#labelName, #productName, input[name="itemName"], input[name="product"], .label-title, .product-title'
                );
                var t = '';
                if (el) { t = (el.value || el.textContent || '').trim(); }
                if (!t) { t = (document.title || '').trim(); }
                if (!t) { t = 'label'; }
                t = t.replace(/[\\\/:*?"<>|]/g,'_').slice(0,40);
                return t;
              }
              return pick();
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { raw ->
            val unquoted = raw?.let {
                if (it.length >= 2 && it.startsWith("\"") && it.endsWith("\"")) it.substring(1, it.length - 1) else it
            } ?: "label"
            val safe = if (unquoted.isBlank()) "label" else unquoted
            callback(safe)
        }
    }

    // ---- ステップA：ダミーPDF作成（確認用） ----
    private fun createDummyPdfBytes(): ByteArray? {
        return try {
            val pdf = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4相当
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            val paint = android.graphics.Paint().apply {
                textSize = 14f
                isAntiAlias = true
            }
            // canvas.drawText("Hello PDF @ ${timestamp()}", 72f, 72f, paint)
            canvas.drawText("USB保存テスト", 72f, 100f, paint)
            pdf.finishPage(page)

            val bos = ByteArrayOutputStream()
            pdf.writeTo(bos)
            pdf.close()
            bos.toByteArray()
        } catch (e: Exception) { null }
    }

    // ---- WebViewをPDF化（可変DPI・1ページ想定）----
    private fun generatePdfFromWebViewSimple(webView: WebView, dpi: Int = 150): ByteArray? {
        return try {
            // A4（8.27×11.69 in）を dpi に合わせたピクセルへ
            val pageW = (8.27f * dpi).roundToInt()
            val pageH = (11.69f * dpi).roundToInt()
            val pdf = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            // WebView幅に合わせて横フィット
            val scale = if (webView.width > 0) pageW.toFloat() / webView.width.toFloat() else 1f
            canvas.scale(scale, scale)
            webView.draw(canvas)

            pdf.finishPage(page)

            val bos = ByteArrayOutputStream()
            pdf.writeTo(bos)
            pdf.close()
            bos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }
    // ==== GAS(localStorage) → 端末へ退避 ====
    private fun requestSnapshotFromWebView(webView: WebView, onDone: (Boolean) -> Unit = {}) {
        // 文字化け回避のため Base64 で返させる（UTF-8）
        val js = """
            (function(){
              try{
                var v = localStorage.getItem('jan48.state.v1');
                if (!v) return null;
                var b64 = btoa(unescape(encodeURIComponent(v)));
                return "base64:" + b64;
              }catch(e){ return null; }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { raw ->
            val s = raw?.trim()
            val value = if (s.isNullOrEmpty() || s == "null") null else s.removeSurrounding("\"")
            if (value != null && value.startsWith("base64:")) {
                getSharedPreferences(SNAP_PREFS, MODE_PRIVATE)
                    .edit().putString(KEY_SNAPSHOT, value.removePrefix("base64:")).apply()
                onDone(true)
            } else {
                onDone(false)
            }
        }
    }

    // ==== 端末 → GAS(localStorage) へ差し戻し（空のときだけ） ====
    private fun pushSnapshotToWebIfMissing(webView: WebView) {
        val snap = getSharedPreferences(SNAP_PREFS, MODE_PRIVATE).getString(KEY_SNAPSHOT, null) ?: return
        val js = """
            (function(){
              try{
                if (!localStorage.getItem('jan48.state.v1')) {
                  var json = decodeURIComponent(escape(atob("$snap")));
                  localStorage.setItem('jan48.state.v1', json);
                  if (typeof restoreState === 'function') restoreState();
                  return "restored";
                }
                return "skip";
              }catch(e){ return "error"; }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    // ---- USBフォルダへ保存（SAF） ----
    private fun saveBytesToUsb(fileName: String, bytes: ByteArray) {
        val tree = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_TREE_URI, null)
        if (tree.isNullOrEmpty()) {
            Toast.makeText(this, "保存先が未設定です（保存先を設定）", Toast.LENGTH_LONG).show()
            return
        }
        val root = DocumentFile.fromTreeUri(this, Uri.parse(tree))
        if (root == null || !root.canWrite()) {
            Toast.makeText(this, "保存先に書き込めません。再設定してください。", Toast.LENGTH_LONG).show()
            return
        }
        val safe = buildSafeName(root, fileName)
        val target = root.createFile("application/pdf", safe)
        if (target == null) {
            Toast.makeText(this, "ファイル作成に失敗しました", Toast.LENGTH_LONG).show()
            return
        }
        try {
            contentResolver.openOutputStream(target.uri)?.use { it.write(bytes) }
            Toast.makeText(this, "USBへ保存しました：$safe", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "書き込み失敗：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildSafeName(parent: DocumentFile, baseName: String): String {
        val nameOnly = baseName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val parts = nameOnly.split(".")
        val stem = if (parts.size >= 2) parts.dropLast(1).joinToString(".") else nameOnly
        val ext = if (parts.size >= 2) parts.last() else "pdf"
        var cand = "$stem.$ext"
        var idx = 2
        while (parent.findFile(cand) != null) {
            cand = "${stem}_$idx.$ext"
            idx++
        }
        return cand
    }

    // private fun timestamp(): String =
    //     SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN).format(Date())
}

