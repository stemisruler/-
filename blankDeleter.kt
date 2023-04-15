import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val files = mutableListOf<File>()
            val uriList = mutableListOf<Uri>()
            val clipData = result.data?.clipData
            val uri = result.data?.data
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    val item = clipData.getItemAt(i)
                    val uri = item.uri
                    val name = getFileName(uri)
                    val file = File(cacheDir, name)
                    contentResolver.openInputStream(uri)?.copyTo(FileOutputStream(file))
                    files.add(file)
                    uriList.add(uri)
                }
            } else if (uri != null) {
                val name = getFileName(uri)
                val file = File(cacheDir, name)
                contentResolver.openInputStream(uri)?.copyTo(FileOutputStream(file))
                files.add(file)
                uriList.add(uri)
            }
            removeSilenceBeforeMusic(files)
            for (uri in uriList) {
                contentResolver.delete(uri, null, null)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Call the file picker when a button is clicked
        findViewById<Button>(R.id.button_pick_files).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/mpeg"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            filePicker.launch(intent)
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = ""
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }
        }
        return name
    }

    private fun removeSilenceBeforeMusic(files: List<File>) {
        files.forEach { file ->
            if (file.isFile && file.name.endsWith(".mp3")) {
                val inputStream = FileInputStream(file)
                val outputStream = FileOutputStream(File(file.parent, "temp.mp3"))
                val buffer = ByteArray(1024)
                var bytesRead: Int
                var foundStart = false
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!foundStart) {
                        // Search for the start of the music data (i.e. skip the ID3v2 tag)
                        val tagSize = getID3v2TagSize(buffer, bytesRead)
                        if (tagSize > 0) {
                            outputStream.write(buffer, tagSize, bytesRead - tagSize)
                        } else {
                            outputStream.write(buffer, 0, bytesRead)
                            foundStart = true
                        }
                    } else {
                        // Remove any silence before the music data
                        var i = 0
                        while (i < bytesRead) {
                            if (buffer[i] == 0.toByte()) {
                                i++
                            } else {
                                break
                            }
                        }
                        if (i < bytesRead) {
                            outputStream.write(buffer, i, bytesRead - i)
                        }
                    }
                }
                inputStream.close()
