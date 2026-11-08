package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Where a custom background can be used. */
enum class BackgroundScope(val prefix: String) {
    /** Home, systems, favorites, search... */
    MENU("menu"),

    /** While a game is running. */
    GAME("game"),
}

enum class BackgroundType {
    DEFAULT,
    COLOR,
    IMAGE,
}

data class BackgroundConfig(
    val type: BackgroundType = BackgroundType.DEFAULT,
    val color: Int = AppBackground.DEFAULT_COLOR,
    val imagePath: String? = null,
    val dim: Float = AppBackground.DEFAULT_DIM,
) {
    /** True when something custom has to be drawn. */
    val isActive: Boolean
        get() =
            when (type) {
                BackgroundType.DEFAULT -> false
                BackgroundType.COLOR -> true
                BackgroundType.IMAGE -> imagePath != null
            }
}

/** Dutch text when the phone is set to Dutch, English otherwise. */
fun bgText(
    nl: String,
    en: String,
): String = if (Locale.getDefault().language == "nl") nl else en

object AppBackground {
    const val DEFAULT_COLOR: Int = 0xFF1B2A41.toInt()
    const val DEFAULT_DIM: Float = 0.4f

    val COLORS: List<Int> =
        listOf(
            0xFF000000,
            0xFF2E2E2E,
            0xFF1B2A41,
            0xFF1F4E79,
            0xFF0B3D2E,
            0xFF4A1942,
            0xFF7A1F1F,
            0xFF5C3B1E,
        ).map { it.toInt() }

    private val menuFlow = MutableStateFlow(BackgroundConfig())
    private val gameFlow = MutableStateFlow(BackgroundConfig())
    private var loaded = false

    private fun flowFor(scope: BackgroundScope): MutableStateFlow<BackgroundConfig> {
        return when (scope) {
            BackgroundScope.MENU -> menuFlow
            BackgroundScope.GAME -> gameFlow
        }
    }

    private fun key(
        scope: BackgroundScope,
        name: String,
    ) = "app_background_${scope.prefix}_$name"

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        BackgroundScope.values().forEach { scope ->
            val type =
                runCatching {
                    BackgroundType.valueOf(prefs.getString(key(scope, "type"), null) ?: "DEFAULT")
                }.getOrDefault(BackgroundType.DEFAULT)
            val path =
                prefs.getString(key(scope, "image"), null)
                    ?.takeIf { File(it).exists() }
            flowFor(scope).value =
                BackgroundConfig(
                    type = type,
                    color = prefs.getInt(key(scope, "color"), DEFAULT_COLOR),
                    imagePath = path,
                    dim = prefs.getFloat(key(scope, "dim"), DEFAULT_DIM),
                )
        }
    }

    /** The current background for a scope; updates whenever it changes. */
    fun observe(
        context: Context,
        scope: BackgroundScope,
    ): StateFlow<BackgroundConfig> {
        ensureLoaded(context.applicationContext)
        return flowFor(scope)
    }

    private fun update(
        context: Context,
        scope: BackgroundScope,
        transform: (BackgroundConfig) -> BackgroundConfig,
    ) {
        val appContext = context.applicationContext
        ensureLoaded(appContext)
        val flow = flowFor(scope)
        val newConfig = transform(flow.value)
        flow.value = newConfig

        val editor = SharedPreferencesHelper.getSharedPreferences(appContext).edit()
        editor.putString(key(scope, "type"), newConfig.type.name)
        editor.putInt(key(scope, "color"), newConfig.color)
        editor.putFloat(key(scope, "dim"), newConfig.dim)
        val path = newConfig.imagePath
        if (path != null) {
            editor.putString(key(scope, "image"), path)
        } else {
            editor.remove(key(scope, "image"))
        }
        editor.apply()
    }

    fun setType(
        context: Context,
        scope: BackgroundScope,
        type: BackgroundType,
    ) {
        update(context, scope) { it.copy(type = type) }
    }

    fun setColor(
        context: Context,
        scope: BackgroundScope,
        color: Int,
    ) {
        update(context, scope) { it.copy(type = BackgroundType.COLOR, color = color) }
    }

    fun setDim(
        context: Context,
        scope: BackgroundScope,
        dim: Float,
    ) {
        update(context, scope) { it.copy(dim = dim) }
    }

    /**
     * Copies the chosen photo into the app's own storage, so it keeps working
     * even if the photo is later removed from the gallery.
     */
    suspend fun importImage(
        context: Context,
        scope: BackgroundScope,
        uri: Uri,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            ensureLoaded(appContext)
            val target = File(appContext.filesDir, "background_${scope.prefix}_${System.currentTimeMillis()}.img")
            val copied =
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } != null
                }.getOrDefault(false)

            if (!copied) {
                target.delete()
                return@withContext false
            }

            val oldPath = flowFor(scope).value.imagePath
            update(appContext, scope) { it.copy(type = BackgroundType.IMAGE, imagePath = target.absolutePath) }
            oldPath?.let { File(it).delete() }
            true
        }
}

/** Draws the chosen color or photo (darkened by the dim setting) and nothing when not active. */
@Composable
fun AppBackgroundLayer(
    config: BackgroundConfig,
    modifier: Modifier = Modifier,
) {
    if (!config.isActive) return

    val imagePath = config.imagePath
    Box(modifier = modifier) {
        if (config.type == BackgroundType.IMAGE && imagePath != null) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(config.color)))
        }
        if (config.dim > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = config.dim)))
        }
    }
}

/** The scaffold must be see-through when a custom menu background is shown. */
@Composable
fun menuScaffoldColor(config: BackgroundConfig): Color {
    return if (config.isActive) Color.Transparent else MaterialTheme.colorScheme.background
}

/** Top and bottom bars get a little see-through when a custom menu background is shown. */
@Composable
fun backgroundAwareBarColor(base: Color): Color {
    val config = AppBackground.observe(LocalContext.current, BackgroundScope.MENU).collectAsState().value
    return if (config.isActive) base.copy(alpha = 0.75f) else base
}

@Composable
fun BackgroundSettingsDialog(
    scope: BackgroundScope,
    title: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val config = AppBackground.observe(context, scope).collectAsState().value
    val coroutineScope = rememberCoroutineScope()

    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                coroutineScope.launch { AppBackground.importImage(context, scope, uri) }
            }
        }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)

                BackgroundChoiceRow(
                    selected = config.type == BackgroundType.DEFAULT,
                    label = bgText("Standaard", "Default"),
                ) { AppBackground.setType(context, scope, BackgroundType.DEFAULT) }

                BackgroundChoiceRow(
                    selected = config.type == BackgroundType.COLOR,
                    label = bgText("Kleur", "Color"),
                ) { AppBackground.setType(context, scope, BackgroundType.COLOR) }

                if (config.type == BackgroundType.COLOR) {
                    LazyRow(
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(AppBackground.COLORS) { value ->
                            val selected = value == config.color
                            Box(
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(value))
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape,
                                        )
                                        .clickable { AppBackground.setColor(context, scope, value) },
                            )
                        }
                    }
                }

                BackgroundChoiceRow(
                    selected = config.type == BackgroundType.IMAGE,
                    label = bgText("Eigen foto", "Own photo"),
                ) {
                    if (config.imagePath != null) {
                        AppBackground.setType(context, scope, BackgroundType.IMAGE)
                    } else {
                        photoPicker.launch("image/*")
                    }
                }

                if (config.type == BackgroundType.IMAGE) {
                    TextButton(onClick = { photoPicker.launch("image/*") }) {
                        Text(text = bgText("Andere foto kiezen", "Choose another photo"))
                    }
                }

                if (config.type != BackgroundType.DEFAULT) {
                    Text(
                        text = bgText("Donkerder maken", "Darken"),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Slider(
                        value = config.dim,
                        onValueChange = { AppBackground.setDim(context, scope, it) },
                        valueRange = 0f..0.9f,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = bgText("Klaar", "Done"))
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundChoiceRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Text(text = label)
    }
}
