    package com.alananasss.kittytune.ui.profile
    
    import android.app.Application
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateListOf
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.ActivityItem
    import kotlinx.coroutines.launch
    
    class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
        private val api = RetrofitClient.create(application)
    
        val activities = mutableStateListOf<ActivityItem>()
        var isLoading by mutableStateOf(true)
        var nextHref: String? = null
        var isLoadingMore by mutableStateOf(false)
    
        init {
            loadNotifications()
        }
    
        fun loadNotifications() {
            viewModelScope.launch {
                isLoading = true
                try {
                    val response = api.getActivities(limit = 20)
                    activities.clear()
                    activities.addAll(response.collection)
                    nextHref = response.next_href
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        }
    
        fun loadMore() {
            if (isLoadingMore || nextHref == null) return
            viewModelScope.launch {
                isLoadingMore = true
                try {
                    val response = api.getActivitiesNextPage(nextHref!!)
                    activities.addAll(response.collection)
                    nextHref = response.next_href
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoadingMore = false
                }
            }
        }
    }


