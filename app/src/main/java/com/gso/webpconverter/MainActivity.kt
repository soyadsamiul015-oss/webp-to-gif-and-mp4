package com.gso.webpconverter

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class MainActivity : AppCompatActivity() {

    private val items = mutableListOf<FileItem>()
    private lateinit var adapter: FileAdapter
    private lateinit var statusText: TextView
    private lateinit var convertBtn: Button
    private lateinit var formatGroup: RadioGroup
    private var converting = false

    // ---- pickers -------------------------------------------------------

    private val pickFiles = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris?.forEach { addUri(it) }
        adapter.notifyDataSetChanged()
        updateStatus()
    }

    private val pickFolder = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val dir = DocumentFile.fromTreeUri(this, treeUri) ?: return@registerForActivityResult
        var found = 0
        dir.listFiles().forEach { f ->
            val name = f.name ?: return@forEach
            if (f.isFile && name.lowercase().endsWith(".webp")) {
                addUri(f.uri, name); found++
            }
        }
        adapter.notifyDataSetChanged()
        updateStatus()
        toast("Found $found .webp files in folder")
    }

    // ---- lifecycle -----------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        convertBtn = findViewById(R.id.convertBtn)
        formatGroup = findViewById(R.id.formatGroup)

        adapter = FileAdapter(items) { updateStatus() }
        findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        findViewById<Button>(R.id.pickFilesBtn).setOnClickListener {
            pickFiles.launch(arrayOf("image/webp"))
        }
        findViewById<Button>(R.id.pickFolderBtn).setOnClickListener {
            pickFolder.launch(null)
        }
        findViewById<Button>(R.id.selectAllBtn).setOnClickListener {
            items.forEach { it.selected = true }
            adapter.notifyDataSetChanged(); updateStatus()
        }
        findViewById<Button>(R.id.unselectAllBtn).setOnClickListener {
            items.forEach { it.selected = false }
            adapter.notifyDataSetChanged(); updateStatus()
        }
        findViewById<Button>(R.id.clearBtn).setOnClickListener {
            items.clear(); adapter.notifyDataSetChanged(); updateStatus()
        }
        convertBtn.setOnClickListener { startConversion() }

        // Android 9 and below need storage permission to write into Downloads
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1
            )
        }

        handleShareIntent(intent)
        updateStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** Lets the user share .webp files straight from the browser into this app. */
    private fun handleShareIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { addUri(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    ?.forEach { addUri(it) }
            }
            Intent.ACTION_VIEW -> intent.data?.let { addUri(it) }
        }
        adapter.notifyDataSetChanged()
        updateStatus()
    }

    // ---- list management -----------------------------------------------

    private fun addUri(uri: Uri, knownName: String? = null) {
        if (items.any { it.uri == uri }) return
        val name = knownName ?: queryName(uri) ?: "file_${items.size + 1}.webp"
        items.add(FileItem(uri, name))
    }

    private fun queryName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (e: Exception) { null }
    }

    private fun updateStatus() {
        val sel = items.count { it.selected }
        statusText.text = "${items.size} files loaded, $sel selected"
        convertBtn.isEnabled = sel > 0 && !converting
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---- conversion ------------------------------------------------------

    private fun startConversion() {
        val selected = items.filter { it.selected }
        if (selected.isEmpty()) return

        val makeGif = formatGroup.checkedRadioButtonId == R.id.radioGif ||
                formatGroup.checkedRadioButtonId == R.id.radioBoth
        val makeMp4 = formatGroup.checkedRadioButtonId == R.id.radioMp4 ||
                formatGroup.checkedRadioButtonId == R.id.radioBoth

        converting = true
        convertBtn.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            var ok = 0
            var fail = 0
            selected.forEachIndexed { i, item ->
                withContext(Dispatchers.Main) {
                    item.status = "converting..."
                    adapter.notifyDataSetChanged()
                    statusText.text = "Converting ${i + 1}/${selected.size}: ${item.name}"
                }
                val result = convertOneSafe(item, makeGif, makeMp4)
                if (result == null) ok++ else fail++
                withContext(Dispatchers.Main) {
                    item.status = result ?: "done ✓"
                    adapter.notifyDataSetChanged()
                }
            }
            withContext(Dispatchers.Main) {
                converting = false
                statusText.text = "Finished: $ok ok, $fail failed. Saved in Downloads/WebpConverter"
                convertBtn.isEnabled = true
            }
        }
    }

    /** Returns null on success, or a short error string on failure (for on-screen debugging). */
    private fun convertOneSafe(item: FileItem, makeGif: Boolean, makeMp4: Boolean): String? {
        val baseName = item.name.removeSuffix(".webp").removeSuffix(".WEBP")
        val inFile = File(cacheDir, "in_${System.nanoTime()}.webp")
        try {
            contentResolver.openInputStream(item.uri)?.use { input ->
                inFile.outputStream().use { input.copyTo(it) }
            } ?: return "err: can't open input"

            if (!inFile.exists() || inFile.length() == 0L) {
                return "err: copy empty (${inFile.length()}b)"
            }

            var lastErr: String? = null

           if (makeGif) {
                val outGif = File(cacheDir, "$baseName.gif")
                // Simple single-pass conversion - more robust against animated-webp
                // frame-count quirks than the palettegen/paletteuse two-pass method.
                val cmd = "-y -i \"${inFile.absolutePath}\" -loop 0 " +
                        "\"${outGif.absolutePath}\""
                val session = FFmpegKit.execute(cmd)
                if (ReturnCode.isSuccess(session.returnCode) && outGif.length() > 0) {
                    if (!saveToDownloads(outGif, "$baseName.gif", "image/gif")) {
                        lastErr = "err: save-fail gif"
                    }
                } else {
                    lastErr = "ff-gif rc=${session.returnCode} ${session.failStackTrace?.take(60) ?: session.output?.takeLast(80)}"
                }
                outGif.delete()
            }
            if (makeMp4) {
                val outMp4 = File(cacheDir, "$baseName.mp4")
                val cmd = "-y -i \"${inFile.absolutePath}\" " +
                        "-vf \"scale=trunc(iw/2)*2:trunc(ih/2)*2\" " +
                        "-c:v libx264 -pix_fmt yuv420p -movflags +faststart " +
                        "\"${outMp4.absolutePath}\""
                val session = FFmpegKit.execute(cmd)
                if (ReturnCode.isSuccess(session.returnCode) && outMp4.length() > 0) {
                    if (!saveToDownloads(outMp4, "$baseName.mp4", "video/mp4")) {
                        lastErr = "err: save-fail mp4"
                    }
                } else {
                    lastErr = "ff-mp4 rc=${session.returnCode} ${session.failStackTrace?.take(60) ?: session.output?.takeLast(80)}"
                }
                outMp4.delete()
            }

            return lastErr
        } catch (e: Throwable) {
            return "exc: ${e.javaClass.simpleName}: ${e.message?.take(80)}"
        } finally {
            inFile.delete()
        }
    }

    /** Copies a finished file into public Downloads/WebpConverter. */
    private fun saveToDownloads(src: File, displayName: String, mime: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/WebpConverter"
                    )
                }
                val uri = contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return false
                contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(src).use { it.copyTo(out) }
                }
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "WebpConverter"
                )
                dir.mkdirs()
                val dest = File(dir, displayName)
                FileInputStream(src).use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
