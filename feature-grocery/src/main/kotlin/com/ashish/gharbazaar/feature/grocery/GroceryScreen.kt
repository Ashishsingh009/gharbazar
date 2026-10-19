package com.ashish.gharbazaar.feature.grocery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ashish.gharbazaar.domain.AccessibilityPolicy
import com.ashish.gharbazaar.domain.GroceryList
import com.ashish.gharbazaar.domain.LanguagePack

@Composable
fun GroceryScreen(pack: LanguagePack, list: GroceryList?, offline: Boolean, onToggle: (String, Boolean) -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text(pack.text("tab_grocery"))
        if (offline) Text(pack.text("offline_banner"))
        Text(pack.text("sync_saved"))
        list?.items?.groupBy { it.aisle }?.forEach { (aisle, items) ->
            Text(aisle)
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = item.purchased,
                        onCheckedChange = { onToggle(item.id, it) },
                        modifier = Modifier
                            .heightIn(min = AccessibilityPolicy.MIN_TOUCH_TARGET_DP.dp)
                            .semantics { contentDescription = pack.text("a11y_grocery_item") },
                    )
                    Text("${item.name} ${item.quantity} ${pack.unit(item.unit)}")
                }
            }
        }
    }
}
