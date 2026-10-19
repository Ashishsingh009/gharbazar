package com.ashish.gharbazaar.feature.mealplan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ashish.gharbazaar.domain.AccessibilityPolicy
import com.ashish.gharbazaar.domain.LanguagePack
import com.ashish.gharbazaar.domain.MealPlan
import com.ashish.gharbazaar.domain.PhotoAnalysis
import com.ashish.gharbazaar.domain.RecipeAnswer

@Composable
fun MealPlanScreen(
    pack: LanguagePack,
    plan: MealPlan?,
    status: String?,
    onGenerate: () -> Unit,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onGrocery: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pack.text("tab_plan"))
        status?.let { Text(it) }
        Button(
            onClick = onGenerate,
            modifier = Modifier
                .heightIn(min = AccessibilityPolicy.MIN_TOUCH_TARGET_DP.dp)
                .semantics { contentDescription = pack.text("a11y_generate_plan") },
        ) { Text(if (plan == null) pack.text("generate_plan") else pack.text("regenerate_plan")) }
        plan?.let { current ->
            Text("${current.days} · ${pack.inr(current.estimatedCostInr)} / ${pack.inr(current.budgetInr)}")
            current.note?.let { Text(it) }
            current.meals.forEach { meal ->
                Text("${meal.day} ${meal.slot}: ${meal.name} ${pack.inr(meal.costInr)}")
                meal.substitution?.let { swap ->
                    Text("${swap.replacementName}: ${swap.reason}")
                    Button(
                        onClick = { onAccept(swap.id) },
                        modifier = Modifier.semantics { contentDescription = pack.text("a11y_accept_swap") },
                    ) { Text(pack.text("accept_swap")) }
                    Button(onClick = { onReject(swap.id) }) { Text(pack.text("reject_swap")) }
                }
            }
            Button(onClick = onGrocery, modifier = Modifier.fillMaxWidth()) { Text(pack.text("build_grocery")) }
        }
    }
}

@Composable
fun RecipeScreen(pack: LanguagePack, answer: RecipeAnswer?, onAsk: (String) -> Unit, onAddGrocery: () -> Unit) {
    var request by rememberSaveable { mutableStateOf("Give me all the ingredients for chicken butter masala; I want 50 grams of protein from one serving.") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pack.text("tab_recipe"))
        OutlinedTextField(request, { request = it }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onAsk(request) }) { Text(pack.text("generate_plan")) }
        answer?.let { result ->
            Text(result.explanation)
            Text(result.disclaimer)
            result.ingredients.forEach { ingredient ->
                Text("${ingredient.name} ${ingredient.quantity} ${pack.unit(ingredient.unit)}")
            }
            result.estimate?.let { estimate ->
                Text("${estimate.macros.proteinGrams} g protein · ${estimate.sourceVersion}")
            }
            if (result.ingredients.isNotEmpty()) {
                Button(onClick = onAddGrocery) { Text(pack.text("build_grocery")) }
            }
        }
    }
}

@Composable
fun PhotoScreen(
    pack: LanguagePack,
    analysis: PhotoAnalysis?,
    onReference: (String, Boolean) -> Unit,
    onCorrect: (String, Double) -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) onReference("unreadable", true) else onReference("fixture:thali", true)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) onReference("unreadable", false)
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pack.text("tab_photo"))
        Text(pack.text("estimate_disclaimer"))
        Button(onClick = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) { Text(pack.text("photo_permission")) }
        Button(onClick = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (granted) onReference("fixture:thali", true) else cameraPermission.launch(Manifest.permission.CAMERA)
        }) { Text(pack.text("photo_correct")) }
        analysis?.let { result ->
            Text(result.message)
            Text(result.assumptions)
            result.foods.forEach { food ->
                Text("${food.name} ${food.portion}")
                if (food.estimatedGrams == null) {
                    Button(onClick = { onCorrect(food.id, 180.0) }) { Text(pack.text("photo_correct")) }
                }
            }
            result.estimate?.let { estimate ->
                val macros = estimate.macros
                Text("P ${macros.proteinGrams} F ${macros.fiberGrams} Fat ${macros.fatGrams} C ${macros.carbohydrateGrams} kcal ${macros.calories}")
            }
            if (!result.saved) {
                Button(onClick = onConfirm) { Text(pack.text("photo_confirm")) }
            }
        }
    }
}
