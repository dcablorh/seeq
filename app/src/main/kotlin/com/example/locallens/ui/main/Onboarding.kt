package com.example.locallens.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.locallens.R
import com.example.locallens.ui.PhotoPermissionScreen
import com.example.locallens.ui.theme.CardShape
import com.example.locallens.ui.theme.Hairline
import com.example.locallens.ui.theme.Ink
import com.example.locallens.ui.theme.InkButton
import com.example.locallens.ui.theme.PillSearchBar
import com.example.locallens.ui.theme.StepLayout

/**
 * First-run flow: welcome, photo access, then the one-time model download. Steps that are already done (access
 * granted, model present) are skipped, and [onFinished] fires as soon as the model is ready.
 */
@Composable
fun OnboardingFlow(
  viewModel: MainViewModel,
  hasAccess: Boolean,
  onAccessGranted: () -> Unit,
  onFinished: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var step by rememberSaveable { mutableIntStateOf(0) }
  val model by viewModel.model.collectAsStateWithLifecycle()

  // Access granted elsewhere (Settings), or already there: nothing to ask on the photo step.
  LaunchedEffect(step, hasAccess) { if (step == 1 && hasAccess) step = 2 }
  LaunchedEffect(model) { if (model == ModelState.Ready) onFinished() }

  Box(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
    when (step) {
      0 -> WelcomeStep(onContinue = { step = if (hasAccess) 2 else 1 })
      1 -> PhotoPermissionScreen(
        onResult = {
          onAccessGranted()
          step = 2
        },
      )
      else -> ModelSetupScreen(viewModel, model)
    }
  }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
  StepLayout(
    eyebrow = "Private photo search",
    headline = "Find any photo by typing.",
    pushActionsDown = false,
    actions = { InkButton("Continue", modifier = Modifier.fillMaxWidth(), onClick = onContinue) },
  ) {
    Text(
      "Describe it the way you remember it.",
      style = MaterialTheme.typography.bodyLarge,
      color = Ink,
    )
    PillSearchBar(
      value = "car at night",
      onValueChange = {},
      placeholder = "",
      enabled = false,
    )
    PhotoCards(Modifier.weight(1f))
    Image(
      painter = painterResource(R.drawable.ic_logo),
      contentDescription = "seeq",
      colorFilter = ColorFilter.tint(Ink),
      modifier = Modifier.width(96.dp).aspectRatio(406f / 365f),
    )
  }
}

/** Loose, uneven cards echoing the photo grid inside the app. */
@Composable
private fun PhotoCards(modifier: Modifier = Modifier) {
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      PhotoCard(R.drawable.onb_car_blue, Modifier.weight(1f))
      PhotoCard(R.drawable.onb_car_red, Modifier.weight(1.4f))
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Spacer(Modifier.height(28.dp))
      PhotoCard(R.drawable.onb_car_yellow, Modifier.weight(1.3f))
      PhotoCard(R.drawable.onb_car_white, Modifier.weight(1f))
    }
  }
}

@Composable
private fun PhotoCard(drawable: Int, modifier: Modifier) {
  Box(modifier.fillMaxWidth().clip(CardShape).background(Hairline).border(1.dp, Hairline, CardShape)) {
    Image(
      painter = painterResource(drawable),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
  }
}
