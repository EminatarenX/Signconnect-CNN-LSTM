package com.example.signconnect.ui.result


import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.signconnect.data.model.ApiResponse
import com.example.signconnect.data.model.Prediction
import com.google.gson.Gson


@Composable
fun ResultScreen(
    videoUri: String,
    apiResponse: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val gson = Gson()

    val videoUriParsed = try {
        Uri.parse(videoUri)
    } catch (e: Exception) {
        Log.e("ResultScreen", "Error al parsear la URI del video: $videoUri", e)
        null
    }

    // Estado para controlar si el video está listo
    var isVideoReady by remember { mutableStateOf(false) }

    // Parsear la respuesta de la API
    val response = remember {
        try {
            gson.fromJson(apiResponse, ApiResponse::class.java)
        } catch (e: Exception) {
            Log.e("ResultScreen", "Error al parsear la respuesta de la API", e)
            null
        }
    }

    // Preparar reproductor de video
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            try {
                if (videoUriParsed != null) {
                    val mediaItem = MediaItem.fromUri(videoUriParsed)
                    setMediaItem(mediaItem)
                    repeatMode = Player.REPEAT_MODE_ALL // Reproducir en bucle
                    prepare()
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_READY -> {
                                    isVideoReady = true
                                    playWhenReady = true
                                    volume = 0f // Sin audio
                                }
                                Player.STATE_ENDED -> {
                                    // Auto-reiniciar en caso de finalización
                                    seekTo(0)
                                    play()
                                }
                            }
                        }
                    })
                } else {
                    Log.e("ResultScreen", "URI del video es nula")
                }
            } catch (e: Exception) {
                Log.e("ResultScreen", "Error al preparar el reproductor", e)
            }
        }
    }

    // Logs adicionales para debuggear
    LaunchedEffect(videoUri) {
        Log.d("ResultScreen", "Video URI: $videoUri")
        Log.d("ResultScreen", "API Response: $apiResponse")
    }

    // Limpiar recursos al salir
    DisposableEffect(videoUri) {
        onDispose {
            exoPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Volver"
                    )
                }

                Text(
                    text = "Resultado del reconocimiento",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Reproductor de video
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 4.dp
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (videoUriParsed != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true // Permitir controles por si hay problemas
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (!isVideoReady) {
                            // Indicador de carga mientras el video se prepara
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.7f)),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Cargando video...",
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        // Mensaje de error si no se puede cargar el video
                        Text(
                            text = "No se pudo cargar el video",
                            color = Color.Red,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Resultados de la predicción
            if (response != null && response.predictions.isNotEmpty()) {
                Text(
                    text = "Predicciones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Mostrar las predicciones
                response.predictions.forEach { prediction ->
                    PredictionItem(prediction = prediction)
                }
            } else {
                Text(
                    text = "No se pudieron cargar las predicciones",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Red,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Botón para volver a grabar
            Button(
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Grabar otro video")
            }
        }
    }
}

@Composable
fun PredictionItem(prediction: Prediction) {
    val backgroundColor = when (prediction.rank) {
        1 -> MaterialTheme.colorScheme.primaryContainer
        2 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${prediction.rank}. ${prediction.className}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (prediction.rank == 1) FontWeight.Bold else FontWeight.Normal
                )

                Text(
                    text = "${(prediction.probability * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { prediction.probability },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}