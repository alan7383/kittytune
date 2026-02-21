    package com.alananasss.kittytune.ui.library
    
    import android.app.Application
    import androidx.compose.runtime.mutableStateListOf
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.User
    import kotlinx.coroutines.async
    import kotlinx.coroutines.coroutineScope
    import kotlinx.coroutines.launch
    
    class PlaylistInfoFullViewModel(application: Application) : AndroidViewModel(application) {
        private val api = RetrofitClient.create(application)
        val likers = mutableStateListOf<User>()
        val reposters = mutableStateListOf<User>()
    
        fun loadData(playlistId: Long) {
            if(likers.isNotEmpty() || reposters.isNotEmpty()) return
    
            viewModelScope.launch {
                try {
                    coroutineScope {
                        val l = async { try { api.getPlaylistLikers(playlistId, limit = 100).collection } catch(e:Exception){ emptyList() } }
                        val r = async { try { api.getPlaylistReposters(playlistId, limit = 100).collection } catch(e:Exception){ emptyList() } }
    
                        likers.addAll(l.await())
                        reposters.addAll(r.await())
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }


