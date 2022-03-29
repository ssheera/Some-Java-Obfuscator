package com.cheatbreaker.obf.utils.configuration;

public final class NumberConversions {
    private NumberConversions() {
    }

    public static int floor(double num) {
        int floor = (int)num;
        return (double)floor == num ? floor : floor - (int)(Double.doubleToRawLongBits(num) >>> 63);
    }

    public static int ceil(double num) {
        int floor = (int)num;
        return (double)floor == num ? floor : floor + (int)(~Double.doubleToRawLongBits(num) >>> 63);
    }

    public static int round(double num) {
        return floor(num + 0.5D);
    }

    public static double square(double num) {
        return num * num;
    }

    public static int toInt(Object object) {
        if (object instanceof Number) {
            return ((Number)object).intValue();
        } else {
            try {
                return Integer.valueOf(object.toString());
            } catch (NumberFormatException var2) {
            } catch (NullPointerException var3) {
            }

            return 0;
        }
    }

    public static float toFloat(Object object) {
        if (object instanceof Number) {
            return ((Number)object).floatValue();
        } else {
            try {
                return Float.valueOf(object.toString());
            } catch (NumberFormatException var2) {
            } catch (NullPointerException var3) {
            }

            return 0.0F;
        }
    }

    public static double toDouble(Object object) {
        if (object instanceof Number) {
            return ((Number)object).doubleValue();
        } else {
            try {
                return Double.valueOf(object.toString());
            } catch (NumberFormatException var2) {
            } catch (NullPointerException var3) {
            }

            return 0.0D;
        }
    }

    public static long toLong(Object object) {
        if (object instanceof Number) {
            return ((Number)object).longValue();
        } else {
            try {
                return Long.valueOf(object.toString());
            } catch (NumberFormatException var2) {
            } catch (NullPointerException var3) {
            }

            return 0L;
        }
    }

    public static short toShort(Object object) {
        if (object instanceof Number) {
            return ((Number)object).shortValue();
        } else {
            try {
                return Short.valueOf(object.toString());
            } catch (NumberFormatException var2) {
            } catch (NullPointerException var3) {
            }

            return 0;
        }
    }

    public static byte toByte(Object object) {
        if (object instanceof Number) {
            return ((Number)object).byteValue();
        } else {
            try {
                return Byte.valueOf(object.toString());
            } catch (NumberFormatException var2) {
            } catch (NullPointerException var3) {
            }

            return 0;
        }
    }

    public static boolean isFinite(double d) {
        return Math.abs(d) <= 1.7976931348623157E308D;
    }

    public static boolean isFinite(float f) {
        return Math.abs(f) <= 3.4028235E38F;
    }

    public static void checkFinite(double d, String message) {
        if (!isFinite(d)) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void checkFinite(float d, String message) {
        if (!isFinite(d)) {
            throw new IllegalArgumentException(message);
        }
    }
}