package com.moneytracker.app.growth

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.moneytracker.app.data.model.TransactionCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * 9:16 High-contrast, dark-mode Fintech Wrapped Composable Card.
 * Designed for 1080x1920 logical canvas rendering and social sharing.
 * Strictly adheres to Privacy Guard (no account numbers, card digits, or raw bank balances).
 */
@Composable
fun FintechWrappedCard(
    data: FintechWrappedData,
    modifier: Modifier = Modifier,
    graphicsLayer: GraphicsLayer? = null,
) {
    val cardModifier = if (graphicsLayer != null) {
        modifier
            .aspectRatio(9f / 16f)
            .drawWithContent {
                graphicsLayer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(graphicsLayer)
            }
    } else {
        modifier.aspectRatio(9f / 16f)
    }

    Box(
        modifier = cardModifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B0F19),
                        Color(0xFF111827),
                        Color(0xFF030712),
                    ),
                ),
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFF38BDF8).copy(alpha = 0.4f),
                        Color(0xFF818CF8).copy(alpha = 0.2f),
                        Color(0xFF1E293B),
                    ),
                ),
                shape = RoundedCornerShape(28.dp),
            )
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header: Period Pill & App Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                ) {
                    Text(
                        text = "FINTECH WRAPPED",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }

                Text(
                    text = data.periodLabel,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hero Section: Persona Emoji & Title
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF38BDF8).copy(alpha = 0.35f),
                                    Color(0xFF1E293B),
                                ),
                            ),
                        )
                        .border(2.dp, Color(0xFF38BDF8).copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = data.persona.emoji,
                        fontSize = 40.sp,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = data.persona.title,
                    color = Color(0xFFF8FAFC),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp,
                )

                Text(
                    text = data.persona.tagLine,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Punchline Callout
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.8f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (data.isAIGeneratedPunchline) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Nano",
                                tint = Color(0xFFA855F7),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "\"${data.persona.punchline}\"",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Key Metrics: Savings Rate & Weekend Pace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Metric 1: Net Savings Rate
                Surface(
                    color = Color(0xFF1E293B).copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Net Savings Rate",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val savingsFormatted = String.format(Locale.US, "%.1f%%", data.netSavingsRate)
                        Text(
                            text = if (data.netSavingsRate > 0) "+$savingsFormatted" else savingsFormatted,
                            color = if (data.netSavingsRate >= 20.0) Color(0xFF10B981) else Color(0xFF38BDF8),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Metric 2: Weekend Velocity
                Surface(
                    color = Color(0xFF1E293B).copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Weekend Pace",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val ratioFormatted = String.format(Locale.US, "%.1fx", data.weekendVelocityRatio)
                        Text(
                            text = "$ratioFormatted vs wkdays",
                            color = Color(0xFFFBBF24),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Breakdown Horizontal Bars
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Top Spending Vectors",
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (data.topCategories.isEmpty()) {
                    Text(
                        text = "No recorded debits for this period",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                    )
                } else {
                    data.topCategories.forEach { categoryShare ->
                        CategoryBarRow(categoryShare)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            // Top Merchant (Sanitized)
            data.topMerchant?.let { topM ->
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Primary Destination",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                        )
                        Text(
                            text = topM.merchant,
                            color = Color(0xFFF1F5F9),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Watermark Footer: Privacy Guard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Private",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(13.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Tracked privately on-device with Money Tracker",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun CategoryBarRow(categoryShare: WrappedCategoryShare) {
    val barColor = when (categoryShare.category) {
        TransactionCategory.FOOD -> Color(0xFFF97316)
        TransactionCategory.SHOPPING -> Color(0xFFA855F7)
        TransactionCategory.TRAVEL -> Color(0xFF38BDF8)
        TransactionCategory.BILLS -> Color(0xFFEAB308)
        TransactionCategory.SUBSCRIPTION -> Color(0xFF6366F1)
        TransactionCategory.HEALTH -> Color(0xFF10B981)
        else -> Color(0xFF64748B)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = categoryShare.category.name.lowercase().replaceFirstChar { it.titlecase() },
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = String.format(Locale.US, "%.1f%%", categoryShare.percentageOfSpend),
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1E293B)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((categoryShare.percentageOfSpend / 100f).toFloat().coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor),
            )
        }
    }
}

/**
 * Exporter and sharing helper for Fintech Wrapped story cards.
 */
object FintechWrappedShareHelper {

    private const val SHARE_IMAGE_FILENAME = "images/wrapped_share.png"

    /**
     * Converts a Composable snapshot GraphicsLayer into a PNG bitmap and saves to cache.
     */
    suspend fun saveGraphicsLayerToShareFile(
        context: Context,
        graphicsLayer: GraphicsLayer,
    ): File = withContext(Dispatchers.IO) {
        val imageBitmap = graphicsLayer.toImageBitmap()
        val androidBitmap = imageBitmap.asAndroidBitmap()
        saveBitmapToShareFile(context, androidBitmap)
    }

    /**
     * Saves a bitmap directly to the app cache directory under images/wrapped_share.png
     */
    fun saveBitmapToShareFile(context: Context, bitmap: Bitmap): File {
        val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
        val shareFile = File(imagesDir, "wrapped_share.png")
        FileOutputStream(shareFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        return shareFile
    }

    /**
     * Renders a 1080x1920 high-resolution bitmap deterministically off-screen using Android Canvas.
     * Perfect for air-gapped or background generation without an active UI window.
     */
    fun renderOffscreenStoryBitmap(data: FintechWrappedData, width: Int = 1080, height: Int = 1920): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background dark gradient
        val bgPaint = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(0xFF0B0F19.toInt(), 0xFF111827.toInt(), 0xFF030712.toInt()),
                null,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle decorative accent circle top-right
        val accentPaint = Paint().apply {
            color = 0x2238BDF8.toInt()
            isAntiAlias = true
        }
        canvas.drawCircle(width * 0.85f, height * 0.15f, 240f, accentPaint)

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = 0xFFF8FAFC.toInt()
        }

        // Header: FINTECH WRAPPED
        val headerPillPaint = Paint().apply {
            color = 0xFF1E293B.toInt()
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(80f, 100f, 420f, 160f), 20f, 20f, headerPillPaint)

        textPaint.apply {
            color = 0xFF38BDF8.toInt()
            textSize = 34f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText("FINTECH WRAPPED", 110f, 143f, textPaint)

        // Month
        textPaint.apply {
            color = 0xFF94A3B8.toInt()
            textSize = 38f
            typeface = android.graphics.Typeface.DEFAULT
        }
        canvas.drawText(data.periodLabel, width - 400f, 143f, textPaint)

        // Emoji Circle
        val circlePaint = Paint().apply {
            color = 0xFF1E293B.toInt()
            isAntiAlias = true
        }
        canvas.drawCircle(width / 2f, 380f, 120f, circlePaint)
        val circleStroke = Paint().apply {
            color = 0xFF38BDF8.toInt()
            strokeWidth = 6f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawCircle(width / 2f, 380f, 120f, circleStroke)

        // Emoji
        textPaint.apply {
            textSize = 100f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(data.persona.emoji, width / 2f, 415f, textPaint)

        // Persona Title
        textPaint.apply {
            color = 0xFFF8FAFC.toInt()
            textSize = 68f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(data.persona.title, width / 2f, 570f, textPaint)

        // Tagline
        textPaint.apply {
            color = 0xFF94A3B8.toInt()
            textSize = 36f
            typeface = android.graphics.Typeface.DEFAULT
        }
        canvas.drawText(data.persona.tagLine, width / 2f, 630f, textPaint)

        // Punchline Box
        val quoteBoxPaint = Paint().apply {
            color = 0xCC0F172A.toInt()
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(80f, 680f, width - 80f, 820f), 30f, 30f, quoteBoxPaint)

        textPaint.apply {
            color = 0xFFE2E8F0.toInt()
            textSize = 34f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("\"${data.persona.punchline}\"", width / 2f, 760f, textPaint)

        // Metrics Row
        val cardPaint = Paint().apply {
            color = 0xBB1E293B.toInt()
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(80f, 860f, width / 2f - 20f, 1040f), 24f, 24f, cardPaint)
        canvas.drawRoundRect(RectF(width / 2f + 20f, 860f, width - 80f, 1040f), 24f, 24f, cardPaint)

        textPaint.apply {
            textAlign = Paint.Align.LEFT
            color = 0xFF94A3B8.toInt()
            textSize = 32f
            typeface = android.graphics.Typeface.DEFAULT
        }
        canvas.drawText("Net Savings Rate", 120f, 920f, textPaint)
        canvas.drawText("Weekend Velocity", width / 2f + 60f, 920f, textPaint)

        val savingsFormatted = String.format(Locale.US, "%.1f%%", data.netSavingsRate)
        textPaint.apply {
            color = if (data.netSavingsRate >= 20.0) 0xFF10B981.toInt() else 0xFF38BDF8.toInt()
            textSize = 58f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText(if (data.netSavingsRate > 0) "+$savingsFormatted" else savingsFormatted, 120f, 995f, textPaint)

        val ratioFormatted = String.format(Locale.US, "%.1fx", data.weekendVelocityRatio)
        textPaint.apply {
            color = 0xFFFBBF24.toInt()
            textSize = 48f
        }
        canvas.drawText("$ratioFormatted pace", width / 2f + 60f, 995f, textPaint)

        // Category Breakdown
        textPaint.apply {
            color = 0xFFF1F5F9.toInt()
            textSize = 42f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText("Top Spending Vectors", 80f, 1120f, textPaint)

        var curY = 1190f
        val barBgPaint = Paint().apply {
            color = 0xFF1E293B.toInt()
            isAntiAlias = true
        }
        for (catShare in data.topCategories) {
            textPaint.apply {
                color = 0xFFCBD5E1.toInt()
                textSize = 34f
                typeface = android.graphics.Typeface.DEFAULT
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText(catShare.category.name.lowercase().replaceFirstChar { it.titlecase() }, 80f, curY, textPaint)

            textPaint.apply {
                color = 0xFF94A3B8.toInt()
                textAlign = Paint.Align.RIGHT
            }
            val pctStr = String.format(Locale.US, "%.1f%%", catShare.percentageOfSpend)
            canvas.drawText(pctStr, width - 80f, curY, textPaint)

            // Progress Bar
            canvas.drawRoundRect(RectF(80f, curY + 16f, width - 80f, curY + 36f), 10f, 10f, barBgPaint)

            val fillWidth = ((width - 160f) * (catShare.percentageOfSpend / 100.0)).toFloat().coerceIn(10f, width - 160f)
            val fillPaint = Paint().apply {
                color = when (catShare.category) {
                    TransactionCategory.FOOD -> 0xFFF97316.toInt()
                    TransactionCategory.SHOPPING -> 0xFFA855F7.toInt()
                    TransactionCategory.TRAVEL -> 0xFF38BDF8.toInt()
                    TransactionCategory.SUBSCRIPTION -> 0xFF6366F1.toInt()
                    TransactionCategory.HEALTH -> 0xFF10B981.toInt()
                    TransactionCategory.BILLS -> 0xFFEAB308.toInt()
                    else -> 0xFF64748B.toInt()
                }
                isAntiAlias = true
            }
            canvas.drawRoundRect(RectF(80f, curY + 16f, 80f + fillWidth, curY + 36f), 10f, 10f, fillPaint)

            curY += 85f
        }

        // Top Destination
        data.topMerchant?.let { topM ->
            val destBoxPaint = Paint().apply {
                color = 0xAA0F172A.toInt()
                isAntiAlias = true
            }
            canvas.drawRoundRect(RectF(80f, 1540f, width - 80f, 1640f), 20f, 20f, destBoxPaint)

            textPaint.apply {
                color = 0xFF94A3B8.toInt()
                textSize = 32f
                textAlign = Paint.Align.LEFT
                typeface = android.graphics.Typeface.DEFAULT
            }
            canvas.drawText("Primary Destination", 120f, 1600f, textPaint)

            textPaint.apply {
                color = 0xFFF8FAFC.toInt()
                textSize = 34f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(topM.merchant, width - 120f, 1600f, textPaint)
        }

        // Privacy Watermark
        textPaint.apply {
            color = 0xFF64748B.toInt()
            textSize = 30f
            typeface = android.graphics.Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("🔒 Tracked privately on-device with Money Tracker", width / 2f, 1820f, textPaint)

        return bitmap
    }

    /**
     * Dispatches the standard Android Share Intent targeting WhatsApp, Instagram Stories, etc.
     */
    fun dispatchShareIntent(context: Context, imageFile: File, chooserTitle: String = "Share Your Fintech Wrapped") {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile,
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Here's my monthly Fintech Wrapped! 🚀 #MoneyTracker")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
