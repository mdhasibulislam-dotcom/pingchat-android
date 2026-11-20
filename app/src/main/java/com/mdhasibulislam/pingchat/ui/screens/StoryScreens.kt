package com.mdhasibulislam.pingchat.ui.screens

import android.app.Application
import android.net.Uri
import android.widget.VideoView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.data.Story
import com.mdhasibulislam.pingchat.data.StoryGroup
import com.mdhasibulislam.pingchat.data.StoryRepository
import com.mdhasibulislam.pingchat.data.TimeFormat
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Stories (v3.1.0): 12-hour photo/video stories.
 *
 * - Ring strip sits above the chat list (added to ChatsScreen).
 * - A story lives 12 hours, then the row + its Storage file are deleted
 *   for good (pg_cron cleanup, see database/migration-v310-stories.sql).
 *   There is NO permanent save / Story Highlight — on purpose.
 * - Unseen = colourful ring, seen = grey ring (seen is local only).
 * - Viewer: full-screen photo (5 s) or video (its own length), segmented
 *   progress bars on top, name + time, close button, delete for my own.
 * - Video plays with the platform VideoView — no new dependency
 *   (no ExoPlayer / media3 in the app) and no paid service.
 */
class StoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = StoryRepository(application.applicationContext)

    private val _groups = MutableStateFlow<List<StoryGroup>>(emptyList())
    val groups: StateFlow<List<StoryGroup>> = _groups.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            try {
                repo.storiesFlow().collect { _groups.value = it }
            } catch (e: Exception) {
                // live refresh is best-effort; the strip still loads once
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                _groups.value = repo.loadStoryGroups()
                _error.value = null
            } catch (e: Exception) {
                // strip stays empty rather than breaking the Chats tab
            }
        }
    }

    /** Upload a picked photo/video as a story (12 h from now). */
    fun uploadStory(bytes: ByteArray, mime: String) {
        if (bytes.size > StoryRepository.STORY_MAX_BYTES) {
            _error.value = "Story must be under 10 MB."
            return
        }
        viewModelScope.launch {
            _uploading.value = true
            try {
                val story = repo.uploadStory(bytes, mime)
                if (story == null) {
                    _error.value = "Could not post the story. Try again."
                } else {
                    _error.value = null
                    refresh()
                }
            } finally {
                _uploading.value = false
            }
        }
    }

    fun deleteStory(story: Story) {
        viewModelScope.launch {
            if (repo.deleteStory(story)) refresh()
            else _error.value = "Could not delete the story."
        }
    }

    fun markSeen(story: Story) {
        repo.markSeen(story.id)
        // Recompute the grey rings without a network round-trip.
        val seen = repo.seenIds()
        _groups.value = _groups.value.map { group ->
            group.copy(allSeen = group.stories.all { seen.contains(it.id) })
        }
    }

    fun clearError() {
        _error.value = null
    }
}

/** Green (unseen, v3.2.0 mockup) / grey (seen) ring around a story avatar. */

/** "2h ago" / "45m ago" / "just now" for the viewer header. */
private fun storyAge(iso: String?): String {
    val t = TimeFormat.parse(iso) ?: return ""
    val mins = Duration.between(t, java.time.Instant.now()).toMinutes()
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 60 * 12 -> "${mins / 60}h ago"
        else -> TimeFormat.fmtListTime(iso)
    }
}

/**
 * Story ring strip for the top of the Chats tab. v3.2.0 (mockup): a
 * "Stories" title row with a working "See all >" link, then a strip whose
 * FIRST item is a soft "Add Story" card (the ONLY strip entry point for
 * adding — MyStoryTile has no "+" badge any more), then my own story
 * circle, then one tile per friend with an unexpired story.
 * Tapping my tile VIEWS my story; with no story yet it does nothing
 * (it never opens the picker). Tapping a tile opens the viewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryStrip() {
    val viewModel: StoryViewModel = viewModel()
    val groups by viewModel.groups.collectAsState()
    val uploading by viewModel.uploading.collectAsState()
    val error by viewModel.error.collectAsState()
    val myProfile by SessionManager.profile.collectAsState()
    val colors = pingChatColors
    val context = LocalContext.current

    var viewing by remember { mutableStateOf<StoryGroup?>(null) }
    var viewingIndex by remember { mutableStateOf(0) }
    // "See all" bottom sheet: every active story owner, in strip order.
    var showAllStories by remember { mutableStateOf(false) }
    val allStoriesSheetState = rememberModalBottomSheetState()

    // Photo/video picker (stories allow BOTH — unlike chat, where only
    // photos can be sent). PickVisualMedia needs no extra permission.
    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri) ?: return@rememberLauncherForActivityResult
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return@rememberLauncherForActivityResult
        viewModel.uploadStory(bytes, mime)
    }
    fun pickStory() {
        viewModel.clearError()
        mediaPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    // Refresh every time the strip is composed (Chats tab re-entry).
    LaunchedEffect(Unit) { viewModel.refresh() }

    val myGroup = groups.firstOrNull { it.isMine }
    val friendGroups = groups.filter { !it.isMine }
    // v3.2.1: "See all" shows only when there are many story owners
    // (mine + friends with active stories) — more than 5. With 5 or
    // fewer the link is hidden entirely; the "Stories" title stays.
    val storyOwnerCount = (if (myGroup != null) 1 else 0) + friendGroups.size

    Column(modifier = Modifier.fillMaxWidth()) {
        // v3.2.0: "Stories" title row (mockup). v3.2.1: the "See all >"
        // link shows only when there are more than 5 story owners —
        // it opens a bottom sheet of all active story owners.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stories",
                style = MaterialTheme.typography.titleLarge,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.weight(1f)
            )
            // v3.2.1: hidden entirely with 5 or fewer story owners
            // (no placeholder, no empty space where it was).
            if (storyOwnerCount > 5) {
                Row(
                    modifier = Modifier.clickable { showAllStories = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Emerald
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "See all stories",
                        tint = Emerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            // v3.2.0: "Add Story" soft card FIRST — the only strip
            // entry point for adding a story.
            item(key = "add-story") {
                AddStoryCard(onClick = { pickStory() })
            }
            // My story tile (always second, even with no stories yet).
            item(key = "my-story") {
                MyStoryTile(
                    hasStories = myGroup != null,
                    allSeen = myGroup?.allSeen ?: true,
                    uploading = uploading,
                    onClick = {
                        // VIEW only: with no story yet, do nothing —
                        // the "Add Story" card is the only add entry.
                        if (myGroup != null) {
                            viewingIndex = 0
                            viewing = myGroup
                        }
                    }
                )
            }
            items(friendGroups, key = { it.user.id }) { group ->
                FriendStoryTile(
                    group = group,
                    onClick = {
                        viewingIndex = 0
                        viewing = group
                    }
                )
            }
        }
        if (error != null) {
            Text(
                text = error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
    }

    // v3.2.0: "See all" sheet — every active story owner (mine first,
    // then friends, same data/order as the strip). Tapping one opens
    // that story in the existing viewer.
    if (showAllStories) {
        ModalBottomSheet(
            onDismissRequest = { showAllStories = false },
            sheetState = allStoriesSheetState
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "Stories",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
                Spacer(Modifier.height(8.dp))
                val allGroups = listOfNotNull(myGroup) + friendGroups
                if (allGroups.isEmpty()) {
                    Text(
                        "No stories yet — tap Add Story to post one.",
                        color = colors.muted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    allGroups.forEach { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAllStories = false
                                    viewingIndex = 0
                                    viewing = group
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(profile = group.user, size = AvatarSize.MD)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (group.isMine) "Your Story"
                                    else group.user.fullName.ifBlank { group.user.username },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${group.stories.size} " +
                                        if (group.stories.size == 1) "story" else "stories",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.muted
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = colors.muted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    val group = viewing
    if (group != null) {
        StoryViewer(
            group = group,
            startIndex = viewingIndex,
            onClose = { viewing = null },
            onMarkSeen = { viewModel.markSeen(it) },
            onDelete = { story ->
                viewModel.deleteStory(story)
                // If it was the last of my stories, the viewer closes.
                if (group.stories.size <= 1) viewing = null
            }
        )
    }
}

/** v3.2.0: "Add Story" soft card — first item in the strip, the ONLY
 *  strip entry point for adding a story (mockup: light grey-green
 *  rounded card, green circle with a white +, "Add Story" below). */
@Composable
private fun AddStoryCard(onClick: () -> Unit) {
    val colors = pingChatColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 68.dp, height = 92.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.card2)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Emerald),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add story",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Add Story",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** My tile: avatar + green/grey ring only when I have stories.
 *  v3.2.0: NO "+" badge any more — the "Add Story" card is the only
 *  add entry point. Tapping views my story (never opens the picker). */
@Composable
private fun MyStoryTile(
    hasStories: Boolean,
    allSeen: Boolean,
    uploading: Boolean,
    onClick: () -> Unit
) {
    val myProfile by SessionManager.profile.collectAsState()
    val colors = pingChatColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (hasStories) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .border(
                            3.dp,
                            if (allSeen) SolidColor(colors.border) else SolidColor(Emerald),
                            CircleShape
                        )
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Avatar(profile = myProfile, size = AvatarSize.MD)
                }
            } else {
                Avatar(profile = myProfile, size = AvatarSize.MD)
            }
        }
        Text(
            text = if (uploading) "Posting…" else "Your Story",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** One friend's tile: ringed avatar + first name underneath. */
@Composable
private fun FriendStoryTile(group: StoryGroup, onClick: () -> Unit) {
    val colors = pingChatColors
    val name = group.user.fullName.ifBlank { group.user.username }
        .substringBefore(" ")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .border(
                    3.dp,
                    if (group.allSeen) SolidColor(colors.border) else SolidColor(Emerald),
                    CircleShape
                )
                .padding(3.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Avatar(profile = group.user, size = AvatarSize.MD)
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Full-screen story viewer: segmented progress bars, avatar + name + age,
 * close button, delete (own stories). NO add-more button inside the
 * viewer (v3.2.0): the "Add Story" card in the strip is the only way
 * to add a story. Photo shows 5 s; video plays once for its own length.
 * Tap right = next, tap left = previous; swiping is not needed.
 */
@Composable
private fun StoryViewer(
    group: StoryGroup,
    startIndex: Int,
    onClose: () -> Unit,
    onMarkSeen: (Story) -> Unit,
    onDelete: (Story) -> Unit
) {
    var index by remember { mutableStateOf(startIndex.coerceIn(0, (group.stories.size - 1).coerceAtLeast(0))) }
    val stories = group.stories
    val story = stories.getOrNull(index)
    var progress by remember { mutableStateOf(0f) }
    // Video length in ms (from VideoView onPrepared); photo is fixed 5 s.
    var videoDurationMs by remember { mutableStateOf<Int?>(null) }

    fun advance() {
        if (index + 1 < stories.size) index += 1 else onClose()
    }
    fun goBack() {
        if (index > 0) index -= 1
    }

    // Mark each story seen as soon as it is shown (turns the ring grey).
    LaunchedEffect(story?.id) {
        videoDurationMs = null
        if (story != null) onMarkSeen(story)
    }

    // Progress timer: photo 5 s, video its own length (10 s until known).
    LaunchedEffect(story?.id, videoDurationMs) {
        val current = story ?: return@LaunchedEffect
        if (current.isVideo && videoDurationMs == null) return@LaunchedEffect
        progress = 0f
        val totalMs = if (current.isVideo) (videoDurationMs ?: 10_000) else 5_000
        val started = System.currentTimeMillis()
        while (true) {
            delay(100)
            val p = (System.currentTimeMillis() - started).toFloat() / totalMs
            if (p >= 1f) {
                progress = 1f
                advance()
                break
            }
            progress = p
        }
    }

    if (story == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Media (full screen).
            if (story.isVideo) {
                key(story.id) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(Uri.parse(story.mediaUrl))
                                setOnPreparedListener { mp ->
                                    videoDurationMs = mp.duration
                                    start()
                                }
                                setOnCompletionListener { advance() }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                AsyncImage(
                    model = story.mediaUrl,
                    contentDescription = "Story",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // Tap zones: left third = previous, rest = next.
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(0.3f)
                        .fillMaxSize()
                        .clickable(onClick = ::goBack)
                )
                Box(
                    modifier = Modifier
                        .weight(0.7f)
                        .fillMaxSize()
                        .clickable(onClick = ::advance)
                )
            }

            // Top: segmented progress + header (avatar, name, age, actions).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 12.dp, end = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stories.forEachIndexed { i, _ ->
                        val filled = when {
                            i < index -> 1f
                            i == index -> progress
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { filled },
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(50)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.35f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Avatar(profile = group.user, size = AvatarSize.SM)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.user.fullName.ifBlank { group.user.username },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = storyAge(story.createdAt),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (group.isMine) {
                        IconButton(onClick = { onDelete(story) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete story", tint = Color.White)
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            // Bottom hint: how long until this story disappears for good.
            val remaining = TimeFormat.parse(story.expiresAt)?.let {
                Duration.between(java.time.Instant.now(), it)
            }
            if (remaining != null && !remaining.isNegative) {
                val hours = remaining.toHours()
                val mins = remaining.toMinutes() % 60
                Text(
                    text = if (hours > 0) "Disappears in ${hours}h ${mins}m" else "Disappears in ${mins}m",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                )
            }
        }
    }
}
