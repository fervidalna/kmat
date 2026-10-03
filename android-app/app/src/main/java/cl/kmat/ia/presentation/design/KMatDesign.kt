package cl.kmat.ia.presentation.design

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import cl.kmat.ia.R

val KMatSkyDeep = Color(0xFFBCE8FF)
val KMatInk = Color(0xFF083D91)
val KMatBlue = Color(0xFF0799E8)
val KMatGreen = Color(0xFF00A951)
val KMatGreenDark = Color(0xFF008645)
val KMatYellow = Color(0xFFFFC829)
val KMatCoral = Color(0xFFFF5C42)
val KMatRed = Color(0xFFFF3D50)
val KMatOutline = Color(0xFFA9D7F2)
val KMatCream = Color(0xFFFFFDF2)

@Composable
fun KMatBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(R.drawable.kmat_landscape_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        content()
    }
}

@Composable
fun KMatLogo(compact: Boolean = false) {
    val logoSize = if (compact) 26.sp else 39.sp
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("K-Mat", color = KMatBlue, fontWeight = FontWeight.ExtraBold, fontSize = logoSize)
        Spacer(Modifier.width(5.dp))
        Text(
            "IA",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (compact) 22.sp else 30.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(KMatCoral)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun KMatMascot(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.kmat_mascot_blue_puppy),
        contentDescription = "Mascota de K-Mat IA",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
fun KMatJumpingMascot(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.kmat_mascot_jumping),
        contentDescription = "Mascota de K-Mat IA saltando",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
fun KMatPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 64.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = KMatGreen, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 21.sp)
    }
}

@Composable
fun KMatSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = KMatInk),
        border = androidx.compose.foundation.BorderStroke(2.dp, KMatOutline),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
fun KMatProgress(completed: Int, total: Int = 5) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (index < completed) KMatGreen else Color(0xFFD4DCE5))
            )
        }
    }
}
