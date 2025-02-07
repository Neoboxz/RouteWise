package com.example.app
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.maps.CameraUpdateFactory
import com.google.android.libraries.maps.GoogleMap
import com.google.android.libraries.maps.OnMapReadyCallback
import com.google.android.libraries.maps.SupportMapFragment
import com.google.android.libraries.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import okhttp3.OkHttpClient
import java.util.Locale
import com.google.android.libraries.maps.model.LatLng as LibrariesLatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.CheckBox
import android.widget.EditText
import androidx.core.text.set

import com.google.android.libraries.maps.model.BitmapDescriptor
import com.google.android.libraries.maps.model.BitmapDescriptorFactory
import com.google.android.libraries.maps.model.LatLng
import com.google.android.libraries.maps.model.Marker
import com.google.android.libraries.maps.model.Polyline
import com.google.android.libraries.maps.model.PolylineOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import java.io.IOException
import kotlin.math.*



class MyLocationDemoActivity : AppCompatActivity(), OnMapReadyCallback {
    private var mGoogle: GoogleMap? = null
    private lateinit var autocompleteFragment: AutocompleteSupportFragment
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    val polylinesIDtrain = mutableMapOf<String, Polyline?>()
    val polylinesIDbus = mutableMapOf<String, Polyline?>()



    override fun onCreate(savedInstanceStatus: Bundle?) {
        super.onCreate(savedInstanceStatus)
        setContentView(R.layout.my_location_demo)

        // Initialize Places API
        Places.initialize(applicationContext, getString(R.string.google_map_api_key))

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val trainCheckBox = findViewById<CheckBox>(R.id.train_check_box)
        val busCheckBox = findViewById<CheckBox>(R.id.bus_check_box)



        // Set up Autocomplete search box
        autocompleteFragment = supportFragmentManager.findFragmentById(R.id.autocomplete) as AutocompleteSupportFragment
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.ADDRESS, Place.Field.LAT_LNG))
        autocompleteFragment.setCountries("SG")
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onError(p0: Status) {
                Toast.makeText(this@MyLocationDemoActivity, "Nah im good :) ", Toast.LENGTH_SHORT)
            }
            //After selecting the location , it will mark car park with in 1 km radius of destination
            override fun onPlaceSelected(place: Place) {
                trainCheckBox.isChecked = false
                busCheckBox.isChecked = false
                mGoogle?.clear()
                var address = place.address
                val gmsLatLng = place.latLng // This is of type com.google.android.gms.maps.model.LatLng
                if (gmsLatLng != null) {
                    val librariesLatLng = LibrariesLatLng(gmsLatLng.latitude, gmsLatLng.longitude)
                    val cameraUpdate = CameraUpdateFactory.newLatLngZoom(librariesLatLng, 15f)
                    mGoogle?.animateCamera(cameraUpdate)
                    mGoogle?.addMarker(
                        MarkerOptions()
                            .position(librariesLatLng)
                            .title(address)
                            ?.icon(getMarkerIconFromDrawable(R.drawable.tree))
                    )
                    //getting user location
                    getLastLocation { latLng ->
                        if (latLng != null) {

                            Toast.makeText(this@MyLocationDemoActivity, "Lat: ${latLng.latitude}, Lng: ${latLng.longitude}", Toast.LENGTH_SHORT).show()
                            // Move the map to the user's location
                           var Useraddress = getReadableAddress(latLng.latitude, latLng.longitude)
                            mGoogle?.addMarker(
                                MarkerOptions()
                                    .position(latLng)
                                    .title(Useraddress))
                                // call the function getMarkerIconFromDrawable to decode tree.png
                                    ?.setIcon(getMarkerIconFromDrawable(R.drawable.tree))
                            //call and fetch carpark details and to be marked on map
                            fetchAndDisplayCarParksConcurrently(gmsLatLng.latitude , gmsLatLng.longitude)


                            // Initial load with the default or empty input
                            fetchAndDisplayBicycleCarParksConcurrently(gmsLatLng.latitude , gmsLatLng.longitude)


                        } else {
                            Toast.makeText(this@MyLocationDemoActivity, "Unable to fetch location.", Toast.LENGTH_SHORT).show()
                        }

                        //future to do
                        // place all this different route into a function to be neater


                        // Start of different route filter

                        // Listen to check box for train
                        trainCheckBox.setOnCheckedChangeListener { _, isChecked ->
                            // check if it is checked
                                if (isChecked){
                                    if (latLng != null) {
                                    Toast.makeText(this@MyLocationDemoActivity , "Adding train route" , Toast.LENGTH_SHORT).show()
                                    getDirection(latLng, librariesLatLng , "transit" , "train")
                                }
                            }else{

                                    val polyline = polylinesIDtrain["train"]
                                    polyline?.remove()
                                    Toast.makeText(this@MyLocationDemoActivity , "removing train route" , Toast.LENGTH_SHORT).show()
                                }

                        }
                        busCheckBox.setOnCheckedChangeListener { _, isChecked ->
                            // check if it is checked
                            if (isChecked){
                                if (latLng != null) {
                                    Toast.makeText(this@MyLocationDemoActivity , "Adding bus route" , Toast.LENGTH_SHORT).show()
                                    getDirection(latLng, librariesLatLng , "transit" , "bus")
                                }
                            }else{

                                val polyline = polylinesIDbus["bus"]
                                polyline?.remove()
                                Toast.makeText(this@MyLocationDemoActivity , "removing bus route" , Toast.LENGTH_SHORT).show()
                            }

                        }
                        // END of different route filter
                        

                        //default value if nothing is checked
                        if (latLng != null) {
                            getDirection(latLng , librariesLatLng , "driving" , "")

                            // will calculate emissions standard
                            calEmissionAndConsumption(latLng , librariesLatLng )
                        }

                        else{
                            Toast.makeText(this@MyLocationDemoActivity, "Unable to fetch location", Toast.LENGTH_SHORT).show()

                        }
                    }

                } else {
                    Toast.makeText(this@MyLocationDemoActivity, "Selected place does not have coordinates.", Toast.LENGTH_SHORT).show()
                }
            }
        })

        // Set up Map Fragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Add button to go to user's location
        val locationButton: Button = findViewById(R.id.btn_go_to_location)
        locationButton.setOnClickListener {
            getUserLocation()
        }
        // when app launch will clear previous data
        mGoogle?.clear()
        // Request and fetch the user's location
        getUserLocation()


    }
    override fun onMapReady(googleMap: GoogleMap?) {
        mGoogle = googleMap
    }



    //Start of LTA bicycle parking
    // Parsing Bicycle Parking Data
    private fun parseBicycleParkingData(responseJson: JSONObject): List<BicycleParking> {

        return try {
            val bicycleParkingList = mutableListOf<BicycleParking>()
            val bicycleParkingArray = responseJson.getJSONArray("value")

            for (i in 0 until bicycleParkingArray.length()) {
                val bicycleParkingJson = bicycleParkingArray.getJSONObject(i)
                val description = bicycleParkingJson.getString("Description")
                val lat = bicycleParkingJson.getDouble("Latitude")
                val lng = bicycleParkingJson.getDouble("Longitude")
                val rackType = bicycleParkingJson.getString("RackType")
                val rackCount = bicycleParkingJson.getInt("RackCount")
                val shelter = bicycleParkingJson.getString("ShelterIndicator")
                bicycleParkingList.add(
                    BicycleParking(
                        descrip = description,
                        latitidue = lat,
                        longitude = lng,
                        rackType = rackType,
                        rackCount = rackCount,
                        shelter = shelter
                    )
                )
            }
            bicycleParkingList

        } catch (e: Exception) {
            e.printStackTrace()
            emptyList() // Return an empty list in case of errors
        }
    }
    // Fetching and Displaying Bicycle Parking Concurrently
    private fun fetchAndDisplayBicycleCarParksConcurrently(destLat: Double, destLng: Double) {

        lifecycleScope.launch {
            val bicycleParking = fetchBicycleCarParkDataConcurrently(destLat, destLng)
            if (!bicycleParking.isNullOrEmpty()) {
                val minAvailable = 20

                bicycleParking.forEach { parking ->
                    if (parking.rackCount >= minAvailable) {
                        // Add markers for parking spots meeting the criteria
                        mGoogle?.addMarker(
                            MarkerOptions()
                                .position(LibrariesLatLng(parking.latitidue, parking.longitude))
                                .title("${parking.descrip}: ${parking.rackCount} racks")
                                ?.icon(getMarkerIconFromDrawable(R.drawable.bicycleparking)) // Update icon

                        )

                    }
                }
            } else {
                Toast.makeText(this@MyLocationDemoActivity, "No bicycle parking data found.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Fetching Bicycle Parking Data Concurrently
    private suspend fun fetchBicycleCarParkDataConcurrently(destLat: Double, destLng: Double): List<BicycleParking>? = coroutineScope {
        val accountKey = getString(R.string.ltaApiKey)

        // Fetch data for multiple pages concurrently
        val responses = (1..2).map { page ->
            async(Dispatchers.IO) {
                getBicycleLtaData(accountKey, destLat, destLng)
            }
        }



        val bicycleCarParks = responses.mapNotNull { deferredResponse ->
            val response = deferredResponse.await() // Wait for the API response
            response?.let { jsonData ->
                parseBicycleParkingData(jsonData)

            }
        }.flatten()

        bicycleCarParks.takeIf { it.isNotEmpty() }
    }

    // Fetching Bicycle LTA Data
    private suspend fun getBicycleLtaData( apiKey: String, destLat: Double, destLng: Double): JSONObject? = withContext(Dispatchers.IO) {
        val client = OkHttpClient()

        val builtUrl = HttpUrl.Builder()
            .scheme("https")
            .host("datamall2.mytransport.sg")
            .addPathSegment("ltaodataservice")
            .addPathSegment("BicycleParkingv2")
            .addQueryParameter("Lat", destLat.toString())
            .addQueryParameter("Long", destLng.toString())
            .build()

        val request = okhttp3.Request.Builder()
            .url(builtUrl)
            .addHeader("AccountKey", apiKey)
            .addHeader("accept", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()


            if (response.isSuccessful) {
                response.body?.string()?.let { JSONObject(it) }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    //END of LTA bicycle parking


//START of emission
    private suspend fun calEmissionAndConsumptionAsync(
        user: LibrariesLatLng,
        destination: LibrariesLatLng
    ): List<String> = coroutineScope {
        val list = mutableListOf<String>()


        // Launch each API call concurrently
        val responses = (0..0).map { i ->
            async {
                fetchEmissionAndConsumption(user, destination)
            }
        }

        // Await each response and process the results
        responses.forEachIndexed { index, deferred ->
            val result = deferred.await()
            result?.let { emissions ->
                list.addAll(emissions)
            } ?: run {
                println("No data for mode index $index")
            }
        }

        list // Return the final list
    }

    private suspend fun fetchEmissionAndConsumption(user: LibrariesLatLng, destination: LibrariesLatLng, ): List<String>? = withContext(Dispatchers.IO) {
        val requestQueue = Volley.newRequestQueue(this@MyLocationDemoActivity)
        val url = Uri.parse("https://maps.googleapis.com/maps/api/directions/json")
            .buildUpon()
            .appendQueryParameter("origin", "${user.latitude},${user.longitude}")
            .appendQueryParameter("destination", "${destination.latitude},${destination.longitude}")
            .appendQueryParameter("mode", "driving")
            .appendQueryParameter("transit_mode", "")
            .appendQueryParameter("key", getString(R.string.google_map_api_key))
            .toString()

        val emissions = mutableListOf<String>()
        val result = CompletableDeferred<List<String>?>()

        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                try {
                    val routes = response.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val legs = route.getJSONArray("legs")
                        val leg = legs.getJSONObject(0)
                        val distance = leg.getJSONObject("distance")

                        val DdistanceValue = distance.getInt("value")

                        val distInKm = DdistanceValue / 1000.0

                        //EU emission standard for 2025
                        val carCO2 = (distInKm * 93.6) /1000
                        val vanCo2 = (distInKm * 153.9) /1000
                        emissions.add(String.format("%.3f", carCO2) + " kg")
                        emissions.add(String.format("%.3f", vanCo2) + " Kg")

                    }
                    result.complete(emissions)
                } catch (e: Exception) {
                    e.printStackTrace()
                    result.complete(null)
                }
            },
            { error ->
                error.printStackTrace()
                result.complete(null)
            }
        )
        requestQueue.add(jsonObjectRequest)
        result.await()
    }

    // Usage
    private fun calEmissionAndConsumption(user: LibrariesLatLng, destination: LibrariesLatLng) {
        lifecycleScope.launch {
            val emissions = calEmissionAndConsumptionAsync(user, destination)
            navigateToTableActivity(emissions , user , destination)
        }
    }

    // Handle navigation to TableActivity
    private fun navigateToTableActivity(dataList: List<String> , user: LatLng , destination: LatLng) {
        findViewById<Button>(R.id.btnShowTable).setOnClickListener {
            val intent = Intent(this, TableActivity::class.java)
            intent.putStringArrayListExtra("dataList", ArrayList(dataList))
            intent.putExtra("user" , doubleArrayOf(user.latitude , user.longitude))
            intent.putExtra("destination", doubleArrayOf(destination.latitude , destination.longitude))
            startActivity(intent)


        }
    }

//END of emission


    // For user marker ICON, used to decode the drawable resource for custom icon
    private fun getMarkerIconFromDrawable(drawableRes: Int): BitmapDescriptor? {
        return try {
            val bitmap = BitmapFactory.decodeResource(resources, drawableRes)
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 100, 100, false) // Resize as needed
            BitmapDescriptorFactory.fromBitmap(scaledBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    //Start of formating and fetching LTA carpark data
    private fun parseCarParkData(responseJson: JSONObject): List<CarPark> {
        return try {
            val carParkList = mutableListOf<CarPark>()
            val carParkArray = responseJson.getJSONArray("value")

            for (i in 0 until carParkArray.length()) {
                val carParkJson = carParkArray.getJSONObject(i)
                val carParkID = carParkJson.getString("CarParkID")
                val area = carParkJson.optString("Area", "Unknown")
                val development = carParkJson.optString("Development", "Unknown")
                val location = carParkJson.optString("Location", "")

                if (location.isNotBlank()) {
                    val latLngParts = location.split(" ")
                    if (latLngParts.size == 2) {
                        val latitude = latLngParts[0].toDoubleOrNull()
                        val longitude = latLngParts[1].toDoubleOrNull()

                        if (latitude != null && longitude != null) {
                            val availableLots = carParkJson.optInt("AvailableLots", 0)
                            val lotType = carParkJson.optString("LotType", "Unknown")
                            val agency = carParkJson.optString("Agency", "Unknown")

                            carParkList.add(
                                CarPark(
                                    id = carParkID,
                                    area = area,
                                    development = development,
                                    location = Pair(latitude, longitude),
                                    availableLots = availableLots,
                                    lotType = lotType,
                                    agency = agency
                                )
                            )
                        }
                    }
                }
            }
            carParkList
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList() // Return an empty list if there is an error
        }
    }

    private fun fetchAndDisplayCarParksConcurrently(destLat: Double , destLng: Double) {
        lifecycleScope.launch {
            val carParks = fetchCarParkDataConcurrently()
            // TO DO impliment customization for user to choose the radius
            var distanceR = 1 // 1 km radius
            var minLotAval = 10
            if (!carParks.isNullOrEmpty()) {
                //the calculation of carpark with in 1km radius from destination
                carParks.forEach { carPark ->
                    var distance = calculateDistance(destLat, destLng ,carPark.location.first, carPark.location.second)
                    if (distance <= distanceR){
                        if (carPark.availableLots >= minLotAval){
                            mGoogle?.addMarker(
                                MarkerOptions()
                                    .position(LibrariesLatLng(carPark.location.first, carPark.location.second))
                                    .title("${carPark.development}: ${carPark.availableLots} lots")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                            )
                        }else{
                            mGoogle?.addMarker(
                                MarkerOptions()
                                    .position(LibrariesLatLng(carPark.location.first, carPark.location.second))
                                    .title("${carPark.development}: ${carPark.availableLots} lots")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                            )
                        }
                    }
                }
            } else {
                Toast.makeText(this@MyLocationDemoActivity, "Failed to fetch car park data.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    //Calculate the distance between destination to carparks
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2) *
                sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c // Distance in kilometers
    }

    private suspend fun fetchCarParkDataConcurrently(): List<CarPark>? = coroutineScope {
        val apiEndpoint = "https://datamall2.mytransport.sg/ltaodataservice/CarParkAvailabilityv2"
        val accountKey = getString(R.string.ltaApiKey)

        // Fetch data concurrently for all pages
        //API call only 500 in 1 page so this will cycle all 6 pages to get all locations
        val responses = (1..6).map { page ->
            async(Dispatchers.IO) {
                getLtaData("$apiEndpoint?page=$page", accountKey) // Example: Add pagination parameter
            }
        }

        // Wait for all responses and parse them
        val carParks = responses.mapNotNull { responseDeferred ->
            responseDeferred.await()?.let { jsonData ->
                withContext(Dispatchers.Default) {
                    parseCarParkData(jsonData)
                }
            }
        }.flatten()

        carParks.takeIf { it.isNotEmpty() }
    }

    private suspend fun getLtaData(url: String, apiKey: String): JSONObject? = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url(url)
            .addHeader("AccountKey", apiKey)
            .addHeader("accept", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                response.body?.string()?.let { JSONObject(it) }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    //END of formating and fetching LTA carpark data and calculating of distination to carpark

    //Start of plotting route
    //to plot routes from user location to searched location
    private fun getDirection(user: LibrariesLatLng, destination: LibrariesLatLng , mode: String , mode1: String) {
        val requestQueue = Volley.newRequestQueue(this)
        val url = Uri.parse("https://maps.googleapis.com/maps/api/directions/json")
            .buildUpon()
            .appendQueryParameter("origin", "${user.latitude},${user.longitude}")
            .appendQueryParameter("destination", "${destination.latitude},${destination.longitude}")
            .appendQueryParameter("mode", mode)
            .appendQueryParameter("transit_mode", mode1)
            .appendQueryParameter("key", getString(R.string.google_map_api_key))
            .toString()

        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                try {
                    val routes = response.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val overviewPolyline = route.getJSONObject("overview_polyline")
                        val points = overviewPolyline.getString("points")
                        drawRouteOnMap(points , mode1 )


                    } else {
                        Toast.makeText(this, "No routes found.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error parsing directions: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Toast.makeText(this, "Error fetching directions: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        )

        requestQueue.add(jsonObjectRequest)
    }

    private fun drawRouteOnMap(encodedPolyline: String , mode : String) {

        if (mode == "train"){
            val polylineOptions = PolylineOptions()
            val decodedPath = decodePolyline(encodedPolyline)
            polylineOptions.addAll(decodedPath)
            polylineOptions.width(10f)
            polylineOptions.geodesic(true)
            polylineOptions.color(android.graphics.Color.rgb(139 ,69 ,19))
            polylineOptions.visible(true)
            val polylinetrain = mGoogle?.addPolyline(polylineOptions)
            if (polylinetrain != null) {
                polylinesIDtrain[mode] = polylinetrain // Use 'mode' as the key to uniquely identify the polyline
            }

        }
        if (mode == "bus"){
            val polylineOptions = PolylineOptions()
            val decodedPath = decodePolyline(encodedPolyline)
            polylineOptions.addAll(decodedPath)
            polylineOptions.width(10f)
            polylineOptions.geodesic(true)
            polylineOptions.color(android.graphics.Color.rgb(0 ,119 ,190))
            polylineOptions.visible(true)
            val polylinebus = mGoogle?.addPolyline(polylineOptions)
            if (polylinebus != null) {
                polylinesIDbus[mode] = polylinebus // Use 'mode' as the key to uniquely identify the polyline
            }

        }

        if(mode == ""){
            val polylineOptions = PolylineOptions()
            val decodedPath = decodePolyline(encodedPolyline)
            polylineOptions.addAll(decodedPath)
            polylineOptions.width(10f)
            polylineOptions.geodesic(true)
            polylineOptions.color(android.graphics.Color.rgb(50,205,50))
            mGoogle?.addPolyline(polylineOptions)
        }


    }

    private fun decodePolyline(encoded: String): List<com.google.android.libraries.maps.model.LatLng> {
        val poly = ArrayList<com.google.android.libraries.maps.model.LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dLat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dLng

            val latLng = com.google.android.libraries.maps.model.LatLng(lat / 1E5, lng / 1E5)
            poly.add(latLng)

        }
        return poly
    }


    //END of plotting different routes



    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userLatLng = LibrariesLatLng(location.latitude, location.longitude)
                val cameraUpdate = CameraUpdateFactory.newLatLngZoom(userLatLng, 15f)
                mGoogle?.animateCamera(cameraUpdate)

                val address = getReadableAddress(location.latitude, location.longitude)
                mGoogle?.addMarker(
                    MarkerOptions()
                        .position(userLatLng)
                        .title(address))
                        ?.setIcon(getMarkerIconFromDrawable(R.drawable.tree))
                Toast.makeText(this, "Your location: $address", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Unable to fetch location.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getReadableAddress(latitude: Double, longitude: Double): String {
        val geocoder = Geocoder(this, Locale.getDefault())
        return try {
            val addresses: MutableList<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses?.isNotEmpty() == true) {
                val address = addresses?.get(0)
                "${address?.getAddressLine(0)}, ${address?.locality}, ${address?.countryName}"
            } else {
                "Address not found"
            }
        } catch (e: Exception) {
            "Geocoder error: ${e.message}"
        }
    }
    private fun getLastLocation(onResult: (LibrariesLatLng?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            onResult(null) // Permission not granted, return null
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onResult(LibrariesLatLng(location.latitude, location.longitude))
            } else {
                onResult(null) // Location not available
            }
        }.addOnFailureListener {
            onResult(null) // Error occurred
        }
    }


    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
    data class CarPark(
        val id: String,
        val area: String,
        val development: String,
        val location: Pair<Double, Double>,
        val availableLots: Int,
        val lotType: String,
        val agency: String
    )
    data class BicycleParking(
        val descrip : String,
        val latitidue : Double,
        val longitude : Double,
        val rackType : String,
        val rackCount: Int,
        val shelter: String
    )

}
