package org.lmthermal.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.lmthermal.core.CelsiusRange

/** Invalid text leaves the current range untouched. Resources supply messages, not core exception text. */
@Composable
internal fun RangeDialog(initial: CelsiusRange, dismiss: () -> Unit, apply: (CelsiusRange) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var lower by rememberSaveable { mutableStateOf(editableCelsius(initial.lower, locale)) }
    var upper by rememberSaveable { mutableStateOf(editableCelsius(initial.upper, locale)) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.measurement_range_dialog)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lower, onValueChange = { lower = it }, label = { Text(stringResource(R.string.measurement_minimum)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(upper, onValueChange = { upper = it }, label = { Text(stringResource(R.string.measurement_maximum)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            if (invalid) Text(stringResource(R.string.measurement_invalid_range), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        val minimum = parseEditableCelsius(lower, locale); val maximum = parseEditableCelsius(upper, locale)
        val bounds = if (minimum != null && maximum != null) runCatching { CelsiusRange(minimum, maximum) }.getOrNull() else null
        if (bounds == null) invalid = true else apply(bounds)
    }) { Text(stringResource(R.string.measurement_apply)) } }, dismissButton = {
        TextButton(onClick = dismiss) { Text(stringResource(R.string.measurement_cancel)) }
    })
}
