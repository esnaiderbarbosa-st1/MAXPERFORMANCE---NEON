package com.example.utils

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

object LongScreenshotHelper {

    private const val TAG = "LongScreenshotHelper"

    /**
     * Checks if full screen capture feature should be available.
     * Accessible strictly in DEBUG / DEV builds.
     */
    val isAvailable: Boolean
        get() = BuildConfig.DEBUG || DeveloperMode.isEnabled

    /**
     * Captures the complete current screen, auto-scrolls if scrollable,
     * saves PNG to Pictures/MAXPERFORMANCE/Capturas/ with MP_YYYY-MM-DD_HH-mm-ss.png naming,
     * and automatically launches the native Android Share Sheet.
     */
    fun captureAndShare(context: Context, activity: Activity) {
        if (!isAvailable) {
            Toast.makeText(context, "Captura disponível apenas em modo DEV", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, "Gerando captura de tela...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val decorView = activity.window.decorView
                val rootView = decorView.findViewById<ViewGroup>(android.R.id.content) ?: decorView

                // Frame 1: Top section capture
                val topFrame = captureWindowViaPixelCopy(activity) 
                    ?: renderFullViewBitmap(rootView) 
                    ?: renderSoftwareLayerBitmap(rootView)

                // Scroll down to capture remaining content
                val hasScrolled = autoScrollToBottom(rootView)
                delay(300) // Delay for layout render after scroll

                var bitmap: Bitmap? = topFrame
                if (hasScrolled && topFrame != null) {
                    val bottomFrame = captureWindowViaPixelCopy(activity) ?: renderSoftwareLayerBitmap(rootView)
                    if (bottomFrame != null && bottomFrame != topFrame) {
                        // Stitch top and bottom frames into a single complete vertical image
                        try {
                            val stitchedHeight = topFrame.height + bottomFrame.height
                            val stitchedBitmap = Bitmap.createBitmap(topFrame.width, stitchedHeight, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(stitchedBitmap)
                            canvas.drawBitmap(topFrame, 0f, 0f, null)
                            canvas.drawBitmap(bottomFrame, 0f, topFrame.height.toFloat(), null)
                            bitmap = stitchedBitmap
                        } catch (e: OutOfMemoryError) {
                            Log.w(TAG, "OOM during bitmap stitching, using single frame", e)
                            bitmap = topFrame
                        }
                    }
                }

                if (bitmap == null) {
                    Toast.makeText(context, "Erro ao processar imagem da tela", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // IO Thread: File saving & Share Sheet
                withContext(Dispatchers.IO) {
                    var savedFile: File? = null
                    var contentUri: Uri? = null

                    try {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                        val timestamp = dateFormat.format(Date())
                        val fileName = "MP_$timestamp.png"

                        // 1. Primary storage: Cache dir (100% guaranteed writable on all Android versions without permissions)
                        val cacheDir = File(context.cacheDir, "capturas")
                        if (!cacheDir.exists()) {
                            cacheDir.mkdirs()
                        }
                        val cacheFile = File(cacheDir, fileName)
                        FileOutputStream(cacheFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        savedFile = cacheFile

                        // Insert into MediaStore for Android Gallery visibility
                        saveToMediaStoreSafely(context, cacheFile, fileName, bitmap)

                        // Obtain FileProvider URI for Share Intent
                        val authority = "${context.packageName}.fileprovider"
                        contentUri = FileProvider.getUriForFile(context, authority, cacheFile)

                    } catch (e: Exception) {
                        Log.e(TAG, "Failed saving screenshot PNG file", e)
                    } finally {
                        bitmap.recycle()
                    }

                    val finalFile = savedFile
                    val finalUri = contentUri

                    // Launch Share Sheet on Main Thread
                    withContext(Dispatchers.Main) {
                        if (finalFile != null && finalUri != null) {
                            Toast.makeText(
                                context,
                                "Captura salva em ${AppExportPaths.PICTURES_CAPTURES_RELATIVE_PATH}/${finalFile.name}",
                                Toast.LENGTH_LONG
                            ).show()

                            openShareSheet(context, finalUri)
                        } else {
                            Toast.makeText(context, "Erro ao salvar o arquivo da captura de tela.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in captureAndShare", e)
                Toast.makeText(context, "Falha na captura: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun autoScrollToBottom(rootView: View): Boolean {
        return try {
            val scrolledStandardView = findAndScrollViews(rootView)
            if (!scrolledStandardView) {
                dispatchScrollGesture(rootView)
                true
            } else {
                scrolledStandardView
            }
        } catch (e: Exception) {
            Log.w(TAG, "Auto-scroll execution failed", e)
            false
        }
    }

    private fun findAndScrollViews(view: View): Boolean {
        var scrolled = false
        if (view is android.widget.ScrollView) {
            view.fullScroll(View.FOCUS_DOWN)
            scrolled = true
        } else if (view is androidx.core.widget.NestedScrollView) {
            view.fullScroll(View.FOCUS_DOWN)
            scrolled = true
        } else if (view is ViewGroup) {
            if (view.javaClass.name.contains("RecyclerView")) {
                try {
                    val scrollToPosMethod = view.javaClass.getMethod("scrollToPosition", Int::class.javaPrimitiveType)
                    val getAdapterMethod = view.javaClass.getMethod("getAdapter")
                    val adapter = getAdapterMethod.invoke(view)
                    if (adapter != null) {
                        val getItemCountMethod = adapter.javaClass.getMethod("getItemCount")
                        val count = getItemCountMethod.invoke(adapter) as Int
                        if (count > 0) {
                            scrollToPosMethod.invoke(view, count - 1)
                            scrolled = true
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Reflection scroll on RecyclerView failed", e)
                }
            }
            if (!scrolled) {
                for (i in 0 until view.childCount) {
                    if (findAndScrollViews(view.getChildAt(i))) {
                        scrolled = true
                    }
                }
            }
        }
        return scrolled
    }

    private fun dispatchScrollGesture(view: View) {
        try {
            val now = SystemClock.uptimeMillis()
            val startX = (view.width.takeIf { it > 0 } ?: 1080) / 2f
            val startY = (view.height.takeIf { it > 0 } ?: 1920) * 0.8f
            val endY = (view.height.takeIf { it > 0 } ?: 1920) * 0.2f

            val downEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, startX, startY, 0)
            val moveEvent = MotionEvent.obtain(now, now + 100, MotionEvent.ACTION_MOVE, startX, endY, 0)
            val upEvent = MotionEvent.obtain(now, now + 200, MotionEvent.ACTION_UP, startX, endY, 0)

            view.dispatchTouchEvent(downEvent)
            view.dispatchTouchEvent(moveEvent)
            view.dispatchTouchEvent(upEvent)

            downEvent.recycle()
            moveEvent.recycle()
            upEvent.recycle()
        } catch (e: Exception) {
            Log.w(TAG, "Scroll gesture dispatch failed", e)
        }
    }

    private fun renderFullViewBitmap(rootView: View): Bitmap? {
        return try {
            val width = rootView.width.coerceAtLeast(1080)
            var height = rootView.height.coerceAtLeast(1920)

            val maxAllowedHeight = 8192
            val scrollHeight = measureScrollableHeight(rootView)
            if (scrollHeight > height) {
                height = scrollHeight.coerceAtMost(maxAllowedHeight)
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            canvas.drawColor(android.graphics.Color.parseColor("#121216"))
            rootView.draw(canvas)
            bitmap
        } catch (e: Throwable) {
            Log.e(TAG, "Error drawing view canvas bitmap", e)
            null
        }
    }

    private suspend fun captureWindowViaPixelCopy(activity: Activity): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            try {
                val window = activity.window
                val decorView = window.decorView
                val width = decorView.width.coerceAtLeast(1080)
                val height = decorView.height.coerceAtLeast(1920)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                val location = IntArray(2)
                decorView.getLocationInWindow(location)
                val rect = Rect(
                    location[0],
                    location[1],
                    location[0] + width,
                    location[1] + height
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    PixelCopy.request(
                        window,
                        rect,
                        bitmap,
                        { copyResult ->
                            if (copyResult == PixelCopy.SUCCESS) {
                                if (continuation.isActive) continuation.resume(bitmap)
                            } else {
                                Log.e(TAG, "PixelCopy failed with result: $copyResult")
                                if (continuation.isActive) continuation.resume(null)
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                } else {
                    if (continuation.isActive) continuation.resume(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "PixelCopy exception", e)
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun renderSoftwareLayerBitmap(rootView: View): Bitmap? {
        return try {
            val width = rootView.width.coerceAtLeast(1080)
            val height = rootView.height.coerceAtLeast(1920)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.parseColor("#121216"))

            val initialLayerType = rootView.layerType
            rootView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            rootView.draw(canvas)
            rootView.setLayerType(initialLayerType, null)
            bitmap
        } catch (e: Throwable) {
            Log.e(TAG, "Software layer rendering failed", e)
            null
        }
    }

    private fun measureScrollableHeight(view: View): Int {
        var maxHeight = view.height
        if (view is android.widget.ScrollView || view is androidx.core.widget.NestedScrollView) {
            val child = (view as ViewGroup).getChildAt(0)
            if (child != null) {
                maxHeight = maxOf(maxHeight, child.height)
            }
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val childHeight = measureScrollableHeight(view.getChildAt(i))
                maxHeight = maxOf(maxHeight, childHeight)
            }
        }
        return maxHeight
    }

    private fun saveToMediaStoreSafely(context: Context, file: File, fileName: String, bitmap: Bitmap) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, AppExportPaths.PICTURES_CAPTURES_RELATIVE_PATH)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not insert into MediaStore", e)
        }
    }

    private fun openShareSheet(context: Context, contentUri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Captura de tela - Max Performance")
                putExtra(Intent.EXTRA_TEXT, "Captura de tela enviada via Max Performance (DEV)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Compartilhar captura de tela")
            if (context !is Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching share intent chooser", e)
            Toast.makeText(context, "Erro ao abrir menu de compartilhamento", Toast.LENGTH_SHORT).show()
        }
    }
}
