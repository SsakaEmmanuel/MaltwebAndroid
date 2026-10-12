package com.maltweb.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Green = Color(0xFF126B50)
private val Pale = Color(0xFFE8F5EF)
private val Ink = Color(0xFF172923)
private val Muted = Color(0xFF6C7A74)
private val Page = Color(0xFFF7F9F7)
private data class Listing(val name: String, val category: String, val location: String, val about: String, val emoji: String)
private val samples = listOf(
    Listing("Green Valley School", "Schools", "Kampala", "Learning and student development.", "🎓"),
    Listing("Hope Community Church", "Churches", "Wakiso", "Community worship and outreach.", "⛪"),
    Listing("Bright Star Graphics", "Graphics designers", "Kampala", "Posters, branding and print design.", "🎨"),
    Listing("QuickFix Services", "Service providers", "Entebbe", "Home and office repair services.", "🛠️"),
    Listing("City Fresh Traders", "Traders", "Kampala", "Everyday products and local trade.", "🛍️"),
    Listing("Community Care Initiative", "Charity outreach", "Mukono", "Community support and outreach.", "🤝"),
    Listing("Maltweb Web Studio", "Web developers", "Kampala", "Websites and digital solutions.", "💻"),
    Listing("Al Noor Mosque", "Mosques", "Kampala", "Local faith and community information.", "🕌")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaltwebApp() } }
}

@Composable
private fun MaltwebApp() {
    var tab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var saved by remember { mutableStateOf(setOf<String>()) }
    var opened by remember { mutableStateOf<Listing?>(null) }
    val categories = listOf("All", "Schools", "Churches", "Mosques", "Traders", "Service providers", "Graphics designers", "Web developers", "Charity outreach")
    val filtered = samples.filter { (category == "All" || it.category == category) && listOf(it.name,it.category,it.location,it.about).any { value -> value.contains(search, true) } }
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Page, surface = Color.White, onSurface = Ink)) {
        Scaffold(containerColor = Page, bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(tab == 0, { tab = 0; opened = null }, { Icon(Icons.Default.Explore, null) }, label = { Text("Discover") })
                NavigationBarItem(tab == 1, { tab = 1; opened = null }, { Icon(Icons.Default.Category, null) }, label = { Text("Categories") })
                NavigationBarItem(tab == 2, { tab = 2; opened = null }, { Icon(Icons.Default.Bookmark, null) }, label = { Text("Saved") })
                NavigationBarItem(tab == 3, { tab = 3; opened = null }, { Icon(Icons.Default.Person, null) }, label = { Text("Profile") })
            }
        }) { pad ->
            when {
                opened != null -> Detail(opened!!, saved.contains(opened!!.name), { opened = null }, { val n = opened!!.name; saved = if (saved.contains(n)) saved - n else saved + n }, Modifier.padding(pad))
                tab == 0 -> Discover(search, { search = it }, category, categories, filtered, saved, { category = it }, { opened = it }, { item -> saved = if (saved.contains(item.name)) saved - item.name else saved + item.name }, Modifier.padding(pad))
                tab == 1 -> Column(Modifier.padding(pad).fillMaxSize().padding(20.dp)) {
                    Text("Explore categories", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Choose a category to discover local listings.", color = Muted)
                    Spacer(Modifier.height(14.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(categories.drop(1)) { c -> Card(Modifier.fillMaxWidth().clickable { category = c; tab = 0 }) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(categoryEmoji(c), fontSize = 24.sp); Spacer(Modifier.width(14.dp)); Text(c, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Icon(Icons.Default.ChevronRight, null) } } } }
                }
                tab == 2 -> Column(Modifier.padding(pad).fillMaxSize().padding(18.dp)) {
                    Text("Saved places", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Your favourite listings.", color = Muted)
                    Spacer(Modifier.height(12.dp))
                    val savedItems = samples.filter { saved.contains(it.name) }
                    if (savedItems.isEmpty()) Text("No saved listings yet. Tap a bookmark in Discover.", color = Muted)
                    else LazyColumn { items(savedItems) { ListingRow(it, true, { opened = it }, { saved = saved - it.name }) } }
                }
                else -> Column(Modifier.padding(pad).fillMaxSize().padding(20.dp)) {
                    Text("Your profile", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Account sign-in will be added in a later step.", color = Muted)
                    Spacer(Modifier.height(20.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Pale)) { Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("🌿", fontSize = 42.sp); Text("Welcome to MALTWEB", fontWeight = FontWeight.Bold, fontSize = 20.sp); Text("Your community, connected.", color = Muted) } }
                    Spacer(Modifier.height(20.dp)); Text("Planned account types", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    listOf("Public visitor", "Ordinary user", "Business / organization", "Service provider", "Administrator").forEach { Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircleOutline, null, tint = Green); Spacer(Modifier.width(10.dp)); Text(it) } }
                }
            }
        }
    }
}

@Composable
private fun Discover(search: String, onSearch: (String) -> Unit, category: String, categories: List<String>, listings: List<Listing>, saved: Set<String>, onCategory: (String) -> Unit, onOpen: (Listing) -> Unit, onSave: (Listing) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Column(Modifier.fillMaxWidth().background(Green).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("M", fontSize = 30.sp, color = Color.White, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.width(10.dp)); Column { Text("MALTWEB", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Text("Your community, connected.", color = Color.White.copy(alpha = .8f), fontSize = 12.sp) } }
            Spacer(Modifier.height(20.dp)); Text("Find what you need.", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold); Text("Discover businesses, people and services near you.", color = Color.White.copy(alpha = .85f))
            Spacer(Modifier.height(14.dp)); OutlinedTextField(search, onSearch, Modifier.fillMaxWidth(), placeholder = { Text("Search local places and services…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(15.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = Color.White, unfocusedBorderColor = Color.White))
        } }
        item { Column(Modifier.padding(top = 18.dp)) { Text("Explore categories", Modifier.padding(horizontal = 18.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(categories.drop(1)) { c -> FilterChip(selected = category == c, onClick = { onCategory(if (category == c) "All" else c) }, label = { Text(c) }) } } } }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Discover around you", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("${listings.size} places", color = Muted, fontSize = 12.sp) } }
        if (listings.isEmpty()) item { Text("No matches. Try another search.", Modifier.padding(22.dp), color = Muted) }
        else items(listings) { ListingRow(it, saved.contains(it.name), { onOpen(it) }, { onSave(it) }) }
        item { Text("Starter preview • Sample listings only", Modifier.padding(18.dp), color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun ListingRow(item: Listing, isSaved: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 5.dp).clickable { onOpen() }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).background(Pale, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Text(item.emoji, fontSize = 28.sp) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(item.category, color = Green, fontSize = 12.sp); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, null, Modifier.size(14.dp), tint = Muted); Text(item.location, color = Muted, fontSize = 12.sp) }; Text(item.about, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            IconButton(onClick = onSave) { Icon(if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, tint = Green) }
        }
    }
}

@Composable
private fun Detail(item: Listing, saved: Boolean, onBack: () -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Green).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }; Text("Listing details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        Column(Modifier.padding(20.dp)) { Box(Modifier.fillMaxWidth().height(150.dp).background(Pale, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) { Text(item.emoji, fontSize = 60.sp) }; Spacer(Modifier.height(16.dp)); Text(item.name, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text(item.category, color = Green, fontWeight = FontWeight.SemiBold); Text("📍 ${item.location}", color = Muted); Spacer(Modifier.height(20.dp)); Text("About", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(item.about, color = Muted); Spacer(Modifier.height(20.dp)); Button(onClick = onSave, Modifier.fillMaxWidth()) { Icon(Icons.Default.Bookmark, null); Spacer(Modifier.width(8.dp)); Text(if (saved) "Remove from saved" else "Save this listing") }; Spacer(Modifier.height(10.dp)); Text("Sample preview only. Online listings and contact details will be connected next.", color = Muted, fontSize = 12.sp) }
    }
}

private fun categoryEmoji(category: String) = when (category) { "Schools" -> "🎓"; "Churches" -> "⛪"; "Mosques" -> "🕌"; "Traders" -> "🛍️"; "Service providers" -> "🛠️"; "Graphics designers" -> "🎨"; "Web developers" -> "💻"; "Charity outreach" -> "🤝"; else -> "📍" }
