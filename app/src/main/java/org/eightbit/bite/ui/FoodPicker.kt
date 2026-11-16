// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Catalogue and favorites are destinations within the add flow, never permanent navigation tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun FoodPicker(favorites:Boolean,selectFavorites:(Boolean)->Unit,close:()->Unit,content:@Composable (Modifier)->Unit) {
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize().testTag("food-picker")) {
            Scaffold(modifier=Modifier.imePadding(),topBar={
                TopAppBar(title={BiteText("Add food")},navigationIcon={
                    IconButton(onClick=close) { Icon(Icons.Default.Close,t("Close")) }
                })
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    SecondaryTabRow(selectedTabIndex=if(favorites) 1 else 0) {
                        Tab(selected=!favorites,onClick={selectFavorites(false)},text={Text(ai("Search","Ara"))})
                        Tab(selected=favorites,onClick={selectFavorites(true)},text={BiteText("Favorites")})
                    }
                    content(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}
