package com.alananasss.kittytune.ui.recognition

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import org.intellij.lang.annotations.Language

@Language("AGSL")
private const val GLOW_SHADER = """
uniform float2 iResolution;
uniform float iTime;
layout(color) uniform half4 iColor;

float2 hash( float2 p ) {
    p = float2( dot(p,float2(127.1,311.7)), dot(p,float2(269.5,183.3)) );
    return -1.0 + 2.0*fract(sin(p)*43758.5453123);
}
float noise( in float2 p ) {
    const float K1 = 0.366025404; // (sqrt(3)-1)/2
    const float K2 = 0.211324865; // (3-sqrt(3))/6
    float2 i = floor( p + (p.x+p.y)*K1 );
    float2 a = p - i + (i.x+i.y)*K2;
    float2 o = (a.x>a.y) ? float2(1.0,0.0) : float2(0.0,1.0);
    float2 b = a - o + K2;
    float2 c = a - 1.0 + 2.0*K2;
    float3 h = max( 0.5-float3(dot(a,a), dot(b,b), dot(c,c) ), 0.0 );
    float3 n = h*h*h*h*float3( dot(a,hash(i+0.0)), dot(b,hash(i+o)), dot(c,hash(i+1.0)));
    return dot( n, float3(70.0) );
}

float blurred_noise(in float2 uv, in float timeFactor) {
    float total = 0.0;
    float2 offset = float2(1.5 / iResolution.x, 1.5 / iResolution.y);

    total += noise(uv + float2(0.0, -timeFactor));
    total += noise(uv + float2(-offset.x, 0.0) + float2(0.0, -timeFactor));
    total += noise(uv + float2(offset.x, 0.0) + float2(0.0, -timeFactor));
    total += noise(uv + float2(0.0, -offset.y) + float2(0.0, -timeFactor));
    total += noise(uv + float2(0.0, offset.y) + float2(0.0, -timeFactor));

    return total / 5.0;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution.xy;
    float y_fade = uv.y;

    float falloff_power = 2.0;
    float vertical_intensity = pow(y_fade, falloff_power);

    float2 noiseCoord = uv * float2(1.2, 0.4); 
    float timeFactor = iTime * 0.35; 

    float raw_noise = blurred_noise(noiseCoord, timeFactor);
    float norm_noise = raw_noise * 0.5 + 0.5;

    float min_brightness = 0.25;
    float remapped_noise = min_brightness + (norm_noise * (1.0 - min_brightness));

    float intensity = remapped_noise * vertical_intensity;
    intensity = clamp(intensity, 0.0, 1.0);

    half4 color = iColor;
    return half4(color.rgb * intensity, color.a * intensity);
}
"""

@Composable
fun GlowView(
    modifier: Modifier = Modifier,
    color: Color
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val time = produceState(0f) {
            while (true) {
                withInfiniteAnimationFrameMillis {
                    value = it / 1000f
                }
            }
        }

        Canvas(modifier = modifier) {
            val shader = RuntimeShader(GLOW_SHADER)
            shader.setFloatUniform("iResolution", size.width, size.height)
            shader.setFloatUniform("iTime", time.value)
            shader.setColorUniform("iColor", color.toArgb())

            drawRect(brush = ShaderBrush(shader))
        }
    } else {
    }
}
