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

        try {
            // 1️⃣ Places SDK Initialize (New API)
            if (!Places.isInitialized()) {
                Places.initializeWithNewPlacesApiEnabled(
                    applicationContext,
                    getString(R.string.google_maps_key)
                )
                Log.d(TAG, "Places SDK initialized successfully")
            }

            // 2️⃣ Current Location receive karein
            val currentLocation = intent.getParcelableExtra<LatLng>("current_location")

            // 3️⃣ Autocomplete Intent Builder (SAME as before)
            val fields = listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG,
                Place.Field.TYPES,
                Place.Field.RATING,
                Place.Field.USER_RATING_COUNT
            )

            // ✅ Use Autocomplete.IntentBuilder (NOT PlaceAutocomplete.IntentBuilder)
            val intentBuilder = Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY,
                fields
            )
                .setCountries(listOf("IN"))

            if (currentLocation != null) {
                val latOffset = 0.45
                val lngOffset = 0.45

                val southwest = LatLng(
                    currentLocation.latitude - latOffset,
                    currentLocation.longitude - lngOffset
                )
                val northeast = LatLng(
                    currentLocation.latitude + latOffset,
                    currentLocation.longitude + lngOffset
                )

                val bounds = RectangularBounds.newInstance(southwest, northeast)
                intentBuilder.setLocationBias(bounds)
                Log.d(TAG, "Location bias applied")
            } else {
                Log.d(TAG, "No current location available - search without bias")
            }

            val intent = intentBuilder.build(this)
            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)

        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
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
                        // ✅ Use Autocomplete.getPlaceFromIntent()
                        val place = Autocomplete.getPlaceFromIntent(data!!)
                        val latLng = place.latLng
                        val address = place.address ?: place.name

                        Log.d(TAG, "Place selected: ${place.name}, Address: $address")

                        if (latLng != null) {
                            val resultIntent = Intent()
                            resultIntent.putExtra("location", latLng)
                            resultIntent.putExtra("address", address)
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        } else {
                            Toast.makeText(this, "No location found", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error: ${e.message}", e)
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