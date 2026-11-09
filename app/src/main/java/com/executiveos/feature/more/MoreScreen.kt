package com.executiveos.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.executiveos.R
import com.executiveos.core.designsystem.component.ExecutiveTopBar
import com.executiveos.core.navigation.MoreDestination
import com.executiveos.core.navigation.NavigationTags

@Composable
fun MoreScreen(onDestinationSelected: (MoreDestination) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ExecutiveTopBar(title = stringResource(R.string.section_more))
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            MoreDestination.entries.forEach { destination ->
                MoreItem(destination, onClick = { onDestinationSelected(destination) })
            }
        }
    }
}

@Composable
private fun MoreItem(destination: MoreDestination, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(destination.labelRes)) },
        supportingContent = { Text(stringResource(destination.descriptionRes)) },
        leadingContent = {
            Icon(painterResource(destination.iconRes), contentDescription = null)
        },
        trailingContent = {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(NavigationTags.more(destination)),
    )
}
