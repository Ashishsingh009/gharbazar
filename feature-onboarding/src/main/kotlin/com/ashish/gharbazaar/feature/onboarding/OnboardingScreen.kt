package com.ashish.gharbazaar.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ashish.gharbazaar.domain.AccessibilityPolicy
import com.ashish.gharbazaar.domain.Diet
import com.ashish.gharbazaar.domain.Household
import com.ashish.gharbazaar.domain.LanguagePack

@Composable
fun OnboardingScreen(pack: LanguagePack, onSave: (Household) -> Unit) {
    var members by rememberSaveable { mutableStateOf("4") }
    var budget by rememberSaveable { mutableStateOf("2500") }
    var region by rememberSaveable { mutableStateOf("Maharashtra") }
    var allergies by rememberSaveable { mutableStateOf("") }
    var exclusions by rememberSaveable { mutableStateOf("") }
    var diet by rememberSaveable { mutableStateOf(Diet.VEGETARIAN.name) }
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(pack.text("onboarding_title"))
        OutlinedTextField(members, { members = it }, label = { Text(pack.text("onboarding_members")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(budget, { budget = it }, label = { Text(pack.text("onboarding_budget")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(region, { region = it }, label = { Text(pack.text("onboarding_region")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(allergies, { allergies = it }, label = { Text(pack.text("onboarding_allergies")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(exclusions, { exclusions = it }, label = { Text(pack.text("onboarding_exclusions")) }, modifier = Modifier.fillMaxWidth())
        Diet.entries.forEach { option ->
            Button(
                onClick = { diet = option.name },
                modifier = Modifier.heightIn(min = AccessibilityPolicy.MIN_TOUCH_TARGET_DP.dp).fillMaxWidth(),
            ) { Text(option.name) }
        }
        Button(
            onClick = {
                val count = members.toIntOrNull() ?: 1
                onSave(
                    Household(
                        id = "home",
                        name = "Household",
                        memberCount = count,
                        region = region,
                        weeklyBudgetInr = budget.toIntOrNull() ?: 0,
                        diet = Diet.valueOf(diet),
                        allergies = allergies.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                        exclusions = exclusions.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                        language = pack.locale,
                        members = List(count) { "Member ${it + 1}" },
                    ),
                )
            },
            modifier = Modifier
                .heightIn(min = AccessibilityPolicy.MIN_TOUCH_TARGET_DP.dp)
                .semantics { contentDescription = pack.text("a11y_generate_plan") },
        ) { Text(pack.text("onboarding_save")) }
    }
}
