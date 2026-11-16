// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.eightbit.bite.data.Preferences
import java.io.File

@Composable
fun ProfileAvatar(prefs:Preferences,modifier:Modifier=Modifier) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val bitmap=remember(prefs.avatarVersion) {
        if(prefs.avatarVersion>0) BitmapFactory.decodeFile(File(context.filesDir,"profile.jpg").absolutePath) else null
    }
    if(bitmap!=null) Image(bitmap.asImageBitmap(),ai("Your profile","Profilin"),contentScale=ContentScale.Crop,modifier=modifier.clip(CircleShape))
    else Icon(Icons.Default.Person,ai("Your profile","Profilin"),modifier=modifier)
}

@Composable
fun ProfilePhoto(vm:BiteViewModel,prefs:Preferences) {
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if(uri!=null) vm.profilePhoto(uri) }
    val bitmap=remember(prefs.avatarVersion) {
        if(prefs.avatarVersion>0) BitmapFactory.decodeFile(File(vm.app.filesDir,"profile.jpg").absolutePath) else null
    }
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Surface(shape=CircleShape,color=MaterialTheme.colorScheme.secondaryContainer,modifier=Modifier.size(80.dp)) {
            if(bitmap!=null) Image(bitmap.asImageBitmap(),ai("Profile photo","Profil fotoğrafı"),contentScale=ContentScale.Crop,modifier=Modifier.clip(CircleShape))
            else Icon(Icons.Default.Person,ai("Profile photo","Profil fotoğrafı"),modifier=Modifier.padding(16.dp))
        }
        Column {
            OutlinedButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}) { Text(ai("Choose profile photo","Profil fotoğrafı seç")) }
            if(bitmap!=null) TextButton(onClick={vm.profilePhoto(null)}) { Text(ai("Remove photo","Fotoğrafı kaldır")) }
            Text(ai("Stored on this device","Bu cihazda saklanır"),style=MaterialTheme.typography.bodySmall)
        }
    }
}
