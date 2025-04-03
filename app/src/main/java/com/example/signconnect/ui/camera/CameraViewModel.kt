package com.example.signconnect.ui.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.signconnect.data.model.ApiService
import com.example.signconnect.data.model.ApiResponse
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.UUID


class CameraViewModel : ViewModel() {
    private val apiService = ApiService.create()
    private val gson = Gson()

    private val _uiState = MutableStateFlow<CameraUIState>(CameraUIState.Initial)
    val uiState: StateFlow<CameraUIState> = _uiState.asStateFlow()

    // Crear un ID único para esta sesión de grabación
    private var currentSessionId = UUID.randomUUID().toString()

    fun uploadVideo(context: Context, videoUri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = CameraUIState.Loading

                // Asignar un nuevo ID para esta operación
                val operationId = UUID.randomUUID().toString()

                Log.d("CameraViewModel", "[$operationId] Inicio de upload de video: $videoUri")

                // Convertir URI a File
                val videoFile = getFileFromUri(context, videoUri)

                Log.d("CameraViewModel", "[$operationId] Archivo creado: ${videoFile.absolutePath}, tamaño: ${videoFile.length()} bytes")

                // Crear parte multipart para enviar a la API
                val requestFile = videoFile.asRequestBody("video/mp4".toMediaTypeOrNull())
                val videoPart = MultipartBody.Part.createFormData("video", videoFile.name, requestFile)

                Log.d("CameraViewModel", "[$operationId] Enviando video a la API...")

                // Llamar a la API
                val response = apiService.predictSignLanguage(videoPart)

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse != null) {
                        // Resetear el ID de sesión para la próxima grabación
                        val previousSessionId = currentSessionId
                        currentSessionId = UUID.randomUUID().toString()

                        // Convertir la respuesta a JSON string para pasarla por navegación
                        val apiResponseJson = gson.toJson(apiResponse)
                        Log.d("CameraViewModel", "[$operationId] Respuesta de API recibida para sesión $previousSessionId")

                        // Asegurar que el video aún existe
                        if (videoFile.exists()) {
                            _uiState.value = CameraUIState.Success(videoUri.toString(), apiResponseJson)
                        } else {
                            _uiState.value = CameraUIState.Error("El archivo de video ha sido eliminado")
                        }
                    } else {
                        _uiState.value = CameraUIState.Error("Respuesta vacía de la API")
                    }
                } else {
                    _uiState.value = CameraUIState.Error("Error: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error al cargar el video", e)
                _uiState.value = CameraUIState.Error("Error: ${e.message}")
            }
        }
    }

    private fun getFileFromUri(context: Context, uri: Uri): File {
        // Crear un archivo temporal único para esta sesión
        val tempFile = File.createTempFile("video_${currentSessionId}", ".mp4", context.cacheDir)

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        Log.d("CameraViewModel", "Video copiado a archivo temporal: ${tempFile.absolutePath}")
        return tempFile
    }

    fun resetState() {
        _uiState.value = CameraUIState.Initial
    }
}

sealed class CameraUIState {
    object Initial : CameraUIState()
    object Loading : CameraUIState()
    data class Success(val videoUri: String, val apiResponse: String) : CameraUIState()
    data class Error(val message: String) : CameraUIState()
}
