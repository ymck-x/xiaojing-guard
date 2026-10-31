package com.fangchenmi.guard.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fangchenmi.guard.R

/**
 * 「小鲸」立绘。素材直接用社区皮肤包里的原图，不重绘。
 * 各表情分别对应不同状态：
 * - [R.drawable.whale_wave]      打招呼 / 正常
 * - [R.drawable.whale_sleepy]    睡觉、时间用完
 * - [R.drawable.whale_excited]   开心
 * - [R.drawable.whale_surprised] 提醒
 */
@Composable
fun WhaleImage(
    @DrawableRes res: Int,
    height: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height).alpha(alpha),
    )
}

/** 小鲸说话：左边是小鲸，右边是气泡。 */
@Composable
fun WhaleSpeech(
    text: String,
    modifier: Modifier = Modifier,
    sleeping: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        WhaleImage(
            res = if (sleeping) R.drawable.whale_sleepy else R.drawable.whale_wave,
            height = 78.dp,
        )
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            color = Color.White.copy(alpha = 0.86f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "小鲸说",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
