// file: app/src/main/java/com/tubes/purry/utils/CustomMediaButtonReceiver.kt
package com.tubes.purry.utils // Or com.tubes.purry.player

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media.session.MediaButtonReceiver // Static methods from here
import com.tubes.purry.ui.player.PlayerController

class CustomMediaButtonReceiver : MediaButtonReceiver() { // Extend the original
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON || !intent.hasExtra(Intent.EXTRA_KEY_EVENT)) {
            Log.w("CustomMediaBtnReceiver", "Received an invalid media button intent. Action: ${intent.action}")
            // Optionally call super if you want to handle some default cases, but for this fix, we avoid it.
            // super.onReceive(context, intent); // This is what throws the error if no service is found.
            return
        }

        Log.d("CustomMediaBtnReceiver", "Media button event received: ${intent.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT)}")

        // Ensure PlayerController (and its MediaSession) is initialized.
        // Application context is crucial here because the receiver's context lifecycle is short.
        val appContext = context.applicationContext
        PlayerController.initialize(appContext) // initialize is idempotent

        if (PlayerController.isMediaSessionActive()) {
            try {
                // Use the static handleIntent from androidx.media.session.MediaButtonReceiver
                // This method correctly extracts the KeyEvent and dispatches it to the session's callback.
                handleIntent(PlayerController.getMediaSessionCompat(), intent)
                Log.d("CustomMediaBtnReceiver", "Intent forwarded to PlayerController's MediaSession.")
            } catch (e: Exception) {
                Log.e("CustomMediaBtnReceiver", "Error handling media button intent with active session: ${e.message}", e)
            }
        } else {
            Log.e("CustomMediaBtnReceiver", "MediaSession is NOT active in PlayerController. Cannot handle media button directly.")
            // Fallback: Attempt to start MainActivity. This is a less direct way to handle the event,
            // as the key event might get lost if MainActivity doesn't explicitly re-handle it from this intent.
            // The primary goal is to have PlayerController's session active via Application.onCreate().
            val pm = appContext.packageManager
            val activityIntent = pm.getLaunchIntentForPackage(appContext.packageName)
            if (activityIntent != null) {
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // You might try to re-package the KeyEvent if MainActivity is designed to handle it
                // activityIntent.putExtra(Intent.EXTRA_KEY_EVENT, intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT))
                appContext.startActivity(activityIntent)
                Log.w("CustomMediaBtnReceiver", "Attempted to start MainActivity as a fallback.")
            } else {
                Log.e("CustomMediaBtnReceiver", "Could not get launch intent for package.")
            }
        }
    }
}