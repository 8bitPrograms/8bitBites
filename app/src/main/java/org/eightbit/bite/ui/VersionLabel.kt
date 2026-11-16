package org.eightbit.bite.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun VersionLabel(vm:BiteViewModel,label:String) {
    val context=LocalContext.current
    val turkish=LocalConfiguration.current.locales[0].language=="tr"
    Text(label,modifier=Modifier.clickable {
        if(vm.versionTap()) Toast.makeText(context,if(turkish) "8bit temaları açıldı" else "8bit themes unlocked",Toast.LENGTH_SHORT).show()
    }.padding(vertical=8.dp))
}
