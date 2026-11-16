// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

@Composable
fun MealCamera(onClose:()->Unit,onCaptured:(File)->Unit) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val executor=remember { ContextCompat.getMainExecutor(context) }
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed=it }
    val previewView=remember { PreviewView(context).apply { implementationMode=PreviewView.ImplementationMode.COMPATIBLE } }
    val preview=remember { Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) } }
    val capture=remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(90).build() }
    var ready by remember { mutableStateOf(false) }
    var taking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if(!allowed) permission.launch(Manifest.permission.CAMERA) }
    DisposableEffect(allowed,owner) {
        var disposed=false
        var provider:ProcessCameraProvider?=null
        if(allowed) {
            val future=ProcessCameraProvider.getInstance(context)
            future.addListener({
                if(!disposed) try {
                    provider=future.get()
                    provider!!.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,capture)
                    ready=true
                } catch(_:Exception) { error="Camera is unavailable. You can choose a photo from your gallery." }
            },executor)
        }
        onDispose { disposed=true;ready=false;provider?.unbind(preview,capture) }
    }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BackHandler(onBack=onClose)
        Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            if(allowed) AndroidView(factory={previewView},modifier=Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha=.65f)).padding(16.dp)) {
                Text(ai("Photograph your meal","Öğününün fotoğrafını çek"),color=Color.White,style=MaterialTheme.typography.titleMedium)
                Text(ai("Keep the whole plate in frame. Add weights in the description for a better estimate.","Tabağın tamamını kadraja al. Daha iyi sonuç için açıklamaya miktarları ekle."),color=Color.White)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha=.75f)).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                error?.let { Text(it,color=Color.White) }
                if(!allowed) {
                    Text(ai("Allow camera access to take a photo here.","Burada fotoğraf çekmek için kamera izni ver."),color=Color.White)
                    Button(onClick={permission.launch(Manifest.permission.CAMERA)}) { Text(ai("Allow camera","Kameraya izin ver")) }
                } else Button(enabled=ready && !taking,onClick=take@{
                    taking=true;error=null
                    capture.targetRotation=previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                    val file=try { File.createTempFile("meal-camera-",".jpg",context.cacheDir) }
                        catch(_:Exception) { taking=false;error="Not enough storage to take a photo.";return@take }
                    capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),executor,object:ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result:ImageCapture.OutputFileResults) { taking=false;onCaptured(file) }
                        override fun onError(exception:ImageCaptureException) { taking=false;file.delete();error="Could not take the photo. Please try again." }
                    })
                },modifier=Modifier.fillMaxWidth()) { Text(ai(if(taking) "Taking photo…" else "Take photo",if(taking) "Fotoğraf çekiliyor…" else "Fotoğraf çek")) }
                TextButton(onClick=onClose,enabled=!taking) { Text(ai("Back","Geri"),color=Color.White) }
            }
        }
    }
}
