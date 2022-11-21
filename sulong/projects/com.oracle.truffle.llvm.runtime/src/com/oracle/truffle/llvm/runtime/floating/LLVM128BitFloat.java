package com.oracle.truffle.llvm.runtime.floating;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;

public final class LLVM128BitFloat extends LLVMInternalTruffleObject {

    private static final int BIT_TO_HEX_FACTOR = 4;
    public static final long SIGN_BIT = (long) (1 << 15);
    private static final int EXPONENT_BIT_WIDTH = 15;
    private static final int FRACTION_BIT_WIDTH = 112;
    public static final long EXPONENT_MASK = 0b111111111111111; // 15 bit
    private static final int HEX_DIGITS_FRACTION = FRACTION_BIT_WIDTH / BIT_TO_HEX_FACTOR;
    private static final LLVM128BitFloat POSITIVE_INFINITY = LLVM128BitFloat.fromRawValues(false, EXPONENT_MASK, bit(112L));
    private static final LLVM128BitFloat NEGATIVE_INFINITY = LLVM128BitFloat.fromRawValues(true, EXPONENT_MASK, bit(112L));
    private static final LLVM128BitFloat POSITIVE_ZERO = LLVM128BitFloat.fromRawValues(false, 0, 0);
    private static final LLVM128BitFloat NEGATIVE_ZERO = LLVM128BitFloat.fromRawValues(true, 0, 0);
    private static final int EXPONENT_BIAS = 16383;
    private static final int FLOAT_EXPONENT_BIAS = 127;


    @Override
    public String toString() {
        return toLLVMString(this);
    }

    @CompilerDirectives.TruffleBoundary
    public String toDebugString() {
        return String.format("sign: %s\nexponent: %s\nfraction: %s %s\n", getSign(), getBinaryString(EXPONENT_BIT_WIDTH, getExponent()), getBinaryString(FRACTION_BIT_WIDTH, getFraction()),
                getHexString(HEX_DIGITS_FRACTION, getFraction()));
    }

    @CompilerDirectives.TruffleBoundary
    public static String toLLVMString(LLVM128BitFloat value) {
        /*if (value.isQNaN()) {
            return "QNaN";

        } else if (value.isSNaN()) {
            return "SNaN";

        } else*/ if (value.isInfinity()) {
            return "INF";

        } else {
            long exponent = value.getExponent();
            if (value.getSign()) {
                exponent |= (1 << EXPONENT_BIT_WIDTH);
            }
            long fraction = value.getFraction();
            return String.format("0xK%4x%16x", exponent, fraction).replace(' ', '0');
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static String getBinaryString(int bitWidth, long number) {
        return String.format("%" + bitWidth + "s", Long.toBinaryString(number)).replace(" ", "0");
    }

    @CompilerDirectives.TruffleBoundary
    private static String getHexString(int bitWidth, long number) {
        return String.format("%" + bitWidth + "x", number).replace(" ", "0");
    }


    private static final int FP128_EXPONENT_BIAS = 16383;
    private static final int DOUBLE_EXPONENT_BIAS = 1023;

    private final long expSignFraction; // 64 bit -- the left over of the fraction goes into here.
    private final long fraction; // 64 bit -- fill this part first.

    public LLVM128BitFloat(long expSignFraction, long fraction) {
        this.expSignFraction = expSignFraction;
        this.fraction = fraction;
    }

    private LLVM128BitFloat(LLVM128BitFloat value) {
        this.expSignFraction = value.expSignFraction;
        this.fraction = value.fraction;
    }

    public static LLVM128BitFloat fromRawValues(boolean sign, long exponentFraction, long fraction) {
        assert (exponentFraction & 0x7FFFF) == exponentFraction;
        long expSignFraction = exponentFraction;
        if (sign) {
            expSignFraction |= SIGN_BIT;
        }
        return new LLVM128BitFloat(expSignFraction, fraction);
    }

    public long getExponent() {
        return (long) (expSignFraction & EXPONENT_MASK);
    }

    public long getFraction() {
        return fraction;
    }

    public long getExpSign() {
        return expSignFraction;
    }

    public boolean getSign() {
        return (expSignFraction & SIGN_BIT) != 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LLVM128BitFloat)) {
            return false;
        }
        LLVM128BitFloat other = ((LLVM128BitFloat) obj);
        return this.expSignFraction == other.expSignFraction && this.fraction == other.fraction;
    }

    public boolean isPositiveInfinity() {
        return POSITIVE_INFINITY.equals(this);
    }

    public boolean isNegativeInfinity() {
        return NEGATIVE_INFINITY.equals(this);
    }

    public boolean isInfinity() {
        return isPositiveInfinity() || isNegativeInfinity();
    }

    public static LLVM128BitFloat createPositiveZero() {
        if (CompilerDirectives.inCompiledCode()) {
            return LLVM128BitFloat.fromRawValues(false, 0, 0);
        } else {
            return POSITIVE_ZERO;
        }
    }

    public static long bit(long i) {
        return 1L << i;
    }

    public static LLVM128BitFloat fromLong(long val) {
        if (val == 0) {
            return createPositiveZero();
        }
        boolean sign = val < 0;
        return fromLong(Math.abs(val), sign);
    }

    private static LLVM128BitFloat fromLong(long val, boolean sign) {
        //int leadingOnePosition = Long.SIZE - Long.numberOfLeadingZeros(val);
        int exponent = EXPONENT_BIAS + Long.SIZE;
        long fractionMask;
        if (leadingOnePosition == Long.SIZE || leadingOnePosition == Long.SIZE - 1) {
            fractionMask = 0xffffffff;
        } else {
            fractionMask = (1L << Long.SIZE + 1) - 1;
        }
        long maskedFractionValue = val & fractionMask;
        long fraction = maskedFractionValue << (Long.SIZE - leadingOnePosition);
        return LLVM128BitFloat.fromRawValues(sign, exponent, fraction);
    }

    public static LLVM128BitFloat fromDouble(double val) {
        boolean sign = val < 0;
        if (DoubleHelper.isPositiveZero(val)) {
            return new LLVM128BitFloat(POSITIVE_ZERO);
        } else if (DoubleHelper.isNegativeZero(val)) {
            return new LLVM128BitFloat(NEGATIVE_ZERO);
        } else {
            long rawValue = Double.doubleToRawLongBits(val);
            int doubleExponent = DoubleHelper.getUnbiasedExponent(val);
            int biasedExponent = doubleExponent + EXPONENT_BIAS;
            long doubleFraction = rawValue & DoubleHelper.FRACTION_MASK;
            long fraction = doubleFraction << (FRACTION_BIT_WIDTH - DoubleHelper.DOUBLE_FRACTION_BIT_WIDTH);
            return LLVM128BitFloat.fromRawValues(sign, biasedExponent, fraction);
        }
    }


}
