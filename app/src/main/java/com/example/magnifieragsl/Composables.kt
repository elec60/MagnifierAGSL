package com.example.magnifieragsl

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.intellij.lang.annotations.Language

@Language("AGSL")
private val AGSL = """
// UNIFORM VARIABLES (passed from Kotlin code):
uniform float2 size;          // Screen/canvas dimensions (width, height)
uniform float2 touchPoint;    // Center point of magnifier circle (x, y)
uniform float magnification;  // Magnification factor (e.g., 3.0 = 3x zoom)
uniform float radius;         // Radius of magnifier circle in pixels
uniform shader content;       // The original content to be magnified

/**
 * Main shader function - called for every pixel (fragment) on screen
 * @param fragCoord: Current pixel coordinates being processed
 * @return: Color value for this pixel (RGBA)
 */
half4 main(float2 fragCoord) {
    // STEP 1: DISTANCE CALCULATION
    // Calculate the center point of our magnifier
    float2 center = touchPoint;
    
    // Calculate offset vector from center to current pixel
    // EXAMPLE: If center = (400, 300) and fragCoord = (460, 320)
    // Then offset = (460-400, 320-300) = (60, 20)
    // This vector points from center toward the current pixel
    float2 offset = fragCoord - center;
    
    // Calculate Euclidean distance using Pythagorean theorem:
    // distance = √(offset.x² + offset.y²)
    // EXAMPLE: With offset = (60, 20)
    // distance = √(60² + 20²) = √(3600 + 400) = √4000 ≈ 63.25 pixels
    // The length() function computes this automatically
    float distance = length(offset);
    
    // STEP 2: REGION DETERMINATION
    // Check if current pixel is outside the magnifier circle
    // EXAMPLE: If radius = 120 and distance = 63.25
    // Since 63.25 < 120, we're INSIDE the magnifier circle
    // If distance was 150, then 150 > 120, so OUTSIDE
    if (distance > radius) {
        // Return original content without any modification
        return content.eval(fragCoord);
    }
    
    // STEP 3: MAGNIFICATION CALCULATION
    // We're inside the magnifier circle, so apply magnification
    float2 magnifiedCoord;
    
    // Create smooth transition zone to avoid sharp edges
    // EXAMPLE: If radius = 120, then smoothRadius = 120 * 0.95 = 114
    // This means magnification is constant from center to 114px
    // From 114px to 120px, we blend between magnified and normal
    float edgeSmooth = 0.95;
    float smoothRadius = radius * edgeSmooth;
    
    if (distance <= smoothRadius) {
        // CORE MAGNIFICATION MATHEMATICS:
        // To magnify, we need to sample from a "smaller" area of the original content
        // 
        // Mathematical principle:
        // - If magnification = 3.0, we want to show 3x larger version
        // - To achieve this, we sample from coordinates that are 1/3 the distance from center
        // - Formula: magnifiedCoord = center + (offset / magnification)
        // 
        // CONCRETE EXAMPLE:
        // - center = (400, 300), offset = (60, 20), magnification = 3.0
        // - magnifiedCoord = (400, 300) + (60/3, 20/3) = (400, 300) + (20, 6.67) = (420, 306.67)
        // - Instead of sampling from (460, 320), we sample from (420, 306.67)
        // - This point is much closer to center, so when displayed at (460, 320),
        //   it appears magnified (we're stretching a small area to fill a larger area)
        //
        // WHY THIS WORKS:
        // - Original pixel at distance 60 from center shows content from distance 20
        // - This makes content appear 3x larger (60/20 = 3x magnification)
        magnifiedCoord = center + (offset / magnification);
    } else {
        // SMOOTH TRANSITION MATHEMATICS:
        // Create smooth blend between magnified and normal content at edges
        
        // Calculate interpolation factor (0 to 1)
        // EXAMPLE: radius = 120, smoothRadius = 114, distance = 117
        // t = (117 - 114) / (120 - 114) = 3 / 6 = 0.5
        // This means we're halfway through the transition zone
        float t = (distance - smoothRadius) / (radius - smoothRadius);
        
        // Calculate both coordinate systems
        float2 normalCoord = fragCoord;                    // Normal: (460, 320)
        float2 magCoord = center + (offset / magnification); // Magnified: (420, 306.67)
        
        // LINEAR INTERPOLATION (LERP):
        // mix(a, b, t) = a * (1-t) + b * t
        // EXAMPLE: mix((420, 306.67), (460, 320), 0.5)
        // x = 420 * (1-0.5) + 460 * 0.5 = 420 * 0.5 + 460 * 0.5 = 210 + 230 = 440
        // y = 306.67 * 0.5 + 320 * 0.5 = 153.33 + 160 = 313.33
        // Result: (440, 313.33) - a blend between magnified and normal
        //
        // When t=0 (at smoothRadius): result = magCoord (fully magnified)
        // When t=1 (at full radius): result = normalCoord (normal view)
        magnifiedCoord = mix(magCoord, normalCoord, t);
    }
    
    // STEP 4: BOUNDS CHECKING
    // Ensure magnified coordinates are within valid screen bounds
    // EXAMPLE: If size = (1080, 1920) and magnifiedCoord = (420, 306.67)
    // Check: 420 >= 0 ✓, 420 <= 1080 ✓, 306.67 >= 0 ✓, 306.67 <= 1920 ✓
    // All conditions pass, so coordinates are valid
    if (magnifiedCoord.x >= 0.0 && magnifiedCoord.x <= size.x && 
        magnifiedCoord.y >= 0.0 && magnifiedCoord.y <= size.y) {
        
        // Sample color from the calculated magnified position
        half4 color = content.eval(magnifiedCoord);
        
        // STEP 5: VISUAL ENHANCEMENTS
        
        // Add glass-like brightness effect
        // Mathematical principle: Brightness decreases linearly from center to edge
        // Formula: brightness = 1.0 - (distance/radius) * dampingFactor
        // EXAMPLE: distance = 63.25, radius = 120
        // glassEffect = 1.0 - (63.25/120) * 0.1 = 1.0 - 0.527 * 0.1 = 1.0 - 0.0527 = 0.947
        // This creates 94.7% brightness (slightly dimmed toward edge)
        // At center (distance=0): glassEffect = 1.0 (full brightness)
        // At edge (distance=120): glassEffect = 1.0 - 1.0 * 0.1 = 0.9 (90% brightness)
        float glassEffect = 1.0 - (distance / radius) * 0.1;
        color.rgb *= glassEffect;
        
        // Add circular border effect
        float borderWidth = 6; // Border thickness in pixels
        
        // Check if we're in the border region (outer edge of circle)
        // EXAMPLE: radius = 120, borderWidth = 6, so border starts at 120-6 = 114px
        // If distance = 117, then 117 > 114, so we're in the border region
        if (distance > radius - borderWidth) {
            // Calculate border alpha (transparency) for smooth border edge
            // EXAMPLE: distance = 117, radius = 120, borderWidth = 6
            // borderAlpha = 1.0 - (117 - (120-6)) / 6 = 1.0 - (117-114) / 6 = 1.0 - 3/6 = 0.5
            // This gives 50% border opacity at this position
            // At inner border (distance=114): borderAlpha = 1.0 (full border)
            // At outer border (distance=120): borderAlpha = 0.0 (no border)
            float borderAlpha = 1.0 - (distance - (radius - borderWidth)) / borderWidth;
            
            // Blend border color with content color
            // EXAMPLE: mix(originalColor, darkGray, 0.5 * 0.3) = mix(originalColor, darkGray, 0.15)
            // This adds 15% dark border color, creating subtle border effect
            color = mix(color, half4(0.2, 0.2, 0.2, 1.0), borderAlpha * 0.3);
        }
        
        return color;
    }
    
    // STEP 6: FALLBACK
    // If magnified coordinates are out of bounds, show normal content
    // EXAMPLE: If magnification pushes sampling point to (-50, 100), which is outside screen bounds,
    // we fall back to showing the original content at the current pixel position
    return content.eval(fragCoord);
}
""".trimIndent()

private val shader = RuntimeShader(AGSL)

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var touchPoint by remember { mutableStateOf(Offset.Zero) }
    var isActive by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                shader.setFloatUniform("size", it.width.toFloat(), it.height.toFloat())
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        touchPoint = offset
                        isActive = true
                    },
                    onDragEnd = {
                        isActive = false
                    }
                ) { change, _ ->
                    touchPoint = change.position
                }
            }
            .graphicsLayer {
                if (isActive) {
                    shader.setFloatUniform("touchPoint", touchPoint.x, touchPoint.y)
                    shader.setFloatUniform("magnification", 3.0f)
                    shader.setFloatUniform("radius", 120f)
                    this.renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
                        .asComposeRenderEffect()
                } else {
                    this.renderEffect = null
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF6366F1),
                                Color(0xFF8B5CF6),
                                Color(0xFFEC4899)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔍 MAGNIFIER DEMO",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Colorful Icon Grid",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val icons = listOf(
            Icons.Default.Home, Icons.Default.Favorite, Icons.Default.Star,
            Icons.Default.Email, Icons.Default.Phone, Icons.Default.Settings,
            Icons.Default.AccountCircle
        )

        val colors = listOf(
            Color.Red, Color.Green, Color.Blue,
            Color.Cyan, Color.Magenta, Color.Yellow
        )

        repeat(4) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(6) { col ->
                    val index = row * 6 + col
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors[index % colors.size]),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icons[index % icons.size],
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Text Samples",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "LARGE TITLE",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1F2937)
                )

                Text(
                    text = "Medium subtitle text",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6B7280)
                )

                Text(
                    text = "Small details: Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF)
                )

                Text(
                    text = "Tiny text: The quick brown fox jumps over the lazy dog. 1234567890",
                    fontSize = 8.sp,
                    color = Color(0xFFD1D5DB)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Code Sample",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "fun magnify() {",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFF10B981)
                )
                Text(
                    text = "    val shader = RuntimeShader(AGSL)",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF60A5FA)
                )
                Text(
                    text = "    // Apply magnification effect",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFF9CA3AF)
                )
                Text(
                    text = "}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFF10B981)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Pattern Grid",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        repeat(8) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                repeat(20) { col ->
                    val isCheckerboard = (row + col) % 2 == 0
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                if (isCheckerboard) Color.Black else Color.White
                            )
                    ) {
                        if (!isCheckerboard) {
                            Text(
                                text = "${(row * 20 + col) % 10}",
                                fontSize = 8.sp,
                                color = Color.Black,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .align(Alignment.Center)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Emoji Collection",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val emojis = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
            "😘", "😗", "😚", "😙", "😋", "😛", "😜", "🤪",
            "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨"
        )

        repeat(4) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(8) { col ->
                    val index = row * 8 + col
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF3F4F6)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emojis[index % emojis.size],
                            fontSize = 20.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

