package org.eightbit.bite.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.eightbit.bite.BuildConfig
import org.eightbit.bite.data.*

@Composable
fun UpdatesPanel(vm: BiteViewModel, status: UpdateState, installed: CatalogueInfo?) {
    val context=LocalContext.current
    var showNotes by remember { mutableStateOf(false) }
    val import=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) vm.importDatabase(uri)
    }
    val language=androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    BiteText("Language",style=MaterialTheme.typography.titleMedium)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf("en" to "English","tr" to "Türkçe").forEachIndexed { index, (tag,label) ->
            SegmentedButton(selected=language==tag,shape=SegmentedButtonDefaults.itemShape(index,2),onClick={
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }) { BiteText(label) }
        }
    }
    HorizontalDivider()
    BiteText("Updates",style=MaterialTheme.typography.titleMedium)
    VersionLabel(vm,"App ${BuildConfig.VERSION_NAME}")
    BiteText("Database ${installed?.version ?: "—"} · ${installed?.count ?: 0} foods")
    BiteText("The food database can be updated without reinstalling the app.")
    OutlinedButton(onClick={import.launch(arrayOf("*/*"))},enabled=!status.installing) { Text(ai("Import SQLite database update","SQLite veritabanı güncellemesi yükle")) }
    OutlinedButton(onClick={vm.checkUpdates()},enabled=!status.checking && !status.installing) {
        BiteText(if(status.checking) "Checking updates…" else "Check for updates")
    }
    if(status.appError || status.databaseError) BiteText("Some update checks failed. Your offline data is still available.")
    status.app?.takeIf { it.code>BuildConfig.VERSION_CODE }?.let { release ->
        BiteText("App update available: ${release.name}")
        if(release.highlights) release.notes[language].orEmpty().ifEmpty { release.notes["en"].orEmpty() }.forEach { BiteText("• $it") }
        Button(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(vm.app.updates.appReleaseUrl)))}) { BiteText("Open app releases") }
    }
    status.database?.takeIf { installed==null || it.newerThan(installed.version) }?.let { release ->
        BiteText("Database update available: ${release.version}")
        if(release.highlights) release.notes[language].orEmpty().ifEmpty { release.notes["en"].orEmpty() }.forEach { BiteText("• $it") }
        val compatible=release.compatible(BuildConfig.VERSION_CODE.toLong())
        if(!compatible) BiteText("Update the app before installing this database version.")
        Button(onClick={vm.updateDatabase()},enabled=compatible && !status.installing && !status.checking) {
            BiteText(if(status.installing) "Updating database…" else "Update database only")
        }
    }
    if(status.installError) BiteText("Database update failed validation. Your previous catalogue is unchanged.")
    TextButton(onClick={showNotes=true}) { BiteText("Database accuracy and source notice") }
    if(showNotes) AlertDialog(onDismissRequest={showNotes=false},title={BiteText("Food data notice")},text={BiteText(dataNotice(language))},
        confirmButton={TextButton(onClick={showNotes=false}){BiteText("Close")}})
}

fun dataNotice(language: String) = if(language=="tr")
    "Veriler gönüllü katkılarla derlenmiştir. Değerler hatalı olabilir; lütfen ürün etiketini kontrol edin."
else "Data is compiled through voluntary contributions. Values may be inaccurate; please check the product label."
