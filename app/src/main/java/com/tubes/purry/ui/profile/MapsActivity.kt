package com.tubes.purry.ui.profile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.tubes.purry.databinding.ActivityMapsBinding
import java.util.Locale

class MapsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMapsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCountrySpinner()

        binding.btnSelectLocation.setOnClickListener {
            val selectedCountryCode = binding.spinnerCountries.selectedItem.toString().split(" - ")[0]
            val resultIntent = Intent()
            resultIntent.putExtra("selected_country_code", selectedCountryCode)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun setupCountrySpinner() {
        val countries = Locale.getISOCountries()
        val countryList = countries.map { code ->
            val locale = Locale("", code)
            "$code - ${locale.displayCountry}"
        }.sorted()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, countryList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCountries.adapter = adapter

        binding.spinnerCountries.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // You can show selected country on a map view if desired
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Nothing to do
            }
        }
    }
}