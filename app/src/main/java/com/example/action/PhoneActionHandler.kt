package com.example.action

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log

data class PhoneActionResult(
    val success: Boolean,
    val actionName: String,
    val message: String
)

object PhoneActionHandler {
    private const val TAG = "PhoneActionHandler"

    /**
     * Inspects text for action tags like [ACTION:OPEN_WHATSAPP] or natural command phrases.
     */
    fun extractAction(text: String): String? {
        val upper = text.uppercase()
        return when {
            upper.contains("[ACTION:OPEN_WHATSAPP]") || upper.contains("OPEN_WHATSAPP") -> "OPEN_WHATSAPP"
            upper.contains("[ACTION:OPEN_YOUTUBE]") || upper.contains("OPEN_YOUTUBE") -> "OPEN_YOUTUBE"
            upper.contains("[ACTION:OPEN_CAMERA]") || upper.contains("OPEN_CAMERA") -> "OPEN_CAMERA"
            upper.contains("[ACTION:OPEN_SETTINGS]") || upper.contains("OPEN_SETTINGS") -> "OPEN_SETTINGS"
            upper.contains("[ACTION:OPEN_DIALER]") || upper.contains("OPEN_DIALER") -> "OPEN_DIALER"
            upper.contains("[ACTION:OPEN_BROWSER") || upper.contains("OPEN_BROWSER") -> "OPEN_BROWSER"
            // Natural language detection (English, Urdu, Roman Urdu)
            matchesWhatsApp(text) -> "OPEN_WHATSAPP"
            matchesYouTube(text) -> "OPEN_YOUTUBE"
            matchesCamera(text) -> "OPEN_CAMERA"
            matchesSettings(text) -> "OPEN_SETTINGS"
            matchesDialer(text) -> "OPEN_DIALER"
            else -> null
        }
    }

    private fun matchesWhatsApp(text: String): Boolean {
        val lower = text.lowercase()
        return (lower.contains("whatsapp") || lower.contains("واٹس ایپ")) &&
               (lower.contains("open") || lower.contains("kholo") || lower.contains("کھولو") || lower.contains("start"))
    }

    private fun matchesYouTube(text: String): Boolean {
        val lower = text.lowercase()
        return (lower.contains("youtube") || lower.contains("یوٹیوب")) &&
               (lower.contains("open") || lower.contains("kholo") || lower.contains("کھولو"))
    }

    private fun matchesCamera(text: String): Boolean {
        val lower = text.lowercase()
        return (lower.contains("camera") || lower.contains("کیمرہ")) &&
               (lower.contains("open") || lower.contains("kholo") || lower.contains("کھولو") || lower.contains("take photo"))
    }

    private fun matchesSettings(text: String): Boolean {
        val lower = text.lowercase()
        return (lower.contains("settings") || lower.contains("سیٹنگز")) &&
               (lower.contains("open") || lower.contains("kholo") || lower.contains("کھولو"))
    }

    private fun matchesDialer(text: String): Boolean {
        val lower = text.lowercase()
        return (lower.contains("dialer") || lower.contains("phone") || lower.contains("کال") || lower.contains("فون")) &&
               (lower.contains("open") || lower.contains("kholo") || lower.contains("کھولو"))
    }

    /**
     * Strips action tags from spoken and displayed text.
     */
    fun cleanResponseText(text: String): String {
        return text.replace(Regex("\\[ACTION:[A-Z_]+(:[^\\]]+)?\\]"), "").trim()
    }

    fun execute(context: Context, action: String): PhoneActionResult {
        return try {
            when (action) {
                "OPEN_WHATSAPP" -> openWhatsApp(context)
                "OPEN_YOUTUBE" -> openYouTube(context)
                "OPEN_CAMERA" -> openCamera(context)
                "OPEN_SETTINGS" -> openSettings(context)
                "OPEN_DIALER" -> openDialer(context)
                "OPEN_BROWSER" -> openBrowser(context, "https://www.google.com")
                else -> PhoneActionResult(false, action, "Unsupported action.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action $action: ${e.message}", e)
            PhoneActionResult(false, action, "Could not complete action: ${e.localizedMessage}")
        }
    }

    private fun openWhatsApp(context: Context): PhoneActionResult {
        val pm = context.packageManager
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return PhoneActionResult(true, "WhatsApp", "Opened WhatsApp successfully.")
            }
        }

        // Fallback: try web intent
        return try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            PhoneActionResult(true, "WhatsApp Web", "WhatsApp app is not installed, opened WhatsApp Web.")
        } catch (e: Exception) {
            PhoneActionResult(false, "WhatsApp", "WhatsApp is not installed on this device.")
        }
    }

    private fun openYouTube(context: Context): PhoneActionResult {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage("com.google.android.youtube")
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            PhoneActionResult(true, "YouTube", "Opened YouTube app.")
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            PhoneActionResult(true, "YouTube Web", "Opened YouTube in browser.")
        }
    }

    private fun openCamera(context: Context): PhoneActionResult {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            PhoneActionResult(true, "Camera", "Opened Camera.")
        } else {
            val stillIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (stillIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(stillIntent)
                PhoneActionResult(true, "Camera", "Opened Camera.")
            } else {
                PhoneActionResult(false, "Camera", "Camera application is not available.")
            }
        }
    }

    private fun openSettings(context: Context): PhoneActionResult {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return PhoneActionResult(true, "Settings", "Opened Android Settings.")
    }

    private fun openDialer(context: Context): PhoneActionResult {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return PhoneActionResult(true, "Phone", "Opened Phone dialer.")
    }

    private fun openBrowser(context: Context, url: String): PhoneActionResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return PhoneActionResult(true, "Browser", "Opened browser.")
    }
}
