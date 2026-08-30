package com.example.suretouchapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.suretouchapp.R

@Composable
fun SureTrustLogo(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    showSubtext: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 2.dp
) {
    Surface(
        shape = shape,
        color = Color.White,
        shadowElevation = elevation,
        modifier = modifier.size(size)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (size <= 40.dp) 2.dp else if (size <= 60.dp) 4.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.sure_trust_official_logo),
                contentDescription = "SURE ProEd Official Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
            )
        }
    }
}
