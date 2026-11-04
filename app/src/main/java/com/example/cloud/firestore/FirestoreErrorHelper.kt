package com.example.cloud.firestore

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

/**
 * Logs sanitized diagnostic information for a Firestore failure without exposing
 * personal account metadata (UIDs, email addresses, auth tokens, provider info,
 * raw document paths, or raw exception messages that may contain PII).
 */
fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val errorType = exception.javaClass.simpleName.ifEmpty { "Exception" }
    val errorCode = (exception as? FirebaseFirestoreException)?.code?.name
    val errorInfoJson = JSONObject().apply {
        put("errorType", errorType)
        put("operationType", operationType.value)
        if (errorCode != null) {
            put("errorCode", errorCode)
        }
    }

    val jsonString = (errorInfoJson.toString() as String?).takeUnless { it.isNullOrBlank() } ?: buildString {
        append("{\"errorType\":\"").append(errorType).append("\"")
        append(",\"operationType\":\"").append(operationType.value).append("\"")
        if (errorCode != null) {
            append(",\"errorCode\":\"").append(errorCode).append("\"")
        }
        append("}")
    }
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}
