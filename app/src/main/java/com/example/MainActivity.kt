package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.paperly.core.PaperlyApplication
import com.example.paperly.ui.PaperlyViewModel
import com.example.paperly.ui.navigation.PaperlyRootHost

class MainActivity : ComponentActivity() {

  private val paperlyApp by lazy { application as PaperlyApplication }
  private val viewModel: PaperlyViewModel by viewModels {
    PaperlyViewModel.Factory(paperlyApp.container)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    processIntent(intent)
    setContent {
      PaperlyRootHost(
        viewModel = viewModel,
        container = paperlyApp.container
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    processIntent(intent)
  }

  private fun processIntent(intent: Intent?) {
    if (intent == null) return
    val uris = mutableListOf<Uri>()
    when (intent.action) {
      Intent.ACTION_VIEW -> intent.data?.let { uris.add(it) }
      Intent.ACTION_SEND -> {
        val extraUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        extraUri?.let { uris.add(it) }
      }
      Intent.ACTION_SEND_MULTIPLE -> {
        val extraUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        if (extraUris != null) uris.addAll(extraUris)
      }
    }
    if (uris.isNotEmpty()) {
      viewModel.handleIncomingExternalUris(uris)
    }
  }
}
