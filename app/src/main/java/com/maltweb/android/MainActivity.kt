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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val PROJECT_URL = "https://kwekebkniywzcpvmdvka.supabase.co"
private const val PUBLIC_KEY = "sb_publishable_aPv6JZQSWC-v3Y0k3TB_ew__HW2qcBA"
private const val TABLE = "listings"
private val Green = Color(0xFF126B50)
private val Pale = Color(0xFFE8F5EF)
private val Ink = Color(0xFF172923)
private val Muted = Color(0xFF6C7A74)
private val Page = Color(0xFFF7F9F7)

data class Listing(val id:String,val name:String,val category:String,val location:String,val description:String,val symbol:String)
data class Result(val rows:List<Listing>,val error:String?=null)

class MainActivity:ComponentActivity(){ override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaltwebApp()}} }

private suspend fun loadListings():Result = withContext(Dispatchers.IO) {
    var conn:HttpURLConnection?=null
    try {
        conn=(URL("$PROJECT_URL/rest/v1/$TABLE?select=*").openConnection() as HttpURLConnection).apply {
            requestMethod="GET"; connectTimeout=15000; readTimeout=15000
            setRequestProperty("apikey",PUBLIC_KEY)
            if (PUBLIC_KEY.startsWith("eyJ")) setRequestProperty("Authorization","Bearer $PUBLIC_KEY")
            setRequestProperty("Accept","application/json")
        }
        val code=conn.responseCode
        val body=(if(code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
        if(code !in 200..299){
            val message=try{JSONObject(body).optString("message",body)}catch(_:Exception){body}
            return@withContext Result(emptyList(),"Supabase HTTP $code: $message")
        }
        val arr=JSONArray(body); val rows=mutableListOf<Listing>()
        for(i in 0 until arr.length()){
            val o=arr.optJSONObject(i)?:continue
            fun v(vararg keys:String):String { for(k in keys){val x=o.opt(k);if(x!=null && x!=JSONObject.NULL && x.toString().isNotBlank())return x.toString()};return "" }
            val name=v("name","title","business_name","organization_name","listing_name")
            if(name.isBlank()) continue
            val category=v("category","category_name","type","listing_type").ifBlank{"Community listing"}
            val location=v("location","district","city","address","area","town").ifBlank{"Location not provided"}
            val description=v("description","about","details","bio","summary").ifBlank{"More information coming soon."}
            val emoji=when {category.contains("school",true)->"🎓";category.contains("church",true)->"⛪";category.contains("mosque",true)->"🕌";category.contains("trad",true)->"🛍️";category.contains("graphic",true)->"🎨";category.contains("web",true)->"💻";category.contains("service",true)->"🛠️";category.contains("charity",true)->"🤝";else->"📍"}
            rows.add(Listing(v("id","uuid").ifBlank{"row-$i"},name,category,location,description,emoji))
        }
        Result(rows)
    }catch(e:Exception){Result(emptyList(),"Could not connect: ${e.localizedMessage ?: "check internet and Supabase settings"}")}
    finally{conn?.disconnect()}
}

@Composable fun MaltwebApp(){
    var tab by remember{mutableIntStateOf(0)}; var query by remember{mutableStateOf("")}; var category by remember{mutableStateOf("All")}
    var saved by remember{mutableStateOf(setOf<String>())}; var detail by remember{mutableStateOf<Listing?>(null)}
    var rows by remember{mutableStateOf<List<Listing>>(emptyList())}; var loading by remember{mutableStateOf(true)}; var error by remember{mutableStateOf<String?>(null)}; var refresh by remember{mutableIntStateOf(0)}
    LaunchedEffect(refresh){loading=true;val result=loadListings();rows=result.rows;error=result.error;loading=false}
    val categories=listOf("All")+rows.map{it.category}.filter{it.isNotBlank()}.distinct().sorted()
    val filtered=rows.filter{(category=="All"||it.category.equals(category,true))&&(it.name.contains(query,true)||it.category.contains(query,true)||it.location.contains(query,true)||it.description.contains(query,true))}
    MaterialTheme(colorScheme=lightColorScheme(primary=Green,background=Page,surface=Color.White,onSurface=Ink)){
        Scaffold(containerColor=Page,bottomBar={NavigationBar(containerColor=Color.White){NavigationBarItem(tab==0,{tab=0;detail=null},{Icon(Icons.Default.Explore,null)},label={Text("Discover")});NavigationBarItem(tab==1,{tab=1;detail=null},{Icon(Icons.Default.Category,null)},label={Text("Categories")});NavigationBarItem(tab==2,{tab=2;detail=null},{Icon(Icons.Default.Bookmark,null)},label={Text("Saved")});NavigationBarItem(tab==3,{tab=3;detail=null},{Icon(Icons.Default.Person,null)},label={Text("Profile")})}}){pad->
            if(detail!=null) Detail(detail!!,saved.contains(detail!!.id),{detail=null},{val id=detail!!.id;saved=if(id in saved)saved-id else saved+id},Modifier.padding(pad)) else when(tab){
                0->Discover(query,{query=it},category,categories,filtered,saved,loading,error,{refresh++},{category=it},{detail=it},{item->saved=if(item.id in saved)saved-item.id else saved+item.id},Modifier.padding(pad))
                1->Column(Modifier.padding(pad).fillMaxSize().padding(18.dp)){Text("Categories",fontSize=27.sp,fontWeight=FontWeight.Bold);Text("Choose a category from live listings.",color=Muted);LazyColumn{items(categories.drop(1)){c->ListItem(headlineContent={Text(c)},leadingContent={Text(emoji(c),fontSize=24.sp)},modifier=Modifier.clickable{category=c;tab=0})}}}
                2->Column(Modifier.padding(pad).fillMaxSize().padding(18.dp)){Text("Saved places",fontSize=27.sp,fontWeight=FontWeight.Bold);val list=rows.filter{it.id in saved};if(list.isEmpty())Text("No saved listings yet.",color=Muted) else LazyColumn{items(list){item->ListItem(headlineContent={Text(item.name)},supportingContent={Text(item.category+" • "+item.location)},modifier=Modifier.clickable{detail=item})}}}
                else->Column(Modifier.padding(pad).fillMaxSize().padding(20.dp)){Text("Your profile",fontSize=27.sp,fontWeight=FontWeight.Bold);Text("Account registration and role-based profiles are coming next.",color=Muted);Spacer(Modifier.height(18.dp));Text("Planned: ordinary users, businesses, service providers and administrators.")}
            }
        }
    }
}
private fun emoji(c:String)=when{c.contains("school",true)->"🎓";c.contains("church",true)->"⛪";c.contains("mosque",true)->"🕌";c.contains("trad",true)->"🛍️";c.contains("graphic",true)->"🎨";c.contains("web",true)->"💻";c.contains("service",true)->"🛠️";else->"📍"}

@Composable private fun Discover(query:String,onQuery:(String)->Unit,category:String,categories:List<String>,rows:List<Listing>,saved:Set<String>,loading:Boolean,error:String?,refresh:()->Unit,onCategory:(String)->Unit,onOpen:(Listing)->Unit,onSave:(Listing)->Unit,modifier:Modifier){
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=18.dp)){
        item{Column(Modifier.fillMaxWidth().background(Green).padding(20.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("M",fontSize=30.sp,fontWeight=FontWeight.ExtraBold,color=Color.White);Spacer(Modifier.width(12.dp));Column{Text("MALTWEB",fontSize=22.sp,fontWeight=FontWeight.ExtraBold,color=Color.White);Text("Your community, connected.",color=Color.White.copy(alpha=.8f))};Spacer(Modifier.weight(1f));Icon(Icons.Default.CloudDone,null,tint=Color.White)};Spacer(Modifier.height(20.dp));Text("Find what you need.",fontSize=27.sp,fontWeight=FontWeight.Bold,color=Color.White);Text("Discover businesses, people and services near you.",color=Color.White.copy(alpha=.86f));Spacer(Modifier.height(14.dp));OutlinedTextField(query,onQuery,Modifier.fillMaxWidth(),placeholder={Text("Search local places and services…")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,shape=RoundedCornerShape(16.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Color.White,unfocusedContainerColor=Color.White,focusedBorderColor=Color.White,unfocusedBorderColor=Color.White))}}
        item{Column(Modifier.padding(top=18.dp)){Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("Explore categories",fontSize=18.sp,fontWeight=FontWeight.Bold);Text("Refresh",color=Green,modifier=Modifier.clickable{refresh()})};LazyRow(contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){items(categories){c->FilterChip(category==c,{onCategory(c)},{Text(c)})}}}}
        item{Row(Modifier.fillMaxWidth().padding(18.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("Discover around you",fontSize=18.sp,fontWeight=FontWeight.Bold);Text(if(loading)"Loading…" else "${rows.size} places",color=Muted)}}
        if(loading)item{Box(Modifier.fillMaxWidth().padding(30.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}}
        else if(error!=null)item{Card(Modifier.fillMaxWidth().padding(horizontal=16.dp)){Column(Modifier.padding(16.dp)){Icon(Icons.Default.CloudOff,null,tint=Color(0xFFB45309));Text("Online listings could not load",fontWeight=FontWeight.Bold);Text(error,color=Muted,fontSize=12.sp);Spacer(Modifier.height(8.dp));Button(onClick=refresh){Text("Try again")}}}}
        else if(rows.isEmpty())item{Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Storefront,null,tint=Muted,modifier=Modifier.size(38.dp));Text("No public listings visible yet",fontWeight=FontWeight.Bold);Text("Connection returned no visible rows. Check the table and RLS policy.",color=Muted)}}
        else items(rows,key={it.id}){item->Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp).clickable{onOpen(item)},shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(62.dp).background(Pale,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Text(item.symbol,fontSize=28.sp)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(item.name,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(item.category,color=Green,fontSize=12.sp);Text("📍 ${item.location}",color=Muted,fontSize=12.sp);Text(item.description,color=Muted,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)};IconButton(onClick={onSave(item)}){Icon(if(item.id in saved)Icons.Default.Bookmark else Icons.Default.BookmarkBorder,null,tint=Green)}}}}
        item{Text("Live Supabase data • listings",color=Muted,fontSize=11.sp,modifier=Modifier.padding(18.dp))}
    }
}
@Composable private fun Detail(item:Listing,isSaved:Boolean,back:()->Unit,save:()->Unit,modifier:Modifier){Column(modifier.fillMaxSize()){Row(Modifier.fillMaxWidth().background(Green).padding(10.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=back){Icon(Icons.Default.ArrowBack,null,tint=Color.White)};Text("Listing details",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Color.White)};Column(Modifier.padding(20.dp)){Text(item.symbol,fontSize=55.sp);Text(item.name,fontSize=25.sp,fontWeight=FontWeight.Bold);Text(item.category,color=Green);Text("📍 ${item.location}",color=Muted);Spacer(Modifier.height(18.dp));Text("About",fontWeight=FontWeight.Bold,fontSize=18.sp);Text(item.description,color=Muted);Spacer(Modifier.height(20.dp));Button(onClick=save,modifier=Modifier.fillMaxWidth()){Text(if(isSaved)"Remove saved listing" else "Save this listing")}}}}
