package com.example.signconnect.ui.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel(),
    onNavigateToResult: (String, String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Estado UI y permisos
    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ==
                        PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED
            }
        )
    }

    // Estado para la selección de cámara (frontal o trasera)
    var isFrontCamera by remember { mutableStateOf(true) }

    var isRecording by remember { mutableStateOf(false) }
    var showCountdown by remember { mutableStateOf(false) }
    var countdownValue by remember { mutableStateOf(0) }

    // Launcher para solicitar permisos
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission

        // Manejar permisos de almacenamiento según la versión Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasStoragePermission = permissions[Manifest.permission.READ_MEDIA_VIDEO] ?: hasStoragePermission
        } else {
            hasStoragePermission = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: hasStoragePermission
        }
    }

    // Launcher para seleccionar video de la galería
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // El usuario seleccionó un video, procesarlo con la API
            Log.d("CameraScreen", "Video seleccionado de la galería: $it")
            // Subir video a la API
            viewModel.uploadVideo(context, it)
        }
    }

    // Componentes para la cámara
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    // IMPORTANTE: Crear el PreviewView como un estado composable
    val previewView = remember {
        PreviewView(context).apply {
            // Configurar el modo de implementación
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            // Asegurar que sea visible
            this.visibility = android.view.View.VISIBLE
        }
    }

    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }

    // Solicitar permisos si no los tiene
    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        // Añadir permisos de almacenamiento según la versión Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (!hasCameraPermission || !hasAudioPermission || !hasStoragePermission) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // Inicializar la cámara cuando la vista se compone o cuando cambia la selección de cámara
    LaunchedEffect(previewView, isFrontCamera) {
        try {
            val cameraProvider = cameraProviderFuture.get()

            // Crear un preview
            val preview = Preview.Builder().build()

            // Configuración para la grabación de video
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(
                    Quality.SD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                ))
                .setAspectRatio(AspectRatio.RATIO_4_3)
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            // Selector de cámara según el estado
            val cameraSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            // Unbind antes de rebind para evitar excepciones
            cameraProvider.unbindAll()

            // Vincular casos de uso a la cámara
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                videoCapture
            )

            // Asignar el surface provider DESPUÉS de vincular
            preview.setSurfaceProvider(previewView.surfaceProvider)

            Log.d("CameraScreen", "Cámara inicializada correctamente: ${if(isFrontCamera) "Frontal" else "Trasera"}")
        } catch (e: Exception) {
            Log.e("CameraScreen", "Error al inicializar la cámara", e)
        }
    }

    // Manejo de countdown para grabación
    LaunchedEffect(showCountdown, countdownValue) {
        if (showCountdown) {
            if (countdownValue > 0) {
                delay(1000)
                countdownValue -= 1
            } else {
                showCountdown = false
                stopRecordingSafely(recording, viewModel, context)
                isRecording = false
            }
        }
    }

    // Observar el estado UI
    LaunchedEffect(uiState) {
        when (uiState) {
            is CameraUIState.Success -> {
                val state = uiState as CameraUIState.Success
                onNavigateToResult(state.videoUri, state.apiResponse)
                viewModel.resetState()
            }
            is CameraUIState.Error -> {
                Toast.makeText(
                    context,
                    (uiState as CameraUIState.Error).message,
                    Toast.LENGTH_LONG
                ).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    // UI principal
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            if (hasCameraPermission && hasAudioPermission) {
                // Vista de la cámara - IMPORTANTE: usa fillMaxSize() para el AndroidView
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .aspectRatio(1f) // Forzar aspecto cuadrado 1:1
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // UI encima de la cámara
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Sección superior: Instrucciones y botones
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Instrucciones para el usuario
                        Text(
                            text = "Realiza una seña frente a la cámara",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Black,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.7f))
                                .padding(8.dp),
                            textAlign = TextAlign.Center
                        )

                        // Fila con botones de acción
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Botón para seleccionar video de la galería
                            IconButton(
                                onClick = {
                                    // Solo permitir cuando no está grabando
                                    if (!isRecording && uiState !is CameraUIState.Loading) {
                                        galleryLauncher.launch("video/*")
                                    }
                                },
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    )
                                    .padding(4.dp),
                                enabled = !isRecording && uiState !is CameraUIState.Loading
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Seleccionar de galería",
                                    tint = if (isRecording || uiState is CameraUIState.Loading)
                                        Color.Gray
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            // Botón para cambiar entre cámaras
                            IconButton(
                                onClick = {
                                    // Solo permitir cambiar cuando no está grabando
                                    if (!isRecording) {
                                        isFrontCamera = !isFrontCamera
                                    }
                                },
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    )
                                    .padding(4.dp),
                                enabled = !isRecording
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlipCameraAndroid,
                                    contentDescription = "Cambiar cámara",
                                    tint = if (isRecording) Color.Gray else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Espacio para mostrar cuenta regresiva
                    if (showCountdown) {
                        Text(
                            text = "$countdownValue",
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .padding(16.dp)
                        )
                    } else if (isRecording) {
                        Text(
                            text = "Grabando...",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier
                                .background(Color.Red.copy(alpha = 0.7f))
                                .padding(8.dp)
                        )
                    } else if (uiState is CameraUIState.Loading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = (uiState as CameraUIState.Loading).message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Botón de grabación
                    if (uiState is CameraUIState.Loading) {
                        // Espacio reservado para mantener la estructura
                        Box(
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .size(72.dp)
                        )
                    } else {
                        IconButton(
                            onClick = {
                                if (!isRecording && allPermissionsGranted(context)) {
                                    startRecordingSafely(videoCapture, executor, context) { newRecording ->
                                        recording = newRecording
                                        isRecording = true
                                        // Configurar countdown de 2.5 segundos
                                        showCountdown = true
                                        countdownValue = 3
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .size(72.dp)
                                .background(
                                    if (isRecording) Color.Red else Color.White,
                                    CircleShape
                                ),
                            enabled = !isRecording && uiState !is CameraUIState.Loading
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Grabar video",
                                tint = if (isRecording) Color.White else Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            } else {
                // Mensaje si no hay permisos
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Se requieren permisos de cámara y micrófono para usar esta aplicación",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }

    // Limpiar recursos al salir
    DisposableEffect(Unit) {
        onDispose {
            coroutineScope.launch {
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                } catch (e: Exception) {
                    Log.e("CameraScreen", "Error al liberar la cámara", e)
                }
                executor.shutdown()
            }
        }
    }
}

private fun allPermissionsGranted(context: Context): Boolean {
    val cameraPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    val audioPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    return cameraPermission && audioPermission && storagePermission
}

// Clase para mantener la URI del video actual
object VideoUriHolder {
    private var currentVideoUri: android.net.Uri? = null

    fun setCurrentVideoUri(uri: android.net.Uri) {
        currentVideoUri = uri
    }

    fun getCurrentVideoUri(): android.net.Uri? {
        return currentVideoUri
    }

    fun clear() {
        currentVideoUri = null
    }
}

private fun startRecordingSafely(
    videoCapture: VideoCapture<Recorder>?,
    executor: Executor,
    context: Context,
    onRecordingStarted: (Recording) -> Unit
) {
    if (videoCapture == null) {
        Log.e("CameraScreen", "VideoCapture no inicializado")
        return
    }

    // Verificar permisos explícitamente
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        Log.e("CameraScreen", "Falta de permisos para grabar")
        return
    }

    try {
        val name = "SignConnect-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(java.util.Date())}"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SignConnect")
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        // Preparar la grabación
        val preparedRecording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .apply {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }

        // Iniciar la grabación
        val recording = preparedRecording.start(executor) { recordEvent ->
            when (recordEvent) {
                is VideoRecordEvent.Start -> {
                    Log.d("CameraScreen", "Grabación iniciada")
                }
                is VideoRecordEvent.Finalize -> {
                    if (recordEvent.hasError()) {
                        Log.e("CameraScreen", "Error en la grabación: ${recordEvent.error}")
                    } else {
                        // Capturar la URI directamente del evento de finalización
                        val videoUri = recordEvent.outputResults.outputUri
                        Log.d("CameraScreen", "Grabación guardada: $videoUri")

                        // Almacenar temporalmente la URI para usarla en stopRecordingSafely
                        VideoUriHolder.setCurrentVideoUri(videoUri)
                    }
                }
                else -> {
                    // No hacer nada para otros eventos
                }
            }
        }

        // Llamar al callback
        onRecordingStarted(recording)

    } catch (e: SecurityException) {
        Log.e("CameraScreen", "SecurityException al iniciar grabación", e)
    } catch (e: Exception) {
        Log.e("CameraScreen", "Error al iniciar grabación", e)
    }
}

private fun stopRecordingSafely(
    recording: Recording?,
    viewModel: CameraViewModel,
    context: Context
) {
    // Verificar si la grabación es nula
    if (recording == null) {
        Log.e("CameraScreen", "Intento de detener una grabación nula")
        return
    }

    try {
        // Detener grabación - esto disparará el evento VideoRecordEvent.Finalize
        recording.stop()

        // Esperar brevemente para que el evento Finalize tenga tiempo de procesar
        // Esto es necesario porque stop() es asíncrono
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Obtener la URI del video desde el holder
            val videoUri = VideoUriHolder.getCurrentVideoUri()

            if (videoUri != null) {
                Log.d("CameraScreen", "Subiendo video a la API: $videoUri")
                // Subir el video a la API
                viewModel.uploadVideo(context, videoUri)
                // Limpiar la URI después de usarla
                VideoUriHolder.clear()
            } else {
                Log.e("CameraScreen", "No se pudo obtener la URI del video grabado")
                // Fallback: usar el método anterior si no tenemos la URI (esto no debería ocurrir)
                fallbackGetVideoUri(context, viewModel)
            }
        }, 500) // Esperar 500ms para asegurar que el evento Finalize se haya procesado

        // Liberar recursos
        recording.close()

    } catch (e: Exception) {
        Log.e("CameraScreen", "Error al detener la grabación", e)
    }
}

// Método de fallback por si falla el método principal
private fun fallbackGetVideoUri(context: Context, viewModel: CameraViewModel) {
    try {
        // Verificar permisos de almacenamiento
        val storagePermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (!storagePermissionGranted) {
            Log.e("CameraScreen", "No hay permiso para leer el almacenamiento")
            return
        }

        // Obtener la URI del video más reciente
        val contentResolver = context.contentResolver
        val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        contentResolver.query(videoUri, projection, null, null, sortOrder)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val id = cursor.getLong(idColumn)
                val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendPath(id.toString()).build()

                Log.d("CameraScreen", "Fallback: Subiendo video más reciente a la API: $contentUri")
                // Subir el video a la API
                viewModel.uploadVideo(context, contentUri)
            } else {
                Log.e("CameraScreen", "No se encontró la grabación reciente")
            }
        }
    } catch (e: Exception) {
        Log.e("CameraScreen", "Error al obtener URI del video", e)
    }
}