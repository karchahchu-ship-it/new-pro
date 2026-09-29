package com.example.network

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object ChromeLauncher {

    /**
     * Opens the URL in Google Chrome if available, or falls back to the system browser.
     * Per requirement 8:
     * "افتح Web Interface الخاصة بالـ ESP32. الرابط: http://IP
     * ويجب فتح الرابط في Google Chrome إذا كان متوفراً.
     * إذا لم يكن Chrome متوفراً، استخدم Android browser المناسب."
     */
    fun openWebInterface(context: Context, url: String) {
        val uri = Uri.parse(url)

        // Try specifically with Google Chrome package
        val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.android.chrome")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(chromeIntent)
        } catch (e: ActivityNotFoundException) {
            // Chrome is not installed, fallback to system browser
            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(fallbackIntent)
            } catch (e2: Exception) {
                Toast.makeText(
                    context,
                    "Aucun navigateur Web n'est disponible sur l'appareil",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e3: Exception) {
            // Generic catch fallback
            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(fallbackIntent)
            } catch (ignored: Exception) {}
        }
    }

    fun copyToClipboard(context: Context, text: String, label: String = "IP") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$text copié dans le presse-papiers", Toast.LENGTH_SHORT).show()
    }
}
