package com.example.signconnect.util

import android.content.ContentValues.TAG
import android.util.Log


/**
 * Utilidad para traducir los nombres técnicos de clases a nombres legibles
 */
object ClassNameUtils {

    /**
     * Mapa que traduce los nombres de clase del modelo a textos legibles
     */
    private val classNameMap = mapOf(
        "class_hola" to "Hola",
        "class_comoestas" to "¿Cómo estás?",
        "class_comida" to "Comida",
        "class_quehaces" to "¿Qué haces?",
        "class_bien" to "Bien",
        "class_adios" to "Adiós",
        "class_muchasgracias" to "Muchas gracias",
        "class_perdon" to "Perdón",
        "class_porfavor" to "Por favor",
        "class_si" to "Sí",
        "class_no" to "No",
        "class_ayuda" to "Ayuda",
        "class_qhorason" to "¿Qué hora es?"
    )

    /**
     * Convierte un nombre de clase técnico en un texto legible
     *
     * @param className Nombre de clase técnico (ej: "class_hola")
     * @return Texto legible (ej: "Hola") o el nombre original si no hay traducción
     */
    fun getReadableClassName(className: String): String {
        // Log para depuración
        Log.d(TAG, "Clase original recibida: '$className'")

        // Intentar encontrar directamente
        val directMatch = classNameMap[className]
        if (directMatch != null) {
            Log.d(TAG, "Coincidencia directa encontrada: '$directMatch'")
            return directMatch
        }

        // Intentar normalizar primero (quitar espacios, convertir a minúsculas)
        val normalizedClassName = className.trim().lowercase()
        Log.d(TAG, "Clase normalizada: '$normalizedClassName'")

        // Buscar coincidencia con la versión normalizada
        for ((key, value) in classNameMap) {
            if (normalizedClassName == key.lowercase() ||
                normalizedClassName == key.replace("class_", "").lowercase() ||
                normalizedClassName == "class_${key.replace("class_", "").lowercase()}") {
                Log.d(TAG, "Coincidencia normalizada encontrada: '$value' para clave '$key'")
                return value
            }
        }

        // Si no hay coincidencia, intentar limpiar el nombre
        val cleanedName = normalizedClassName.replace("class_", "")
            .replaceFirstChar { it.uppercase() }
        Log.d(TAG, "No se encontró coincidencia. Retornando nombre limpio: '$cleanedName'")
        return cleanedName
    }


}