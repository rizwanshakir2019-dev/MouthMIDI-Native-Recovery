package com.mouthmidi.app

class AttackReleaseProcessor {

    private var previous = 0f

    fun process(
        input: Float,
        attackAmount: Float,
        releaseAmount: Float
    ): Float {

        // Complete bypass at center
        if (attackAmount == 0f && releaseAmount == 0f) {
            previous = input
            return input
        }

        val rising = input > previous
        val falling = input < previous

        var output = input


        if (rising) {

            output =
                when {

                    // Attack negative = smoothing only
                    attackAmount < 0f -> {

                        val smoothFactor =
                            (0.20f + attackAmount * 0.17f)
                                .coerceIn(0.03f, 0.20f)

                        previous +
                                (input - previous) * smoothFactor
                    }


                    // Attack positive = acceleration only
                    attackAmount > 0f -> {

                        val jumpFactor =
                            (1f + attackAmount * 4f)
                                .coerceIn(1f, 5f)

                        previous +
                                (input - previous) * jumpFactor
                    }


                    else -> input
                }

        }


        else if (falling) {

            output =
                when {

                    // Release negative = smoothing only
                    releaseAmount < 0f -> {

                        val smoothFactor =
                            (0.20f + releaseAmount * 0.17f)
                                .coerceIn(0.03f, 0.20f)

                        previous +
                                (input - previous) * smoothFactor
                    }


                    // Release positive = acceleration only
                    releaseAmount > 0f -> {

                        val jumpFactor =
                            (1f + releaseAmount * 4f)
                                .coerceIn(1f, 5f)

                        previous +
                                (input - previous) * jumpFactor
                    }


                    else -> input
                }

        }


        // Option B: prevent MIDI range overshoot only
        output =
            output.coerceIn(0f, 1f)


        previous = output

        return output
    }


    fun reset() {
        previous = 0f
    }
}
