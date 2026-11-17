package com.alananasss.kittytune.ui.upload

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import kotlin.math.max

class PermaLinkVisualTransformation(
    private val prefix: String,
    private val prefixColor: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder()
        builder.pushStyle(SpanStyle(color = prefixColor))
        builder.append(prefix)
        builder.pop()
        builder.append(text)

        val prefixLength = prefix.length
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset + prefixLength
            override fun transformedToOriginal(offset: Int): Int = max(0, offset - prefixLength)
        }

        return TransformedText(builder.toAnnotatedString(), offsetMapping)
    }
}
