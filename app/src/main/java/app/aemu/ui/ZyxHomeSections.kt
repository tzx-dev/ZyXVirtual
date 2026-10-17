package app.aemu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aemu.R

@Composable
internal fun ZyxHomeHero(
    version: String,
    onImport: () -> Unit,
    onOpenGitHub: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary)))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(color = Color.White.copy(alpha = 0.16f), shape = CircleShape) {
            Text(
                stringResource(R.string.home_eyebrow),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
        Text(
            "ZyX Virtual",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.92f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroPill("LineageOS 23.2")
            HeroPill("ARM64 · Android 16")
        }
        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = colors.primary),
        ) {
            Icon(Icons.Rounded.FileOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_cta_import))
        }
        TextButton(
            onClick = onOpenGitHub,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        ) {
            Text(stringResource(R.string.home_cta_github))
        }
        Text(
            stringResource(R.string.as_version, version),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.82f),
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun HeroPill(text: String) {
    Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
internal fun ZyxHomeSectionHeader(label: String, title: String, icon: ImageVector) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun ZyxHomeAboutCard() {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ZyxHomeSectionHeader(
            stringResource(R.string.home_about_label),
            stringResource(R.string.home_about_title),
            Icons.Rounded.Info,
        )
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(shape = RoundedCornerShape(18.dp), color = colors.tertiaryContainer, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Android, contentDescription = null, tint = colors.onTertiaryContainer)
                    }
                }
                Text(stringResource(R.string.home_about_text), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ZyxHomeEmptyCard(onImport: () -> Unit, onHelp: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = colors.primaryContainer, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Android, contentDescription = null, tint = colors.onPrimaryContainer)
                    }
                }
                Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleLarge)
            }
            Text(stringResource(R.string.home_empty_text), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.FileOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_empty_import))
            }
            TextButton(onClick = onHelp, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.empty_help))
            }
        }
    }
}

@Composable
internal fun ZyxHomeFeatures() {
    val colors = MaterialTheme.colorScheme
    val features = listOf(
        Triple(Icons.Rounded.Settings, R.string.home_feature_vm_title, R.string.home_feature_vm_text),
        Triple(Icons.Rounded.FileOpen, R.string.home_feature_package_title, R.string.home_feature_package_text),
        Triple(Icons.Rounded.Info, R.string.home_feature_saf_title, R.string.home_feature_saf_text),
        Triple(Icons.Rounded.Android, R.string.home_feature_data_title, R.string.home_feature_data_text),
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ZyxHomeSectionHeader(
            stringResource(R.string.home_features_label),
            stringResource(R.string.home_features_title),
            Icons.Rounded.Settings,
        )
        features.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { columnIndex, (icon, title, body) ->
                    val container = if ((rowIndex + columnIndex) % 2 == 0) colors.surfaceContainer else colors.secondaryContainer
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = container),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if ((rowIndex + columnIndex) % 2 == 0) colors.primaryContainer else colors.tertiaryContainer,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icon, contentDescription = null, tint = if ((rowIndex + columnIndex) % 2 == 0) colors.onPrimaryContainer else colors.onTertiaryContainer)
                                }
                            }
                            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ZyxHomeGuide() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ZyxHomeSectionHeader(
            stringResource(R.string.home_guide_label),
            stringResource(R.string.home_guide_title),
            Icons.Rounded.PlayArrow,
        )
        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StepRow(1, R.string.home_step_import)
                StepRow(2, R.string.home_step_verify)
                StepRow(3, R.string.home_step_boot)
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, text: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(number.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(stringResource(text), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ZyxHomeProject(onOpenGitHub: () -> Unit, onOpenReleases: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary)))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.home_project_label), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.86f))
        Text(stringResource(R.string.home_project_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(stringResource(R.string.home_project_text), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
        Button(
            onClick = onOpenGitHub,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = colors.primary),
        ) { Text(stringResource(R.string.home_cta_github)) }
        TextButton(
            onClick = onOpenReleases,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        ) { Text(stringResource(R.string.home_cta_release)) }
    }
}
