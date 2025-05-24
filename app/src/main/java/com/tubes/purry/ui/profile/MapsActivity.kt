package com.tubes.purry.ui.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tubes.purry.databinding.ActivityMapsBinding
import java.util.Locale

class MapsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapsBinding
    private var selectedCountryCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCountrySpinner()
        setupClickListeners()
    }

    private fun setupCountrySpinner() {
        val countries = Locale.getISOCountries()
        val countryList = countries.map { code ->
            val locale = Locale("", code)
            CountryItem(code, locale.displayCountry)
        }.sortedBy { it.name }

        val displayList = countryList.map { "${it.code} - ${it.name}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCountries.adapter = adapter

        binding.spinnerCountries.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCountryCode = countryList[position].code
                binding.txtSelectedCountry.text = "Selected: ${countryList[position].name}"
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedCountryCode = null
            }
        }

        // Set default selection ke Indonesia
        val indonesiaIndex = countryList.indexOfFirst { it.code == "ID" }
        if (indonesiaIndex != -1) {
            binding.spinnerCountries.setSelection(indonesiaIndex)
        }
    }

    private fun setupClickListeners() {
        binding.btnSelectLocation.setOnClickListener {
            selectedCountryCode?.let { countryCode ->
                val countryName = Locale("", countryCode).displayCountry

                val resultIntent = Intent()
                resultIntent.putExtra("selected_country_code", countryCode)
                resultIntent.putExtra("selected_country_name", countryName)

                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } ?: run {
                Toast.makeText(this, "Please select a country", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenGoogleMaps.setOnClickListener {
            openGoogleMapsApp()
        }

        binding.btnCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun openGoogleMapsApp() {
        try {
            // Intent untuk membuka Google Maps
            val gmmIntentUri = Uri.parse("geo:0,0?q=")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")

            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
                Toast.makeText(this, "Select your location in Google Maps, then return here to choose your country", Toast.LENGTH_LONG).show()
            } else {
                // Fallback ke browser
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com"))
                startActivity(browserIntent)
                Toast.makeText(this, "Google Maps app not found, opening in browser", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening Google Maps: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    data class CountryItem(val code: String, val name: String)
}