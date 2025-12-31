package com.alananasss.kittytune.ui.profile

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.BackupManager
import kotlinx.coroutines.launch

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    var isLoading by mutableStateOf(false)
    var statusMessage by mutableStateOf<String?>(null)

    fun backup(uri: Uri) {
        viewModelScope.launch {
            isLoading = true
            try {
                BackupManager.createBackup(getApplication(), uri)
                statusMessage = getApplication<Application>().getString(R.string.backup_success)
                Toast.makeText(getApplication(), getApplication<Application>().getString(R.string.backup_toast_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                statusMessage = getApplication<Application>().getString(R.string.backup_error, e.message ?: "")
                Toast.makeText(getApplication(), getApplication<Application>().getString(R.string.backup_toast_error), Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            isLoading = true
            try {
                BackupManager.restoreBackup(getApplication(), uri)
                statusMessage = getApplication<Application>().getString(R.string.restore_success)
                Toast.makeText(getApplication(), getApplication<Application>().getString(R.string.restore_toast_success), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                statusMessage = getApplication<Application>().getString(R.string.backup_error, e.message ?: "")
                Toast.makeText(getApplication(), getApplication<Application>().getString(R.string.restore_toast_error), Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }
}