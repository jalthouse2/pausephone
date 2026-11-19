package com.jalthouse.pausephone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jalthouse.pausephone.data.Store
import com.jalthouse.pausephone.ui.BackHeader
import com.jalthouse.pausephone.ui.Ink
import com.jalthouse.pausephone.ui.PrimaryButton
import com.jalthouse.pausephone.ui.Type

/** "Today's intention": one word or a few, to carry through the day and see at each pause. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IntentionScreen(back: () -> Unit) {
    watchStore()
    val current = Store.todayIntention()
    var writing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        BackHeader("Today's intention", back)
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Something to carry through the day. You'll see it on the home screen and at each pause before your apps.",
                style = Type.body,
                color = Ink.muted,
            )
            if (current != null) {
                Text(current, style = Type.title.copy(fontSize = 32.sp, fontWeight = FontWeight.Light), color = Ink.ink)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (Store.DAY_INTENTIONS + listOfNotNull(current?.takeIf { it !in Store.DAY_INTENTIONS })).forEach { word ->
                    val on = word == current
                    Text(
                        word,
                        style = Type.body.copy(fontSize = 15.sp),
                        color = if (on) Ink.paper else Ink.ink,
                        modifier = Modifier
                            .background(if (on) Ink.ink else Ink.paper, RoundedCornerShape(20.dp))
                            .border(1.dp, if (on) Ink.ink else Ink.track, RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button) { Store.setTodayIntention(word) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
                Text(
                    "+ your own",
                    style = Type.body.copy(fontSize = 15.sp),
                    color = Ink.ink,
                    modifier = Modifier
                        .border(1.dp, Ink.track, RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button) { writing = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            PrimaryButton("Done", back, Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp))
        }
    }
    if (writing) {
        NameDialog("Today, I'd like…", current ?: "", ideas = emptyList(), placeholder = "to listen more") { text ->
            writing = false
            if (!text.isNullOrBlank()) Store.setTodayIntention(text.lowercase())
        }
    }
}
