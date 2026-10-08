package com.hyrumrichardson.scrywall

import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScryWallTheme {
                ScryWallScreen()
            }
        }
    }
}

@Composable
fun ScryWallTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScryWallScreen(vm: MainViewModel = viewModel()) {
    val focus = LocalFocusManager.current
    val uri = LocalUriHandler.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("ScryWall") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Search ----
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                label = { Text("Scryfall search") },
                placeholder = { Text("e.g. t:dragon is:fullart") },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    vm.search()
                }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { focus.clearFocus(); vm.search() },
                    enabled = vm.query.isNotBlank() && !vm.searching,
                ) { Text("Search") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { uri.openUri("https://scryfall.com/docs/syntax") }) {
                    Text("Syntax help")
                }
            }
            if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            // ---- Results ----
            if (vm.samples.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("Preview")
                        Text(
                            "${vm.total} ${if (vm.total == 1) "artwork matches" else "artworks match"} · tap one to preview it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.search() }, enabled = !vm.searching) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Show 5 different cards")
                    }
                }
                Thumbnails(vm)
                vm.samples.getOrNull(vm.selected)?.let {
                    Text(it.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }

                HorizontalDivider()

                // ---- Settings ----
                SectionTitle("Image")
                Choices(ImageStyle.entries, vm.style, { it.label }, vm::updateStyle)

                SectionTitle("Scaling")
                Choices(ScaleMode.entries, vm.scale, { it.label }, vm::updateScale)

                SectionTitle("Apply to")
                Choices(WallTarget.entries, vm.target, { it.label }, vm::updateTarget)

                SectionTitle("Change wallpaper every")
                Choices(Interval.entries, vm.interval, { it.label }, vm::updateInterval)

                HorizontalDivider()

                // ---- Format preview ----
                SectionTitle("How it will look")
                PhonePreview(vm)

                Button(
                    onClick = { vm.apply() },
                    enabled = !vm.applying,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (vm.interval == Interval.NEVER) "Set a random wallpaper"
                        else "Set wallpaper & change it ${vm.interval.phrase}"
                    )
                }
                Text(
                    "Picks a random card from the whole search, not just the five above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (vm.applying) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            StatusCard(vm)

            Text(
                "Card data and images from Scryfall. Card art © Wizards of the Coast. " +
                    "ScryWall is not affiliated with Scryfall or Wizards of the Coast.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
private fun Thumbnails(vm: MainViewModel) {
    val art = vm.style == ImageStyle.ART
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(vm.samples) { index, card ->
            val shape = RoundedCornerShape(8.dp)
            val isSelected = index == vm.selected
            AsyncImage(
                model = card.thumbnail(vm.style),
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .then(if (art) Modifier.size(140.dp, 102.dp) else Modifier.size(110.dp, 153.dp))
                    .clip(shape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = shape,
                    )
                    .clickable { vm.select(index) },
            )
        }
    }
}

@Composable
private fun PhonePreview(vm: MainViewModel) {
    val (w, h) = vm.screen
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .width(180.dp)
                .aspectRatio(w.toFloat() / h.toFloat())
                .clip(shape)
                .border(4.dp, MaterialTheme.colorScheme.onSurface, shape),
            contentAlignment = Alignment.Center,
        ) {
            val img = vm.preview
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = "Wallpaper preview",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (vm.previewLoading) CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
private fun StatusCard(vm: MainViewModel) {
    if (vm.lastCard.isBlank() && !vm.rotating) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Current wallpaper", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (vm.lastCard.isNotBlank()) {
                val ago = DateUtils.getRelativeTimeSpanString(vm.lastChanged).toString()
                Text("${vm.lastCard} · set $ago", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                if (vm.rotating) "Changes ${vm.activeInterval.phrase} from: ${vm.activeQuery}"
                else "Not changing automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.changeNow() }, enabled = !vm.applying) { Text("Change now") }
                if (vm.rotating) {
                    OutlinedButton(onClick = { vm.stopRotating() }) { Text("Stop changing") }
                }
            }
        }
    }
}
