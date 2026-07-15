package com.mouthmidi.app

class SmoothingProcessor(

    private var amount: Float = 0f

) {

    private var previous = 0f


    fun setAmount(
        value: Float
    ) {
        amount =
            value.coerceIn(
                0f,
                0.99f
            )
    }


    fun process(
        input: Float
    ): Float {

        val delta =
            kotlin.math.abs(input - previous)

        val adaptiveAmount =
            when {
                delta > 0.25f ->
                    amount * 0.25f

                delta > 0.10f ->
                    amount * 0.50f

                else ->
                    amount
            }

        previous =
            previous +
            (input - previous) *
            (1f - adaptiveAmount)

        return previous
    }


    fun reset() {

        previous = 0f

    }
}
