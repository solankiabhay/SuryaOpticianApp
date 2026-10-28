package `in`.surya.optician

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun dateTextToUtcMillis(value: String, pattern: String): Long? {
    if (value.isBlank()) return null
    val formatter = SimpleDateFormat(pattern, Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val position = ParsePosition(0)
    val parsed = formatter.parse(value.trim(), position) ?: return null
    return if (position.index == value.trim().length && formatter.format(parsed) == value.trim()) parsed.time else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    displayFormat: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    var showCalendar by remember { mutableStateOf(false) }
    val initialMillis = dateTextToUtcMillis(value, displayFormat)
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        supportingText = supportingText?.let { hint -> { Text(hint) } },
        trailingIcon = {
            TextButton(onClick = { showCalendar = true }) { Text("📅") }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )

    if (showCalendar) {
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val formatter = SimpleDateFormat(displayFormat, Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        onValueChange(formatter.format(Date(millis)))
                    }
                    showCalendar = false
                }) { Text("Select date") }
            },
            dismissButton = {
                TextButton(onClick = { showCalendar = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState, title = { Text("Choose $label") })
        }
    }
}
