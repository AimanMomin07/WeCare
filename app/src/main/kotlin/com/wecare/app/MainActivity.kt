package com.wecare.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Ink = Color(0xFF142B3B)
private val Teal = Color(0xFF138C83)
private val Mint = Color(0xFFE7F5F1)
private val Paper = Color(0xFFF5F8F8)
private val Muted = Color(0xFF71818A)
private val Line = Color(0xFFE6ECEC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WeCareApp() }
    }
}

private data class Doctor(
    val name: String, val specialty: String, val rating: String, val experience: String,
    val initials: String, val tint: Color, val fee: String
)
private data class Visit(
    val doctor: String, val specialty: String, val date: String, val time: String,
    val initials: String, val status: String = "Confirmed"
)
private val doctors = listOf(
    Doctor("Dr. Maya Chen", "Cardiologist", "4.9", "12 years", "MC", Color(0xFFD9E9F5), "$85"),
    Doctor("Dr. James Wilson", "Dermatologist", "4.8", "9 years", "JW", Color(0xFFF5E2D5), "$70"),
    Doctor("Dr. Amara Okafor", "Family medicine", "5.0", "15 years", "AO", Color(0xFFE8E1F5), "$65"),
    Doctor("Dr. Sofia Patel", "Pediatrician", "4.9", "11 years", "SP", Color(0xFFF5E8CE), "$75")
)
private enum class Destination(val label: String) {
    HOME("Home"), DOCTORS("Find care"), VISITS("My visits"), PROFILE("Profile")
}

@Composable
private fun WeCareApp() {
    val context = LocalContext.current
    val auth = remember { runCatching { FirebaseAuth.getInstance() }.getOrNull() }
    var currentUser by remember { mutableStateOf(auth?.currentUser) }
    val visits = remember { mutableStateListOf<Visit>().apply { addAll(loadVisits(context)) } }
    var destination by rememberSaveable { mutableStateOf(Destination.HOME.name) }
    var bookingDoctor by remember { mutableStateOf<Doctor?>(null) }
    val wide = LocalConfiguration.current.screenWidthDp >= 700

    MaterialTheme(
        colorScheme = lightColorScheme(primary = Teal, onPrimary = Color.White, secondary = Color(0xFFDAA762),
            background = Paper, surface = Color.White, onSurface = Ink, onBackground = Ink),
        typography = Typography(
            headlineMedium = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, color = Ink),
            titleLarge = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, color = Ink),
            titleMedium = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold, color = Ink),
            bodyLarge = androidx.compose.ui.text.TextStyle(color = Ink),
            bodyMedium = androidx.compose.ui.text.TextStyle(color = Muted)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            if (auth == null) {
                FirebaseUnavailableScreen()
            } else if (currentUser == null) {
                AuthScreen(auth) { currentUser = auth.currentUser }
            } else if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor = Color.White, modifier = Modifier.fillMaxHeight().padding(vertical = 14.dp)) {
                        Spacer(Modifier.height(14.dp))
                        Text("W", Modifier.padding(bottom = 28.dp).clip(CircleShape).background(Mint).padding(13.dp),
                            color = Teal, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Destination.entries.forEach { item ->
                            NavigationRailItem(destination == item.name, { destination = item.name },
                                icon = { DestinationIcon(item) }, label = { Text(item.label) })
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        MainContent(destination, visits, currentUser!!, { auth.signOut(); currentUser = null },
                            { destination = it.name }, { bookingDoctor = it })
                    }
                }
            } else {
                Scaffold(containerColor = Paper, bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        Destination.entries.forEach { item ->
                            NavigationBarItem(destination == item.name, { destination = item.name },
                                icon = { DestinationIcon(item) }, label = { Text(item.label, maxLines = 1) })
                        }
                    }
                }) { padding ->
                    MainContent(destination, visits, currentUser!!, { auth.signOut(); currentUser = null },
                        { destination = it.name }, { bookingDoctor = it }, Modifier.padding(padding))
                }
            }
            bookingDoctor?.let { doctor ->
                BookingDialog(doctor, { bookingDoctor = null }) { date, time ->
                    visits.add(0, Visit(doctor.name, doctor.specialty, date, time, doctor.initials))
                    saveVisits(context, visits)
                    bookingDoctor = null
                    destination = Destination.VISITS.name
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(item: Destination) {
    val symbol = when (item) {
        Destination.HOME -> "⌂"
        Destination.DOCTORS -> "⌕"
        Destination.VISITS -> "▦"
        Destination.PROFILE -> "●"
    }
    Text(symbol, color = Teal, fontSize = 22.sp)
}

@Composable
private fun FirebaseUnavailableScreen() {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("WeCare", color = Teal, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        Spacer(Modifier.height(12.dp))
        Text("Firebase is not configured", color = Ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("Check the Firebase setup and try again.", color = Muted)
    }
}

@Composable
private fun AuthScreen(auth: FirebaseAuth, onAuthenticated: () -> Unit) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
        Text("WECARE", color = Teal, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text(if (registering) "Create your account" else "Welcome back", color = Ink,
            fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(if (registering) "Start managing your care in one place." else "Sign in to continue to your care.",
            color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (registering) OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Full name") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Email") },
                    singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Button(onClick = {
                    error = null
                    if (email.isBlank() || password.length < 6 || (registering && name.isBlank())) {
                        error = if (registering) "Enter your name, a valid email, and a password with at least 6 characters."
                            else "Enter your email and a password with at least 6 characters."
                    } else {
                        busy = true
                        val task = if (registering) auth.createUserWithEmailAndPassword(email.trim(), password)
                            else auth.signInWithEmailAndPassword(email.trim(), password)
                        task.addOnCompleteListener { result ->
                            busy = false
                            if (result.isSuccessful) {
                                val user = auth.currentUser
                                if (registering && user != null) {
                                    user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())
                                        .addOnCompleteListener { onAuthenticated() }
                                } else onAuthenticated()
                            } else {
                                error = result.exception?.localizedMessage ?: "Sign in failed. Please try again."
                            }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !busy,
                    shape = RoundedCornerShape(14.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(if (registering) "Create account" else "Sign in", fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { registering = !registering; error = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (registering) "Already have an account? Sign in" else "New to WeCare? Create an account")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Secure sign in powered by Firebase", Modifier.align(Alignment.CenterHorizontally),
            color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun FirebaseConnectionBadge() {
    var isConnected by remember { mutableStateOf<Boolean?>(null) }
    val connectionReference = remember {
        runCatching { FirebaseDatabase.getInstance().getReference(".info/connected") }.getOrNull()
    }

    DisposableEffect(connectionReference) {
        val listener = if (connectionReference == null) null else object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isConnected = snapshot.getValue(Boolean::class.java) == true
            }

            override fun onCancelled(error: DatabaseError) {
                isConnected = false
            }
        }
        if (listener == null) {
            isConnected = false
        } else {
            connectionReference?.addValueEventListener(listener)
        }
        onDispose {
            if (listener != null) connectionReference?.removeEventListener(listener)
        }
    }

    val badgeColor = if (isConnected == true) Teal else Muted
    val label = when (isConnected) {
        true -> "Firebase live"
        false -> "Firebase offline"
        null -> "Connecting to Firebase"
    }
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(badgeColor))
        Spacer(Modifier.width(7.dp))
        Text(label, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MainContent(destination: String, visits: List<Visit>, user: FirebaseUser,
                       onSignOut: () -> Unit, onDestination: (Destination) -> Unit,
                       onBook: (Doctor) -> Unit, modifier: Modifier = Modifier) {
    when (Destination.valueOf(destination)) {
        Destination.HOME -> HomeScreen(visits, onDestination, onBook, modifier)
        Destination.DOCTORS -> DoctorsScreen(onBook, modifier)
        Destination.VISITS -> VisitsScreen(visits, { onDestination(Destination.DOCTORS) }, modifier)
        Destination.PROFILE -> ProfileScreen(user, onSignOut, modifier)
    }
}

@Composable
private fun HomeScreen(visits: List<Visit>, onDestination: (Destination) -> Unit,
                       onBook: (Doctor) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("WECARE", color = Teal, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, fontSize = 13.sp)
                    Text("Good morning, Alex", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("Your health, in good hands.", color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    FirebaseConnectionBadge()
                }
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Text("A", color = Teal, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
        item { CareHero { onDestination(Destination.DOCTORS) } }
        item { SectionHeader("Your next visit", "See all") { onDestination(Destination.VISITS) } }
        item { if (visits.isEmpty()) EmptyVisitCard { onBook(doctors.first()) } else VisitCard(visits.first()) }
        item {
            Column {
                Text("Care that fits you", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Choose a specialty to get started", color = Muted, fontSize = 13.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpecialtyCard("Heart health", "Cardiology", "♡", Modifier.weight(1f)) { onDestination(Destination.DOCTORS) }
                SpecialtyCard("Skin health", "Dermatology", "✦", Modifier.weight(1f)) { onDestination(Destination.DOCTORS) }
            }
        }
        item { SectionHeader("Recommended for you", "Browse") { onDestination(Destination.DOCTORS) } }
        items(doctors.take(2)) { doctor -> DoctorCard(doctor, onBook) }
        item { DemoNote() }
    }
}

@Composable
private fun CareHero(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF17394A), Color(0xFF176E69)))).padding(23.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("CARE, MADE PERSONAL", color = Color(0xFFB7E8DD), letterSpacing = 1.4.sp, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("A healthier you\nstarts here.", color = Color.White, fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
            Text("Find trusted specialists and book a visit that works for you.", color = Color.White.copy(alpha = .82f), fontSize = 14.sp, lineHeight = 20.sp)
            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink),
                shape = RoundedCornerShape(14.dp)) {
                Text("Find a doctor", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp)); Text("→")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(action, Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(8.dp),
            color = Teal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyVisitCard(onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) {
                Text("▦", color = Teal, fontSize = 22.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text("Nothing on your calendar", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 14.sp)
                Text("Book your first appointment", color = Muted, fontSize = 12.sp)
            }
            Text("Book", Modifier.clip(RoundedCornerShape(10.dp)).background(Mint).clickable(onClick = onClick)
                .padding(horizontal = 13.dp, vertical = 9.dp), color = Teal, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VisitCard(visit: Visit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(visit.initials, Color(0xFFD9E9F5), 48)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(visit.doctor, fontWeight = FontWeight.Bold, color = Ink)
                Text(visit.specialty, color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text("${visit.date}  ·  ${visit.time}", color = Teal, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            Text("✓", color = Teal, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SpecialtyCard(title: String, subtitle: String, symbol: String, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(symbol, color = Teal, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DoctorsScreen(onBook: (Doctor) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedSpecialty by rememberSaveable { mutableStateOf("All") }
    val specialties = listOf("All", "Heart", "Skin", "Family", "Children")
    val filtered = doctors.filter {
        (query.isBlank() || it.name.contains(query, true) || it.specialty.contains(query, true)) &&
            (selectedSpecialty == "All" || it.specialty.contains(selectedSpecialty, true))
    }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Find care", Modifier.padding(top = 24.dp), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("The right care starts with the right doctor.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Doctor, specialty…") }, leadingIcon = { Text("⌕", color = Muted, fontSize = 22.sp) },
            singleLine = true, shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Teal,
                unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            specialties.forEach { specialty ->
                FilterChip(selectedSpecialty == specialty, { selectedSpecialty = specialty },
                    label = { Text(specialty) }, shape = RoundedCornerShape(50))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("${filtered.size} specialists", color = Muted, fontSize = 13.sp)
        if (LocalConfiguration.current.screenWidthDp >= 700) {
            LazyVerticalGrid(GridCells.Adaptive(310.dp), Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered.size) { index -> DoctorCard(filtered[index], onBook) }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) { items(filtered) { DoctorCard(it, onBook) } }
        }
    }
}

@Composable
private fun DoctorCard(doctor: Doctor, onBook: (Doctor) -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(doctor.initials, doctor.tint, 56)
                Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                    Text(doctor.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(doctor.specialty, color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("★", color = Color(0xFFE2AD54), fontSize = 13.sp)
                        Text(" ${doctor.rating}  ·  ${doctor.experience}", color = Muted, fontSize = 12.sp)
                    }
                }
                Text(doctor.fee, color = Ink, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp)); HorizontalDivider(color = Line); Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⌖", color = Teal, fontSize = 16.sp)
                Text("WeCare Health Centre", Modifier.weight(1f).padding(start = 4.dp), color = Muted, fontSize = 12.sp)
                Button(onClick = { onBook(doctor) }, contentPadding = PaddingValues(horizontal = 17.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)) { Text("Book visit") }
            }
        }
    }
}

@Composable
private fun VisitsScreen(visits: List<Visit>, onBook: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("My visits", Modifier.padding(top = 24.dp), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Your appointments, all in one place.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        if (visits.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) {
                        Text("▦", color = Teal, fontSize = 32.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Your care plan starts here", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Book an appointment with a trusted specialist.", color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBook, shape = RoundedCornerShape(14.dp)) { Text("Explore doctors") }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Text("UPCOMING", color = Teal, fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold) }
                items(visits) { VisitCard(it) }
                item { DemoNote() }
            }
        }
    }
}

@Composable
private fun ProfileScreen(user: FirebaseUser, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var profile by remember(user.uid) { mutableStateOf(loadProfile(context, user)) }
    var editing by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().padding(22.dp)) {
        Text("Profile", Modifier.padding(top = 8.dp), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(20.dp))
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile.name.initials(), Mint, 58)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(profile.name.ifBlank { "Your profile" }, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(user.email.orEmpty(), color = Muted, fontSize = 13.sp)
                }
                TextButton(onClick = { editing = true }) { Text("Edit") }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("PERSONAL INFORMATION", color = Muted, fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ProfileRow("Full name", profile.name.ifBlank { "Add your name" })
        ProfileRow("Phone number", profile.phone.ifBlank { "Add your phone number" })
        ProfileRow("Date of birth", profile.birthDate.ifBlank { "Add your date of birth" })
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("Sign out")
        }
        Spacer(Modifier.height(12.dp))
        DemoNote()
    }
    if (editing) {
        EditProfileDialog(profile, onDismiss = { editing = false }) { updated ->
            profile = updated
            saveProfile(context, user, updated)
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(updated.name).build())
            editing = false
        }
    }
}

@Composable
private fun ProfileRow(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        Text("✎", color = Teal, fontSize = 16.sp)
    }
    HorizontalDivider(color = Line)
}

private data class UserProfile(val name: String, val phone: String, val birthDate: String)

private fun loadProfile(context: Context, user: FirebaseUser): UserProfile {
    val preferences = context.getSharedPreferences("wecare_profile_${user.uid}", Context.MODE_PRIVATE)
    return UserProfile(user.displayName ?: "", preferences.getString("phone", "") ?: "",
        preferences.getString("birthDate", "") ?: "")
}

private fun saveProfile(context: Context, user: FirebaseUser, profile: UserProfile) {
    context.getSharedPreferences("wecare_profile_${user.uid}", Context.MODE_PRIVATE).edit()
        .putString("phone", profile.phone).putString("birthDate", profile.birthDate).apply()
}

private fun String.initials(): String = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifBlank { "WC" }

@Composable
private fun EditProfileDialog(profile: UserProfile, onDismiss: () -> Unit, onSave: (UserProfile) -> Unit) {
    var name by remember(profile) { mutableStateOf(profile.name) }
    var phone by remember(profile) { mutableStateOf(profile.phone) }
    var birthDate by remember(profile) { mutableStateOf(profile.birthDate) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Edit personal information", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone number") }, singleLine = true)
                OutlinedTextField(birthDate, { birthDate = it }, label = { Text("Date of birth") },
                    placeholder = { Text("DD/MM/YYYY") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(UserProfile(name.trim(), phone.trim(), birthDate.trim())) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(24.dp))
}

@Composable
private fun DemoNote() {
    Text("DEMO EXPERIENCE  ·  Appointments are stored on this device.",
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mint).padding(12.dp),
        color = Teal, fontSize = 11.sp, lineHeight = 16.sp)
}

@Composable
private fun Avatar(initials: String, tint: Color, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
        Text(initials, color = Ink, fontWeight = FontWeight.Bold, fontSize = if (size > 50) 16.sp else 14.sp)
    }
}

@Composable
private fun BookingDialog(doctor: Doctor, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var dayIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedTime by rememberSaveable { mutableStateOf("10:30 AM") }
    val dates = (1..3).map { LocalDate.now().plusDays(it.toLong()) }
    val slots = listOf("09:00 AM", "10:30 AM", "01:00 PM", "03:30 PM")
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Book your visit", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(doctor.name, color = Teal, fontWeight = FontWeight.SemiBold)
                Text(doctor.specialty, color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(18.dp)); Text("Choose a day", color = Ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    dates.forEachIndexed { index, date ->
                        val label = date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                        FilterChip(dayIndex == index, { dayIndex = index }, label = { Text(label, fontSize = 11.sp) })
                    }
                }
                Spacer(Modifier.height(14.dp)); Text("Available times", color = Ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                slots.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { slot ->
                            FilterChip(selectedTime == slot, { selectedTime = slot },
                                modifier = Modifier.weight(1f), label = { Text(slot, fontSize = 12.sp) })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp)); Text("Consultation  ${doctor.fee}", color = Muted, fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                val formatted = dates[dayIndex].format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                onConfirm(formatted, selectedTime)
            }) { Text("Confirm visit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(24.dp))
}

private fun loadVisits(context: Context): List<Visit> = runCatching {
    val raw = context.getSharedPreferences("wecare_demo", Context.MODE_PRIVATE).getString("visits", "[]") ?: "[]"
    val array = JSONArray(raw)
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        Visit(item.getString("doctor"), item.getString("specialty"), item.getString("date"),
            item.getString("time"), item.getString("initials"))
    }
}.getOrDefault(emptyList())

private fun saveVisits(context: Context, visits: List<Visit>) {
    val array = JSONArray()
    visits.forEach { visit ->
        array.put(JSONObject().apply {
            put("doctor", visit.doctor); put("specialty", visit.specialty)
            put("date", visit.date); put("time", visit.time); put("initials", visit.initials)
        })
    }
    context.getSharedPreferences("wecare_demo", Context.MODE_PRIVATE)
        .edit().putString("visits", array.toString()).apply()
}
