package com.smartsplit.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartsplit.app.data.AppRepository
import com.smartsplit.app.data.BillItem
import com.smartsplit.app.data.User
import com.smartsplit.app.data.sharesFor
import com.smartsplit.app.ui.theme.PrimaryTealDark

@Composable
fun BillItemAllocationEditor(
    item: BillItem,
    participants: List<User>,
    onItemChange: (BillItem) -> Unit
) {
    val participantIds = participants.map(User::id)
    val selectedIds = (item.payerUserIds.ifEmpty { participantIds })
        .filter { it in participantIds }
    var amountTexts by remember(item.id, item.totalPrice, selectedIds) {
        mutableStateOf(
            item.copy(payerUserIds = selectedIds).sharesFor(participantIds)
                .mapValues { (_, amount) -> amount.toString() }
        )
    }
    val parsedAmounts = selectedIds.associateWith { userId ->
        amountTexts[userId]?.toLongOrNull() ?: 0L
    }
    val hasValidAmounts = selectedIds.isNotEmpty() &&
        selectedIds.all { amountTexts[it]?.toLongOrNull() != null } &&
        parsedAmounts.values.all { it >= 0L } &&
        parsedAmounts.values.sum() == item.totalPrice

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Ai tham gia món này?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        participants.forEach { participant ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val nextIds = if (participant.id in selectedIds) {
                            if (selectedIds.size > 1) selectedIds - participant.id else selectedIds
                        } else {
                            selectedIds + participant.id
                        }
                        if (nextIds != selectedIds) {
                            val nextItem = item.copy(
                                payerUserIds = nextIds,
                                payerAmounts = emptyMap()
                            )
                            amountTexts = nextItem.sharesFor(participantIds)
                                .mapValues { (_, amount) -> amount.toString() }
                            onItemChange(nextItem)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = participant.id in selectedIds,
                    onCheckedChange = { checked ->
                        val nextIds = when {
                            checked -> (selectedIds + participant.id).distinct()
                            selectedIds.size > 1 -> selectedIds - participant.id
                            else -> selectedIds
                        }
                        if (nextIds != selectedIds) {
                            val nextItem = item.copy(
                                payerUserIds = nextIds,
                                payerAmounts = emptyMap()
                            )
                            amountTexts = nextItem.sharesFor(participantIds)
                                .mapValues { (_, amount) -> amount.toString() }
                            onItemChange(nextItem)
                        }
                    }
                )
                Text(participant.name, modifier = Modifier.weight(1f), fontSize = 12.sp)
                if (participant.id in selectedIds) {
                    OutlinedTextField(
                        value = amountTexts[participant.id].orEmpty(),
                        onValueChange = { value ->
                            val nextTexts = amountTexts + (participant.id to value.filter(Char::isDigit))
                            amountTexts = nextTexts
                            onItemChange(
                                item.copy(
                                    payerUserIds = selectedIds,
                                    payerAmounts = selectedIds.associateWith { id ->
                                        nextTexts[id]?.toLongOrNull() ?: 0L
                                    }
                                )
                            )
                        },
                        modifier = Modifier.width(128.dp),
                        label = { Text("VNĐ", fontSize = 10.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = BillInputFieldColors()
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (hasValidAmounts) {
                "Đã chia: ${AppRepository.formatCurrency(parsedAmounts.values.sum())}"
            } else {
                "Tổng phần chia phải bằng ${AppRepository.formatCurrency(item.totalPrice)}."
            },
            fontSize = 11.sp,
            color = if (hasValidAmounts) PrimaryTealDark else androidx.compose.material3.MaterialTheme.colorScheme.error
        )
    }
}
