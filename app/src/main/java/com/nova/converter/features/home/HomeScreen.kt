package com.nova.converter.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.converter.R
import com.nova.converter.core.NovaTool
import com.nova.converter.core.Queue
import com.nova.converter.core.Section
import com.nova.converter.core.Tools
import com.nova.converter.ui.ToolGlyph
import com.nova.converter.core.Settings as AppSettings
import com.nova.converter.ui.theme.LocalNovaBgDeep

/**
 * Homepage — har tool ka alag card (screenshot style):
 * hero gradient card + VIDEO / AUDIO / IMAGE / MORE sections.
 * Card click → file picker auto-open → auto load.
 */
@Composable
fun HomeScreen(onTool: (String) -> Unit) {
    val hero = remember { Tools.all.filter { it.section == Section.HERO } }
    val video = remember { Tools.all.filter { it.section == Section.VIDEO } }
    val audio = remember { Tools.all.filter { it.section == Section.AUDIO } }
    val image = remember { Tools.all.filter { it.section == Section.IMAGE } }
    val more = remember { Tools.all.filter { it.section == Section.MORE } }

    LazyVerticalGrid(
        // 3 columns = 104dp tile. 4 columns sirf 76dp dete hain, jisme bada
        // tool icon (46dp) + kind badge thuss kar clip ho jaate hain.
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .background(LocalNovaBgDeep.current),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
            HomeHeader()
        }

        // HERO: bada gradient card + do chhote cards
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val main = hero.firstOrNull()
                if (main != null) {
                    HeroCard(
                        tool = main,
                        modifier = Modifier
                            .weight(1.65f)
                            .height(226.dp)
                    ) { onTool(main.id) }
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    hero.drop(1).forEach { t ->
                        SmallCard(
                            tool = t,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(108.dp)
                        ) { onTool(t.id) }
                    }
                }
            }
        }

        // first-run tips — ek baar dikhta hai, Got it dabao
        if (!AppSettings.tipsShown.value) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                TipsCard(onDismiss = { AppSettings.setTipsShown(true) })
            }
        }

        if (video.isNotEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                SectionTitle(stringResource(R.string.section_video))
            }
            items(video) { t -> ToolGridCard(t) { onTool(t.id) } }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Spacer(Modifier.height(2.dp))
            }
        }

        if (audio.isNotEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                SectionTitle(stringResource(R.string.section_audio))
            }
            items(audio) { t -> ToolGridCard(t) { onTool(t.id) } }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Spacer(Modifier.height(2.dp))
            }
        }

        if (image.isNotEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                SectionTitle(stringResource(R.string.section_image))
            }
            items(image) { t -> ToolGridCard(t) { onTool(t.id) } }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Spacer(Modifier.height(2.dp))
            }
        }

        if (more.isNotEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                SectionTitle(stringResource(R.string.section_more))
            }
            items(more) { t -> ToolGridCard(t) { onTool(t.id) } }
        }
    }
}

@Composable
private fun HomeHeader() {
    // sirf header khud collect karta hai — job % tick pe poora grid recompose nahi hota
    val tasks by Queue.tasks.collectAsState()
    val activeJobs = tasks.count { it.state == Queue.State.RUNNING || it.state == Queue.State.QUEUED }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (activeJobs > 0) stringResource(R.string.home_jobs_running, activeJobs)
                else stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = if (activeJobs > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    ) {
        Box(
            Modifier
                .width(5.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** Pehli baar app kholne pe 3-line guide — Got it dabao to dobara nahi dikhta */
@Composable
private fun TipsCard(onDismiss: () -> Unit) {
    OutlinedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.tips_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.tips_ok))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.tips_1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.tips_2),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.tips_3),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeroCard(tool: NovaTool, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(tool.tint, tool.tint.copy(alpha = 0.65f), Color(0xFFFFC48A))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        // depth ke liye halka sa decorative blob
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(120.dp)
                .offset(x = 34.dp, y = 34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
        )
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Box(
                Modifier
                    // Icon area. Card 226dp, padding 14dp → andar 198dp.
                    // Content = area 72 + 10 + title 28 + 2 + subtitle 32 +
                    // 10 + CTA 30 = 184dp → 14dp slack.
                    // (Pehle area 100 + 18dp padding + 14/4/14 spacers se
                    // content 234dp ho jata tha aur "Start now" CTA card ke
                    // bahar clip ho jata tha — device measurement se pakda.)
                    .size(72.dp),
                contentAlignment = Alignment.Center
            ) {
                // Hero ka background khud tool ka gradient hai, isliye plate
                // **white translucent** (highlighted) + white icons — har hue par safe.
                ToolGlyph(
                    tool = tool,
                    plateSize = 42.dp,
                    iconSize = 29.dp,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    plateFill = Color.White.copy(alpha = 0.24f),
                    plateBorder = Color.White.copy(alpha = 0.35f),
                    iconTint = Color.White
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(tool.title),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(tool.subtitle),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.24f))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    stringResource(R.string.cta_start_now),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SmallCard(tool: NovaTool, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(9.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Do highlighted plate (har ek tool ke apne solid tint ka) card ke
            // surface par baithe hain — dark aur light dono theme me alag dikhte hain.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                contentAlignment = Alignment.Center
            ) {
                ToolGlyph(
                    tool = tool,
                    plateSize = 28.dp,
                    iconSize = 19.dp,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                stringResource(tool.title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// GRID TILE
//
// Card sirf icon ka tile hai, title **card ke bahar niche** aa jaata hai.
//
// Tile khud icon ka "plate" hai: uspar tool ke tint ka halka gradient hai aur
// poore tile ko **bada tool icon** (46dp) bharta hai, corner me media-kind ka
// chhota badge hota hai. Pehle tile neutral tha aur beech me 34dp ke do chhote
// chips latakte the — icon card ke muqable chhota aur "khaali" lagta tha.
//
// Har tool ka bada icon unique hai (Tools.kt me assign kiya gaya), isliye ek
// nazar me pata chalta hai kaun sa tool hai. Poora Column clickable hai, isliye
// title par tap karne se bhi tool khulta hai.
//
// Hero + 2 small cards (top 3) apne alag layout par hain — unme title andar hi
// rehta hai (gradient / chhota card).
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun ToolGridCard(tool: NovaTool, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
        ) {
            // Do **highlighted plate** (44dp, tool ka apna solid tint) diagonal
            // offsets me — tile ka ~85% bharta hai. Plate solid hai aur icon ka
            // colour `contrastOn()` se chuna jaata hai, isliye dark aur light
            // dono theme me har hue par contrast sahi rehta hai.
            ToolGlyph(
                tool = tool,
                plateSize = 44.dp,
                iconSize = 30.dp,
                modifier = Modifier.fillMaxSize().padding(4.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(tool.title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
