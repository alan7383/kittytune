export const codeSnippets: Record<string, string> = {
  '8d': `
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> rotationSpeed = (<span style="color: #d19a66;">0.000002</span> + <span class="code-highlight" id="code-speed-val" style="background: rgba(208, 188, 255, 0.15); color: #d0bcff;">0.50f</span> * <span style="color: #d19a66;">0.000038</span>)
<span style="color: #7f848e;">
time += rotationSpeed
<span style="color: #c678dd;">val</span> pan = sin(time) 
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> leftVol = (<span style="color: #d19a66;">1.0</span> - pan) / <span style="color: #d19a66;">2.0</span>
<span style="color: #c678dd;">val</span> rightVol = (<span style="color: #d19a66;">1.0</span> + pan) / <span style="color: #d19a66;">2.0</span>
<span style="color: #c678dd;">val</span> newLeft = (originalLeft * leftVol).toInt().toShort()
<span style="color: #c678dd;">val</span> newRight = (originalRight * rightVol).toInt().toShort()`,
  'biquad': `
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> A = Math.pow(<span style="color: #d19a66;">10.0</span>, gain / <span style="color: #d19a66;">40.0</span>).toFloat()
<span style="color: #c678dd;">val</span> w0 = (<span style="color: #d19a66;">2.0</span> * PI * <span style="color: #d19a66;">100f</span> / sampleRate).toFloat()
<span style="color: #c678dd;">val</span> alpha = sin(w0) / <span style="color: #d19a66;">2f</span> * Math.sqrt(((A + <span style="color: #d19a66;">1f</span>/A)*(<span style="color: #d19a66;">1f</span>/S - <span style="color: #d19a66;">1f</span>)+<span style="color: #d19a66;">2f</span>).toDouble()).toFloat()
<span style="color: #c678dd;">val</span> a0 = (A + <span style="color: #d19a66;">1f</span>) + (A - <span style="color: #d19a66;">1f</span>) * cos(w0) + beta
<span style="color: #c678dd;">val</span> b0 = (A * ((A + <span style="color: #d19a66;">1f</span>) - (A - <span style="color: #d19a66;">1f</span>) * cos(w0) + beta)) / a0
<span style="color: #7f848e;">
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> y = b0*x + b1*x1 + b2*x2 - a1*y1 - a2*y2
<span style="color: #7f848e;">
x2 = x1; x1 = x
y2 = y1; y1 = y
<span style="color: #7f848e;">
output = y.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()`,
  'reverb': `
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> delayedSample = buffer[cursor]
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> outputSample = (inputSample + delayedSample * decay)
    .toInt()
    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    .toShort()
<span style="color: #7f848e;">
buffer[cursor] = outputSample
<span style="color: #7f848e;">
cursor++
<span style="color: #c678dd;">if</span> (cursor >= buffer.size) cursor = <span style="color: #d19a66;">0</span>`,
  'muffled': `
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> w0 = (<span style="color: #d19a66;">2.0</span> * PI * <span style="color: #d19a66;">300f</span> / sampleRate).toFloat()
<span style="color: #c678dd;">val</span> cosW0 = cos(w0)
<span style="color: #c678dd;">val</span> alpha = sin(w0) / (<span style="color: #d19a66;">2f</span> * <span style="color: #d19a66;">0.5f</span>) <span style="color: #7f848e;">
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> a0 = <span style="color: #d19a66;">1f</span> + alpha
<span style="color: #c678dd;">val</span> b0 = ((<span style="color: #d19a66;">1f</span> - cosW0) / <span style="color: #d19a66;">2f</span>) / a0
<span style="color: #c678dd;">val</span> b1 = (<span style="color: #d19a66;">1f</span> - cosW0) / a0
<span style="color: #7f848e;">
<span style="color: #7f848e;">
<span style="color: #c678dd;">val</span> y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
<span style="color: #7f848e;">
x2 = x1; x1 = x
y2 = y1; y1 = y
output = y.toInt().toShort()`
};
