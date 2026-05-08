package app.haulio.android.features.scanner

import android.Manifest
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.haulio.android.features.scanner.ui.ScanOverlay
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.koin.androidx.compose.koinViewModel
import java.util.concurrent.Executors

/**
 * Full-screen barcode scanner powered by CameraX + ML Kit.
 *
 * Flow:
 * 1. Requests CAMERA permission at runtime.
 * 2. Starts a CameraX [Preview] + [ImageAnalysis] pipeline.
 * 3. ML Kit processes every frame looking for QR, Code 128, and PDF 417.
 * 4. On detection → [BarcodeScannerViewModel.onBarcodeDetected] → address extraction + geocode.
 * 5. [ScannerState.Success] → calls [onAddressExtracted] with normalised address + pre-geocoded suggestion.
 * 6. [ScannerState.Fallback] → shows raw text in editable field so driver can correct it.
 *
 * @param onAddressExtracted  Called with the extracted/geocoded address string so the caller
 *                            can pre-fill [AddressSearchScreen].
 * @param onBack              Navigate back (user cancelled scan).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BarcodeScannerScreen(
    onAddressExtracted: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BarcodeScannerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    when {
        !cameraPermission.status.isGranted -> {
            PermissionRationale(
                onRequest = { cameraPermission.launchPermissionRequest() },
                onBack    = onBack,
            )
        }
        state is ScannerState.Processing -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Text("Reading label…", modifier = Modifier.padding(top = 72.dp))
            }
        }
        state is ScannerState.Success -> {
            val s = state as ScannerState.Success
            onAddressExtracted(s.extractedAddress)
        }
        state is ScannerState.Fallback -> {
            val f = state as ScannerState.Fallback
            FallbackEditor(
                raw     = f.rawBarcode,
                message = f.message,
                onUseText = { onAddressExtracted(it) },
                onRetry   = viewModel::resetScanner,
            )
        }
        state is ScannerState.Error -> {
            val e = state as ScannerState.Error
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(e.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Go Back") }
                }
            }
        }
        else -> {
            // Scanning
            CameraPreview(
                onBarcodeDetected = viewModel::onBarcodeDetected,
                onError = viewModel::onCameraError,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Camera preview composable
// ---------------------------------------------------------------------------

@Composable
private fun CameraPreview(
    onBarcodeDetected: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor      = remember { Executors.newSingleThreadExecutor() }

    val previewView = remember { PreviewView(context) }
    val scanner     = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory  = { previewView },
            modifier = Modifier.fillMaxSize(),
        ) { view ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = view.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val inputImage = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees,
                        )
                        scanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                barcodes
                                    .firstOrNull { it.valueType in SUPPORTED_TYPES }
                                    ?.rawValue
                                    ?.let { onBarcodeDetected(it) }
                            }
                            .addOnFailureListener { err ->
                                Log.e("BarcodeScanner", "ML Kit error", err)
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                } catch (ex: Exception) {
                    onError(ex.message ?: "Camera bind failed")
                }
            }, ContextCompat.getMainExecutor(context))
        }

        ScanOverlay(hint = "Align shipping label barcode")
    }
}

private val SUPPORTED_TYPES = setOf(
    Barcode.TYPE_URL,
    Barcode.TYPE_TEXT,
    Barcode.TYPE_UNKNOWN,
)

// ---------------------------------------------------------------------------
// Permission rationale screen
// ---------------------------------------------------------------------------

@Composable
private fun PermissionRationale(
    onRequest: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = "Camera permission is required to scan package barcodes.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequest) { Text("Grant Camera Permission") }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBack)    { Text("Cancel") }
        }
    }
}

// ---------------------------------------------------------------------------
// Fallback editor when extraction fails
// ---------------------------------------------------------------------------

@Composable
private fun FallbackEditor(
    raw: String,
    message: String,
    onUseText: (String) -> Unit,
    onRetry: () -> Unit,
) {
    var editableText by remember { mutableStateOf(raw) }

    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Column {
            Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value         = editableText,
                onValueChange = { editableText = it },
                label         = { Text("Extracted text") },
                modifier      = Modifier.fillMaxWidth(),
                minLines      = 3,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onUseText(editableText) }, modifier = Modifier.fillMaxWidth()) {
                Text("Use this address")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Scan again")
            }
        }
    }
}
