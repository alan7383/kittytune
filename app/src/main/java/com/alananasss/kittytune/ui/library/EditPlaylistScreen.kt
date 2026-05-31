package com.alananasss.kittytune.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.TagSuggestion
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistScreen(
    initialTitle: String,
    initialDescription: String?,
    initialSharing: String?,
    initialTagList: String?,
    initialGenre: String?,
    initialSetType: String?,
    initialReleaseDate: String?,
    initialPermalink: String?,
    playlistUser: User?,
    onDismissRequest: () -> Unit,
    onSave: (title: String, description: String?, sharing: String, tagList: String?, genre: String?, setType: String?, releaseDate: String?, permalink: String?) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription ?: "") }
    var sharing by remember { mutableStateOf(initialSharing ?: "public") }
    
    var genre by remember { mutableStateOf(initialGenre ?: "") }
    var setType by remember { mutableStateOf(initialSetType ?: "") }
    var releaseDate by remember { mutableStateOf(initialReleaseDate ?: "") }
    var permalink by remember { mutableStateOf(initialPermalink ?: "") }
    
    var showGenreDropdown by remember { mutableStateOf(false) }
    var showSetTypeDropdown by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    var tags by remember { 
        mutableStateOf(
            parseTags(initialTagList ?: "")
        ) 
    }
    var tagInput by remember { mutableStateOf("") }
    
    var tagSuggestions by remember { mutableStateOf<List<TagSuggestion>>(emptyList()) }
    var isSearchingTags by remember { mutableStateOf(false) }

    LaunchedEffect(tagInput) {
        if (tagInput.length >= 2) {
            isSearchingTags = true
            delay(300) // debounce
            try {
                val api = RetrofitClient.create(context)
                val response = api.searchTags(query = tagInput, limit = 5)
                tagSuggestions = response.suggestions ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSearchingTags = false
            }
        } else {
            tagSuggestions = emptyList()
            isSearchingTags = false
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit Playlist") },
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                onSave(
                                    title,
                                    description,
                                    sharing,
                                    tags.joinToString(" ") { if (it.contains(" ")) "\"$it\"" else it },
                                    genre,
                                    setType,
                                    releaseDate,
                                    permalink
                                )
                            },
                            enabled = title.isNotBlank() && (setType == "" || releaseDate.isNotBlank()) && permalink.matches(Regex("^[a-z0-9_-]+$"))
                        ) {
                            Text(stringResource(R.string.btn_save))
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }

                item {
                    val isValidPermalink = permalink.matches(Regex("^[a-z0-9_-]+$"))
                    OutlinedTextField(
                        value = permalink,
                        onValueChange = { permalink = it },
                        label = { Text("Permalink *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        isError = !isValidPermalink,
                        prefix = { Text("soundcloud.com/${playlistUser?.permalink ?: "user"}/sets/") },
                        supportingText = {
                            if (!isValidPermalink) {
                                Text("Use only numbers, lowercase letters, underscores, or hyphens.")
                            }
                        }
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        maxLines = 5
                    )
                }

                item {
                    Text("Privacy", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = sharing == "public",
                            onClick = { sharing = "public" }
                        )
                        Text("Public", modifier = Modifier.clickable { sharing = "public" })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = sharing == "private",
                            onClick = { sharing = "private" }
                        )
                        Text("Private", modifier = Modifier.clickable { sharing = "private" })
                    }
                }

                item {
                    Text("Playlist Type", style = MaterialTheme.typography.titleMedium)
                    ExposedDropdownMenuBox(
                        expanded = showSetTypeDropdown,
                        onExpandedChange = { showSetTypeDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = when(setType) {
                                "" -> "Playlist"
                                "album" -> "Album"
                                "ep" -> "EP"
                                "single" -> "Single"
                                "compilation" -> "Compilation"
                                else -> "Playlist"
                            },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSetTypeDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = showSetTypeDropdown,
                            onDismissRequest = { showSetTypeDropdown = false }
                        ) {
                            listOf("" to "Playlist", "album" to "Album", "ep" to "EP", "single" to "Single", "compilation" to "Compilation").forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { 
                                        setType = value
                                        showSetTypeDropdown = false 
                                    }
                                )
                            }
                        }
                    }
                }
                
                if (setType != "") {
                    item {
                        OutlinedTextField(
                            value = releaseDate,
                            onValueChange = { releaseDate = it },
                            label = { Text("Release Date (YYYY-MM-DD) *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            trailingIcon = {
                                TextButton(onClick = { showDatePicker = true }) {
                                    Text("Pick")
                                }
                            }
                        )
                    }
                }

                item {
                    Text("Genre", style = MaterialTheme.typography.titleMedium)
                    val predefinedGenres = listOf("None", "Custom", "Alternative Rock", "Ambient", "Classical", "Country", "Dance & EDM", "Dancehall", "Deep House", "Disco", "Drum & Bass", "Dubstep", "Electronic", "Folk & Singer-Songwriter", "Hip-hop & Rap", "House", "Indie", "Jazz & Blues", "Latin", "Metal", "Piano", "Pop", "R&B & Soul", "Reggae", "Reggaeton", "Rock", "Soundtrack", "Techno", "Trance", "Trap", "Triphop", "World", "Audiobooks", "Business", "Comedy", "Entertainment", "Learning", "News & Politics", "Religion & Spirituality", "Science", "Sports", "Storytelling", "Technology")
                    var selectedGenreCategory by remember { mutableStateOf(if (genre in predefinedGenres || genre == "") genre else "Custom") }
                    
                    ExposedDropdownMenuBox(
                        expanded = showGenreDropdown,
                        onExpandedChange = { showGenreDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = if (selectedGenreCategory == "") "None" else selectedGenreCategory,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showGenreDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = showGenreDropdown,
                            onDismissRequest = { showGenreDropdown = false }
                        ) {
                            predefinedGenres.forEach { label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { 
                                        val value = if (label == "None") "" else label
                                        selectedGenreCategory = value
                                        if (value != "Custom") genre = value
                                        showGenreDropdown = false 
                                    }
                                )
                            }
                        }
                    }
                    if (selectedGenreCategory == "Custom") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = genre,
                            onValueChange = { genre = it },
                            label = { Text("Custom Genre") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                item {
                    Text("Tags", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text("Add tags") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        trailingIcon = {
                            if (tagInput.isNotBlank()) {
                                IconButton(onClick = { 
                                    if (!tags.contains(tagInput.trim())) {
                                        tags = tags + tagInput.trim()
                                    }
                                    tagInput = "" 
                                }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Add tag")
                                }
                            }
                        }
                    )
                }
                
                if (tagSuggestions.isNotEmpty()) {
                    items(tagSuggestions) { suggestion ->
                        ListItem(
                            headlineContent = { Text(suggestion.id) },
                            modifier = Modifier.clickable {
                                if (!tags.contains(suggestion.id)) {
                                    tags = tags + suggestion.id
                                }
                                tagInput = ""
                            }
                        )
                    }
                }

                if (tags.isNotEmpty()) {
                    item {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEach { tag ->
                                InputChip(
                                    selected = false,
                                    onClick = { tags = tags - tag },
                                    label = { Text(tag) },
                                    trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Remove") }
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val calendar = java.util.Calendar.getInstance()
                        calendar.timeInMillis = millis
                        val y = calendar.get(java.util.Calendar.YEAR)
                        val m = calendar.get(java.util.Calendar.MONTH) + 1
                        val d = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                        releaseDate = "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

fun parseTags(tagList: String): List<String> {
    val tags = mutableListOf<String>()
    var inQuotes = false
    val currentTag = java.lang.StringBuilder()
    for (char in tagList) {
        if (char == '"') {
            inQuotes = !inQuotes
        } else if (char == ' ' && !inQuotes) {
            if (currentTag.isNotEmpty()) {
                tags.add(currentTag.toString())
                currentTag.clear()
            }
        } else {
            currentTag.append(char)
        }
    }
    if (currentTag.isNotEmpty()) {
        tags.add(currentTag.toString())
    }
    return tags
}
