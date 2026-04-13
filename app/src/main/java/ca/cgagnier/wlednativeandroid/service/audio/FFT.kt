package ca.cgagnier.wlednativeandroid.service.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Radix-2 Cooley-Tukey FFT implementation.
 * Input size must be a power of 2.
 */
object FFT {

    fun compute(real: FloatArray, imag: FloatArray) {
        val n = real.size
        require(n == imag.size) { "Real and imaginary arrays must have the same size" }
        require(n > 0 && n and (n - 1) == 0) { "Size must be a power of 2" }

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var temp = real[i]
                real[i] = real[j]
                real[j] = temp
                temp = imag[i]
                imag[i] = imag[j]
                imag[j] = temp
            }
        }

        // Cooley-Tukey butterfly
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wReal = cos(angle).toFloat()
            val wImag = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var curReal = 1.0f
                var curImag = 0.0f
                for (k in 0 until len / 2) {
                    val u = i + k
                    val v = u + len / 2
                    val tReal = curReal * real[v] - curImag * imag[v]
                    val tImag = curReal * imag[v] + curImag * real[v]
                    real[v] = real[u] - tReal
                    imag[v] = imag[u] - tImag
                    real[u] = real[u] + tReal
                    imag[u] = imag[u] + tImag
                    val newCurReal = curReal * wReal - curImag * wImag
                    curImag = curReal * wImag + curImag * wReal
                    curReal = newCurReal
                }
                i += len
            }
            len = len shl 1
        }
    }
}
