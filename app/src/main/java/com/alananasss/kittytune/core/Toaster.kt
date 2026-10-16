package com.alananasss.kittytune.core

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.alananasss.kittytune.KittyTuneApp

object Toaster {
    fun show(message: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(KittyTuneApp.instance, message, Toast.LENGTH_SHORT).show()
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(KittyTuneApp.instance, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
