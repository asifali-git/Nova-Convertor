package com.nova.converter.core

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import java.io.File

/**
 * "File kahan hai" + "folder kholo" — pure helpers, koi UI nahi.
 *
 * Outputs hamesha MediaStore se save hote hain (`Outputs.saveViaMediaStore`):
 *   video → Movies/NovaConverter
 *   audio → Music/NovaConverter
 *   image → Pictures/NovaConverter
 * Yahi woh jagah hai jahan user file Gallery/Files app me actually dekhta hai,
 * isliye "Open folder" yahin kholta hai — cache/app-private path nahi, wo
 * user ke liye bekaar hota hai.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * FOLDER OPEN KARNE KA MASLA (field report: "no file manager found")
 * ─────────────────────────────────────────────────────────────────────────
 * Sabse pehle `DocumentsContract.buildDocumentUri("com.android.externalstorage.documents",
 * "primary:Pictures/NovaConverter")` + `ACTION_VIEW` + `vnd.android.document/directory`
 * use hota tha. Ye *theoretically* sahi hai, par bahut saare phones (is device
 * par Infinix + `com.google.android.documentsui`) par ye **SecurityException**
 * maar deta hai: DocumentsUI ka ExternalStorageProvider apne authority ke
 * andar caller ki storage access check karta hai, aur normal app ke paas
 * MANAGE_DOCUMENTS (signature-level) hota hi nahi. Result: `startActivity`
 * throw → pehle ka code use `catch (_: Throwable)` se chup chaap nigal deta tha
 * → user ko galat message ("no file manager found") jabki file manager MOJUD
 * tha, sirf direct-dokhol ka route band tha.
 *
 * `file://` URI bhi option nahi hai — Android 7+ pe FileUriExposedException,
 * aur FileProvider folder serve hi nahi kar sakta (path traversal block).
 *
 * Isliye ab **5-step fallback chain** hai. Har step pehle wala route try karta
 * hai, har failure pe agla. Aakhir me system ka folder-picker (guaranteed
 * chalta hai) + clipboard fallback. Har step Logcat me `NovaFiles` tag se
 * log hota hai taaki field me exact pata chale kaunsa step fail hua.
 */
object FileActions {

    private const val TAG = "NovaFiles"
    private const val SUBFOLDER = "NovaConverter"
    private const val AUTHORITY = "com.android.externalstorage.documents"
    private const val PRIMARY = "primary"
    private const val MIME_DIR = "vnd.android.document/directory"
    private const val MIME_ROOT = "vnd.android.document/root"

    /** Folder-open ka natija — caller iske hisaab se message chunata hai. */
    enum class FolderOpen {
        /** Seedha us folder me chala gaya */
        OPENED,

        /** System folder-picker khula (user ko "Use this folder" dabana hoga) */
        PICKER,

        /** Koi route kaam nahi — path clipboard me daal diya */
        COPIED
    }

    /** Public folder jisme files save hoti hain — "Pictures/NovaConverter" */
    fun savedFolder(mime: String): String = when {
        mime.startsWith("video/") -> "${Environment.DIRECTORY_MOVIES}/$SUBFOLDER"
        mime.startsWith("audio/") -> "${Environment.DIRECTORY_MUSIC}/$SUBFOLDER"
        mime.startsWith("image/") -> "${Environment.DIRECTORY_PICTURES}/$SUBFOLDER"
        else -> "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER"
    }

    /** User-facing location of a saved file — "Pictures/NovaConverter/clip.mp4" */
    fun locationOf(fileName: String, mime: String): String =
        "${savedFolder(mime)}/${File(fileName).name}"

    /** Folder abhi bana hi nahi hoga (pehli baar) — khaali folder dikhane se bekaar */
    private fun ensureDir(rel: String) {
        try {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(rel)
            if (!dir.exists()) dir.mkdirs()
        } catch (_: Throwable) {
        }
    }

    private fun docUri(docId: String): Uri? = try {
        DocumentsContract.buildDocumentUri(AUTHORITY, docId)
    } catch (_: Throwable) {
        null
    }

    private fun rootUri(): Uri? = try {
        DocumentsContract.buildRootUri(AUTHORITY, PRIMARY)
    } catch (_: Throwable) {
        null
    }

    private fun needsNewTask(context: Context) = context !is android.app.Activity

    /**
     * DocumentsUI (ya OEM file manager) ko folder ka URI dekar ACTION_VIEW bhejta
     * hai. `grant` = FLAG_GRANT_READ_URI_PERMISSION lagana hai ya nahi — kuch
     * providers is flag ke saath "foreign authority" maan ke SecurityException
     * dete hain, isliye dono variants try karte hain.
     */
    private fun tryViewDirectory(context: Context, uri: Uri, mime: String, grant: Boolean): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            // DocumentsUI is extra se seedha us folder pe khulta hai
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
            if (grant) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (needsNewTask(context)) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no handler for $mime ($uri): ${e.message}")
            false
        } catch (e: SecurityException) {
            // DocumentsUI ExternalStorageProvider ne mana kiya — common on OEM builds
            Log.w(TAG, "SecurityException for $uri grant=$grant: ${e.message}")
            false
        } catch (e: Throwable) {
            Log.w(TAG, "open $uri failed: ${e.message}")
            false
        }
    }

    /**
     * Folder kholo — best-effort, kabhi crash nahi.
     *
     * Order: exact folder → parent public folder → storage root → system picker
     * → clipboard. Pehla do try DocumentsUI hai, teesra root URI (kuch OEM
     * file managers yehi samajhte hain), chautha `ACTION_OPEN_DOCUMENT_TREE`
     * jo har Android device par guaranteed chalta hai (wahan user ko bas
     * "Use this folder" confirm karna hota hai).
     */
    fun openFolder(context: Context, mime: String): FolderOpen {
        val rel = savedFolder(mime)
        ensureDir(rel)
        val label = "${Environment.getExternalStorageDirectory()}/$rel"
        val exact = docUri("$PRIMARY:$rel")
        val parentRel = rel.substringBeforeLast('/', "")

        // 1 + 2: exact folder, phir wahi bina URI-grant flag ke
        if (exact != null) {
            if (tryViewDirectory(context, exact, MIME_DIR, true)) return FolderOpen.OPENED
            if (tryViewDirectory(context, exact, MIME_DIR, false)) return FolderOpen.OPENED
        }

        // 3: parent folder (Movies/, Pictures/, Music/) — DocumentsUI kabhi
        //    isko zyada willingly khulta hai
        if (parentRel.isNotEmpty()) {
            docUri("$PRIMARY:$parentRel")?.let { parent ->
                if (tryViewDirectory(context, parent, MIME_DIR, true)) return FolderOpen.OPENED
            }
        }

        // 4: storage root (gallery/phone app me "Internal storage" seedha)
        rootUri()?.let { root ->
            if (tryViewDirectory(context, root, MIME_ROOT, true)) return FolderOpen.OPENED
        }

        // 5: system folder picker — DocumentsUI ka guaranteed route. User ko
        //    bas "Use this folder" dabana hoga, aur wo hamesha folder pe khulta hai.
        //    Ye bhi fail ho (aabloud launcher etc.) to clipboard — kam se kam
        //    path to mil jayega.
        val opened = tryOpenTreePicker(context, exact)
        if (opened) {
            Log.i(TAG, "opened via folder picker: $label")
            return FolderOpen.PICKER
        }
        copy(context, label)
        Log.i(TAG, "all folder routes failed, path copied: $label")
        return FolderOpen.COPIED
    }

    /**
     * `ACTION_OPEN_DOCUMENT_TREE` + `EXTRA_INITIAL_URI`. Ye har device par
     * chalta hai (system DocumentsUI hi handle karta hai). Extra se wo seedha
     * NovaConverter folder dikhata hai; user "Use this folder" dabaye to wahi
     * set hota hai. Exception aaye to false.
     */
    private fun tryOpenTreePicker(context: Context, initial: Uri?): Boolean {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
            initial?.let {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, it)
                // ACTION_OPEN_DOCUMENT_TREE pe ye extra "seedha iss folder pe jao"
                // ka official tareeka hai (EXTRA_INITIAL_URI ke saath).
                putExtra("android.provider.extra.INITIAL_URI", it)
            }
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "document tree picker failed: ${e.message}")
            false
        }
    }

    /** Path/filename clipboard me copy — folder kholne ka har route fail ho to yehi raasta hai */
    fun copy(context: Context, label: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            cm.setPrimaryClip(ClipData.newPlainText(label, label))
        } catch (_: Throwable) {
        }
    }

    /** `content://` URI for a local file (share / in-app viewers ke liye) */
    fun uriFor(context: Context, path: String): Uri? = try {
        androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", File(path)
        )
    } catch (_: Throwable) {
        Uri.fromFile(File(path))
    }
}