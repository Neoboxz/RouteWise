package com.example.app

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.merge
import org.json.JSONArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Document


class TableActivity : AppCompatActivity() {
    private lateinit var capturedImageView: ImageView
    private val CAMERA_REQUEST_CODE = 100
    val emissionList = FloatArray(2)
    val dist = FloatArray(1)



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_table)

        val dataList = intent.getStringArrayListExtra("dataList")

        val tableLayout = findViewById<TableLayout>(R.id.tableLayout)
        val btnBackToMap = findViewById<Button>(R.id.btnBackToMap)


        getDistance()

        // Clear any existing rows in the table
        tableLayout.removeAllViews()

        // Add headers to the table
        addTableHeader(tableLayout)

        // Add data rows to the table
        dataList?.let {
            addTableData(tableLayout, it)
            it.clear()
        }

        // Set up the back button
        btnBackToMap.setOnClickListener {
            finish() // Finish the activity and return to the previous screen (MapActivity)
        }
        val openCameraButton = findViewById<Button>(R.id.openCameraButton)
        capturedImageView = findViewById(R.id.selectedImageView)

        openCameraButton.setOnClickListener {
            openCamera()
        }

        // Request Camera permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
        }


    }


    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        try {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {

                //convert data to bitmap
                val photo: Bitmap = data?.extras?.get("data") as Bitmap

                val examplePhoto = findViewById<TextView>(R.id.example)
                examplePhoto.visibility = View.GONE
                val vehicleName = findViewById<TextView>(R.id.vehicleName)
                //setting UI to visible
                capturedImageView.visibility = View.VISIBLE

                capturedImageView.setImageBitmap(photo)
                lifecycleScope.launch {


                    //loading UI
                    val loading = findViewById<ProgressBar>(R.id.progressBar)
                    loading.visibility = View.VISIBLE

                    // call AI which output data:[{brand:<the brand> , model:<the model> ]
                    val output = promptAI(photo)
                    println(output.text)
                    if (output.text != "ERROR") {
                        val formattedJson = output.text?.substringAfter("data:")
                        val jsonArray = JSONArray(formattedJson)
                        val firstObject = jsonArray.getJSONObject(0)
                        val brand = firstObject.getString("brand")
                        val model = firstObject.getString("model")
                        println(brand + model)

                        CoroutineScope(Dispatchers.Main).launch {
                            //update the frontend with the brand and model
                            vehicleName.setText("$brand " + "$model")
                            //call webscrapeing() function
                            webscrapeing(brand, model)

                        }

                    } else {
                        Toast.makeText(this@TableActivity, "Please try again", Toast.LENGTH_SHORT)
                            .show()
                    }

                }

            }
        }catch (e : Exception){
            println(e)
        }
    }


    private fun addTableHeader(tableLayout: TableLayout) {
        val headers = arrayOf("Mode of \nTransport", "EU Emission \nStandard")
        val headerRow = TableRow(this)

        headers.forEach { header ->
            val textView = TextView(this).apply {
                text = header
                setTextColor(Color.BLACK)
                textSize = 18f
                setPadding(16, 16, 16, 16)
                setBackgroundColor(Color.LTGRAY)
            }
            headerRow.addView(textView)
        }

        tableLayout.addView(headerRow)
    }

    private fun addTableData(tableLayout: TableLayout, dataList: ArrayList<String>) {
        val descriptions = arrayOf("Car CO2 emission" ,  "Van CO2 emission ")

        // Ensure the dataList has the required number of elements
        val safeDataList = ArrayList(dataList)
        while (safeDataList.size < descriptions.size) {
            safeDataList.add("N/A") // Fill missing data with a placeholder
        }

        descriptions.indices.forEach { i ->
            val row = TableRow(this)

            val descTextView = TextView(this).apply {
                text = descriptions[i]
                setTextColor(Color.DKGRAY)
                textSize = 16f
                setPadding(16, 16, 16, 16)
            }

            val valueTextView = TextView(this).apply {
                text = safeDataList[i]
                setTextColor(Color.DKGRAY)
                textSize = 16f
                setPadding(16, 16, 16, 16)
            }

            row.addView(descTextView)
            row.addView(valueTextView)
            tableLayout.addView(row)
        }
    }

    private suspend fun promptAI(bitmap: Bitmap): GenerateContentResponse {
        val generativeModel = GenerativeModel(

            modelName = "gemini-1.5-flash-8b",

            apiKey = getString(R.string.Gemini_API_KEY)
        )
        val prompt = content {
            text("ONLY return a sentence in this format data:[{brand:< ONLY the full brand name and add a - if there is spacing in the brand name DONT INCLUDE MODEL and TYPE ONLY BRAND NAME> , model:<just the model name> ] DON'T ADD ANYTHING ELSE like '''json ''' , when answering first letter will always be upper and the rest will be lower, if the image is not a vehicle ONLY return ERROR ")
            image(bitmap)
        }
        val response = generativeModel.generateContent(prompt)
        return response
    }


    private fun webscrapeing(brand : String, model: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // URL of the webpage to scrape
                val url = "https://car-emissions.com/cars/model/$brand/$model"

                // Connect to the webpage and parse the HTML
                val document: Document = Jsoup.connect(url).get()

                // Extract the MPG, litres/100km, and g/km values using CSS selectors
                val litres: String? =
                    document.selectFirst("div#content_column span.big:contains(litres/100km)")?.text()
                val emissions: String? =
                    document.selectFirst("div#content_column span.big:contains(g/km)")?.text()
                CoroutineScope(Dispatchers.Main).launch {

                    val litersSlice = litres?.replace(" litres/100km", "")
                    val emissionsSlice = emissions?.replace(" g/km" , "")
                    println(litersSlice)
                    println(emissionsSlice)
                    //sliced data to be placed in emissionList
                    emissionList[0] = litersSlice?.toFloat()!!
                    emissionList[1] = emissionsSlice?.toFloat()!!
                    println(emissionList.joinToString())

                    val distInKm = dist[0]
                    println(distInKm)
                    val totalFuelUsed = distInKm * ( emissionList[0] / 100.0 )
                    val totalemission = (emissionList[1] * distInKm) / 1000

                    val showVehicleMetrics = findViewById<TextView>(R.id.vehicleMetrics)
                    val loading = findViewById<ProgressBar>(R.id.progressBar)
                    loading.visibility = View.GONE
                    showVehicleMetrics.visibility = View.VISIBLE
                    showVehicleMetrics.setText("Vehicle Carbon Emission is ${String.format("%.3f",totalemission)} kg \nVehicle Fuel Consumtion is ${String.format("%.3f",totalFuelUsed)} L")

                    //initialize the emission metrics first
                    calculationOfMetrics(1.toFloat())

                    //to listen to changes when user enter quantity
                    val quantity = findViewById<EditText>(R.id.quantity)
                    quantity.addTextChangedListener( object : TextWatcher{
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                            val numb = 1.toString().toFloat()
                            calculationOfMetrics(numb)
                        }

                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            val numb = s
                            println("Text changed: $s")
                        }

                        override fun afterTextChanged(s: Editable?) {
                            try{
                                val numb = s.toString().toFloat()
                                calculationOfMetrics(numb)
                            }catch(e : Exception){
                                val numb = 1.toString().toFloat()
                                calculationOfMetrics(numb)

                            }


                        }
                    })

                }

            } catch (e: Exception) {
                Toast.makeText(this@TableActivity , "No data found" , Toast.LENGTH_SHORT ).show()

            }
        }
    }

    private fun calculationOfMetrics(numb : Float){
        //For UI purpose
        val metircs = findViewById<LinearLayout>(R.id.metrics)
        metircs.visibility = View.VISIBLE

        val metircs2 = findViewById<LinearLayout>(R.id.metrics2)
        metircs2.visibility = View.VISIBLE

        val setQuantity = findViewById<LinearLayout>(R.id.setQuantity)
        setQuantity.visibility = View.VISIBLE

        val metricsIcon = findViewById<LinearLayout>(R.id.metricsIcon)
        metricsIcon.visibility = View.VISIBLE

        val metricsIcon2 = findViewById<LinearLayout>(R.id.metricsIcon2)
        metricsIcon2.visibility = View.VISIBLE


        val distInKm = dist[0]
        println(distInKm)
        val totalFuelUsed = distInKm * ( emissionList[0] / 100.0 )
        val totalemission = emissionList[1] * distInKm
        //20kg of Carbon to 1 tree

        val treeMetrics = findViewById<TextView>(R.id.tree)
        val treeEmission = totalemission / 1000
        val totalTreeEmission = (treeEmission / 20) * numb
        treeMetrics.setText("Trees to plant: ${String.format("%.3f",totalTreeEmission)}")

        //10.6kg of carbon to 1 cubic meter of water
        val waterMetrics = findViewById<TextView>(R.id.water)
        val waterEmission = totalemission / 1000
        val totalwaterEmission = (waterEmission / 10.6) * numb
        waterMetrics.setText("Emission to water: ${String.format("%.3f",totalwaterEmission)} m3")

        //total emission
        val emissionMetrics = findViewById<TextView>(R.id.totalco2)
        val totalCo2 = (totalemission / 1000) * numb
        emissionMetrics.setText("Total Emission: ${String.format("%.3f",totalCo2)} Kg")

        //1 carbon offset = 1 carbon credit
        //1000kg of carbon to 1 carbon credit
        val credit = totalCo2 / 1000
        val carbonMetrics = findViewById<TextView>(R.id.carbonCredit)
        carbonMetrics.setText("Total Carbon Credit: ${String.format("%.3f",credit)}")

        //$30 for 1 carbon offset
        val offsetCost = credit * 30
        val carbonOffset = findViewById<TextView>(R.id.cost)
        carbonOffset.setText("Total cost to offset:\n$${String.format("%.2f",offsetCost)}")


    }
    private fun getDistance() {

        val user: DoubleArray? = intent.getDoubleArrayExtra("user")
        val destination: DoubleArray? = intent.getDoubleArrayExtra("destination")
        val requestQueue = Volley.newRequestQueue(this)
        val url = Uri.parse("https://maps.googleapis.com/maps/api/directions/json")
            .buildUpon()
            .appendQueryParameter("origin", "${user?.get(0)},${user?.get(1)}")
            .appendQueryParameter("destination", "${destination?.get(0)},${destination?.get(1)}")
            .appendQueryParameter("mode", "driving")
            .appendQueryParameter("key", getString(R.string.google_map_api_key))
            .toString()
        CoroutineScope(Dispatchers.IO).launch {
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
                        val distInKm = DdistanceValue / 1000.0  // Convert to kilometers
                        dist[0] = distInKm.toFloat()




                    } else {
                        Toast.makeText(this@TableActivity, "Please Try Again!", Toast.LENGTH_SHORT).show()

                    }
                } catch (e: Exception) {
                    Toast.makeText(this@TableActivity, "Error parsing directions: ${e.message}", Toast.LENGTH_SHORT).show()

                }
            },
            { error ->
                Toast.makeText(this@TableActivity, "Error fetching directions: ${error.message}", Toast.LENGTH_SHORT).show()

            }
        )

        requestQueue.add(jsonObjectRequest)
        }
    }

    fun loadingGone(){
        val loading = findViewById<ProgressBar>(R.id.progressBar)
        loading.visibility = View.GONE
    }


    // add an input so user can add quantity of vehicles ,
}