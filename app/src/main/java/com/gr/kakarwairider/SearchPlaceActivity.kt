package com.gr.kakarwairider

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode

class SearchPlaceActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SearchPlaceActivity"
        private const val AUTOCOMPLETE_REQUEST_CODE = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Current Location receive karein
        val currentLocation = intent.getParcelableExtra<LatLng>("current_location")

        try {
            // 1️⃣ Places SDK Initialize करें (New API के साथ)
            if (!Places.isInitialized()) {
                Places.initializeWithNewPlacesApiEnabled(
                    applicationContext,
                    getString(R.string.google_maps_key)
                )
                Log.d(TAG, "Places SDK initialized successfully with New Places API")
            }

            // 2️⃣ Autocomplete Intent बनाएँ
            val fields = listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG,
                Place.Field.TYPES
            )

            val intent = Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY,
                fields
            )
                .setTypeFilter(TypeFilter.ADDRESS)  // सिर्फ Address दिखाएँ
                .setCountries(listOf("IN"))  // ✅ Fix: List में Pass करें
                .apply {
                    // ✅ Agar current location available hai toh bias set karein
                    currentLocation?.let {
                        val bounds = RectangularBounds.newInstance(
                            LatLng(it.latitude - 0.05, it.longitude - 0.05),
                            LatLng(it.latitude + 0.05, it.longitude + 0.05)
                        )
                        setLocationBias(bounds)
                    }
                }
                .build(this)

            // 3️⃣ Autocomplete Activity Launch करें
            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Places: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> {
                    try {
                        val place = Autocomplete.getPlaceFromIntent(data!!)
                        val latLng = place.latLng
                        val address = place.address ?: place.name

                        Log.d(TAG, "Place selected: $address, LatLng: $latLng")

                        if (latLng != null) {
                            val resultIntent = Intent()
                            resultIntent.putExtra("location", latLng)
                            resultIntent.putExtra("address", address)
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        } else {
                            Toast.makeText(this, "No location found for this place", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting place: ${e.message}", e)
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }

                AutocompleteActivity.RESULT_ERROR -> {
                    try {
                        val status = Autocomplete.getStatusFromIntent(data!!)
                        Log.e(TAG, "Autocomplete error: ${status.statusMessage}")
                        Toast.makeText(this, "Error: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Autocomplete error occurred", Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }

                else -> {
                    finish()
                }
            }
        }
    }
}