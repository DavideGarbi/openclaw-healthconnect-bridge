package io.github.davidegarbi.openclaw_healthconnect_bridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity

class PermissionRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))
        startActivity(intent)
        finish()
    }

    companion object {
        private const val PRIVACY_URL =
            "https://github.com/DavideGarbi/openclaw-healthconnect-bridge#disclaimer"
    }
}
