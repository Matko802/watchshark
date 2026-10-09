package watchshark.duckdns.org.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

data class SectionTab(
    val label: String,
    val iconRes: Int? = null,
    val iconVector: ImageVector? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionTabs(
    tabs: List<SectionTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        SingleChoiceSegmentedButtonRow {
            tabs.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                    icon = {
                        when {
                            tab.iconRes != null -> Icon(
                                painterResource(tab.iconRes),
                                contentDescription = null,
                            )
                            tab.iconVector != null -> Icon(
                                tab.iconVector,
                                contentDescription = null,
                            )
                        }
                    },
                    label = { Text(tab.label) },
                )
            }
        }
    }
}
