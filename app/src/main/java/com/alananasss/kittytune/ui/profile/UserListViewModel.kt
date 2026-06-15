package com.alananasss.kittytune.ui.profile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.*
import kotlinx.coroutines.launch

class UserListViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create(application)

    var users = mutableStateListOf<User>()
    var isLoading by mutableStateOf(true)
    var isLoadingMore by mutableStateOf(false)
    var title by mutableStateOf("")
    
    private var nextCursor: String? = null
    private var currentUserId: Long = 0
    private var currentType: String = ""

    private val userSchema = "urn permalink username avatarUrl firstName lastName city country countryCode tracksCount playlistCount followersCount followingsCount verified isPro description userAvatarUrlTemplate visualUrlTemplate stationUrns createdAt badges"
    private val followersQuery = "query UserFollowersQuery(\$input: UserFollowsInput!) { userFollowers(input: \$input) { pageInfo { endCursor } items { user { $userSchema } } } }"
    private val followingsQuery = "query UserFollowingsQuery(\$input: UserFollowsInput!) { userFollowings(input: \$input) { pageInfo { endCursor } items { user { $userSchema } } } }"

    fun loadUsers(userId: Long, type: String) {
        if (currentUserId == userId && currentType == type) return
        
        currentUserId = userId
        currentType = type
        viewModelScope.launch {
            isLoading = true
            users.clear()
            nextCursor = null
            
            try {
                val operationName = if (type == "followers") "UserFollowersQuery" else "UserFollowingsQuery"
                val queryStr = if (type == "followers") followersQuery else followingsQuery
                
                val req = GraphQlFollowsRequest(
                    operationName = operationName,
                    query = queryStr,
                    variables = GraphQlFollowsVariables(
                        input = GraphQlFollowsInput(
                            urn = "soundcloud:users:$userId",
                            first = 30,
                            after = null
                        )
                    )
                )
                
                val newUsers = mutableListOf<User>()
                
                if (type == "followers") {
                    val response = api.getUserFollowersGraphQL(req)
                    val result = response.data?.userFollowers
                    result?.items?.forEach { it.user?.let { u -> newUsers.add(u) } }
                    nextCursor = result?.pageInfo?.endCursor
                } else {
                    val response = api.getUserFollowingsGraphQL(req)
                    val result = response.data?.userFollowings
                    result?.items?.forEach { it.user?.let { u -> newUsers.add(u) } }
                    nextCursor = result?.pageInfo?.endCursor
                }
                
                users.addAll(newUsers)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore || nextCursor == null) return

        viewModelScope.launch {
            isLoadingMore = true
            try {
                val operationName = if (currentType == "followers") "UserFollowersQuery" else "UserFollowingsQuery"
                val queryStr = if (currentType == "followers") followersQuery else followingsQuery
                
                val req = GraphQlFollowsRequest(
                    operationName = operationName,
                    query = queryStr,
                    variables = GraphQlFollowsVariables(
                        input = GraphQlFollowsInput(
                            urn = "soundcloud:users:$currentUserId",
                            first = 30,
                            after = nextCursor
                        )
                    )
                )
                
                val newUsers = mutableListOf<User>()
                
                if (currentType == "followers") {
                    val response = api.getUserFollowersGraphQL(req)
                    val result = response.data?.userFollowers
                    result?.items?.forEach { it.user?.let { u -> newUsers.add(u) } }
                    nextCursor = result?.pageInfo?.endCursor
                } else {
                    val response = api.getUserFollowingsGraphQL(req)
                    val result = response.data?.userFollowings
                    result?.items?.forEach { it.user?.let { u -> newUsers.add(u) } }
                    nextCursor = result?.pageInfo?.endCursor
                }
                
                users.addAll(newUsers)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingMore = false
            }
        }
    }
}
