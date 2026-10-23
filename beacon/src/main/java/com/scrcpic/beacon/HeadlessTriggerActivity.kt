package com.scrcpic.beacon

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

class HeadlessTriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            BeaconService.start(this)
            Toast.makeText(this, "Scrcpic Beacon Active (Ready)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting Beacon: ${e.message}", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
