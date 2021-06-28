package com.crewmates.autolibodb

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import com.crewmates.autolibodb.repository.Repository
import com.crewmates.autolibodb.viewModel.MainViewModel
import com.crewmates.autolibodb.viewModel.MainViewModelFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import java.io.IOException
import java.lang.Exception
import java.net.URISyntaxException
import java.util.*

val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

class MainActivity : FragmentActivity(), OnMapReadyCallback {
     companion object {
         @JvmStatic lateinit var viewModel: MainViewModel
         @JvmStatic lateinit var context : LifecycleOwner
         @JvmStatic lateinit var temperatureDisplay : TextView
         @JvmStatic var gmap : GoogleMap? = null

     }

    private lateinit var sharedViewModel: SharedViewModel

    private lateinit var mSocket: Socket

    private var nameTablet: String? = null

    private val onError: Emitter.Listener = Emitter.Listener {
        this@MainActivity.runOnUiThread(Runnable {
            try {
                val data: Exception = it[0] as Exception
                Toast.makeText(this@MainActivity, data.message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                val data: JSONObject = it[0] as JSONObject

            }

        })
    }
    private val onLink: Emitter.Listener = Emitter.Listener {

        val data: JSONObject = it[0] as JSONObject
        nameTablet = data.getString("nomLocataire")
        this@MainActivity.runOnUiThread(Runnable {
            Toast.makeText(this@MainActivity, "Discovering ...", Toast.LENGTH_SHORT).show()
        })
        val discovering = bluetoothAdapter?.startDiscovery()
    }

    private val onDisconnect: Emitter.Listener = Emitter.Listener {
        this@MainActivity.runOnUiThread(Runnable {
//            findNavController(R.id.fragmentContainer).navigate(R.id.navigation_loading)
            startActivity(Intent(this, LoadingActivity::class.java))
            Toast.makeText(this@MainActivity, "Diconnected!", Toast.LENGTH_SHORT).show()
        })
    }



    private val REQUEST_CODE_LOCATION_PERMISSION = 1
       var latitude = 0.0
       var longitude = 0.0


     override fun onStart() {
         super.onStart()
         context=this
         val repository = Repository()
         val viewModelFactory = MainViewModelFactory(repository)
         viewModel = ViewModelProvider(this,viewModelFactory)
             .get(MainViewModel::class.java)
         temperatureDisplay= findViewById(R.id.tempDisplay)
     }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                AlertDialog.Builder(this)
                    .setTitle("Location Permission Needed")
                    .setMessage("This app needs the Location permission, please accept to use location functionality")
                    .setPositiveButton(
                        "OK"
                    ) { _, _ ->
                        //Prompt the user once explanation has been shown
                        requestLocationPermission()
                    }
                    .create()
                    .show()
            } else {
                // No explanation needed, we can request the permission.
                requestLocationPermission()
            }
        }

        val requestCode = 1
        val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        startActivityForResult(discoverableIntent, requestCode)

        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 100)
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(receiver, filter)

        sharedViewModel =
            ViewModelProvider(this).get(SharedViewModel::class.java)


        try {
            val opts = IO.Options()
            opts.port = 8000
            opts.path = "/socket"


            mSocket = IO.socket("http://192.168.43.222:8123", opts)
        } catch(e: URISyntaxException) {
            e.printStackTrace()
        }

        mSocket.on("error", onError)
        mSocket.on("connect_error", onError)
        mSocket.on("start link", onLink)
        mSocket.on("disconnect", onDisconnect)
        mSocket.connect()


        val mapFragment = findViewById<MapView>(R.id.map)
        mapFragment.onCreate(savedInstanceState)
        mapFragment.getMapAsync(this)
        startLocationUpdate.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_CODE_LOCATION_PERMISSION
                )
            } else {
                startLocationService()
            }
        }
        stopLocationUpdate.setOnClickListener {
            stopLocationService()
        }


    }


    override fun onMapReady(p0: GoogleMap) {
        gmap = p0
        val latLng : LatLng = if (longitude> 0){
            LatLng(latitude, longitude)
        }else {
            LatLng(36.704998, 3.173918)
        }



    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_LOCATION_PERMISSION && grantResults.size > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationService()
            } else {
                Toast.makeText(this, "Permission denied ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isLocationServiceRunning(): Boolean {

        return LocationService.isMyServiceRunning

    }

    private fun startLocationService() {
        if (!isLocationServiceRunning()) {
            val intent = Intent(applicationContext, LocationService::class.java)
            intent.action = Constants.ACTION_START_LOCATION_SERVICE
            startService(intent)
            LocationService.isMyServiceRunning = true
            Toast.makeText(this, "Location service started", Toast.LENGTH_SHORT).show()

        }
    }

    private fun stopLocationService() {
        if (isLocationServiceRunning()) {
            LocationService.isMyServiceRunning = false
            val intent = Intent(applicationContext, LocationService::class.java)
            intent.action = Constants.ACTION_STOP_LOCATION_SERVICE
            startService(intent)
            Toast.makeText(this, "Location service stopped", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                100
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
        }
    }

    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action!!) {

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Toast.makeText(this@MainActivity, "Starting discovery ...", Toast.LENGTH_SHORT).show()

                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Toast.makeText(this@MainActivity, "Finishing discovery ...", Toast.LENGTH_SHORT).show()

                }

                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val deviceName = device?.name
                    val deviceHardwareAddress = device?.address // MAC address

                    if (deviceName.equals(nameTablet)) {
                        val connectThread = ConnectThread(device!!,this@MainActivity)
                        connectThread.start()
                    }
                }

            }
        }
    }


    fun getSocket(): Socket {
        return mSocket
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(receiver)
        mSocket.disconnect()
        mSocket.off("error", onError)
        mSocket.off("connect_error", onError)
        mSocket.off("start link", onLink)
    }
}

private class ConnectThread(val device: BluetoothDevice, val activity: MainActivity) : Thread() {

    private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
        device.createRfcommSocketToServiceRecord(UUID(100, 200))
    }

    override fun run() {
        // Cancel discovery because it otherwise slows down the connection.
        bluetoothAdapter?.cancelDiscovery()

        mmSocket?.let { socket ->
            // Connect to the remote device through the socket. This call blocks
            // until it succeeds or throws an exception.
            socket.connect()

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            var output = ByteArray(1)
            output[0] = 10.toByte()
            socket.outputStream.write(output)

            activity.runOnUiThread { Toast.makeText(activity, "Connexion effectuée avec ${socket.remoteDevice.name}", Toast.LENGTH_LONG).show() }
            try {

                // mmSocket?.close()
            } catch (e: IOException) {
                Log.e(ContentValues.TAG, "Could not close the client socket", e)
            }
        }
    }

}