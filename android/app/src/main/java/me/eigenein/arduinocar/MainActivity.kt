package me.eigenein.arduinocar

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.*

class MainActivity : Activity() {

    private val serialUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val connectRetriesCount = 5L

    private val connectionDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_main_connect -> {
                connectionDisposable.clear() // close any existing connection beforehand
                showDevicesDialog { connectTo(it) }
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        super.onPause()
        connectionDisposable.clear()
    }

    private fun showDevicesDialog(listener: (BluetoothDevice) -> Unit) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!bluetoothAdapter.isEnabled) {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 0)
            return
        }
        val devices = bluetoothAdapter.bondedDevices.sortedBy { it.name }.toTypedArray()
        if (devices.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_paired_devices, Toast.LENGTH_LONG).show()
            return
        }
        showAlertDialog(this) {
            setTitle(R.string.dialog_title_choose_vehicle)
            setCancelable(true)
            setItems(devices.map { it.name }.toTypedArray(), { _, which -> listener(devices[which]) })
        }
    }

    private fun connectTo(device: BluetoothDevice) {
        connectionDisposable.clear() // close any still existing connection
        connectionDisposable.add(
            device.listen(serialUUID)
                .subscribeOn(Schedulers.newThread())
                .retry(connectRetriesCount)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { onMessage(it) },
                    { Toast.makeText(this, getString(R.string.toast_connection_failed, device.name), Toast.LENGTH_SHORT).show() }
                )
        )
    }

    private fun onMessage(message: Message) {
        Log.d(logTag, "Message: %s".format(message))
        when (message) {
            is ConnectingMessage -> Toast.makeText(this, getString(R.string.toast_connecting, message.name), Toast.LENGTH_SHORT).show()
            is ConnectedMessage -> Toast.makeText(this, getString(R.string.toast_connected, message.name), Toast.LENGTH_SHORT).show()
        }
    }
}
