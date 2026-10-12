import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Selected host-library probes for the unaccepted LMTR documentation review.
 * This is not a JSON parser, runtime serializer, Kotlin/ART test or R3 primitive.
 * Inputs are bounded number tokens already checked by the documentation checker;
 * no recording, device, camera, index or timer is opened or implemented.
 */
public class LmtrJsonLibraryProbe {
    /** Compare exact coefficient/scale spelling without any rounding MathContext. */
    private static String decimalSpelling(String token) {
        // Normalize a bounded lexical exponent first: leading zeros must not make
        // BigDecimal's constructor-specific exponent parser determine wire validity.
        String[] parts = token.split("[eE]", -1);
        String bounded = parts[0] + (parts.length == 2 ? "e" + Integer.parseInt(parts[1]) : "");
        BigDecimal value = new BigDecimal(bounded).stripTrailingZeros();
        if (value.signum() == 0) return "0";
        int adjusted = value.precision() - value.scale() - 1;
        if (adjusted >= -6 && adjusted <= 20) return value.toPlainString();
        String digits = value.unscaledValue().abs().toString();
        return (value.signum() < 0 ? "-" : "") + digits.charAt(0)
                + (digits.length() > 1 ? "." + digits.substring(1) : "") + "e" + adjusted;
    }

    /** Exercise the precise operations that differ from natural String/float use. */
    public static void main(String[] tokens) {
        for (String token : tokens) System.out.println(decimalSpelling(token));
        String bmp = "\ue000";
        String supplementary = "\ud83d\ude00";
        if (bmp.compareTo(supplementary) <= 0
                || Arrays.compare(bmp.codePoints().toArray(), supplementary.codePoints().toArray()) >= 0) {
            throw new AssertionError("UTF-16 ordering must not stand in for scalar ordering");
        }
        if (Float.floatToRawIntBits(Float.intBitsToFloat(0x80000000)) != 0x80000000
                || Double.doubleToRawLongBits(Double.longBitsToDouble(0x8000000000000000L)) != 0x8000000000000000L) {
            throw new AssertionError("Source negative-zero bits changed");
        }
        for (int bits : new int[] {1, 0x7f7fffff}) {
            float original = Float.intBitsToFloat(bits);
            // The widening conversion to double is exact for a finite binary32.
            BigDecimal exact = new BigDecimal((double) original);
            if (Float.floatToRawIntBits(exact.floatValue()) != bits) throw new AssertionError("f32 roundtrip");
        }
        for (long bits : new long[] {1L, 0x7fefffffffffffffL}) {
            BigDecimal exact = new BigDecimal(Double.longBitsToDouble(bits));
            if (Double.doubleToRawLongBits(exact.doubleValue()) != bits) throw new AssertionError("f64 roundtrip");
        }
        System.out.println("scalar_order_and_exact_float_bits_pass");
    }
}
