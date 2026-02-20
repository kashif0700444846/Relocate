// [Relocate] [SearchBar.kt] - Address Search with Autocomplete
// Uses Nominatim API for live address suggestions.
// v1.4.0: Completely rewritten to eliminate all crash paths.
// - No LazyColumn (crashes inside verticalScroll)
// - No AnimatedVisibility (can cause measurement crash on rapid recomposition)
// - No Divider/HorizontalDivider (version-dependent)
// - Full try/catch around every coroutine and API call

package com.relocate.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relocate.app.network.NominatimApi
import com.relocate.app.network.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    onLocationSelected: (SearchResult) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showDropdown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    Column(modifier = modifier) {
        // Search input
        OutlinedTextField(
            value = query,
            onValueChange = { newQuery ->
                query = newQuery
                searchJob?.cancel()
                if (newQuery.length >= 2) {
                    searchJob = scope.launch {
                        try {
                            delay(400) // Debounce
                            isSearching = true
                            val searchResults = try {
                                NominatimApi.search(newQuery)
                            } catch (e: Exception) {
                                android.util.Log.e("SearchBar", "Search failed: ${e.message}")
                                emptyList()
                            }
                            results = searchResults
                            showDropdown = searchResults.isNotEmpty()
                        } catch (e: Exception) {
                            // Coroutine cancelled or other error — safe to ignore
                            android.util.Log.w("SearchBar", "Search coroutine error: ${e.message}")
                        } finally {
                            isSearching = false
                        }
                    }
                } else {
                    showDropdown = false
                    isSearching = false
                    results = emptyList()
                }
            },
            placeholder = {
                Text("Search address or city...", fontSize = 13.sp)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search")
            },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        results = emptyList()
                        showDropdown = false
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        // Autocomplete dropdown — plain Column, no LazyColumn, no AnimatedVisibility
        if (showDropdown && results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    results.forEach { result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    query = result.name
                                    showDropdown = false
                                    results = emptyList()
                                    onLocationSelected(result)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📍", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = try {
                                    result.fullName.split(", ").drop(2).take(2).joinToString(", ")
                                } catch (e: Exception) { "" }
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
