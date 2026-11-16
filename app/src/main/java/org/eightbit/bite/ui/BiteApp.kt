// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.MealType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.eightbit.bite.core.*
import org.eightbit.bite.data.*
import org.eightbit.bite.health.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val meals = linkedMapOf(MealType.MEAL_TYPE_BREAKFAST to "Breakfast", MealType.MEAL_TYPE_LUNCH to "Lunch",
    MealType.MEAL_TYPE_DINNER to "Dinner", MealType.MEAL_TYPE_SNACK to "Snack")
internal fun Double.display() = java.math.BigDecimal.valueOf(this).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
internal fun Double?.nutrient(unit: String) = this?.let { "${it.display()} $unit" } ?: "—"
internal fun String.numeric() = replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiteApp(vm: BiteViewModel = viewModel(), launchAction: String? = null, launchId: Long = 0, aiVm:AiViewModel=viewModel()) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val favorites by vm.favoriteFoods.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val water by vm.waterEntries.collectAsStateWithLifecycle()
    val lastWaterId by vm.lastWaterId.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val supplements by vm.supplements.collectAsStateWithLifecycle()
    val intakes by vm.supplementIntakes.collectAsStateWithLifecycle()
    val catalogue by vm.catalogueInfo.collectAsStateWithLifecycle()
    val updates by vm.updateState.collectAsStateWithLifecycle()
    val query by vm.foodQuery.collectAsStateWithLifecycle()
    val externalBusy by vm.externalBusy.collectAsStateWithLifecycle()
    val aiState by aiVm.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    LaunchedEffect(language) {
        context.getSharedPreferences("locale", android.content.Context.MODE_PRIVATE).edit().putString("selected", language).apply()
        org.eightbit.bite.widgets.WidgetUpdates.request(context)
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, vm) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) vm.onOpen() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(vm) { vm.onOpen() }
    var news by remember(catalogue, language) { mutableStateOf(vm.app.updates.news(language)) }
    val noticePrefs = remember { context.getSharedPreferences("data-notice", android.content.Context.MODE_PRIVATE) }
    var notice by remember { mutableStateOf(!noticePrefs.getBoolean("seen-v1", false)) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var favoriteQuery by rememberSaveable { mutableStateOf("") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var aiSettings by rememberSaveable { mutableStateOf(false) }
    var foodSection by rememberSaveable { mutableIntStateOf(0) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = LocalDate.parse(selectedDate)
    var showFoodEditor by remember { mutableStateOf(false) }
    var addingFood by remember { mutableStateOf<Food?>(null) }
    var editingEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    var addingSupplement by rememberSaveable { mutableStateOf(false) }
    var doseProduct by remember { mutableStateOf<Supplement?>(null) }
    var editingDose by remember { mutableStateOf<SupplementIntake?>(null) }
    var targetProduct by remember { mutableStateOf<Supplement?>(null) }
    var privacy by remember { mutableStateOf(false) }
    var cameraRequest by remember { mutableLongStateOf(0) }
    var modelsRequest by remember { mutableLongStateOf(0) }
    var waterRequest by remember { mutableLongStateOf(0) }
    var consumedLaunch by rememberSaveable { mutableLongStateOf(-1) }
    BackHandler(settings) { if(aiSettings) aiSettings=false else settings=false }
    LaunchedEffect(tab) { if(tab==2) { foodSection=2;tab=1 } }
    LaunchedEffect(launchId) {
        if (consumedLaunch == launchId) return@LaunchedEffect
        consumedLaunch = launchId
        settings = false
        if (launchAction in setOf("today", "water", "foods", "ai", "camera")) selectedDate = LocalDate.now().toString()
        when (launchAction) {
            "ai", "camera" -> { tab = 3; if (launchAction == "camera") cameraRequest = launchId }
            "models", "ai-settings" -> { settings=true;aiSettings=true;if(launchAction=="models") modelsRequest=launchId }
            "foods" -> { tab=1;foodSection=0 }
            "profile", "today" -> tab = 0
            "water" -> { tab = 0; waterRequest = launchId }
        }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm, language) { vm.messages.collect { snackbar.showSnackbar(localized(context, it)) } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importCatalogue(uri) }
    val permission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(nutritionPermissions)) SyncScheduler.now(vm.app, manual = true)
    }
    fun connect() {
        if (HealthConnectClient.getSdkStatus(vm.app) == HealthConnectClient.SDK_AVAILABLE) permission.launch(nutritionPermissions)
        else vm.messages.tryEmit("Install or update Health Connect in Android settings")
    }
    fun edit(entry: DiaryEntry) {
        val factor = 100.0 / entry.grams
        addingFood = Food(id = entry.foodId, name = entry.name, source = entry.source,
            kcal = entry.kcal?.times(factor), protein = entry.protein?.times(factor), carbs = entry.carbs?.times(factor),
            fat = entry.fat?.times(factor), fiber = entry.fiber?.times(factor), sugar = entry.sugar?.times(factor), sodium = entry.sodium?.times(factor))
        editingEntry = entry
    }
    BiteTheme(prefs) {
        Scaffold(
            topBar = { TopAppBar(title = { if(settings && aiSettings) Text(ai("AI settings","AI ayarları")) else if(!settings && tab==3) Text(ai("Chat","Sohbet")) else BiteText(if(settings) "Settings" else if(tab==0) "You" else "Foods") },
                navigationIcon = { if (settings) IconButton(onClick = { if(aiSettings) aiSettings=false else settings=false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back")) } },
                actions = { if (!settings) IconButton(onClick = { cameraRequest = 0; modelsRequest = 0; aiSettings=tab==3;settings = true }) { Icon(Icons.Default.Settings, t("Settings")) } }) },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { NavigationBar {
                listOf(0,1,3).forEach { index -> val label=when(index) { 0->t("You");1->t("Foods");else->aiProviderName(aiState.provider) }
                    NavigationBarItem(selected = !settings && tab == index,
                    onClick = { settings = false; tab = index; cameraRequest = 0; modelsRequest = 0; waterRequest = 0; if (index == 0 || index == 3) addingSupplement = false },
                    icon = { when (index) {
                        0 -> ProfileAvatar(prefs, Modifier.size(28.dp))
                        1 -> Icon(Icons.AutoMirrored.Filled.List, t(label))
                        else -> AiProviderIcon(aiState.provider,Modifier.size(24.dp),label)
                    } }, label = { Text(label) }) }
            } },
            floatingActionButton = { if (!settings && tab != 3) ExtendedFloatingActionButton(onClick = {
                if (tab == 0 || (tab==1 && foodSection==1)) { addingSupplement = false;foodSection=0;tab = 1 } else showFoodEditor = true
            }, icon = { Icon(Icons.Default.Add, null) }, text = { BiteText(if (tab == 0 || (tab==1 && foodSection==1)) "Add food" else "Create food") }) },
        ) { insets ->
            val modifier = Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)
            if(settings && aiSettings) AiSettingsScreen(modifier,aiVm,modelsRequest)
            else if (settings) TrackerSettingsScreen(modifier, prefs, pending, vm::settings,
                connect = ::connect, sync = { SyncScheduler.now(vm.app, manual = true) },
                updates = { UpdatesPanel(vm, updates, catalogue) }, profile = { ProfilePhoto(vm, prefs) }, about = { privacy = true },
                aiSettings={ OutlinedCard(onClick={aiSettings=true},modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        AiProviderIcon(aiState.provider,Modifier.size(28.dp));Column { Text(ai("AI and chat","AI ve sohbet"),style=MaterialTheme.typography.titleMedium);Text(aiProviderName(aiState.provider),style=MaterialTheme.typography.bodySmall) }
                    }
                } })
            else when (tab) {
                0 -> DashboardScreen(modifier, date, entries, water, supplements, intakes, prefs,
                    changeDate = { selectedDate = it.toString() }, add = { addingSupplement = false;foodSection=0; tab = 1 },
                    addSupplement = { addingSupplement = true;foodSection=0; tab = 1 }, delete = vm::delete,
                    repeat = { vm.repeat(it, date) }, edit = ::edit,
                    addWater = { vm.addWater(it, date) }, deleteWater = vm::deleteWater, undoWater = vm::undoWater,
                    canUndoWater = water.any { it.id == lastWaterId && Instant.ofEpochMilli(it.time).atZone(ZoneId.systemDefault()).toLocalDate() == date },
                    save = vm::settings, settings = { settings = true },
                    dose = { doseProduct = it }, editDose = { dose -> supplements.find { it.id == dose.productId }?.let { doseProduct = it; editingDose = dose } },
                    deleteDose = vm::deleteSupplementIntake, editTarget = { targetProduct = it }, waterRequest = waterRequest)
                1, 2 -> {
                    val activeQuery = if (foodSection == 2) favoriteQuery else query
                    val matches = if (foodSection == 2) favorites.filter { food -> vm.app.catalogue.translator.alternatives(activeQuery)
                        .any { foldFoodText("${food.name} ${food.brand}").contains(it) } } else vm.foods.collectAsStateWithLifecycle().value
                    Column(modifier) {
                        SecondaryTabRow(selectedTabIndex=foodSection) { listOf("Foods","Meals","Favorites").forEachIndexed { index,label ->
                            Tab(selected=foodSection==index,onClick={foodSection=index},text={BiteText(label)})
                        } }
                        if(foodSection==1) MealsScreen(Modifier.fillMaxWidth().weight(1f),date,entries,{selectedDate=it.toString()},
                            {addingSupplement=false;foodSection=0},::edit,{vm.repeat(it,date)},vm::delete)
                        else FoodScreen(Modifier.fillMaxWidth().weight(1f), matches, favorites.map { it.id }.toSet(), foodSection == 2, vm::favorite,
                        query = activeQuery, queryChange = { if (foodSection == 2) favoriteQuery = it else { vm.foodQuery.value = it; vm.clearExternal() } }, busy = externalBusy,
                        external = { provider, key -> vm.externalSearch(provider, key) }, translator = vm.app.catalogue.translator,
                        add = { addingFood = it }, create = { showFoodEditor = true }, import = { import.launch(arrayOf("application/json", "text/plain")) })
                    }
                }
                3 -> AiScreen(date, onClose = { tab = 0; cameraRequest = 0; modelsRequest = 0 },vm=aiVm,
                    cameraRequest = cameraRequest, embedded = true,openSettings={settings=true;aiSettings=true},modifier=modifier)
            }
        }
        if (showFoodEditor) FoodEditor(onDismiss = { showFoodEditor = false }, save = { vm.saveFood(it); showFoodEditor = false })
        addingFood?.let { food -> PortionEditor(food, editingEntry, prefs.lastMealType, vm::selectMeal,
            initialSupplement = editingEntry?.isSupplement ?: addingSupplement,
            onDismiss = { addingFood = null; editingEntry = null },
            save = { selected, grams, meal, supplement, kind ->
                vm.add(selected, grams, meal, date, editingEntry, supplement, kind)
                addingFood = null; editingEntry = null
            }) }
        if (privacy) PrivacyDialog(vm, onDismiss = { privacy = false })
        if (notice) AlertDialog(onDismissRequest = { noticePrefs.edit().putBoolean("seen-v1", true).apply(); notice = false },
            title = { BiteText("Food data notice") }, text = { BiteText(dataNotice(language)) }, confirmButton = { TextButton(onClick = {
                noticePrefs.edit().putBoolean("seen-v1", true).apply(); notice = false
            }) { BiteText("Continue") } })
        else news?.let { item -> AlertDialog(onDismissRequest = { vm.app.updates.acknowledge(item); news = null },
            title = { BiteText("What’s new") }, text = { Column { item.notes.forEach { BiteText("• $it") } } },
            confirmButton = { TextButton(onClick = { vm.app.updates.acknowledge(item); news = null }) { BiteText("Continue") } }) }
        doseProduct?.let { product -> SupplementDoseEditor(product, editingDose,
            onDismiss = { doseProduct = null; editingDose = null }, save = { amount ->
                vm.logSupplement(product, amount, date, editingDose); doseProduct = null; editingDose = null
            }) }
        targetProduct?.let { product -> SupplementTargetEditor(product, onDismiss = { targetProduct = null }, save = { target ->
            vm.saveSupplement(product.copy(dailyTarget = target)); targetProduct = null
        }) }
    }
}

@Composable
private fun FoodScreen(modifier: Modifier, foods: List<Food>, favorites: Set<String>, onlyFavorites: Boolean,
    favorite: (Food, Boolean) -> Unit, query: String, queryChange: (String) -> Unit, busy: Boolean,
    external: (FoodProvider, String) -> Unit, translator: FoodTranslator,
    add: (Food) -> Unit, create: () -> Unit, import: () -> Unit) {
    var provider by remember { mutableStateOf(FoodProvider.OPEN_FOOD_FACTS) }
    var key by remember { mutableStateOf("") }
    var online by rememberSaveable { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { OutlinedTextField(query, queryChange, label = { BiteText(if (onlyFavorites) "Search favorites" else "Search your foods") },
            singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (!onlyFavorites) item { Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { online = !online }) { BiteText("Search online") }
            Spacer(Modifier.weight(1f))
            Box { IconButton(onClick = { options = true }) { Icon(Icons.Default.MoreVert, t("More options")) }
                DropdownMenu(options, onDismissRequest = { options = false }) {
                    DropdownMenuItem(text = { BiteText("Create food") }, onClick = { options = false; create() })
                    DropdownMenuItem(text = { BiteText("Import catalogue") }, onClick = { options = false; import() })
                }
            }
        } }
        if (!onlyFavorites && online) {
            item { BiteText("Searches local foods first. External queries are sent only when you tap Search online.", style = MaterialTheme.typography.bodySmall) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(provider == FoodProvider.OPEN_FOOD_FACTS, { provider = FoodProvider.OPEN_FOOD_FACTS }, label = { Text("Open Food Facts") })
                FilterChip(provider == FoodProvider.USDA, { provider = FoodProvider.USDA }, label = { Text("USDA") })
            } }
            if (provider == FoodProvider.USDA) item { OutlinedTextField(key, { key = it }, label = { BiteText("USDA API key · session only") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true) }
            item { OutlinedButton(onClick = { external(provider, key) }, enabled = !busy && query.trim().length >= 2) { BiteText(if (busy) "Searching…" else "Search online") } }
        }
        if (foods.isEmpty()) item { BiteText(if (onlyFavorites) "Star foods to keep them here for quick access." else "No matching foods. Try another name or create a food.") }
        items(foods, key = { it.id }) { food -> Card(onClick = { add(food) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 12.dp, end = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val displayName = translator.translate(food.name, language)
                        BiteText(displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (food.brand.isNotBlank()) Text(food.brand, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { favorite(food, food.id !in favorites) }) {
                        Icon(Icons.Default.Star, t(if (food.id in favorites) "Remove favorite" else "Add favorite"),
                            tint = if (food.id in favorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .5f))
                    }
                }
                Text("${food.kcal.nutrient("kcal")} · ${food.protein.nutrient("g")} ${t("Protein")} / 100 g", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(food.source, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (food.sourceUrl.isNotBlank()) TextButton(onClick = { runCatching {
                        val uri = android.net.Uri.parse(food.sourceUrl)
                        if (uri.scheme == "https") context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } }) { BiteText("Source") }
                }
            }
        } }
    }
}

@Composable
internal fun FoodEditor(onDismiss: () -> Unit, save: (Food) -> Unit, initialName:String="") {
    var name by remember { mutableStateOf(initialName) }
    var brand by remember { mutableStateOf("") }
    val values = remember { mutableStateMapOf("kcal" to "", "protein" to "", "carbs" to "", "fat" to "") }
    val valid = name.isNotBlank() && values.values.all { it.isBlank() || it.numeric() != null }
    AlertDialog(onDismissRequest = onDismiss, title = { BiteText("Create food · per 100 g") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(name, { name = it }, label = { BiteText("Food name") }, singleLine = true) }
            item { OutlinedTextField(brand, { brand = it }, label = { BiteText("Brand (optional)") }, singleLine = true) }
            item { BiteText("Leave unreported nutrients blank.", style = MaterialTheme.typography.bodySmall) }
            items(listOf("kcal" to "Energy · kcal", "protein" to "Protein · g", "carbs" to "Carbohydrate · g", "fat" to "Fat · g")) { (key, label) ->
                OutlinedTextField(values[key].orEmpty(), { values[key] = it }, label = { BiteText(label) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    isError = values[key].orEmpty().let { it.isNotBlank() && it.numeric() == null })
            }
        } }, confirmButton = { TextButton(enabled = valid, onClick = {
            save(Food(name = name.trim(), brand = brand.trim(), kcal = values["kcal"]?.numeric(),
                protein = values["protein"]?.numeric(), carbs = values["carbs"]?.numeric(), fat = values["fat"]?.numeric()))
        }) { BiteText("Save food") } }, dismissButton = { TextButton(onClick = onDismiss) { BiteText("Cancel") } })
}


@Composable
private fun PrivacyDialog(vm: BiteViewModel, onDismiss: () -> Unit) {
    var credits by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { VersionLabel(vm,"8bitBites · ${org.eightbit.bite.BuildConfig.VERSION_NAME}") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { BiteText("Your diary and profile stay on your device. GitHub is contacted for update checks. Food providers receive a search only when you request it. No account or analytics are required. Uninstalling removes local data.") }
            item { Text(ai("AI API mode sends only the description and photo you submit to your chosen service. API keys remain in session memory. Local AI stays on your device.",
                "AI API modu yalnızca gönderdiğin açıklama ve fotoğrafı seçtiğin servise iletir. API anahtarları oturum belleğinde tutulur. Yerel AI cihazında çalışır.")) }
            item { BiteText("Health Connect sharing is optional and writes only your nutrition entries. Edits and deletions remain queued until sync succeeds. Android controls permissions and background timing.") }
            item { BiteText("Open source · GPL-3.0-or-later. Themes retain their original license notices.") }
            item { TextButton(onClick = { credits = vm.app.assets.open("themes/NOTICES.md").bufferedReader().use { it.readText() } }) { BiteText("Theme credits") } }
            item { TextButton(onClick = { credits = vm.app.assets.open("licenses/litert-lm.txt").bufferedReader().use { it.readText() } }) { Text(ai("AI runtime license","AI çalışma ortamı lisansı")) } }
            item { TextButton(onClick = { credits = vm.app.assets.open("licenses/chatgpt-brand.txt").bufferedReader().use { it.readText() } }) { Text(ai("ChatGPT mark attribution","ChatGPT simgesi hakkında")) } }
            credits?.let { item { BiteText(it, style = MaterialTheme.typography.bodySmall) } }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { BiteText("Close") } })
}
