package `in`.surya.optician.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

/** Formats paise as Indian rupees, e.g. 1234567 -> ₹12,345.67. */
fun formatRupees(paise: Long): String =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(paise / 100.0)

/** One icon per app section, shared by the bottom bar, menu and headers. */
fun sectionIcon(tab: String): ImageVector = when (tab) {
    "Dashboard" -> Icons.Rounded.Home
    "Bills" -> Icons.Rounded.Schedule
    "New Bill" -> Icons.Rounded.AddCircle
    "Customers" -> Icons.Rounded.People
    "Products" -> Icons.Rounded.Inventory2
    "Payment" -> Icons.Rounded.Payments
    "Prescription" -> Icons.Rounded.Visibility
    "Reports" -> Icons.Rounded.Assessment
    "Settings" -> Icons.Rounded.Settings
    "Users" -> Icons.Rounded.AdminPanelSettings
    else -> Icons.Rounded.Circle
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A bordered white card with a numbered or icon header, used to group form fields. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    step: Int? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step != null) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("$step", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else if (icon != null) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(34.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            content()
        }
    }
}

/** Standard text field: rounded, optional leading icon, number keypad support. */
@Composable
fun AppField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    supportingText: String? = null,
    prefix: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    onClear: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = if (onClear != null && value.isNotBlank()) {
            { androidx.compose.material3.IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = "Clear") } }
        } else null,
        prefix = prefix?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

/** Horizontally scrolling single-choice chips (glass type, payment method...). */
@Composable
fun ChoiceChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = selected == option
            val check: (@Composable () -> Unit)? =
                if (isSelected) { { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                label = { Text(option) },
                leadingIcon = check
            )
        }
    }
}

@Composable
fun StatusPill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = container, modifier = modifier) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** PAID / PART PAID / UNPAID badge for an invoice. */
@Composable
fun PaymentBadge(duePaise: Long, paidPaise: Long, modifier: Modifier = Modifier) {
    when {
        duePaise <= 0 -> StatusPill("PAID", Brand.SuccessBg, Brand.Success, modifier)
        paidPaise > 0 -> StatusPill("PART PAID", Brand.WarningBg, Brand.Warning, modifier)
        else -> StatusPill("UNPAID", Brand.DangerBg, Brand.Danger, modifier)
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    tintBg: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Card(
        modifier = modifier.then(clickModifier),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = tintBg, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AmountRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
    valueColor: Color = Color.Unspecified
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (emphasize) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** White bordered card for list rows (customers, products, bill items...). */
@Composable
fun RecordCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Card(
        modifier = modifier.fillMaxWidth().then(clickModifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/** Round badge showing the first letter of a name. */
@Composable
fun AvatarBadge(text: String, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text.trim().take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Compact horizontal stat: icon, label and value on one line. */
@Composable
fun MiniStat(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    tintBg: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Card(
        modifier = modifier.then(clickModifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = CircleShape, color = tintBg, modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
        }
    }
}

/** One tappable row of a menu list: icon, title, optional badge and chevron. */
@Composable
fun MenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    badgeContainer: Color = Brand.WarningBg,
    badgeContent: Color = Brand.Warning
) {
    Row(
        modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (badge != null) StatusPill(badge, badgeContainer, badgeContent)
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** Password field with show/hide toggle. */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null
) {
    var visible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        trailingIcon = {
            androidx.compose.material3.IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password"
                )
            }
        },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None
        else androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

/** Decimal field for prescription powers with a +/- button (many number keypads have no minus key). */
@Composable
fun SignedDecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = {
            androidx.compose.material3.IconButton(
                onClick = { onValueChange(`in`.surya.optician.data.PrescriptionRules.toggleSign(value)) },
                modifier = Modifier.semantics { contentDescription = "Switch between plus and minus" }
            ) { Text("±", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
        },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}
