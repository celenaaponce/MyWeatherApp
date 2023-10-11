package com.example.weatherapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.CallLog.Locations
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import org.json.JSONObject
import retrofit.*
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient : FusedLocationProviderClient
    private var mProgressDialog: Dialog? = null
    private lateinit var mSharedPreferences : SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        setupUI()
        if(!isLocationEnabled()){
            Toast.makeText(this, "Your location is off", Toast.LENGTH_SHORT).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withActivity(this)
                .withPermissions(android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(object: MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()){
                            requestLocationData()
                        }
                        if(report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity, "Permissions denied", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationaleDialogForPermissions()
                    }
                }).onSameThread().check()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
            mLocationCallback, Looper.myLooper())
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")
            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if(Constants.isNetworkAvailable(this)){
            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()
            val service :WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)
            val listCall : Call<WeatherResponse> =
                service.getWeather(latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID)

            showProgressDialog()
            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {
                    if(response!!.isSuccess){
                        hideProgressDialog()
                        val weatherList: WeatherResponse = response.body()
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()
                        setupUI()
                        Log.i("Response result", "$weatherList")
                    }else{
                        val rc = response.code()
                        when(rc){
                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("error", "generic error")
                            }
                        }
                    }
                }

                override fun onFailure(t: Throwable?) {
                    hideProgressDialog()
                    Log.e("Error", t!!.message.toString())
                }

            })
        }else{
            Toast.makeText(this@MainActivity, "Internet not connected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRationaleDialogForPermissions(){
        AlertDialog.Builder(this)
            .setMessage("Permissions are off")
            .setPositiveButton("GO TO SETTINGS"){_, _ ->
                try{
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }catch(e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") {dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager : LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Method is used to show the Custom Progress Dialog.
     */
    private fun showProgressDialog() {
        mProgressDialog = Dialog(this)

        /*Set the screen content from a layout resource.
        The resource will be inflated, adding all top-level views to the screen.*/
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        //Start the dialog and display it on screen.
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog() {
        if(mProgressDialog != null){
            mProgressDialog!!.dismiss()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                requestLocationData()
            true
            }
            else -> return super.onOptionsItemSelected(item)
            }
    }

    private fun setupUI(){
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")
        if(!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            for(i in weatherList.weather.indices){
                Log.i("Weather name", weatherList.weather.toString())
                val tvMain: TextView = findViewById(R.id.tv_main)
                val tvMainDescription: TextView = findViewById(R.id.tv_main_description)
                val tvTemp : TextView = findViewById(R.id.tv_temp)
                val tvSunriseTime: TextView = findViewById(R.id.tv_sunrise_time)
                val tvSunsetTime: TextView = findViewById(R.id.tv_sunset_time)
                val tvMinimum : TextView = findViewById(R.id.tv_min)
                val tvMaximum : TextView = findViewById(R.id.tv_max)
                val tvWind : TextView = findViewById(R.id.tv_speed)
                val tvWindUnits : TextView = findViewById(R.id.tv_speed_unit)
                val tvCountry: TextView = findViewById(R.id.tv_country)
                val tvName: TextView = findViewById(R.id.tv_name)
                val tvHumidity:TextView = findViewById(R.id.tv_humidity)
                val ivMain : ImageView = findViewById(R.id.iv_main)

                tvMain.text = weatherList.weather[i].main
                tvMainDescription.text = weatherList.weather[i].description
                tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                tvSunriseTime.text = unixTime(weatherList.sys.sunrise)
                tvSunsetTime.text = unixTime(weatherList.sys.sunset)
                tvMinimum.text = weatherList.main.temp_min.toString() + getUnit(application.resources.configuration.locales.toString())
                tvMaximum.text = weatherList.main.temp_max.toString() + getUnit(application.resources.configuration.locales.toString())
                tvWind.text = weatherList.wind.speed.toString()
                tvWindUnits.text = getUnitWind(application.resources.configuration.locales.toString())
                tvCountry.text = weatherList.sys.country
                tvName.text = weatherList.name
                tvHumidity.text = weatherList.main.humidity.toString() + " %"

                when(weatherList.weather[i].icon){
                    "01d" -> ivMain.setImageResource(R.drawable.sunny)
                    "02d" -> ivMain.setImageResource(R.drawable.cloud)
                    "03d" -> ivMain.setImageResource(R.drawable.cloud)
                    "04d" -> ivMain.setImageResource(R.drawable.cloud)
                    "09d" -> ivMain.setImageResource(R.drawable.rain)
                    "10d" -> ivMain.setImageResource(R.drawable.rain)
                    "11d" -> ivMain.setImageResource(R.drawable.storm)
                    "13d" -> ivMain.setImageResource(R.drawable.snowflake)
                    "01n" -> ivMain.setImageResource(R.drawable.sunny)
                    "02n" -> ivMain.setImageResource(R.drawable.cloud)
                    "03n" -> ivMain.setImageResource(R.drawable.cloud)
                    "04n" -> ivMain.setImageResource(R.drawable.cloud)
                    "09n" -> ivMain.setImageResource(R.drawable.rain)
                    "10n" -> ivMain.setImageResource(R.drawable.rain)
                    "11n" -> ivMain.setImageResource(R.drawable.storm)
                    "13n" -> ivMain.setImageResource(R.drawable.snowflake)
                }
            }
        }

    }

    private fun getUnit(value: String): String?{
        var temp = "°C"
        if(value == "US" || value == "LR" || value == "MM"){
            temp = "°F"
        }
        return temp
    }

    private fun getUnitWind(value: String): String?{
        var speed = "km/hr"
        if(value == "US" || value == "LR" || value == "MM"){
            speed = "miles/hr"
        }
        return speed
    }

    private fun unixTime(timex: Long) : String?{
        val date = Date(timex *1000L)
        val sdf = SimpleDateFormat("hh:mm a", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        Log.i("time", "${sdf.format(date)}")
        return sdf.format(date)
    }

}