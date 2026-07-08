package com.gr.kakarwairider.utils

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class FirestoreTest {

    private val TAG = "FirestoreTest"
    private val db = FirebaseFirestore.getInstance()

    fun runAllTests() {

        Log.d(TAG, "===================================")
        Log.d(TAG, "🚀 FIRESTORE TEST STARTED")
        Log.d(TAG, "===================================")

        val testData = hashMapOf(
            "testId" to "test_001",
            "testName" to "Kakarwai Test",
            "testLat" to 25.6948,
            "testLng" to 79.3321,
            "isActive" to true,
            "createdAt" to System.currentTimeMillis()
        )

        Log.d(TAG, "➡️ About to write document...")

        val task = db.collection("test_collection")
            .document("test_document")
            .set(testData)

        task.addOnCompleteListener {

            Log.d(TAG, "===================================")
            Log.d(TAG, "📌 TASK COMPLETE")
            Log.d(TAG, "Complete = ${it.isComplete}")
            Log.d(TAG, "Success = ${it.isSuccessful}")

            if (it.exception != null) {
                Log.e(TAG, "❌ Exception", it.exception)
            }

            if (it.isSuccessful) {

                Log.d(TAG, "✅ DOCUMENT WRITTEN SUCCESSFULLY")

                db.collection("test_collection")
                    .document("test_document")
                    .get()
                    .addOnCompleteListener { readTask ->

                        Log.d(TAG, "===================================")
                        Log.d(TAG, "📌 READ COMPLETE")
                        Log.d(TAG, "Read Success = ${readTask.isSuccessful}")

                        if (readTask.exception != null) {
                            Log.e(TAG, "❌ Read Exception", readTask.exception)
                        }

                        if (readTask.result != null) {

                            val doc = readTask.result!!

                            Log.d(TAG, "Exists = ${doc.exists()}")

                            if (doc.exists()) {
                                Log.d(TAG, "ID = ${doc.id}")
                                Log.d(TAG, "DATA = ${doc.data}")
                            }
                        }
                    }

            } else {

                Log.e(TAG, "❌ WRITE FAILED")

            }
        }

        Thread {

            try {

                Tasks.await(task, 15, TimeUnit.SECONDS)

            } catch (e: Exception) {

                Log.e(TAG, "⏰ TASK TIMEOUT OR ERROR", e)

            }

        }.start()
    }
}